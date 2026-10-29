package ua.bereza.launcher.install

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Єдине джерело стану встановлення для UI та сервісу.
 * Сама робота виконується в [InstallService], щоб система не зупинила процес під час завантаження.
 */
class InstallController(private val installer: GameInstaller) {

    private val _progress = MutableStateFlow(InstallProgress())
    val progress: StateFlow<InstallProgress> = _progress.asStateFlow()

    @Volatile
    private var job: Job? = null

    val isRunning: Boolean get() = job?.isActive == true

    /** Запит з UI: стартує foreground-сервіс, який викличе [runIn]. */
    fun start(context: Context, mode: InstallMode) {
        if (isRunning) return
        _progress.value = InstallProgress(mode = mode, phase = Phase.PREPARING)
        InstallService.start(context, mode)
    }

    /** Викликається сервісом. */
    fun runIn(scope: CoroutineScope, mode: InstallMode, onFinished: () -> Unit): Job {
        job?.takeIf { it.isActive }?.let { return it }
        val j = scope.launch {
            try {
                installer.run(mode, _progress)
            } finally {
                onFinished()
            }
        }
        job = j
        return j
    }

    fun cancel() {
        job?.cancel()
    }

    /** Скинути завершений стан (після показу результату). */
    fun acknowledge() {
        if (!isRunning) _progress.value = InstallProgress()
    }

    fun markClientInstalled() {
        if (_progress.value.phase == Phase.AWAITING_CLIENT) _progress.value = _progress.value.copy(phase = Phase.DONE)
    }
}
