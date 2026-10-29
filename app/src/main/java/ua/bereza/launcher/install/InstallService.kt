package ua.bereza.launcher.install

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ua.bereza.launcher.BerezaApp
import ua.bereza.launcher.MainActivity
import ua.bereza.launcher.R
import ua.bereza.launcher.core.Format

/** Foreground-сервіс: завантаження не переривається при згортанні лаунчера чи вимкненні екрана. */
class InstallService : LifecycleService() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var lastNotify = 0L

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val controller = (application as BerezaApp).container.installController

        if (intent?.action == ACTION_CANCEL) {
            controller.cancel()
            return START_NOT_STICKY
        }

        val mode = intent?.getStringExtra(EXTRA_MODE)?.let { runCatching { InstallMode.valueOf(it) }.getOrNull() }
            ?: InstallMode.INSTALL

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(controller.progress.value), type)

        acquireWakeLock()
        controller.runIn(lifecycleScope, mode) {
            releaseWakeLock()
            showResult(controller.progress.value)
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        lifecycleScope.launch {
            controller.progress.collectLatest { p ->
                val now = SystemClock.elapsedRealtime()
                if (p.phase.isRunning && now - lastNotify > 1000) {
                    lastNotify = now
                    notifySafe(NOTIF_ID, buildNotification(p))
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    private fun buildNotification(p: InstallProgress): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val cancel = PendingIntent.getService(
            this, 1, Intent(this, InstallService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = when (p.phase) {
            Phase.VERIFYING -> "Перевірка файлів"
            Phase.DOWNLOADING -> "Завантаження Bereza RP"
            Phase.EXTRACTING -> "Розпакування"
            Phase.CLIENT_DOWNLOAD -> "Завантаження клієнта"
            Phase.FINALIZING -> "Завершення"
            else -> "Підготовка"
        }
        val b = NotificationCompat.Builder(this, BerezaApp.CHANNEL_INSTALL)
            .setSmallIcon(R.drawable.ic_stat_bereza)
            .setContentTitle(title)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .addAction(0, "Скасувати", cancel)
        when (p.phase) {
            Phase.DOWNLOADING, Phase.CLIENT_DOWNLOAD -> {
                val pct = (p.downloadFraction * 1000).toInt()
                b.setProgress(1000, pct, p.bytesTotal <= 0)
                b.setContentText("${Format.bytes(p.bytesDone)} / ${Format.bytes(p.bytesTotal)} · ${Format.speed(p.speedBps)}")
            }
            Phase.VERIFYING, Phase.EXTRACTING -> {
                b.setProgress(1000, (p.stageFraction * 1000).toInt(), p.stageTotal <= 0)
                b.setContentText(p.currentFile ?: "")
            }
            else -> b.setProgress(0, 0, true)
        }
        return b.build()
    }

    private fun showResult(p: InstallProgress) {
        val (title, text) = when (p.phase) {
            Phase.DONE -> "Bereza RP готова" to "Натисніть «Грати», щоб зайти на сервер"
            Phase.AWAITING_CLIENT -> "Майже готово" to "Відкрийте лаунчер, щоб встановити клієнт"
            Phase.FAILED -> "Помилка встановлення" to (p.error?.message ?: "Відкрийте лаунчер для деталей")
            Phase.CANCELLED -> return
            else -> return
        }
        val open = PendingIntent.getActivity(
            this, 2, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(this, BerezaApp.CHANNEL_INSTALL)
            .setSmallIcon(R.drawable.ic_stat_bereza)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        notifySafe(NOTIF_RESULT_ID, n)
    }

    private fun notifySafe(id: Int, n: Notification) {
        val nm = NotificationManagerCompat.from(this)
        if (Build.VERSION.SDK_INT < 33 || nm.areNotificationsEnabled()) {
            try { nm.notify(id, n) } catch (_: SecurityException) { }
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(PowerManager::class.java) ?: return
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "bereza:install").apply {
            setReferenceCounted(false)
            acquire(3 * 60 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    companion object {
        private const val NOTIF_ID = 1001
        private const val NOTIF_RESULT_ID = 1002
        private const val EXTRA_MODE = "mode"
        private const val ACTION_CANCEL = "ua.bereza.launcher.CANCEL_INSTALL"

        fun start(context: Context, mode: InstallMode) {
            val i = Intent(context, InstallService::class.java).putExtra(EXTRA_MODE, mode.name)
            ContextCompat.startForegroundService(context, i)
        }
    }
}
