package ua.bereza.launcher.data

import ua.bereza.launcher.network.HttpJsonClient

/**
 * Новини з JSON за адресою news.url:
 * { "news": [ { "id": "1", "title": "...", "date": "2026-10-29", "body": "...", "url": "https://...", "tag": "update" } ] }
 */
class NewsRepository(
    private val http: HttpJsonClient,
    private val url: String,
) {
    val isConfigured: Boolean get() = url.isNotBlank()

    suspend fun load(): List<NewsItem> {
        val root = http.getJson(url)
        val array = root.optJSONArray("news") ?: throw InvalidDataException("missing \"news\" array")
        val result = ArrayList<NewsItem>()
        for (i in 0 until minOf(array.length(), MAX_ITEMS)) {
            val obj = array.optJSONObject(i) ?: continue
            val title = obj.optString("title").trim()
            if (title.isEmpty()) continue
            val link = obj.optString("url").trim().takeIf { it.startsWith("https://") }
            result += NewsItem(
                id = obj.optString("id", i.toString()),
                title = title.take(200),
                date = obj.optString("date").trim().take(40),
                body = obj.optString("body").trim().take(5000),
                url = link,
                tag = obj.optString("tag").trim().takeIf { it.isNotEmpty() }?.take(30),
            )
        }
        return result
    }

    private companion object {
        const val MAX_ITEMS = 50
    }
}
