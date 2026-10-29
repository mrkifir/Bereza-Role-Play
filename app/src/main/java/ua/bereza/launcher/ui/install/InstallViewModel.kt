package ua.bereza.launcher.ui.install

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.bereza.launcher.AppContainer
import ua.bereza.launcher.R
import ua.bereza.launcher.data.ErrorMessages
import ua.bereza.launcher.data.LauncherUpdateInfo
import ua.bereza.launcher.data.LoadState
import ua.bereza.launcher.data.UiText
import ua.bereza.launcher.diagnostics.DiagnosticsLog
import ua.bereza.launcher.installer.CopyProgress
import ua.bereza.launcher.installer.CopyReport
import ua.bereza.launcher.installer.SafTree
import java.io.File

data class InstallUiState(
    val sourceUri: String? = null,
    val sourceName: String? = null,
    val targetUri: String? = null,
    val targetName: String? = null,
    val overwrite: Boolean = false,
    val copying: Boolean = false,
    val progress: CopyProgress? = null,
    val report: CopyReport? = null,
    val update: LoadState<LauncherUpdateInfo> = LoadState.Loading,
    val downloading: Boolean = false,
    val downloadedBytes: Long = 0,
    val downloadTotal: Long = -1,
    val readyApk: String? = null,
)

class InstallViewModel(private val c: AppContainer) : ViewModel() {
    private val context = c.appContext
    private val _state = MutableStateFlow(InstallUiState())
    val state: StateFlow<InstallUiState> = _state.asStateFlow()

    private val _messages = Channel<UiText>(Channel.BUFFERED)
    val messages: Flow<UiText> = _messages.receiveAsFlow()

    private var copyJob: Job? = null
    private var downloadJob: Job? = null

    init {
        loadTarget()
        checkUpdate()
    }

    fun loadTarget() {
        viewModelScope.launch {
            val target = c.settings.current().gameTreeUri
            val name = withContext(Dispatchers.IO) { target?.let { t -> SafTree.displayName(context, Uri.parse(t)) } }
            _state.update { it.copy(targetUri = target, targetName = name) }
        }
    }

    fun onSourcePicked(uri: Uri?) {
        if (uri == null) return
        try {
            SafTree.takePersistablePermission(context, uri, write = false)
        } catch (e: SecurityException) {
            DiagnosticsLog.w("install", "no persistable read permission for source: ${e.message}")
        }
        viewModelScope.launch {
            val name = withContext(Dispatchers.IO) { SafTree.displayName(context, uri) }
            _state.update { it.copy(sourceUri = uri.toString(), sourceName = name, report = null) }
        }
    }

    fun setOverwrite(value: Boolean) = _state.update { it.copy(overwrite = value) }

    fun startCopy() {
        val s = _state.value
        val source = s.sourceUri
        val target = s.targetUri
        if (source == null) {
            viewModelScope.launch { _messages.send(UiText.Res(R.string.err_no_source)) }
            return
        }
        if (target == null) {
            viewModelScope.launch { _messages.send(UiText.Res(R.string.err_no_folder)) }
            return
        }
        if (copyJob?.isActive == true) return
        copyJob = viewModelScope.launch {
            _state.update { it.copy(copying = true, progress = null, report = null) }
            try {
                val report = c.installer.copyTree(Uri.parse(source), Uri.parse(target), s.overwrite) { p ->
                    _state.update { it.copy(progress = p) }
                }
                _state.update { it.copy(report = report) }
                _messages.send(
                    if (report.failed.isEmpty()) UiText.Res(R.string.msg_copy_done, report.copied, report.skipped)
                    else UiText.Res(R.string.msg_copy_failed_some, report.failed.size)
                )
            } catch (e: CancellationException) {
                _messages.trySend(UiText.Res(R.string.msg_cancelled))
                throw e
            } catch (e: Exception) {
                DiagnosticsLog.e("install", "copy failed", e)
                _messages.send(ErrorMessages.from(e))
            } finally {
                _state.update { it.copy(copying = false) }
            }
        }
    }

    fun cancelCopy() {
        copyJob?.cancel()
    }

    fun checkUpdate() {
        if (!c.updates.isConfigured) {
            _state.update { it.copy(update = LoadState.NotConfigured) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(update = LoadState.Loading) }
            val result: LoadState<LauncherUpdateInfo> = try {
                LoadState.Success(c.updates.load())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LoadState.Error(ErrorMessages.from(e))
            }
            _state.update { it.copy(update = result) }
        }
    }

    fun downloadUpdate() {
        val info = (_state.value.update as? LoadState.Success)?.data ?: return
        if (!c.updater.isNewer(info)) return
        if (!info.canBeInstalled) {
            viewModelScope.launch { _messages.send(UiText.Res(R.string.err_update_unsigned)) }
            return
        }
        if (downloadJob?.isActive == true) return
        downloadJob = viewModelScope.launch {
            _state.update { it.copy(downloading = true, downloadedBytes = 0, downloadTotal = info.sizeBytes, readyApk = null) }
            try {
                val file = c.updater.download(info) { done, total ->
                    _state.update { it.copy(downloadedBytes = done, downloadTotal = if (total > 0) total else info.sizeBytes) }
                }
                _state.update { it.copy(readyApk = file.absolutePath) }
                _messages.send(UiText.Res(R.string.msg_update_ready))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DiagnosticsLog.e("update", "download failed", e)
                _messages.send(ErrorMessages.from(e))
            } finally {
                _state.update { it.copy(downloading = false) }
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
    }

    fun canRequestInstall(): Boolean = c.updater.canRequestInstall()

    fun installPermissionIntent() = c.updater.installPermissionIntent()

    fun installIntent(): android.content.Intent? = _state.value.readyApk?.let { c.updater.installIntent(File(it)) }

    fun isNewer(info: LauncherUpdateInfo): Boolean = c.updater.isNewer(info)
}
