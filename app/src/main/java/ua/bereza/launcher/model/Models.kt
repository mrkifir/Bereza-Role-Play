package ua.bereza.launcher.model

import kotlinx.serialization.Serializable

/** Вшита конфігурація лаунчера (assets/launcher_config.json). */
@Serializable
data class LauncherConfig(
    val server: ServerEndpoint,
    val manifestUrl: String,
    val newsUrl: String,
    val links: Links = Links(),
)

@Serializable
data class ServerEndpoint(
    val name: String,
    val host: String,
    val port: Int,
)

@Serializable
data class Links(
    val site: String = "",
    val discord: String = "",
    val telegram: String = "",
)

/**
 * Маніфест файлів (схема 2). Лежить за [LauncherConfig.manifestUrl].
 * Кожен файл має розмір і SHA-256; лаунчер качає лише ті, що відсутні або змінились.
 */
@Serializable
data class Manifest(
    val schema: Int = 2,
    val version: String,
    val minLauncherVersionCode: Int = 1,
    /** База для відносних url у [files]. Порожньо — url мають бути повними. */
    val baseUrl: String = "",
    val files: List<ManifestFile> = emptyList(),
    /** Шляхи, які треба видалити з ігрової папки (застарілі файли). */
    val remove: List<String> = emptyList(),
    val launcher: LauncherRelease? = null,
)

@Serializable
data class ManifestFile(
    /** Шлях відносно ігрової папки. Для архівів — ім'я архіву (тимчасове). */
    val path: String,
    val url: String,
    val size: Long,
    val sha256: String,
    /** Якщо задано — це ZIP, який розпаковується в цю підпапку ("" = корінь). */
    val extractTo: String? = null,
    val required: Boolean = true,
    val title: String = "",
)

@Serializable
data class LauncherRelease(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val size: Long,
    val notes: String = "",
)

@Serializable
data class NewsFeed(val items: List<NewsItem> = emptyList())

@Serializable
data class NewsItem(
    val id: String,
    val title: String,
    val body: String,
    val date: String = "",
    val tag: String = "",
    val url: String = "",
    val pinned: Boolean = false,
)

/** Локальний стан встановлення (files/install_state.json). */
@Serializable
data class InstallState(
    val manifestVersion: String = "",
    val files: Map<String, InstalledFile> = emptyMap(),
)

@Serializable
data class InstalledFile(
    val sha256: String,
    val size: Long,
    /** Для архівів — розпаковані файли з їхніми хешами. */
    val extracted: List<ExtractedEntry> = emptyList(),
)

@Serializable
data class ExtractedEntry(
    val path: String,
    val size: Long,
    val sha256: String,
)
