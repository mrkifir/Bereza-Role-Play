package ua.bereza.launcher.diagnostics

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Кільцевий журнал подій лаунчера (у пам'яті) + дублювання в logcat. */
object DiagnosticsLog {
    private const val MAX_ENTRIES = 300
    private const val TAG = "BerezaLauncher"

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val _entries = MutableStateFlow<List<String>>(emptyList())
    val entries: StateFlow<List<String>> = _entries.asStateFlow()

    fun i(source: String, message: String) {
        Log.i(TAG, "[$source] $message")
        add("I", source, message)
    }

    fun w(source: String, message: String) {
        Log.w(TAG, "[$source] $message")
        add("W", source, message)
    }

    fun e(source: String, message: String, error: Throwable? = null) {
        Log.e(TAG, "[$source] $message", error)
        val details = error?.let { " (${it.javaClass.simpleName}: ${it.message})" } ?: ""
        add("E", source, message + details)
    }

    fun clear() {
        synchronized(this) { _entries.value = emptyList() }
    }

    private fun add(level: String, source: String, message: String) {
        synchronized(this) {
            val line = "${timeFormat.format(Date())} $level/$source: $message"
            _entries.value = (_entries.value + line).takeLast(MAX_ENTRIES)
        }
    }
}
