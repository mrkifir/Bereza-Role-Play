package ua.bereza.launcher.ui

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.bereza.launcher.BerezaApp
import ua.bereza.launcher.client.Availability
import ua.bereza.launcher.client.ClientRegistry
import ua.bereza.launcher.model.LauncherRelease
import ua.bereza.launcher.news.NewsRepository
import ua.bereza.launcher.server.ServerStatus
import ua.bereza.launcher.update.EngineState
import ua.bereza.launcher.update.InstallService

data class NewsUi(val loading: Boolean = false, val result: NewsRepository.Result? = null)

data class StorageUi(val used: Long = 0, val free: Long = 0, val path: String = "")

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = (app as BerezaApp).graph

    val config = graph.config
    val settings = graph.settings
    val engine: StateFlow<EngineState> = graph.engine.state
    val launcherUpdate = graph.launcherUpdater.state

    private val _server = MutableStateFlow<ServerStatus>(ServerStatus.Unknown)
    val server: StateFlow<ServerStatus> = _server.asStateFlow()

    private val _news = MutableStateFlow(NewsUi(result = NewsRepository.Result(graph.news.cached(), true, null)))
    val news: StateFlow<NewsUi> = _news.asStateFlow()

    private val _storage = MutableStateFlow(StorageUi())
    val storage: StateFlow<StorageUi> = _storage.asStateFlow()

    private val _client = MutableStateFlow(ClientRegistry.availability(app, graph.storage.gameDir))
    val client: StateFlow<Availability> = _client.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun consumeMessage() { _message.value = null }

    val serverAddress = graph.server.address
    val deviceAbis = ClientRegistry.deviceAbis()

    private var polling: Job? = null

    init {
        viewModelScope.launch {
            val s = graph.settingsRepo.current()
            if (s.autoCheck && graph.engine.state.value is EngineState.Idle) graph.engine.check()
            refreshNews()
        }
        refreshStorage()
    }

    /** Опитування сервера, поки видно головний екран. */
    fun startPolling() {
        if (polling?.isActive == true) return
        polling = viewModelScope.launch {
            while (isActive) {
                if (_server.value is ServerStatus.Unknown) _server.value = ServerStatus.Loading
                _server.value = graph.server.query()
                delay(20_000)
            }
        }
    }

    fun stopPolling() { polling?.cancel(); polling = null }

    fun refreshServer() = viewModelScope.launch {
        _server.value = ServerStatus.Loading
        _server.value = graph.server.query()
    }

    fun refreshNews() = viewModelScope.launch {
        _news.value = _news.value.copy(loading = true)
        _news.value = NewsUi(false, graph.news.refresh())
    }

    fun checkUpdates(full: Boolean = false) = viewModelScope.launch {
        graph.engine.check(full)
        refreshStorage()
    }

    fun startInstall() {
        val ctx = getApplication<Application>()
        if (settings.value.wifiOnly && isMetered()) {
            _message.value = "Увімкнено «лише Wi-Fi». Підключись до Wi-Fi або вимкни це в налаштуваннях"
            return
        }
        InstallService.start(ctx)
    }

    fun cancelInstall() = InstallService.cancel(getApplication())

    fun refreshStorage() = viewModelScope.launch {
        _storage.value = withContext(Dispatchers.IO) {
            StorageUi(graph.storage.usedBytes(), graph.storage.freeBytes(), graph.storage.gameDir.absolutePath)
        }
        _client.value = ClientRegistry.availability(getApplication(), graph.storage.gameDir)
    }

    fun wipeGameFiles() = viewModelScope.launch {
        if (graph.engine.isBusy) { _message.value = "Спершу зупини завантаження"; return@launch }
        withContext(Dispatchers.IO) { graph.storage.wipe() }
        graph.engine.reset()
        refreshStorage()
        _message.value = "Файли гри видалено"
        graph.engine.check()
    }

    fun setNickname(v: String) = viewModelScope.launch { graph.settingsRepo.setNickname(v); _message.value = "Нік збережено" }
    fun setWifiOnly(v: Boolean) = viewModelScope.launch { graph.settingsRepo.setWifiOnly(v) }
    fun setAutoCheck(v: Boolean) = viewModelScope.launch { graph.settingsRepo.setAutoCheck(v) }
    fun setManifestOverride(v: String) = viewModelScope.launch { graph.settingsRepo.setManifestOverride(v); graph.engine.check() }
    fun setNewsOverride(v: String) = viewModelScope.launch { graph.settingsRepo.setNewsOverride(v); refreshNews() }

    fun downloadLauncher(release: LauncherRelease) = viewModelScope.launch { graph.launcherUpdater.download(release) }
    val launcherUpdater get() = graph.launcherUpdater

    private fun isMetered(): Boolean {
        val cm = getApplication<Application>().getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return true
        return !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }
}
