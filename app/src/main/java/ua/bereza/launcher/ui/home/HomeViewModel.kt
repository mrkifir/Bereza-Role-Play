package ua.bereza.launcher.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import ua.bereza.launcher.AppContainer
import ua.bereza.launcher.R
import ua.bereza.launcher.config.AppConfig
import ua.bereza.launcher.data.ErrorMessages
import ua.bereza.launcher.data.LauncherUpdateInfo
import ua.bereza.launcher.data.LoadState
import ua.bereza.launcher.data.NewsItem
import ua.bereza.launcher.data.ServerSnapshot
import ua.bereza.launcher.data.UiText
import ua.bereza.launcher.diagnostics.DiagnosticsLog
import ua.bereza.launcher.launcher.ClientAvailability
import ua.bereza.launcher.launcher.LaunchRequest
import ua.bereza.launcher.launcher.LaunchResult
import ua.bereza.launcher.settings.NicknameValidator

class HomeViewModel(private val c: AppContainer) : ViewModel() {

    val server: StateFlow<ServerSnapshot> = c.serverMonitor.snapshot

    private val _news = MutableStateFlow<LoadState<List<NewsItem>>>(LoadState.Loading)
    val news: StateFlow<LoadState<List<NewsItem>>> = _news.asStateFlow()

    private val _update = MutableStateFlow<LoadState<LauncherUpdateInfo>>(LoadState.Loading)
    val update: StateFlow<LoadState<LauncherUpdateInfo>> = _update.asStateFlow()

    private val _client = MutableStateFlow<ClientAvailability>(ClientAvailability.NotConfigured)
    val client: StateFlow<ClientAvailability> = _client.asStateFlow()

    private val _nickname = MutableStateFlow("")
    val nickname: StateFlow<String> = _nickname.asStateFlow()

    private val _nickError = MutableStateFlow<UiText?>(null)
    val nickError: StateFlow<UiText?> = _nickError.asStateFlow()

    private val _launching = MutableStateFlow(false)
    val launching: StateFlow<Boolean> = _launching.asStateFlow()

    private val _messages = Channel<UiText>(Channel.BUFFERED)
    val messages: Flow<UiText> = _messages.receiveAsFlow()

    init {
        viewModelScope.launch {
            val settings = c.settings.current()
            _nickname.value = settings.nickname
            loadNews()
            if (settings.checkUpdatesOnStart) {
                loadUpdate()
            } else {
                _update.value = LoadState.NotConfigured
            }
        }
    }

    /** Викликається з екрана, коли він видимий (кожні 30 с). */
    suspend fun refreshServer() {
        c.serverMonitor.refresh()
    }

    fun refreshClient() {
        viewModelScope.launch {
            val pkg = c.settings.current().clientPackage
            _client.value = withContext(Dispatchers.IO) { c.clientLauncher(pkg).availability() }
        }
    }

    fun onNicknameChange(value: String) {
        _nickname.value = value.take(NicknameValidator.MAX_LENGTH + 4).replace(" ", "_")
        _nickError.value = null
    }

    fun reloadAll() {
        viewModelScope.launch { c.serverMonitor.refresh() }
        viewModelScope.launch { loadNews() }
        viewModelScope.launch { loadUpdate() }
        refreshClient()
    }

    fun play() {
        if (_launching.value) return
        viewModelScope.launch {
            val nick = _nickname.value.trim()
            val error = NicknameValidator.validate(nick, AppConfig.nickRpFormat)
            if (error != null) {
                _nickError.value = error
                return@launch
            }
            _launching.value = true
            try {
                c.settings.setNickname(nick)
                val settings = c.settings.current()
                val result = c.clientLauncher(settings.clientPackage).launch(
                    LaunchRequest(
                        host = AppConfig.serverHost,
                        port = AppConfig.serverPort,
                        nickname = nick,
                        copyAddress = settings.copyAddressOnLaunch,
                    )
                )
                val message = when (result) {
                    is LaunchResult.Started -> when {
                        result.directConnect -> UiText.Res(R.string.msg_launch_direct)
                        result.addressCopied -> UiText.Res(R.string.msg_launch_copied, AppConfig.serverAddress, nick)
                        else -> UiText.Res(R.string.msg_launch_manual, AppConfig.serverAddress, nick)
                    }
                    LaunchResult.NotConfigured -> UiText.Res(R.string.msg_client_not_configured)
                    LaunchResult.NotInstalled -> UiText.Res(R.string.msg_client_not_installed, settings.clientPackage)
                    LaunchResult.NoLaunchActivity -> UiText.Res(R.string.msg_client_no_activity)
                    is LaunchResult.Failed -> UiText.Res(R.string.msg_launch_failed, result.reason)
                }
                _messages.send(message)
            } finally {
                _launching.value = false
            }
            refreshClient()
        }
    }

    private suspend fun loadNews() {
        if (!c.news.isConfigured) {
            _news.value = LoadState.NotConfigured
            return
        }
        _news.value = LoadState.Loading
        _news.value = try {
            LoadState.Success(c.news.load())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DiagnosticsLog.w("news", "load failed: ${e.message}")
            LoadState.Error(ErrorMessages.from(e))
        }
    }

    private suspend fun loadUpdate() {
        if (!c.updates.isConfigured) {
            _update.value = LoadState.NotConfigured
            return
        }
        _update.value = LoadState.Loading
        _update.value = try {
            LoadState.Success(c.updates.load())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DiagnosticsLog.w("update", "check failed: ${e.message}")
            LoadState.Error(ErrorMessages.from(e))
        }
    }
}
