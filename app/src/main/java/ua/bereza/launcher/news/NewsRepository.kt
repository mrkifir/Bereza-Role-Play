package ua.bereza.launcher.news

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import ua.bereza.launcher.model.NewsFeed
import ua.bereza.launcher.model.NewsItem
import ua.bereza.launcher.net.Urls
import java.io.File
import java.io.IOException

class NewsRepository(context: Context, private val http: OkHttpClient, private val url: () -> String) {
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = File(context.filesDir, "news_cache.json")

    data class Result(val items: List<NewsItem>, val fromCache: Boolean, val error: String?)

    fun cached(): List<NewsItem> = runCatching { sort(json.decodeFromString(NewsFeed.serializer(), cache.readText()).items) }
        .getOrDefault(emptyList())

    suspend fun refresh(): Result = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(Urls.resolve("", url())).header("Cache-Control", "no-cache").build()
            val text = http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
                r.body?.string().orEmpty()
            }
            val feed = json.decodeFromString(NewsFeed.serializer(), text)
            cache.writeText(text)
            Result(sort(feed.items), fromCache = false, error = null)
        } catch (e: Exception) {
            Result(cached(), fromCache = true, error = e.message ?: "Немає з'єднання")
        }
    }

    private fun sort(items: List<NewsItem>) = items.sortedWith(compareByDescending<NewsItem> { it.pinned }.thenByDescending { it.date })
}
