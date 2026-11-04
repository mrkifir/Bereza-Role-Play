package ua.bereza.launcher.server

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset

/**
 * Клієнт офіційного UDP query-протоколу SA-MP
 * (пакет "SAMP" + IPv4 + порт LE + опкод: 'i' інфо, 'r' правила, 'c' гравці, 'p' пінг).
 * Рядки сервер віддає в CP1251 — саме так їх і декодуємо.
 */
object SampQuery {
    private val CP1251: Charset = Charset.forName("windows-1251")

    data class Info(
        val passworded: Boolean,
        val players: Int,
        val maxPlayers: Int,
        val hostname: String,
        val gamemode: String,
        val language: String,
    )

    data class Player(val name: String, val score: Int)

    fun buildPacket(ip: ByteArray, port: Int, opcode: Char, payload: ByteArray = ByteArray(0)): ByteArray {
        require(ip.size == 4) { "Потрібна IPv4-адреса" }
        val buf = ByteBuffer.allocate(11 + payload.size).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("SAMP".toByteArray(Charsets.US_ASCII))
        buf.put(ip)
        buf.putShort(port.toShort())
        buf.put(opcode.code.toByte())
        buf.put(payload)
        return buf.array()
    }

    /** Перевіряє заголовок відповіді та повертає буфер, встановлений після нього. */
    private fun body(data: ByteArray, length: Int, opcode: Char): ByteBuffer {
        if (length < 11) throw QueryException("Закоротка відповідь")
        val head = String(data, 0, 4, Charsets.US_ASCII)
        if (head != "SAMP" || data[10] != opcode.code.toByte()) throw QueryException("Чужа відповідь")
        return ByteBuffer.wrap(data, 11, length - 11).order(ByteOrder.LITTLE_ENDIAN)
    }

    private fun ByteBuffer.str32(): String {
        val len = int
        if (len < 0 || len > remaining()) throw QueryException("Пошкоджений рядок")
        val b = ByteArray(len); get(b); return String(b, CP1251)
    }

    private fun ByteBuffer.str8(): String {
        val len = get().toInt() and 0xFF
        if (len > remaining()) throw QueryException("Пошкоджений рядок")
        val b = ByteArray(len); get(b); return String(b, CP1251)
    }

    fun parseInfo(data: ByteArray, length: Int = data.size): Info {
        try {
            val b = body(data, length, 'i')
            val pass = b.get().toInt() != 0
            val players = b.short.toInt() and 0xFFFF
            val max = b.short.toInt() and 0xFFFF
            return Info(pass, players, max, b.str32(), b.str32(), b.str32())
        } catch (e: java.nio.BufferUnderflowException) {
            throw QueryException("Обрізана відповідь")
        }
    }

    fun parseRules(data: ByteArray, length: Int = data.size): Map<String, String> {
        try {
            val b = body(data, length, 'r')
            val count = b.short.toInt() and 0xFFFF
            return buildMap { repeat(count) { put(b.str8(), b.str8()) } }
        } catch (e: java.nio.BufferUnderflowException) {
            throw QueryException("Обрізана відповідь")
        }
    }

    fun parsePlayers(data: ByteArray, length: Int = data.size): List<Player> {
        try {
            val b = body(data, length, 'c')
            val count = b.short.toInt() and 0xFFFF
            return List(count) { Player(b.str8(), b.int) }
        } catch (e: java.nio.BufferUnderflowException) {
            throw QueryException("Обрізана відповідь")
        }
    }

    class QueryException(msg: String) : Exception(msg)

    /** Блокуючий запит; викликати з Dispatchers.IO. Повертає відповідь і час у мс. */
    fun request(host: String, port: Int, opcode: Char, timeoutMs: Int = 2500, attempts: Int = 2): Pair<ByteArray, Long> {
        val addr = InetAddress.getAllByName(host).firstOrNull { it is Inet4Address }
            ?: throw QueryException("Немає IPv4 для $host")
        val packet = buildPacket(addr.address, port, opcode)
        DatagramSocket().use { socket ->
            socket.soTimeout = timeoutMs
            var last: Exception? = null
            repeat(attempts) {
                try {
                    val start = System.nanoTime()
                    socket.send(DatagramPacket(packet, packet.size, addr, port))
                    val buf = ByteArray(8192)
                    while (true) {
                        val resp = DatagramPacket(buf, buf.size)
                        socket.receive(resp)
                        if (resp.length >= 11 && buf[10] == opcode.code.toByte()) {
                            val ms = (System.nanoTime() - start) / 1_000_000
                            return buf.copyOf(resp.length) to ms
                        }
                    }
                } catch (e: SocketTimeoutException) {
                    last = e
                }
            }
            throw last ?: QueryException("Немає відповіді")
        }
    }
}
