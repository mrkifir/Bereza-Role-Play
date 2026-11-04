package ua.bereza.launcher.server

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ua.bereza.launcher.model.ServerEndpoint

sealed interface ServerStatus {
    data object Unknown : ServerStatus
    data object Loading : ServerStatus
    data class Online(
        val info: SampQuery.Info,
        val pingMs: Long,
        val version: String?,
        val players: List<SampQuery.Player>?,
        val checkedAt: Long,
    ) : ServerStatus
    data class Offline(val reason: String, val checkedAt: Long) : ServerStatus
}

class ServerMonitor(private val endpoint: ServerEndpoint) {
    val address: String get() = "${endpoint.host}:${endpoint.port}"

    suspend fun query(withPlayers: Boolean = true): ServerStatus = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        try {
            val (raw, ping) = SampQuery.request(endpoint.host, endpoint.port, 'i')
            val info = SampQuery.parseInfo(raw)
            val version = runCatching {
                SampQuery.parseRules(SampQuery.request(endpoint.host, endpoint.port, 'r', attempts = 1).first)["version"]
            }.getOrNull()
            // SA-MP віддає список гравців лише якщо їх ≤ 100.
            val players = if (withPlayers && info.players in 1..100) runCatching {
                SampQuery.parsePlayers(SampQuery.request(endpoint.host, endpoint.port, 'c', attempts = 1).first)
            }.getOrNull() else if (info.players == 0) emptyList() else null
            ServerStatus.Online(info, ping, version, players, now)
        } catch (e: java.net.SocketTimeoutException) {
            ServerStatus.Offline("Сервер не відповідає", now)
        } catch (e: java.net.UnknownHostException) {
            ServerStatus.Offline("Немає інтернету", now)
        } catch (e: Exception) {
            ServerStatus.Offline(e.message ?: "Помилка запиту", now)
        }
    }
}
