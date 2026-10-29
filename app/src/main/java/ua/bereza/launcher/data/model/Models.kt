package ua.bereza.launcher.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/* ------------------------------------------------------------------ */
/*  Конфігурація лаунчера (assets/launcher_config.json)                */
/* ------------------------------------------------------------------ */

@Serializable
data class LauncherConfig(
    val configVersion: Int = 1,
    val server: ServerConfig = ServerConfig(),
    val manifestUrl: String = "",
    val newsUrl: String = "",
    /** Необов'язковий HTTP API статусу. Порожньо — використовується SA-MP query по UDP. */
    val statusApiUrl: String = "",
    val minLauncherVersionCode: Int = 1,
    val siteUrl: String = "",
    val discordUrl: String = "",
    val telegramUrl: String = "",
    /** Вимагати RP-формат нікнейму Ім'я_Прізвище. */
    val requireRpNickname: Boolean = true,
)

@Serializable
data class ServerConfig(
    val name: String = "Bereza Role Play",
    val host: String = "",
    val port: Int = 7777,
    val clientVersion: String = "",
) {
    /** true, якщо адресу ще не налаштовано (залишилось значення-заглушка). */
    val isPlaceholder: Boolean
        get() = host.isBlank() || host.contains("CHANGE_ME", ignoreCase = true)
}

/* ------------------------------------------------------------------ */
/*  Маніфест файлів (manifestUrl)                                      */
/* ------------------------------------------------------------------ */

@Serializable
data class GameManifest(
    val manifestVersion: Int = 1,
    /** Версія збірки ігрових ресурсів Bereza RP, напр. "1.0.3". */
    val gameVersion: String,
    val minLauncherVersionCode: Int = 1,
    /** Якщо задано — перевизначає блок server з локальної конфігурації. */
    val server: ServerConfig? = null,
    val files: List<ManifestFile> = emptyList(),
    val client: ClientSpec? = null,
    val launch: LaunchSpec = LaunchSpec(),
)

@Serializable
enum class FileSource {
    /** Файл завантажується з вашого HTTPS-сервера. */
    @SerialName("remote") REMOTE,

    /** Файл імпортується з копії гри, яку гравець законно має (вибір папки через SAF). */
    @SerialName("user") USER,
}

@Serializable
data class ManifestFile(
    /** Відносний шлях у папці гри. Для архіву — шлях, куди тимчасово кладеться сам архів. */
    val path: String,
    val size: Long,
    /** SHA-256 у hex (64 символи). Для USER-файлу може бути порожнім, якщо acceptAnyHash=true. */
    val sha256: String = "",
    val source: FileSource = FileSource.REMOTE,
    /** URL для REMOTE. Відносні URL розв'язуються від адреси маніфесту. */
    val url: String = "",
    /** true — це ZIP, який треба розпакувати в extractTo. */
    val extract: Boolean = false,
    val extractTo: String = "",
    /** Розмір після розпакування (для перевірки вільного місця). */
    val unpackedSize: Long = 0,
    /** Ім'я файлу, яке шукати в папці гравця (для USER). За замовчуванням — ім'я з path. */
    val userFileName: String = "",
    val acceptAnyHash: Boolean = false,
    val optional: Boolean = false,
)

@Serializable
data class ClientSpec(
    val displayName: String = "",
    /** Package name сумісного клієнта — з документації клієнта. Порожньо — не налаштовано. */
    val packageName: String = "",
    val minVersionCode: Long = 0,
    /** URL APK, лише якщо ви маєте право його розповсюджувати. */
    val apkUrl: String = "",
    val apkSha256: String = "",
    val apkSize: Long = 0,
    /** SHA-256 сертифіката підпису клієнта (hex). Якщо задано — перевіряється. */
    val signingCertSha256: String = "",
    /** Сторінка, де гравець може отримати клієнт сам (якщо APK не розповсюджується). */
    val infoUrl: String = "",
)

@Serializable
enum class LaunchMode {
    /** Лише запуск клієнта; адреса копіюється в буфер обміну. */
    @SerialName("none") NONE,

    /** Передача параметрів через Intent extras (ключі — з документації клієнта). */
    @SerialName("intent_extras") INTENT_EXTRAS,

    /** Відкриття URI за шаблоном (схема — з документації клієнта). */
    @SerialName("uri") URI,
}

@Serializable
data class LaunchSpec(
    val mode: LaunchMode = LaunchMode.NONE,
    /** Повне ім'я Activity клієнта. Порожньо — launch intent пакета. */
    val activity: String = "",
    /**
     * Ключ → шаблон значення. Підстановки: {host} {port} {nick} {password} {server_name}.
     * Префікс "int:" передає число, напр. "int:{port}".
     */
    val extras: Map<String, String> = emptyMap(),
    val uriTemplate: String = "",
    /** Файл налаштувань, який записується перед запуском (якщо клієнт так читає ник/адресу). */
    val configFile: ConfigFileSpec? = null,
)

@Serializable
data class ConfigFileSpec(
    /** Шлях відносно папки гри. */
    val path: String,
    /** Повний вміст файлу з підстановками {host} {port} {nick} ... */
    val template: String,
)

/* ------------------------------------------------------------------ */
/*  Новини (newsUrl)                                                   */
/* ------------------------------------------------------------------ */

@Serializable
data class NewsFeed(val items: List<NewsItem> = emptyList())

@Serializable
data class NewsItem(
    val id: String,
    val title: String,
    val body: String = "",
    /** ISO-дата "2026-10-29". */
    val date: String = "",
    val tag: String = "",
    val url: String = "",
    val pinned: Boolean = false,
)

/* ------------------------------------------------------------------ */
/*  Стан встановлення (зберігається локально)                          */
/* ------------------------------------------------------------------ */

@Serializable
data class InstalledState(
    val gameVersion: String? = null,
    /** Версія, яка встановлюється зараз (для відновлення після переривання). */
    val pendingVersion: String? = null,
    val files: Map<String, InstalledFile> = emptyMap(),
    val lastVerifiedAt: Long = 0,
)

@Serializable
data class InstalledFile(
    val sha256: String,
    val size: Long,
    /** Для архівів — розпаковані файли (відносний шлях → розмір). */
    val extracted: Map<String, Long> = emptyMap(),
    val lastModified: Long = 0,
)
