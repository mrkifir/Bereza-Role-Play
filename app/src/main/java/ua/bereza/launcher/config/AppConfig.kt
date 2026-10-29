package ua.bereza.launcher.config

import ua.bereza.launcher.BuildConfig

/**
 * Усі адреси, порти, версії та посилання беруться з bereza.properties
 * (через BuildConfig). Ніде в коді вони не дублюються.
 */
object AppConfig {
    val serverName: String = BuildConfig.SERVER_NAME
    val serverHost: String = BuildConfig.SERVER_HOST
    val serverPort: Int = BuildConfig.SERVER_PORT
    val serverAddress: String get() = "$serverHost:$serverPort"

    val defaultClientPackage: String = BuildConfig.CLIENT_PACKAGE
    val clientName: String = BuildConfig.CLIENT_NAME

    val nickRpFormat: Boolean = BuildConfig.NICK_RP_FORMAT

    val newsUrl: String = BuildConfig.NEWS_URL
    val updateUrl: String = BuildConfig.UPDATE_URL

    val discordUrl: String = BuildConfig.LINK_DISCORD
    val siteUrl: String = BuildConfig.LINK_SITE
    val telegramUrl: String = BuildConfig.LINK_TELEGRAM

    val versionName: String = BuildConfig.VERSION_NAME
    val versionCode: Int = BuildConfig.VERSION_CODE

    val isServerConfigured: Boolean get() = serverHost.isNotBlank() && serverPort in 1..65535
    val hasAnyLink: Boolean get() = listOf(discordUrl, siteUrl, telegramUrl).any { it.isNotBlank() }

    /** Інтервал автоматичного оновлення статусу сервера на головному екрані. */
    const val SERVER_POLL_INTERVAL_MS: Long = 30_000L
}
