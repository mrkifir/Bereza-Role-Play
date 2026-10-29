package ua.bereza.launcher.launcher

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import ua.bereza.launcher.diagnostics.DiagnosticsLog

/**
 * Запуск клієнта через стандартний Android API getLaunchIntentForPackage —
 * це просто відкриття застосунку, як з іконки на робочому столі.
 *
 * Документованого способу передати клієнту адресу сервера чи нікнейм немає,
 * тому supportsDirectConnect = false: адреса копіюється в буфер обміну,
 * а гравець вставляє її в клієнті вручну.
 */
class PackageLaunchAdapter(
    private val context: Context,
    private val packageName: String,
) : GameClientLauncher {

    override val supportsDirectConnect: Boolean = false

    override fun availability(): ClientAvailability {
        if (packageName.isBlank()) return ClientAvailability.NotConfigured
        val pm = context.packageManager
        val info = try {
            packageInfo(pm, packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            return ClientAvailability.NotInstalled(packageName)
        }
        val label = info.applicationInfo?.let { pm.getApplicationLabel(it).toString() } ?: packageName
        return ClientAvailability.Installed(
            packageName = packageName,
            label = label,
            versionName = info.versionName ?: "?",
            versionCode = PackageInfoCompat.getLongVersionCode(info),
            launchable = pm.getLaunchIntentForPackage(packageName) != null,
        )
    }

    override fun launch(request: LaunchRequest): LaunchResult {
        when (val availability = availability()) {
            ClientAvailability.NotConfigured -> return LaunchResult.NotConfigured
            is ClientAvailability.NotInstalled -> return LaunchResult.NotInstalled
            is ClientAvailability.Installed -> if (!availability.launchable) return LaunchResult.NoLaunchActivity
        }
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return LaunchResult.NoLaunchActivity

        var copied = false
        if (request.copyAddress) {
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("Bereza RP", "${request.host}:${request.port}"))
                copied = true
            }
        }

        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            DiagnosticsLog.i("launch", "started $packageName (nick=${request.nickname})")
            LaunchResult.Started(directConnect = false, addressCopied = copied)
        } catch (e: ActivityNotFoundException) {
            DiagnosticsLog.e("launch", "no activity for $packageName", e)
            LaunchResult.NoLaunchActivity
        } catch (e: SecurityException) {
            DiagnosticsLog.e("launch", "security error", e)
            LaunchResult.Failed(e.message ?: "SecurityException")
        }
    }

    companion object {
        fun packageInfo(pm: PackageManager, pkg: String): PackageInfo =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, 0)
            }
    }
}
