package ua.bereza.launcher.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import ua.bereza.launcher.config.AppConfig
import ua.bereza.launcher.diagnostics.DiagnosticsLog
import ua.bereza.launcher.network.NetworkMonitor
import ua.bereza.launcher.network.ServerStatusSource

/** Спільний для всіх екранів стан сервера. Дані — лише з реальної відповіді сервера. */
class ServerMonitor(
    private val source: ServerStatusSource,
    private val network: NetworkMonitor,
) {
    private val mutex = Mutex()
    private val _snapshot = MutableStateFlow(ServerSnapshot())
    val snapshot: StateFlow<ServerSnapshot> = _snapshot.asStateFlow()

    suspend fun refresh(withRules: Boolean = false) {
        if (!mutex.tryLock()) return
        try {
            if (!AppConfig.isServerConfigured) {
                _snapshot.value = ServerSnapshot(state = ServerState.NotConfigured)
                return
            }
            if (!network.isOnline()) {
                _snapshot.update { it.copy(state = ServerState.NoInternet, refreshing = false) }
                return
            }
            _snapshot.update { it.copy(refreshing = true) }
            val host = AppConfig.serverHost
            val port = AppConfig.serverPort
            val newState = try {
                val info = source.queryInfo(host, port)
                ServerState.Online(info, System.currentTimeMillis())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DiagnosticsLog.w("server", "query failed: ${e.message}")
                ServerState.Offline(ErrorMessages.from(e), System.currentTimeMillis())
            }
            var rules = _snapshot.value.rules
            if (withRules && newState is ServerState.Online) {
                rules = try {
                    source.queryRules(host, port)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    DiagnosticsLog.w("server", "rules failed: ${e.message}")
                    emptyMap()
                }
            }
            _snapshot.value = ServerSnapshot(state = newState, refreshing = false, rules = rules)
        } finally {
            _snapshot.update { it.copy(refreshing = false) }
            mutex.unlock()
        }
    }
}
