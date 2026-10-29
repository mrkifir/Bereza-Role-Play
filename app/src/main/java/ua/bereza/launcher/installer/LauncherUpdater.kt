package ua.bereza.launcher.installer

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ua.bereza.launcher.config.AppConfig
import ua.bereza.launcher.data.InvalidDataException
import ua.bereza.launcher.data.LauncherUpdateInfo
import ua.bereza.launcher.diagnostics.DiagnosticsLog
import ua.bereza.launcher.network.HttpJsonClient
import java.io.File

/**
 * Оновлення самого лаунчера з довіреного джерела (update.url):
 * 1) завантаження APK лише по https;
 * 2) обов'язкова перевірка SHA-256 з version.json;
 * 3) перевірка, що APK — це саме цей застосунок і новіша версія;
 * 4) встановлення через системний інсталятор (користувач підтверджує сам).
 * Підпис APK додатково перевіряє Android: оновлення має бути підписане тим самим ключем.
 */
class LauncherUpdater(
    private val context: Context,
    private val http: HttpJsonClient,
) {
    private val updatesDir: File get() = File(context.cacheDir, "updates")

    fun isNewer(info: LauncherUpdateInfo): Boolean = info.versionCode > AppConfig.versionCode

    suspend fun download(info: LauncherUpdateInfo, onProgress: (Long, Long) -> Unit): File {
        if (!info.canBeInstalled) throw InvalidDataException("update has no https apkUrl or sha256")
        val target = File(updatesDir, "bereza-launcher-${info.versionCode}.apk")
        withContext(Dispatchers.IO) {
            updatesDir.mkdirs()
            updatesDir.listFiles()?.forEach { it.delete() }
        }
        http.download(info.apkUrl, target, MAX_APK_BYTES, onProgress)
        withContext(Dispatchers.IO) {
            val actual = SafTree.sha256OfFile(target)
            if (actual != info.sha256) {
                target.delete()
                throw InvalidDataException("SHA-256 mismatch")
            }
            val archive = archiveInfo(target)
                ?: run { target.delete(); throw InvalidDataException("file is not a valid APK") }
            if (archive.packageName != context.packageName) {
                target.delete()
                throw InvalidDataException("APK package ${archive.packageName} is not this launcher")
            }
            if (PackageInfoCompat.getLongVersionCode(archive) <= AppConfig.versionCode) {
                target.delete()
                throw InvalidDataException("APK version is not newer")
            }
        }
        DiagnosticsLog.i("update", "downloaded and verified ${info.versionName}")
        return target
    }

    fun canRequestInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun installPermissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    fun installIntent(apk: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private fun archiveInfo(file: File): PackageInfo? {
        val pm = context.packageManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(file.absolutePath, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(file.absolutePath, 0)
        }
    }

    private companion object {
        const val MAX_APK_BYTES = 200L * 1024 * 1024
    }
}
