package ua.bereza.launcher.data

import android.content.Context
import ua.bereza.launcher.core.AppJson
import ua.bereza.launcher.core.AppLog
import ua.bereza.launcher.core.ErrorCode
import ua.bereza.launcher.core.LauncherException
import ua.bereza.launcher.data.model.LauncherConfig

/** Читає assets/launcher_config.json. Значення змінюються лише перезбиранням APK. */
class ConfigRepository(private val context: Context) {

    val config: LauncherConfig by lazy { load() }

    private fun load(): LauncherConfig = try {
        val text = context.assets.open("launcher_config.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        AppJson.decodeFromString(LauncherConfig.serializer(), text).also {
            AppLog.i("Конфігурацію завантажено: ${it.server.name}, маніфест=${it.manifestUrl}")
        }
    } catch (t: Throwable) {
        AppLog.e("Не вдалося прочитати launcher_config.json", t)
        throw LauncherException(ErrorCode.CONFIG, "Пошкоджено файл конфігурації лаунчера", t)
    }

    /** Список проблем конфігурації, які заважають роботі (порожній — усе гаразд). */
    fun problems(): List<String> {
        val c = config
        val p = mutableListOf<String>()
        if (c.manifestUrl.isBlank() || c.manifestUrl.contains("CHANGE_ME")) p += "Не задано manifestUrl"
        else if (!c.manifestUrl.startsWith("https://")) p += "manifestUrl має бути HTTPS"
        if (c.server.isPlaceholder) p += "Не задано адресу сервера (server.host)"
        if (c.server.port !in 1..65535) p += "Некоректний порт сервера"
        return p
    }
}
