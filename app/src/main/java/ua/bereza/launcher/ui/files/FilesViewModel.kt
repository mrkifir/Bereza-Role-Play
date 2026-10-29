package ua.bereza.launcher.ui.files

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
import ua.bereza.launcher.data.UiText
import ua.bereza.launcher.diagnostics.DiagnosticsLog
import ua.bereza.launcher.installer.FileCheckResult
import ua.bereza.launcher.installer.GameFilesManifest
import ua.bereza.launcher.installer.SafTree
import ua.bereza.launcher.installer.VerifyProgress
import ua.bereza.launcher.installer.isProblem

data class FilesUiState(
    val folderUri: String? = null,
    val folderName: String? = null,
    val folderAccessible: Boolean = false,
    val manifestName: String = "",
    val manifestCount: Int = 0,
    val manifestIsCustom: Boolean = false,
    val running: Boolean = false,
    val progress: VerifyProgress? = null,
    val results: List<FileCheckResult>? = null,
) {
    val problems: Int get() = results?.count { it.isProblem } ?: 0
}

class FilesViewModel(private val c: AppContainer) : ViewModel() {
    private val context = c.appContext
    private val _state = MutableStateFlow(FilesUiState())
    val state: StateFlow<FilesUiState> = _state.asStateFlow()

    private val _messages = Channel<UiText>(Channel.BUFFERED)
    val messages: Flow<UiText> = _messages.receiveAsFlow()

    private var job: Job? = null
    private var manifest: GameFilesManifest = GameFilesManifest("", emptyList())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val settings = c.settings.current()
            manifest = c.activeManifest(settings.customManifestJson)
            val uriString = settings.gameTreeUri
            val (name, accessible) = withContext(Dispatchers.IO) {
                if (uriString == null) {
                    null to false
                } else {
                    val uri = Uri.parse(uriString)
                    val ok = SafTree.hasPersistedPermission(context, uri, write = false) && SafTree.root(context, uri) != null
                    SafTree.displayName(context, uri) to ok
                }
            }
            _state.update {
                it.copy(
                    folderUri = uriString,
                    folderName = name,
                    folderAccessible = accessible,
                    manifestName = manifest.name,
                    manifestCount = manifest.entries.size,
                    manifestIsCustom = settings.customManifestJson != null,
                )
            }
        }
    }

    fun onFolderPicked(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            try {
                val old = c.settings.current().gameTreeUri
                SafTree.takePersistablePermission(context, uri, write = true)
                if (old != null && old != uri.toString()) SafTree.releasePermission(context, Uri.parse(old))
                c.settings.setGameTreeUri(uri.toString())
                DiagnosticsLog.i("files", "game folder selected: $uri")
                _state.update { it.copy(results = null) }
                refresh()
            } catch (e: SecurityException) {
                DiagnosticsLog.e("files", "cannot persist permission", e)
                _messages.send(UiText.Res(R.string.err_folder_permission))
            }
        }
    }

    fun verify() {
        val uriString = _state.value.folderUri ?: run {
            viewModelScope.launch { _messages.send(UiText.Res(R.string.err_no_folder)) }
            return
        }
        if (manifest.isEmpty) {
            viewModelScope.launch { _messages.send(UiText.Res(R.string.msg_manifest_empty)) }
            return
        }
        runJob {
            val results = c.verifier.verify(Uri.parse(uriString), manifest) { p ->
                _state.update { it.copy(progress = p) }
            }
            _state.update { it.copy(results = results) }
            val problems = results.count { it.isProblem }
            _messages.send(
                if (problems == 0) UiText.Res(R.string.msg_verify_ok, results.size)
                else UiText.Res(R.string.msg_verify_problems, problems)
            )
        }
    }

    /** Сканує обрану папку і записує маніфест у файл, обраний користувачем; робить його активним. */
    fun buildManifest(output: Uri?) {
        if (output == null) return
        val uriString = _state.value.folderUri ?: run {
            viewModelScope.launch { _messages.send(UiText.Res(R.string.err_no_folder)) }
            return
        }
        runJob {
            val name = _state.value.folderName ?: "Bereza RP"
            val built = c.verifier.buildManifest(Uri.parse(uriString), name) { p ->
                _state.update { it.copy(progress = p) }
            }
            val json = built.toJson()
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(output, "wt")?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                    ?: throw java.io.IOException("cannot write manifest file")
            }
            c.settings.setCustomManifest(json)
            _messages.send(UiText.Res(R.string.msg_manifest_built, built.entries.size))
            refresh()
        }
    }

    fun importManifest(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val bytes = stream.readBytes()
                        if (bytes.size > 5_000_000) throw IllegalStateException("manifest too large")
                        String(bytes, Charsets.UTF_8)
                    } ?: throw java.io.IOException("cannot open file")
                }
                val parsed = GameFilesManifest.parse(text)
                c.settings.setCustomManifest(parsed.toJson())
                _messages.send(UiText.Res(R.string.msg_manifest_imported, parsed.entries.size))
                _state.update { it.copy(results = null) }
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DiagnosticsLog.e("files", "manifest import failed", e)
                _messages.send(ErrorMessages.from(e))
            }
        }
    }

    fun resetManifest() {
        viewModelScope.launch {
            c.settings.setCustomManifest(null)
            _state.update { it.copy(results = null) }
            refresh()
        }
    }

    fun cancel() {
        job?.cancel()
    }

    private fun runJob(block: suspend () -> Unit) {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            _state.update { it.copy(running = true, progress = null) }
            try {
                block()
            } catch (e: CancellationException) {
                _messages.trySend(UiText.Res(R.string.msg_cancelled))
                throw e
            } catch (e: Exception) {
                DiagnosticsLog.e("files", "operation failed", e)
                _messages.send(ErrorMessages.from(e))
            } finally {
                _state.update { it.copy(running = false, progress = null) }
            }
        }
    }
}
