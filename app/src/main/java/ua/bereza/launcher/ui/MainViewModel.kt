package ua.bereza.launcher.ui

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ua.bereza.launcher.BerezaApp
import ua.bereza.launcher.BuildConfig
import ua.bereza.launcher.client.ClientStatus
import ua.bereza.launcher.core.AppLog
import ua.bereza.launcher.core.ErrorCode
import ua.bereza.launcher.core.Format
import ua.bereza.launcher.core.LauncherException
import ua.bereza.launcher.data.Nickname
import ua.bereza.launcher.data.StorageMode
import ua.bereza.launcher.data.UserSettings
import ua.bereza.launcher.data.model.NewsItem
import ua.bereza.launcher.data.model.ServerConfig
import ua.bereza.launcher.install.GameStatus
import ua.bereza.launcher.install.InstallMode
import ua.bereza.launcher.install.InstallProgress
import ua.bereza.launcher.install.Phase
import ua.bereza.launcher.launch.ConnectParams
import ua.bereza.launcher.server.ServerState

sealed interface NewsUi {
    data object NotConfigured : NewsUi
    data object Loading : NewsUi
    data class Items(val items: List<NewsItem>, val stale: Boolean) : NewsUi
    data class Failed(val message: String) : NewsUi
}

sealed interface UiEvent {
    data class Toast(val text: String) : UiEvent
    data object OpenInstall : UiEvent
    data object OpenError : UiEvent
    data object AskNickname : UiEvent
    data class OpenUrl(val url: String) : UiEvent
    data object OpenUnknownSourcesSettings : UiEvent
}

