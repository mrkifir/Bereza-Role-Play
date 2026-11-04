package ua.bereza.launcher.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import ua.bereza.launcher.files.Downloader
import ua.bereza.launcher.files.Sha256
import ua.bereza.launcher.model.LauncherRelease
import ua.bereza.launcher.net.Urls
import java.io.File

/** Самооновлення лаунчера: качає новий APK, перевіряє SHA-256, відкриває системне встановлення. */
class LauncherUpdater(private val context: Context, http: OkHttpClient) {
    sealed interface State {
        data object Idle : State
        data class Downloading(val fraction: Float) : State
        data class ReadyToInstall(val apk: File) : State
        data class Failed(val message: String) : State
    }

    private val downloader = Downloader(http)
    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val dir = File(context.cacheDir, "updates").apply { mkdirs() }

    suspend fun download(release: LauncherRelease) {
        try {
            val part = File(dir, "launcher-${release.versionCode}.apk.part")
            downloader.download(Urls.resolve("", release.apkUrl), part, release.size) { d, t ->
                _state.value = State.Downloading(if (t > 0) d.toFloat() / t else 0f)
            }
            if (!Sha256.matches(Sha256.of(part), release.sha256)) {
                part.delete(); _state.value = State.Failed("APK пошкоджений (SHA-256 не збігається)"); return
            }
            val apk = File(dir, "launcher-${release.versionCode}.apk")
            apk.delete(); part.renameTo(apk)
            _state.value = State.ReadyToInstall(apk)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = State.Failed(e.message ?: "Помилка завантаження")
        }
    }

    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun openUnknownSourcesSettings(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    fun installIntent(apk: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        return Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
