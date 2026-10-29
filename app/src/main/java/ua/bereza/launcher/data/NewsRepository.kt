package ua.bereza.launcher.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import ua.bereza.launcher.core.AppJson
import ua.bereza.launcher.core.AppLog
import ua.bereza.launcher.core.ErrorCode
import ua.bereza.launcher.core.Http
import ua.bereza.launcher.core.LauncherException
import ua.bereza.launcher.data.model.NewsFeed
import ua.bereza.launcher.data.model.NewsItem
import java.io.File
import java.io.IOException

class NewsRepository(
    private val context: Context,
    private val configRepository: ConfigRepository,
) {
    private val cacheFile get() = File(context.cacheDir, "news_cache.json")

    val isConfigured: Boolean
        get() = configRepository.config.newsUrl.let { it.isNotBlank() && !it.contains("CHANGE_ME") }

    suspend fun fetch(): List<NewsItem> = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext emptyList()
        val url = configRepository.config.newsUrl
        val body = try {
            Http.client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) throw LauncherException(ErrorCode.NETWORK, "Новини: HTTP ${r.code}")
                r.body?.string().orEmpty()
            }
        } catch (e: IOException) {
            throw LauncherException(ErrorCode.NETWORK, "Не вдалося завантажити новини", e)
        }
        val feed = try {
            AppJson.decodeFromString(NewsFeed.serializer(), body)
        } catch (t: Throwable) {
            AppLog.w("Новини мають неправильний формат", t)
            throw LauncherException(ErrorCode.CONFIG, "Стрічка новин має неправильний формат")
        }
        runCatching { cacheFile.writeText(body) }
        sort(feed.items)
    }

    fun cached(): List<NewsItem> = runCatching {
        if (!cacheFile.exists()) emptyList()
        else sort(AppJson.decodeFromString(NewsFeed.serializer(), cacheFile.readText()).items)
    }.getOrDefault(emptyList())

    private fun sort(items: List<NewsItem>) =
        items.sortedWith(compareByDescending<NewsItem> { it.pinned }.thenByDescending { it.date })
}