data class Diagnostics(val lines: List<Pair<String, String>>)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val c = (app as BerezaApp).container
    val config = c.config.config

    val settings: StateFlow<UserSettings> = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())
    val install: StateFlow<InstallProgress> = c.installController.progress

    private val _game = MutableStateFlow<GameStatus>(GameStatus.Unknown)
    val game: StateFlow<GameStatus> = _game.asStateFlow()

    private val _server = MutableStateFlow<ServerState>(ServerState.Loading)
    val server: StateFlow<ServerState> = _server.asStateFlow()

    private val _news = MutableStateFlow<NewsUi>(NewsUi.Loading)
    val news: StateFlow<NewsUi> = _news.asStateFlow()

    private val _client = MutableStateFlow<ClientStatus>(ClientStatus.NotConfigured)
    val client: StateFlow<ClientStatus> = _client.asStateFlow()

    private val _error = MutableStateFlow<LauncherException?>(null)
    val error: StateFlow<LauncherException?> = _error.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    /** Запустити гру одразу після успішного оновлення/перевірки. */
    private var playAfterInstall = false
    /** Файли щойно перевірено/встановлено — повторна перевірка перед запуском не потрібна. */
    private var freshlyVerified = false
    private var serverPoll: Job? = null

    val configProblems: List<String> = c.config.problems()

    /** Сервер: блок із маніфесту має пріоритет над локальною конфігурацією. */
    val effectiveServer: ServerConfig
        get() = c.manifests.lastManifest?.server?.takeIf { !it.isPlaceholder } ?: config.server

    init {
        _news.value = if (!c.news.isConfigured) NewsUi.NotConfigured else c.news.cached().let {
            if (it.isEmpty()) NewsUi.Loading else NewsUi.Items(it, stale = true)
        }
        refreshAll()
        viewModelScope.launch {
            install.collect { p -> onInstallProgress(p) }
        }
    }

    fun refreshAll() {
        refreshGame()
        refreshNews()
        startServerPolling()
    }

    fun refreshGame() {
        viewModelScope.launch {
            _checking.value = true
            _game.value = try { c.installer.quickStatus() } catch (e: LauncherException) { GameStatus.Blocked(e) }
            _client.value = c.clients.status(c.manifests.lastManifest?.client ?: c.manifests.cached()?.client)
            _checking.value = false
        }
    }

    fun refreshNews() {
        if (!c.news.isConfigured) { _news.value = NewsUi.NotConfigured; return }
        viewModelScope.launch {
            try {
                _news.value = NewsUi.Items(c.news.fetch(), stale = false)
            } catch (e: LauncherException) {
                val cached = c.news.cached()
                _news.value = if (cached.isNotEmpty()) NewsUi.Items(cached, stale = true) else NewsUi.Failed(e.message)
            }
        }
    }

    fun startServerPolling() {
        serverPoll?.cancel()
        serverPoll = viewModelScope.launch {
            while (isActive) {
                if (_server.value !is ServerState.Online) _server.value = ServerState.Loading
                _server.value = c.serverStatus.query(effectiveServer, config.statusApiUrl)
                delay(30_000)
            }
        }
    }

    fun stopServerPolling() { serverPoll?.cancel(); serverPoll = null }

    /* ---------------- головна кнопка ---------------- */

    fun primaryAction(context: Context) {
        when (val g = _game.value) {
            GameStatus.NotInstalled, is GameStatus.Interrupted -> startInstall(context, InstallMode.INSTALL)
            is GameStatus.UpdateAvailable -> { playAfterInstall = true; startInstall(context, InstallMode.UPDATE) }
            is GameStatus.Damaged -> { playAfterInstall = true; startInstall(context, InstallMode.REPAIR) }
            is GameStatus.Installed -> play(context)
            is GameStatus.Blocked -> showError(g.error)
            GameStatus.Unknown -> refreshGame()
        }
    }

    fun startInstall(context: Context, mode: InstallMode) {
        if (configProblems.isNotEmpty() && mode != InstallMode.VERIFY) {
            showError(LauncherException(ErrorCode.CONFIG, "Лаунчер не налаштовано: " + configProblems.joinToString("; ")))
            return
        }
        c.installController.start(context, mode)
        _events.tryEmit(UiEvent.OpenInstall)
    }

    fun cancelInstall() = c.installController.cancel()

    fun play(context: Context) {
        val s = settings.value
        val nickError = Nickname.validate(s.nickname, config.requireRpNickname)
        if (nickError != null) { _events.tryEmit(UiEvent.AskNickname); return }

        viewModelScope.launch {
            // 1–3: актуальність файлів (якщо є інтернет)
            _checking.value = true
            val status = runCatching { c.installer.quickStatus() }.getOrElse { GameStatus.Unknown }
            _game.value = status
            _checking.value = false
            when (status) {
                is GameStatus.UpdateAvailable -> { playAfterInstall = true; startInstall(context, InstallMode.UPDATE); return@launch }
                is GameStatus.Damaged -> { playAfterInstall = true; startInstall(context, InstallMode.REPAIR); return@launch }
                is GameStatus.Blocked -> { showError(status.error); return@launch }
                GameStatus.NotInstalled, is GameStatus.Interrupted -> { startInstall(context, InstallMode.INSTALL); return@launch }
                else -> Unit
            }
            val skipVerify = freshlyVerified
            freshlyVerified = false
            if (s.verifyBeforeLaunch && !skipVerify) {
                playAfterInstall = true
                startInstall(context, InstallMode.VERIFY)
                return@launch
            }
            // 4: клієнт
            val manifest = c.manifests.lastManifest ?: c.manifests.cached()
            val clientStatus = c.clients.status(manifest?.client)
            _client.value = clientStatus
            if ((clientStatus is ClientStatus.NotInstalled && clientStatus.canAutoInstall) ||
                (clientStatus is ClientStatus.Outdated && clientStatus.canAutoInstall)
            ) {
                playAfterInstall = true
                startInstall(context, InstallMode.INSTALL)
                return@launch
            }
            if (clientStatus is ClientStatus.NotInstalled && clientStatus.infoUrl.isNotBlank()) {
                showError(LauncherException(ErrorCode.CLIENT_MISSING, "Встановіть сумісний клієнт SA-MP за посиланням на екрані помилки, потім натисніть «Грати» ще раз."))
                return@launch
            }
            // 5–7: запуск + підключення
            try {
                val server = effectiveServer
                if (server.isPlaceholder) throw LauncherException(ErrorCode.CONFIG, "Адресу сервера не налаштовано")
                val gameDir = c.storage.gameDir(s)
                val result = c.launcher.launch(
                    context, manifest?.client, manifest?.launch ?: ua.bereza.launcher.data.model.LaunchSpec(), gameDir,
                    ConnectParams(server.host, server.port, s.nickname, server.name),
                )
                if (!result.autoConnect) {
                    _events.tryEmit(UiEvent.Toast("Клієнт не приймає адресу автоматично. Адресу ${server.host}:${server.port} скопійовано."))
                }
            } catch (e: LauncherException) {
                showError(e)
            }
        }
    }

    private fun onInstallProgress(p: InstallProgress) {
        when (p.phase) {
            Phase.DONE -> {
                refreshGame()
                if (playAfterInstall) {
                    playAfterInstall = false
                    freshlyVerified = true
                    // Гравець натиснув «Грати» — продовжуємо запуск після оновлення.
                    val ctx = getApplication<Application>()
                    play(ctx)
                }
            }
            Phase.FAILED -> { playAfterInstall = false; p.error?.let { _error.value = it }; refreshGame() }
            Phase.CANCELLED -> { playAfterInstall = false; refreshGame() }
            else -> Unit
        }
    }

    /* ---------------- клієнт ---------------- */

    fun installClient() {
        val spec = (c.manifests.lastManifest ?: c.manifests.cached())?.client ?: return
        if (!c.clients.canRequestPackageInstalls()) { _events.tryEmit(UiEvent.OpenUnknownSourcesSettings); return }
        viewModelScope.launch {
            try {
                val apk = c.clients.apkFile(spec)
                if (!apk.exists()) { startInstall(getApplication<Application>(), InstallMode.INSTALL); return@launch }
                val ok = c.clients.install(apk)
                if (ok) {
                    c.installController.markClientInstalled()
                    _client.value = c.clients.status(spec)
                    _events.tryEmit(UiEvent.Toast("Клієнт встановлено"))
                } else {
                    _events.tryEmit(UiEvent.Toast("Встановлення клієнта скасовано"))
                }
            } catch (e: LauncherException) {
                showError(e)
            }
        }
    }

    fun unknownSourcesIntent() = c.clients.unknownSourcesSettingsIntent()

    fun onResume() {
        _client.value = c.clients.status((c.manifests.lastManifest ?: c.manifests.cached())?.client)
        if (install.value.phase == Phase.AWAITING_CLIENT && _client.value is ClientStatus.Ready) {
            c.installController.markClientInstalled()
        }
    }

    /* ---------------- налаштування ---------------- */

    fun setNickname(v: String) = viewModelScope.launch { c.settings.setNickname(v) }

    /** Зберегти нік і одразу продовжити запуск (чекаємо, поки DataStore віддасть нове значення). */
    fun saveNicknameAndPlay(v: String, context: Context) = viewModelScope.launch {
        c.settings.setNickname(v)
        settings.first { it.nickname == v.trim() }
        play(context)
    }
    fun setWifiOnly(v: Boolean) = viewModelScope.launch { c.settings.setWifiOnly(v) }
    fun setVerifyBeforeLaunch(v: Boolean) = viewModelScope.launch { c.settings.setVerifyBeforeLaunch(v) }
    fun setStorageMode(v: StorageMode) = viewModelScope.launch { c.settings.setStorageMode(v); refreshGame() }
    fun setSharedFolder(v: String) = viewModelScope.launch { c.settings.setSharedFolder(v); refreshGame() }
    fun setUserAssetsTree(uri: String) = viewModelScope.launch {
        c.settings.setUserAssetsTree(uri)
        c.userAssets.invalidate()
    }
    fun hasStorageAccess(): Boolean = c.storage.hasAccess(settings.value)
    fun nicknameError(nick: String) = Nickname.validate(nick, config.requireRpNickname)

    fun clearDownloadCache() = viewModelScope.launch {
        runCatching {
            c.storage.stagingDir(c.storage.gameDir(settings.value)).deleteRecursively()
            getApplication<Application>().cacheDir.resolve("client").deleteRecursively()
        }
        _events.tryEmit(UiEvent.Toast("Кеш завантажень очищено"))
    }

    /* ---------------- помилки ---------------- */

    fun showError(e: LauncherException) {
        _error.value = e
        _events.tryEmit(UiEvent.OpenError)
    }

    fun clearError() { _error.value = null }
    fun acknowledgeInstall() = c.installController.acknowledge()

    fun openUrl(url: String) { if (url.isNotBlank()) _events.tryEmit(UiEvent.OpenUrl(url)) }

    /** Повний розмір збірки за маніфестом (для підпису на кнопці «Встановити»). */
    fun fullInstallSize(): Long = (c.manifests.lastManifest ?: c.manifests.cached())?.files?.sumOf { it.size } ?: 0L

    fun clientInfoUrl(): String = (c.manifests.lastManifest ?: c.manifests.cached())?.client?.infoUrl.orEmpty()

    /* ---------------- діагностика ---------------- */

    fun diagnostics(): Diagnostics {
        val s = settings.value
        val dir = c.storage.gameDir(s)
        val state = runCatching { c.stateStore.load(dir) }.getOrNull()
        val m = c.manifests.lastManifest ?: c.manifests.cached()
        val srv = _server.value
        return Diagnostics(
            listOf(
                "Лаунчер" to "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                "Пристрій" to "${Build.MANUFACTURER} ${Build.MODEL}",
                "ABI" to Build.SUPPORTED_ABIS.joinToString(),
                "Сховище" to "${s.storageMode.name} · доступ: ${if (c.storage.hasAccess(s)) "так" else "ні"}",
                "Папка гри" to dir.path,
                "Вільно" to Format.bytes(c.storage.freeBytes(dir)),
                "Маніфест" to config.manifestUrl,
                "Версія на сервері" to (m?.gameVersion ?: "—"),
                "Встановлено" to (state?.gameVersion ?: "—"),
                "Незавершене" to (state?.pendingVersion ?: "—"),
                "Файлів у стані" to (state?.files?.size?.toString() ?: "0"),
                "Клієнт" to (m?.client?.packageName?.ifBlank { "не налаштовано" } ?: "не налаштовано"),
                "Статус клієнта" to _client.value.javaClass.simpleName,
                "Сервер" to "${effectiveServer.host}:${effectiveServer.port}",
                "Відповідь сервера" to when (srv) {
                    is ServerState.Online -> "${srv.players}/${srv.maxPlayers}, ${srv.pingMs} мс (${srv.source})"
                    is ServerState.Unreachable -> srv.reason
                    ServerState.NotConfigured -> "не налаштовано"
                    ServerState.Loading -> "перевірка…"
                },
                "Проблеми конфігурації" to (configProblems.joinToString("; ").ifBlank { "немає" }),
            ),
        )
    }

    fun diagnosticsText(): String = buildString {
        diagnostics().lines.forEach { (k, v) -> append(k).append(": ").append(v).append('\n') }
        append("\n--- Журнал ---\n")
        AppLog.lines.value.takeLast(120).forEach { append(it).append('\n') }
    }
}
