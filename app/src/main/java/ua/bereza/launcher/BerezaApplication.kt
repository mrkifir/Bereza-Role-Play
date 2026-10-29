package ua.bereza.launcher

import android.app.Application
import ua.bereza.launcher.diagnostics.DiagnosticsLog

class BerezaApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        DiagnosticsLog.i("app", "started v${BuildConfig.VERSION_NAME}")
    }
}
