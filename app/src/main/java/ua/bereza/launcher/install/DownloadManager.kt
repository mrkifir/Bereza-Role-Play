package ua.bereza.launcher.install

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Request
import ua.bereza.launcher.core.AppLog
import ua.bereza.launcher.core.ErrorCode
import ua.bereza.launcher.core.Http
import ua.bereza.launcher.core.LauncherException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Завантажувач із докачуванням через HTTP Range.
 * Часткові дані зберігаються у файлі *.part і не втрачаються при обриві зв'язку або закритті лаунчера.
 */
class DownloadManager(
    private val maxAttempts: Int = 6,
) {
    /**
     * @param onBytes викликається з кількістю НОВИХ байтів (може бути від'ємним,
     *  якщо сервер не підтримує Range і завантаження почалося спочатку).
     */
    suspend fun download(
        url: String,
        dest: File,
        expectedSize: Long,
        onBytes: (Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        dest.parentFile?.mkdirs()
        var attempt = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            try {
                downloadOnce(url, dest, expectedSize, onBytes)
                return@withContext
            } catch (e: CancellationException) {
                throw e
            } catch (e: LauncherException) {
                throw e
            } catch (e: IOException) {
                attempt++
                if (attempt >= maxAttempts) {
                    throw LauncherException(ErrorCode.NETWORK, "Обрив з'єднання під час завантаження ${dest.name}. Спробуйте ще раз — завантаження продовжиться з місця зупинки.", e)
                }
                val wait = (1000L shl (attempt - 1)).coerceAtMost(20_000L)
                AppLog.w("Обрив завантаження ${dest.name}, спроба $attempt через ${wait} мс", e)
                delay(wait)
            }
        }
    }

    private suspend fun downloadOnce(url: String, dest: File, expectedSize: Long, onBytes: (Long) -> Unit) {
        var existing = if (dest.exists()) dest.length() else 0L
        if (expectedSize > 0 && existing > expectedSize) {
            onBytes(-existing); dest.delete(); existing = 0
        }
        if (expectedSize > 0 && existing == expectedSize) return

        val builder = Request.Builder().url(url).header("Accept-Encoding", "identity")
        if (existing > 0) builder.header("Range", "bytes=$existing-")
        val call = Http.client.newCall(builder.build())
        val job = currentCoroutineContext()[Job]
        val handle = job?.invokeOnCompletion { if (it != null) call.cancel() }

        try {
            call.execute().use { resp ->
                val append: Boolean = when (resp.code) {
                    206 -> {
                        val start = parseContentRangeStart(resp.header("Content-Range"))
                        if (start != existing) {
                            // Сервер віддав інший діапазон — починаємо заново.
                            onBytes(-existing); dest.delete(); existing = 0
                            throw IOException("Невідповідний Content-Range")
                        }
                        true
                    }
                    200 -> {
                        if (existing > 0) {
                            AppLog.w("Сервер не підтримує Range для ${dest.name} — завантаження спочатку")
                            onBytes(-existing); existing = 0
                        }
                        false
                    }
                    416 -> {
                        onBytes(-existing); dest.delete()
                        throw IOException("HTTP 416 — частковий файл застарів")
                    }
                    in 500..599 -> throw IOException("HTTP ${resp.code}")
                    else -> throw LauncherException(ErrorCode.NETWORK, "Сервер файлів відповів HTTP ${resp.code} для ${dest.name}")
                }
                val body = resp.body ?: throw IOException("Порожня відповідь")
                body.byteStream().use { input ->
                    FileOutputStream(dest, append).use { out ->
                        val buf = ByteArray(128 * 1024)
                        var written = existing
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            written += n
                            onBytes(n.toLong())
                            if (expectedSize > 0 && written > expectedSize) {
                                throw LauncherException(ErrorCode.CHECKSUM, "Файл ${dest.name} більший, ніж зазначено в маніфесті")
                            }
                        }
                        out.fd.sync()
                    }
                }
                if (expectedSize > 0 && dest.length() < expectedSize) throw IOException("Завантаження перервано")
            }
        } finally {
            handle?.dispose()
        }
    }

    private fun parseContentRangeStart(h: String?): Long? {
        // Формат: "bytes 1000-1999/5000"
        if (h == null) return null
        val m = Regex("""bytes\s+(\d+)-\d+/(\d+|\*)""").find(h) ?: return null
        return m.groupValues[1].toLongOrNull()
    }
}
