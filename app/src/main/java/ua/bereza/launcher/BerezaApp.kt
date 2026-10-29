package ua.bereza.launcher

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import ua.bereza.launcher.client.ClientProvider
import ua.bereza.launcher.data.ConfigRepository
import ua.bereza.launcher.data.InstalledStateStore
import ua.bereza.launcher.data.ManifestRepository
import ua.bereza.launcher.data.NewsRepository
import ua.bereza.launcher.data.SettingsRepository
import ua.bereza.launcher.data.StorageLocations
import ua.bereza.launcher.install.DownloadManager
import ua.bereza.launcher.install.GameInstaller
import ua.bereza.launcher.install.InstallController
import ua.bereza.launcher.install.RemoteAssetsProvider
import ua.bereza.launcher.install.UserOwnedAssetsProvider
import ua.bereza.launcher.launch.ConfigurableClientLauncher
import ua.bereza.launcher.launch.GameLauncher
import ua.bereza.launcher.launch.ServerConnector
import ua.bereza.launcher.server.ServerStatusRepository

class BerezaApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm?.createNotificationChannel(
            NotificationChannel(CHANNEL_INSTALL, getString(R.string.notif_channel_install), NotificationManager.IMPORTANCE_LOW),
        )
    }

    companion object {
        const val CHANNEL_INSTALL = "install"
    }
}

/** Ручне впровадження залежностей — без зайвих бібліотек. */
class AppContainer(context: Context) {
    private val app = context.applicationContext

    val config = ConfigRepository(app)
    val settings = SettingsRepository(app)
    val storage = StorageLocations(app)
    val stateStore = InstalledStateStore()
    val manifests = ManifestRepository(app, config)
    val news = NewsRepository(app, config)
    val serverStatus = ServerStatusRepository()

    val downloader = DownloadManager()
    val userAssets = UserOwnedAssetsProvider(app) { settings.current().userAssetsTreeUri }
    val clients = ClientProvider(app, downloader)
    val connector = ServerConnector()
    val launcher: GameLauncher = ConfigurableClientLauncher(clients, connector)

    val installer = GameInstaller(
        context = app,
        settings = settings,
        storage = storage,
        stateStore = stateStore,
        manifests = manifests,
        providers = listOf(RemoteAssetsProvider(downloader), userAssets),
        clients = clients,
    )
    val installController = InstallController(installer)
}
