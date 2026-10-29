package ua.bereza.launcher.diagnostics

import android.content.Context
import android.net.Uri
import android.os.Build
import ua.bereza.launcher.config.AppConfig
import ua.bereza.launcher.data.ServerState
import ua.bereza.launcher.data.asString
import ua.bereza.launcher.installer.SafTree
import ua.bereza.launcher.launcher.ClientAvailability
import ua.bereza.launcher.network.NetworkMonitor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Збирає текстовий звіт для передачі розробнику (без особистих даних, окрім нікнейму). */
class DiagnosticsCollector(
    private val context: Context,
    private val network: NetworkMonitor,
) {
    fun buildReport(
        serverState: ServerState,
        client: ClientAvailability,
        gameTreeUri: String?,
        nickname: String,
    ): String = buildString {
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        appendLine("=== Bereza RP Launcher — діагностика ===")
        appendLine("Час: $now")
        appendLine("Лаунчер: ${AppConfig.versionName} (${AppConfig.versionCode})")
        appendLine("Пристрій: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
        appendLine()
        appendLine("Мережа: ${if (network.isOnline()) "є (${network.transportName()})" else "немає"}")
        appendLine("Сервер: ${AppConfig.serverAddress}")
        appendLine("Статус: " + when (serverState) {
            is ServerState.Online -> "онлайн, ${serverState.info.players}/${serverState.info.maxPlayers}, " +
                "ping ${serverState.info.pingMs} ms, \"${serverState.info.hostname}\""
            is ServerState.Offline -> "недоступний: ${serverState.reason.asString(context)}"
            ServerState.NoInternet -> "немає інтернету"
            ServerState.NotConfigured -> "не налаштовано"
            ServerState.Unknown -> "не перевірено"
        })
        appendLine()
        appendLine("Клієнт: " + when (client) {
            ClientAvailability.NotConfigured -> "пакет не задано"
            is ClientAvailability.NotInstalled -> "${client.packageName} — не встановлено"
            is ClientAvailability.Installed -> "${client.label} (${client.packageName}) " +
                "v${client.versionName} [${client.versionCode}], запуск: ${if (client.launchable) "так" else "ні"}"
        })
        if (gameTreeUri == null) {
            appendLine("Папка гри: не обрано")
        } else {
            val uri = Uri.parse(gameTreeUri)
            val access = SafTree.hasPersistedPermission(context, uri, write = false)
            val exists = SafTree.root(context, uri) != null
            appendLine("Папка гри: ${SafTree.displayName(context, uri)}, доступ: $access, існує: $exists")
        }
        appendLine("Нікнейм: ${nickname.ifBlank { "—" }}")
        appendLine()
        appendLine("--- Журнал (останні 80) ---")
        DiagnosticsLog.entries.value.takeLast(80).forEach { appendLine(it) }
    }
}
