package ua.bereza.launcher.net

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object Http {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }
}

object DropboxLinks {
    /**
     * Перетворює посилання Dropbox "поділитися" на пряме завантаження:
     *  - www.dropbox.com/s/… або /scl/fi/…?rlkey=…&dl=0  →  той самий url з dl=1
     *  - dl.dropboxusercontent.com/…                      →  без змін
     * Решта посилань повертається як є.
     */
    fun toDirect(url: String): String {
        val u = url.trim().toHttpUrlOrNull() ?: return url.trim()
        val host = u.host.lowercase()
        if (host != "dropbox.com" && host != "www.dropbox.com") return u.toString()
        return u.newBuilder()
            .host("www.dropbox.com")
            .removeAllQueryParameters("dl")
            .removeAllQueryParameters("raw")
            .addQueryParameter("dl", "1")
            .build()
            .toString()
    }

    /** Посилання на папку Dropbox віддаються як zip "на льоту" без докачування — попереджаємо. */
    fun isFolderLink(url: String): Boolean {
        val u = url.trim().toHttpUrlOrNull() ?: return false
        val segs = u.pathSegments
        return u.host.endsWith("dropbox.com") && (segs.firstOrNull() == "sh" || (segs.getOrNull(0) == "scl" && segs.getOrNull(1) == "fo"))
    }
}

object Urls {
    /** Відносний url з маніфесту → абсолютний від [base]. Абсолютні лишаються. Dropbox → dl=1. */
    fun resolve(base: String, url: String): String {
        val abs = if (url.startsWith("http://") || url.startsWith("https://")) url
        else base.toHttpUrlOrNull()?.resolve(url)?.toString() ?: url
        return DropboxLinks.toDirect(abs)
    }
}
