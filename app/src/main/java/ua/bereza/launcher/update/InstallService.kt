package ua.bereza.launcher.update

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ua.bereza.launcher.BerezaApp
import ua.bereza.launcher.MainActivity
import ua.bereza.launcher.R
import ua.bereza.launcher.files.formatBytes

/**
 * Foreground-сервіс: завантаження великих архівів не обривається,
 * коли гравець згортає лаунчер. Android 14+: тип dataSync.
 */
class InstallService : LifecycleService() {
    private var job: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_CANCEL) {
            job?.cancel(); stopSelf(); return START_NOT_STICKY
        }
        if (job?.isActive == true) return START_NOT_STICKY

        val graph = (application as BerezaApp).graph
        val plan = (graph.engine.state.value as? EngineState.NeedsUpdate)?.plan
            ?: run { stopSelf(); return START_NOT_STICKY }

        createChannel(this)
        ServiceCompat.startForeground(
            this, NOTIF_ID, build("Підготовка…", null, 0),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )

        val nm = getSystemService(NotificationManager::class.java)
        val watcher = lifecycleScope.launch {
            graph.engine.state.collectLatest { s ->
                if (s is EngineState.Working) {
                    val text = when (s.stage) {
                        Stage.DOWNLOAD -> "${s.fileName}: ${formatBytes(s.overallDone)} / ${formatBytes(s.overallTotal)}"
                        Stage.VERIFY -> "Перевірка ${s.fileName}"
                        Stage.EXTRACT -> "Розпакування ${s.fileName}"
                        else -> "Оновлення файлів"
                    }
                    nm.notify(NOTIF_ID, build(text, (s.fraction * 100).toInt(), s.fileIndex))
                }
            }
        }
        job = lifecycleScope.launch {
            try { graph.engine.install(plan) } finally {
                watcher.cancel()
                val end = graph.engine.state.value
                ServiceCompat.stopForeground(this@InstallService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                val msg = when (end) {
                    is EngineState.Ready -> "Файли гри встановлено"
                    is EngineState.Failed -> end.message
                    else -> null
                }
                if (msg != null) nm.notify(DONE_ID, NotificationCompat.Builder(this@InstallService, CHANNEL)
                    .setSmallIcon(R.drawable.ic_stat_bereza).setContentTitle("Bereza RP").setContentText(msg)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(msg))
                    .setContentIntent(openApp()).setAutoCancel(true).build())
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun build(text: String, percent: Int?, file: Int): Notification {
        val cancel = PendingIntent.getService(
            this, 1, Intent(this, InstallService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_bereza)
            .setContentTitle("Bereza RP — завантаження")
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .setProgress(100, percent ?: 0, percent == null)
            .setContentIntent(openApp())
            .addAction(0, "Зупинити", cancel)
            .build()
    }

    companion object {
        private const val CHANNEL = "install"
        private const val NOTIF_ID = 1001
        private const val DONE_ID = 1002
        private const val ACTION_CANCEL = "ua.bereza.launcher.CANCEL"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, InstallService::class.java))
        }

        fun cancel(context: Context) {
            context.startService(Intent(context, InstallService::class.java).setAction(ACTION_CANCEL))
        }

        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= 26) {
                context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                    NotificationChannel(CHANNEL, "Завантаження файлів гри", NotificationManager.IMPORTANCE_LOW),
                )
            }
        }
    }
}
