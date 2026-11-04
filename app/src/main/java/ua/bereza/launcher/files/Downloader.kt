package ua.bereza.launcher.files

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class DownloadException(message: String, val retryable: Boolean = false) : IOException(message)

/**
 * Завантажувач із докачуванням: дані пишуться в [part]; якщо файл уже частково є —
 * надсилається `Range: bytes=N-`. Сервер відповів 206 — дописуємо, 200 — починаємо спочатку.
 * Обрив мережі → повтор із того ж місця (до [maxAttempts] разів).
 */
class Downloader(
    private val client: OkHttpClient,
    private val maxAttempts: Int = 6,
    private val retryDelayMs: Long = 2000,
) {
    fun interface Progress { fun onProgress(done: Long, total: Long) }

    suspend fun download(url: String, part: File, expectedSize: Long, progress: Progress): File =
        withContext(Dispatchers.IO) {
            part.parentFile?.mkdirs()
            var attempt = 0
            while (true) {
                currentCoroutineContext().ensureActive()
                try {
                    if (expectedSize > 0 && part.length() == expectedSize) {
                        progress.onProgress(expectedSize, expectedSize)
                        return@withContext part
                    }
                    if (expectedSize > 0 && part.length() > expectedSize) part.delete()
                    once(url, part, expectedSize, progress)
                    return@withContext part
                } catch (e: CancellationException) {
                    throw e
                } catch (e: DownloadException) {
                    if (!e.retryable || ++attempt >= maxAttempts) throw e
                } catch (e: IOException) {
                    if (++attempt >= maxAttempts) throw DownloadException("Мережа: ${e.message ?: e.javaClass.simpleName}")
                }
                delay(retryDelayMs * attempt)
            }
            @Suppress("UNREACHABLE_CODE") part
        }

    private suspend fun once(url: String, part: File, expectedSize: Long, progress: Progress) {
        val have = part.length()
        val req = Request.Builder().url(url)
            .header("User-Agent", "BerezaRP-Launcher")
            .apply { if (have > 0) header("Range", "bytes=$have-") }
            .build()
        client.newCall(req).execute().use { resp ->
            val append: Boolean = when (resp.code) {
                206 -> {
                    // Перевіряємо, що сервер продовжує саме з нашого байта.
                    val start = resp.header("Content-Range")?.let { Regex("""bytes (\d+)-""").find(it)?.groupValues?.get(1)?.toLongOrNull() }
                    if (start != null && start != have) { part.delete(); throw DownloadException("Сервер змістив докачування", retryable = true) }
                    true
                }
                200 -> false
                416 -> {
                    // Range поза файлом: або вже все скачано, або локальний шматок зіпсований.
                    if (expectedSize > 0 && have == expectedSize) return
                    part.delete(); throw DownloadException("Почнемо завантаження спочатку", retryable = true)
                }
                404 -> throw DownloadException("Файл не знайдено на сервері (404)")
                401, 403 -> throw DownloadException("Доступ до файлу заборонено (${resp.code}). Перевір посилання Dropbox")
                429 -> throw DownloadException("Забагато запитів (429), повтор пізніше", retryable = true)
                in 500..599 -> throw DownloadException("Помилка сервера ${resp.code}", retryable = true)
                else -> throw DownloadException("HTTP ${resp.code}")
            }
            val type = resp.header("Content-Type").orEmpty()
            if (type.startsWith("text/html")) {
                throw DownloadException("Посилання веде на вебсторінку, а не на файл. Для Dropbox потрібне dl=1")
            }
            val body = resp.body ?: throw DownloadException("Порожня відповідь", retryable = true)
            val total = if (expectedSize > 0) expectedSize else {
                val len = body.contentLength()
                if (len > 0) (if (append) have + len else len) else -1
            }
            var done = if (append) have else 0L
            var lastReport = 0L
            FileOutputStream(part, append).use { out ->
                body.byteStream().use { input ->
                    val buf = ByteArray(1 shl 18)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (expectedSize > 0 && done > expectedSize) {
                            out.close(); part.delete()
                            throw DownloadException("Файл більший, ніж у маніфесті — маніфест застарів")
                        }
                        val now = System.nanoTime()
                        if (now - lastReport > 150_000_000) { lastReport = now; progress.onProgress(done, total) }
                    }
                }
            }
            progress.onProgress(done, total)
            if (expectedSize > 0 && done < expectedSize) {
                throw DownloadException("З'єднання обірвалось ($done з $expectedSize)", retryable = true)
            }
        }
    }
}
