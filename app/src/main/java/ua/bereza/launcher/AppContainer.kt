package ua.bereza.launcher

import android.content.Context
import ua.bereza.launcher.config.AppConfig
import ua.bereza.launcher.data.NewsRepository
import ua.bereza.launcher.data.ServerMonitor
import ua.bereza.launcher.data.UpdateRepository
import ua.bereza.launcher.diagnostics.DiagnosticsCollector
import ua.bereza.launcher.diagnostics.DiagnosticsLog
import ua.bereza.launcher.installer.ComponentInstaller
import ua.bereza.launcher.installer.FileVerifier
import ua.bereza.launcher.installer.GameFilesManifest
import ua.bereza.launcher.installer.LauncherUpdater
import ua.bereza.launcher.installer.SafCopyInstaller
import ua.bereza.launcher.installer.SafFileVerifier
import ua.bereza.launcher.launcher.GameClientLauncher
import ua.bereza.launcher.launcher.PackageLaunchAdapter
import ua.bereza.launcher.network.HttpJsonClient
import ua.bereza.launcher.network.NetworkMonitor
import ua.bereza.launcher.network.SampQueryClient
import ua.bereza.launcher.network.ServerStatusSource
import ua.bereza.launcher.settings.SettingsRepository

/** Ручне впровадження залежностей: одна точка, де створюються всі сервіси. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    val settings = SettingsRepository(appContext)
    val network = NetworkMonitor(appContext)
    val http = HttpJsonClient()
    private val serverStatusSource: ServerStatusSource = SampQueryClient()
    val serverMonitor = ServerMonitor(serverStatusSource, network)
    val news = NewsRepository(http, AppConfig.newsUrl)
    val updates = UpdateRepository(http, AppConfig.updateUrl)
    val verifier: FileVerifier = SafFileVerifier(appContext)
    val installer: ComponentInstaller = SafCopyInstaller(appContext)
    val updater = LauncherUpdater(appContext, http)
    val diagnostics = DiagnosticsCollector(appContext, network)

    /** Адаптер клієнта. Щоб підключити інший спосіб запуску — замініть реалізацію тут. */
    fun clientLauncher(packageName: String): GameClientLauncher =
        PackageLaunchAdapter(appContext, packageName)

    fun bundledManifest(): GameFilesManifest = try {
        val json = appContext.assets.open("game_manifest.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        GameFilesManifest.parse(json)
    } catch (e: Exception) {
        DiagnosticsLog.e("manifest", "bundled manifest error", e)
        GameFilesManifest("empty", emptyList())
    }

    fun activeManifest(customJson: String?): GameFilesManifest {
        if (customJson != null) {
            try {
                return GameFilesManifest.parse(customJson)
            } catch (e: Exception) {
                DiagnosticsLog.e("manifest", "custom manifest error", e)
            }
        }
        return bundledManifest()
    }
}
