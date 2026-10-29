package ua.bereza.launcher.core

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

val AppJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
    prettyPrint = false
}

object Http {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(false) // HTTPS → HTTP редиректи заборонені
            .build()
    }
}

/** Коди помилок, які бачить гравець і які зручно передати адміністрації. */
enum class ErrorCode(val code: String) {
    NETWORK("BZ-NET"),
    MANIFEST("BZ-MAN"),
    CONFIG("BZ-CFG"),
    NO_SPACE("BZ-SPACE"),
    CHECKSUM("BZ-HASH"),
    ARCHIVE("BZ-ZIP"),
    STORAGE("BZ-STOR"),
    USER_ASSETS("BZ-ASSET"),
    CLIENT_MISSING("BZ-CLI"),
    CLIENT_SIGNATURE("BZ-SIG"),
    CLIENT_INSTALL("BZ-APK"),
    LAUNCH("BZ-RUN"),
    LAUNCHER_OUTDATED("BZ-OLD"),
    NICKNAME("BZ-NICK"),
    CANCELLED("BZ-STOP"),
    UNKNOWN("BZ-ERR"),
}

class LauncherException(
    val error: ErrorCode,
    override val message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** Невеликий журнал у пам'яті для екрана діагностики. */
object AppLog {
    private const val MAX = 400
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    fun i(msg: String) = add("I", msg, null)
    fun w(msg: String, t: Throwable? = null) = add("W", msg, t)
    fun e(msg: String, t: Throwable? = null) = add("E", msg, t)

    private fun add(level: String, msg: String, t: Throwable?) {
        val line = "${fmt.format(Date())} $level ${msg}" + (t?.let { " — ${it.javaClass.simpleName}: ${it.message}" } ?: "")
        when (level) {
            "E" -> Log.e("Bereza", msg, t)
            "W" -> Log.w("Bereza", msg, t)
            else -> Log.i("Bereza", msg)
        }
        _lines.update { (it + line).takeLast(MAX) }
    }
}

object Format {
    fun bytes(b: Long): String {
        if (b < 1024) return "$b Б"
        val units = arrayOf("КБ", "МБ", "ГБ", "ТБ")
        var v = b / 1024.0
        var i = 0
        while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
        return if (v >= 100) "%.0f %s".format(Locale.US, v, units[i])
        else "%.1f %s".format(Locale.US, v, units[i])
    }

    fun speed(bps: Long): String = bytes(bps) + "/с"

    fun eta(seconds: Long): String = when {
        seconds < 0 -> "—"
        seconds < 60 -> "${seconds} с"
        seconds < 3600 -> "${seconds / 60} хв ${seconds % 60} с"
        else -> "${seconds / 3600} год ${(seconds % 3600) / 60} хв"
    }
}
