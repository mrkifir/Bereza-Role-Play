package ua.bereza.launcher.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset

/** Дані, які сервер SA-MP / open.mp повертає на запит 'i'. */
data class SampServerInfo(
    val passworded: Boolean,
    val players: Int,
    val maxPlayers: Int,
    val hostname: String,
    val gamemode: String,
    val language: String,
    val pingMs: Long,
)

enum class QueryErrorKind { DNS, NO_IPV4, TIMEOUT, MALFORMED, NETWORK }

class SampQueryException(val kind: QueryErrorKind, message: String, cause: Throwable? = null) :
    Exception(message, cause)

/** Джерело статусу сервера. Інша реалізація (наприклад, HTTP API) може замінити UDP-запит. */
interface ServerStatusSource {
    suspend fun queryInfo(host: String, port: Int): SampServerInfo
    suspend fun queryRules(host: String, port: Int): Map<String, String>
}

/**
 * Реалізація документованого SA-MP Query протоколу (UDP):
 * запит = "SAMP" + 4 байти IPv4 + 2 байти порту (little-endian) + опкод.
 * 'i' — інформація, 'r' — правила (version, weburl, mapname...).
 * Рядки сервера декодуються як windows-1251 (кирилиця у SA-MP).
 */
class SampQueryClient(
    private val timeoutMs: Int = 2500,
    private val attempts: Int = 2,
) : ServerStatusSource {

    private val charset: Charset =
        runCatching { Charset.forName("windows-1251") }.getOrDefault(Charsets.ISO_8859_1)

    override suspend fun queryInfo(host: String, port: Int): SampServerInfo =
        withContext(Dispatchers.IO) {
            val (buffer, ping) = exchange(host, port, OPCODE_INFO)
            parseInfo(buffer, ping)
        }

    override suspend fun queryRules(host: String, port: Int): Map<String, String> =
        withContext(Dispatchers.IO) {
            val (buffer, _) = exchange(host, port, OPCODE_RULES)
            parseRules(buffer)
        }

    private fun resolveIpv4(host: String): InetAddress {
        val all = try {
            InetAddress.getAllByName(host)
        } catch (e: UnknownHostException) {
            throw SampQueryException(QueryErrorKind.DNS, "Cannot resolve $host", e)
        } catch (e: SecurityException) {
            throw SampQueryException(QueryErrorKind.NETWORK, "No network permission", e)
        }
        return all.firstOrNull { it is Inet4Address }
            ?: throw SampQueryException(QueryErrorKind.NO_IPV4, "No IPv4 address for $host")
    }

    private fun exchange(host: String, port: Int, opcode: Byte): Pair<ByteBuffer, Long> {
        val address = resolveIpv4(host)
        val request = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(MAGIC)
            put(address.address)
            putShort(port.toShort())
            put(opcode)
        }.array()

        var lastError: Exception? = null
        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = timeoutMs
                repeat(attempts) {
                    try {
                        val start = System.nanoTime()
                        socket.send(DatagramPacket(request, request.size, address, port))
                        val receiveBuffer = ByteArray(MAX_PACKET)
                        val packet = DatagramPacket(receiveBuffer, receiveBuffer.size)
                        while (true) {
                            socket.receive(packet)
                            if (packet.length >= HEADER_SIZE && hasValidHeader(receiveBuffer, opcode)) {
                                val pingMs = (System.nanoTime() - start) / 1_000_000
                                val buffer = ByteBuffer.wrap(receiveBuffer, 0, packet.length)
                                    .order(ByteOrder.LITTLE_ENDIAN)
                                buffer.position(HEADER_SIZE)
                                return buffer to pingMs
                            }
                            // Сторонній пакет — ігноруємо і чекаємо далі (обмежено soTimeout).
                            packet.length = receiveBuffer.size
                        }
                    } catch (e: SocketTimeoutException) {
                        lastError = e
                    }
                }
            }
        } catch (e: SampQueryException) {
            throw e
        } catch (e: java.io.IOException) {
            throw SampQueryException(QueryErrorKind.NETWORK, e.message ?: "I/O error", e)
        }
        throw SampQueryException(QueryErrorKind.TIMEOUT, "No response from $host:$port", lastError)
    }

    private fun hasValidHeader(data: ByteArray, opcode: Byte): Boolean {
        for (i in MAGIC.indices) if (data[i] != MAGIC[i]) return false
        return data[10] == opcode
    }

    private fun parseInfo(b: ByteBuffer, pingMs: Long): SampServerInfo {
        try {
            val passworded = b.get().toInt() != 0
            val players = b.short.toInt() and 0xFFFF
            val maxPlayers = b.short.toInt() and 0xFFFF
            val hostname = readString32(b)
            val gamemode = readString32(b)
            val language = if (b.remaining() >= 4) readString32(b) else ""
            if (maxPlayers > MAX_SANE_PLAYERS || players > maxPlayers) {
                throw SampQueryException(QueryErrorKind.MALFORMED, "Invalid players $players/$maxPlayers")
            }
            return SampServerInfo(passworded, players, maxPlayers, hostname, gamemode, language, pingMs)
        } catch (e: BufferUnderflowException) {
            throw SampQueryException(QueryErrorKind.MALFORMED, "Truncated info packet", e)
        }
    }

    private fun parseRules(b: ByteBuffer): Map<String, String> {
        try {
            val count = b.short.toInt() and 0xFFFF
            val result = LinkedHashMap<String, String>()
            repeat(count) {
                val name = readString8(b)
                val value = readString8(b)
                result[name] = value
            }
            return result
        } catch (e: BufferUnderflowException) {
            throw SampQueryException(QueryErrorKind.MALFORMED, "Truncated rules packet", e)
        }
    }

    private fun readString32(b: ByteBuffer): String {
        val length = b.int
        if (length < 0 || length > b.remaining()) {
            throw SampQueryException(QueryErrorKind.MALFORMED, "Bad string length $length")
        }
        val bytes = ByteArray(length)
        b.get(bytes)
        return String(bytes, charset).trim()
    }

    private fun readString8(b: ByteBuffer): String {
        val length = b.get().toInt() and 0xFF
        if (length > b.remaining()) {
            throw SampQueryException(QueryErrorKind.MALFORMED, "Bad string length $length")
        }
        val bytes = ByteArray(length)
        b.get(bytes)
        return String(bytes, charset).trim()
    }

    private companion object {
        val MAGIC = byteArrayOf('S'.code.toByte(), 'A'.code.toByte(), 'M'.code.toByte(), 'P'.code.toByte())
        const val HEADER_SIZE = 11
        const val MAX_PACKET = 4096
        const val MAX_SANE_PLAYERS = 1000
        val OPCODE_INFO: Byte = 'i'.code.toByte()
        val OPCODE_RULES: Byte = 'r'.code.toByte()
    }
}
