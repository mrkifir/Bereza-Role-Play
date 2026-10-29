package ua.bereza.launcher.client

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import ua.bereza.launcher.core.AppLog
import ua.bereza.launcher.core.ErrorCode
import ua.bereza.launcher.core.LauncherException
import java.util.concurrent.ConcurrentHashMap

/** Отримує результат сесії Package Installer і показує системний діалог підтвердження. */
class PackageInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val sessionId = intent.getIntExtra(EXTRA_SESSION, -1)
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(confirm) }
                        .onFailure { complete(sessionId, LauncherException(ErrorCode.CLIENT_INSTALL, "Не вдалося відкрити системний інсталятор", it)) }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                AppLog.i("Клієнт встановлено (сесія $sessionId)")
                pending.remove(sessionId)?.complete(true)
            }
            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                AppLog.w("Встановлення клієнта скасовано гравцем")
                pending.remove(sessionId)?.complete(false)
            }
            else -> {
                val text = when (status) {
                    PackageInstaller.STATUS_FAILURE_STORAGE -> "Недостатньо місця для встановлення клієнта"
                    PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "Клієнт несумісний із цим пристроєм"
                    PackageInstaller.STATUS_FAILURE_CONFLICT -> "Конфлікт із уже встановленою версією (інший підпис?)"
                    PackageInstaller.STATUS_FAILURE_BLOCKED -> "Встановлення заблоковано системою"
                    PackageInstaller.STATUS_FAILURE_INVALID -> "APK клієнта некоректний"
                    else -> "Помилка встановлення клієнта"
                }
                AppLog.e("$text: $message")
                complete(sessionId, LauncherException(ErrorCode.CLIENT_INSTALL, text))
            }
        }
    }

    private fun complete(sessionId: Int, e: Throwable) {
        pending.remove(sessionId)?.completeExceptionally(e)
    }

    companion object {
        const val ACTION = "ua.bereza.launcher.INSTALL_RESULT"
        const val EXTRA_SESSION = "session"
        private val pending = ConcurrentHashMap<Int, CompletableDeferred<Boolean>>()

        fun register(id: Int, d: CompletableDeferred<Boolean>) { pending[id] = d }
        fun unregister(id: Int) { pending.remove(id) }
    }
}
