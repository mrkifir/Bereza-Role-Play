package ua.bereza.launcher.client

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ua.bereza.launcher.core.AppLog
import ua.bereza.launcher.core.ErrorCode
import ua.bereza.launcher.core.LauncherException
import ua.bereza.launcher.data.model.ClientSpec
import ua.bereza.launcher.install.DownloadManager
import ua.bereza.launcher.install.FileVerifier
import ua.bereza.launcher.install.FileVerifier.toHex
import java.io.File
import java.security.MessageDigest

sealed interface ClientStatus {
    /** У маніфесті не вказано packageName клієнта. */
    data object NotConfigured : ClientStatus
    data class NotInstalled(val canAutoInstall: Boolean, val infoUrl: String) : ClientStatus
    data class Outdated(val installedVersionCode: Long, val required: Long, val canAutoInstall: Boolean) : ClientStatus
    data object SignatureMismatch : ClientStatus
    data class Ready(val versionName: String, val versionCode: Long) : ClientStatus
}

/**
 * Отримання сумісного Android-клієнта SA-MP.
 * APK завантажується, лише якщо у маніфесті вказано apkUrl (ви маєте право розповсюдження),
 * перевіряється SHA-256 і (за наявності) сертифікат підпису, а встановлення виконує
 * системний Package Installer із підтвердженням гравця.
 */
class ClientProvider(
    private val context: Context,
    private val downloader: DownloadManager,
) {
    private val pm: PackageManager get() = context.packageManager

    fun status(spec: ClientSpec?): ClientStatus {
        if (spec == null || spec.packageName.isBlank()) return ClientStatus.NotConfigured
        val canAuto = spec.apkUrl.isNotBlank()
        val info = packageInfo(spec.packageName)
            ?: return ClientStatus.NotInstalled(canAuto, spec.infoUrl)
        if (spec.signingCertSha256.isNotBlank() && !certMatches(info, spec.signingCertSha256)) {
            return ClientStatus.SignatureMismatch
        }
        val vc = PackageInfoCompat.getLongVersionCode(info)
        if (vc < spec.minVersionCode) return ClientStatus.Outdated(vc, spec.minVersionCode, canAuto)
        return ClientStatus.Ready(info.versionName ?: vc.toString(), vc)
    }

    fun apkFile(spec: ClientSpec): File =
        File(File(context.cacheDir, "client").apply { mkdirs() }, "client-${spec.apkSha256.take(12)}.apk")

    /** Завантажує й перевіряє APK. Повертає перевірений файл. */
    suspend fun downloadApk(spec: ClientSpec, onBytes: (Long) -> Unit): File = withContext(Dispatchers.IO) {
        if (spec.apkUrl.isBlank()) throw LauncherException(ErrorCode.CLIENT_MISSING, "Джерело клієнта не налаштоване")
        val apk = apkFile(spec)
        val part = File(apk.path + ".part")
        if (apk.exists() && FileVerifier.matches(apk, spec.apkSha256, spec.apkSize)) return@withContext apk
        downloader.download(spec.apkUrl, part, spec.apkSize, onBytes)
        val sha = FileVerifier.sha256(part)
        if (!sha.equals(spec.apkSha256, ignoreCase = true)) {
            part.delete()
            throw LauncherException(ErrorCode.CHECKSUM, "Контрольна сума APK клієнта не збігається")
        }
        if (apk.exists()) apk.delete()
        if (!part.renameTo(apk)) throw LauncherException(ErrorCode.STORAGE, "Не вдалося зберегти APK клієнта")
        verifyArchive(spec, apk)
        apk
    }

    private fun verifyArchive(spec: ClientSpec, apk: File) {
        @Suppress("DEPRECATION")
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val info = pm.getPackageArchiveInfo(apk.path, flags)
            ?: throw LauncherException(ErrorCode.CLIENT_INSTALL, "Файл клієнта не є коректним APK")
        if (info.packageName != spec.packageName) {
            apk.delete()
            throw LauncherException(ErrorCode.CLIENT_SIGNATURE, "APK має інший package name: ${info.packageName}")
        }
        if (spec.signingCertSha256.isNotBlank() && !certMatches(info, spec.signingCertSha256)) {
            apk.delete()
            throw LauncherException(ErrorCode.CLIENT_SIGNATURE, "Підпис APK клієнта не збігається з очікуваним")
        }
    }

    fun canRequestPackageInstalls(): Boolean = pm.canRequestPackageInstalls()

    fun unknownSourcesSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Передає APK системному інсталятору. Система покаже гравцю діалог підтвердження.
     * Викликати, коли лаунчер на передньому плані.
     */
    suspend fun install(apk: File): Boolean = withContext(Dispatchers.IO) {
        if (!canRequestPackageInstalls()) {
            throw LauncherException(ErrorCode.CLIENT_INSTALL, "Дозвольте лаунчеру встановлювати застосунки (системне налаштування)")
        }
        val installer = pm.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
        }
        params.setSize(apk.length())
        val sessionId = installer.createSession(params)
        val deferred = CompletableDeferred<Boolean>()
        PackageInstallReceiver.register(sessionId, deferred)
        try {
            installer.openSession(sessionId).use { session ->
                session.openWrite("client.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out, 256 * 1024) }
                    session.fsync(out)
                }
                val intent = Intent(context, PackageInstallReceiver::class.java)
                    .setAction(PackageInstallReceiver.ACTION)
                    .putExtra(PackageInstallReceiver.EXTRA_SESSION, sessionId)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                val pi = PendingIntent.getBroadcast(context, sessionId, intent, flags)
                session.commit(pi.intentSender)
            }
            AppLog.i("APK клієнта передано Package Installer, сесія $sessionId")
            deferred.await()
        } catch (t: Throwable) {
            PackageInstallReceiver.unregister(sessionId)
            runCatching { installer.abandonSession(sessionId) }
            if (t is LauncherException) throw t
            throw LauncherException(ErrorCode.CLIENT_INSTALL, "Не вдалося встановити клієнт: ${t.message}", t)
        }
    }

    fun launchIntent(packageName: String): Intent? = pm.getLaunchIntentForPackage(packageName)

    @Suppress("DEPRECATION")
    private fun packageInfo(pkg: String): PackageInfo? = try {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        pm.getPackageInfo(pkg, flags)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    @Suppress("DEPRECATION")
    private fun certMatches(info: PackageInfo, expectedHex: String): Boolean {
        val sigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val si = info.signingInfo ?: return false
            if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory
        } else {
            info.signatures
        } ?: return false
        val expected = expectedHex.replace(":", "").lowercase()
        return sigs.any { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).toHex() == expected }
    }
}
