package ua.bereza.launcher.server

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.Request
import ua.bereza.launcher.core.AppJson
import ua.bereza.launcher.core.AppLog
import ua.bereza.launcher.core.Http
import ua.bereza.launcher.data.model.ServerConfig
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset

sealed interface ServerState {
    /** Адресу сервера не налаштовано. */
    data object NotConfigured : ServerState
    data object Loading : ServerState
    data class Online(
        val hostname: String,
        val gamemode: String,
        val language: String,
        val players: Int,
        val maxPlayers: Int,
        val passworded: Boolean,
        val pingMs: Long,
        val source: String,
    ) : ServerState
    /** Сервер не відповів — показуємо «немає даних», а не вигадане число. */
    data class Unreachable(val reason: String) : ServerState
}

/**
 * Реальний статус сервера.
 * 1) Якщо в конфігурації задано statusApiUrl — HTTP JSON {"online":true,"players":12,"maxPlayers":500,"hostname":"..."}.
 * 2) Інакше — стандартний SA-MP query-протокол (UDP, опкод 'i'), який підтримує будь-який SA-MP/open.mp сервер.
 */
class ServerStatusRepository {

    suspend fun query(server: ServerConfig, statusApiUrl: String): ServerState = withContext(Dispatchers.IO) {
        if (server.isPlaceholder) return@withContext ServerState.NotConfigured
        if (statusApiUrl.isNotBlank()) {
            runCatching { return@withContext httpStatus(statusApiUrl) }
                .onFailure { AppLog.w("Status API недоступний, пробую SA-MP query", it) }
        }
        sampQuery(server.host, server.port)
    }

    @Serializable
    private data class ApiStatus(
        val online: Boolean = false,
        val players: Int = 0,
        val maxPlayers: Int = 0,
        val hostname: String = "",
        val gamemode: String = "",
        val language: String = "",
    )

    private fun httpStatus(url: String): ServerState {
        val started = System.nanoTime()
        val body = Http.client.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            r.body?.string().orEmpty()
        }
        val s = AppJson.decodeFromString(ApiStatus.serializer(), body)
        val ping = (System.nanoTime() - started) / 1_000_000
        return if (s.online) ServerState.Online(s.hostname, s.gamemode, s.language, s.players, s.maxPlayers, false, ping, "API")
        else ServerState.Unreachable("Сервер вимкнено")
    }

    private fun sampQuery(host: String, port: Int): ServerState {
        val addr: Inet4Address = try {
            InetAddress.getAllByName(host).filterIsInstance<Inet4Address>().firstOrNull()
                ?: return ServerState.Unreachable("Немає IPv4-адреси для $host")
        } catch (e: Exception) {
            return ServerState.Unreachable("Не вдалося знайти адресу $host")
        }
        val header = ByteBuffer.allocate(11).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("SAMP".toByteArray(Charsets.US_ASCII))
            put(addr.address)
            putShort(port.toShort())
            put('i'.code.toByte())
        }.array()

        DatagramSocket().use { socket ->
            socket.soTimeout = 1500
            repeat(3) { attempt ->
                try {
                    val start = System.nanoTime()
                    socket.send(DatagramPacket(header, header.size, addr, port))
                    val buf = ByteArray(2048)
                    val packet = DatagramPacket(buf, buf.size)
                    socket.receive(packet)
                    val ping = (System.nanoTime() - start) / 1_000_000
                    return parseInfo(buf, packet.length, ping)
                        ?: ServerState.Unreachable("Некоректна відповідь сервера")
                } catch (_: SocketTimeoutException) {
                    AppLog.w("SA-MP query: тайм-аут (спроба ${attempt + 1})")
                } catch (e: Exception) {
                    return ServerState.Unreachable("Помилка мережі: ${e.message}")
                }
            }
        }
        return ServerState.Unreachable("Сервер не відповідає")
    }

    private fun parseInfo(buf: ByteArray, len: Int, ping: Long): ServerState? {
        if (len < 11 + 1 + 2 + 2 + 4) return null
        if (String(buf, 0, 4, Charsets.US_ASCII) != "SAMP" || buf[10] != 'i'.code.toByte()) return null
        val bb = ByteBuffer.wrap(buf, 11, len - 11).order(ByteOrder.LITTLE_ENDIAN)
        return try {
            val password = bb.get().toInt() != 0
            val players = bb.short.toInt() and 0xFFFF
            val max = bb.short.toInt() and 0xFFFF
            val hostname = readStr(bb)
            val gamemode = readStr(bb)
            val language = readStr(bb)
            ServerState.Online(hostname, gamemode, language, players, max, password, ping, "SA-MP query")
        } catch (_: Exception) {
            null
        }
    }

    private fun readStr(bb: ByteBuffer): String {
        val n = bb.int
        if (n < 0 || n > bb.remaining()) error("bad length")
        val bytes = ByteArray(n)
        bb.get(bytes)
        return String(bytes, CP1251)
    }

    private companion object {
        val CP1251: Charset = runCatching { Charset.forName("windows-1251") }.getOrDefault(Charsets.ISO_8859_1)
    }
}
