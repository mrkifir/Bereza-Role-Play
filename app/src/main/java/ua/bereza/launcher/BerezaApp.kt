package ua.bereza.launcher

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json
import ua.bereza.launcher.model.LauncherConfig
import ua.bereza.launcher.net.Http
import ua.bereza.launcher.news.NewsRepository
import ua.bereza.launcher.server.ServerMonitor
import ua.bereza.launcher.settings.SettingsRepository
import ua.bereza.launcher.settings.UserSettings
import ua.bereza.launcher.update.GameStorage
import ua.bereza.launcher.update.InstallService
import ua.bereza.launcher.update.LauncherUpdater
import ua.bereza.launcher.update.UpdateEngine

class BerezaApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        InstallService.createChannel(this)
    }
}

/** Ручне DI: усі сервіси — одиночки на рівні застосунку. */
class AppGraph(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val config: LauncherConfig = context.assets.open("launcher_config.json").bufferedReader().use {
        Json { ignoreUnknownKeys = true }.decodeFromString(LauncherConfig.serializer(), it.readText())
    }

    val settingsRepo = SettingsRepository(context)
    val settings: StateFlow<UserSettings> = settingsRepo.flow.stateIn(scope, SharingStarted.Eagerly, UserSettings())

    val storage = GameStorage(context)
    val server = ServerMonitor(config.server)

    val engine = UpdateEngine(context, storage, Http.client) {
        settings.value.manifestUrlOverride.ifBlank { config.manifestUrl }
    }
    val news = NewsRepository(context, Http.client) {
        settings.value.newsUrlOverride.ifBlank { config.newsUrl }
    }
    val launcherUpdater = LauncherUpdater(context, Http.client)
}
