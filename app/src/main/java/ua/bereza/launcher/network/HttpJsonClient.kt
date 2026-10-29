package ua.bereza.launcher.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import ua.bereza.launcher.config.AppConfig
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

enum class HttpErrorKind { NOT_CONFIGURED, INSECURE_URL, BAD_URL, DNS, TIMEOUT, NETWORK, HTTP_STATUS, TOO_LARGE, BAD_JSON }

class HttpException(val kind: HttpErrorKind, message: String, val statusCode: Int = 0, cause: Throwable? = null) :
    Exception(message, cause)

/** Мінімальний HTTPS-клієнт на стандартному HttpURLConnection + org.json. */
class HttpJsonClient(
    private val connectTimeoutMs: Int = 8_000,
    private val readTimeoutMs: Int = 15_000,
) {
    private val userAgent = "BerezaLauncher/${AppConfig.versionName} (Android)"

    suspend fun getJson(url: String, maxBytes: Int = 1_000_000): JSONObject = withContext(Dispatchers.IO) {
        val bytes = request(url) { connection ->
            connection.inputStream.use { readLimited(it, maxBytes) }
        }
        try {
            JSONObject(String(bytes, Charsets.UTF_8))
        } catch (e: JSONException) {
            throw HttpException(HttpErrorKind.BAD_JSON, "Invalid JSON", cause = e)
        }
    }

    /** Завантаження файлу з прогресом. onProgress(завантажено, всього або -1). */
    suspend fun download(
        url: String,
        target: File,
        maxBytes: Long,
        onProgress: (Long, Long) -> Unit,
    ): Unit = withContext(Dispatchers.IO) {
        request(url) { connection ->
            val total = connection.contentLengthLong
            if (total > maxBytes) throw HttpException(HttpErrorKind.TOO_LARGE, "File too large: $total")
            target.parentFile?.mkdirs()
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    var lastReport = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        if (done > maxBytes) throw HttpException(HttpErrorKind.TOO_LARGE, "File too large")
                        if (done - lastReport >= 256 * 1024) {
                            lastReport = done
                            onProgress(done, total)
                        }
                    }
                    onProgress(done, total)
                }
            }
        }
    }

    private suspend fun <T> request(url: String, body: suspend (HttpURLConnection) -> T): T {
        if (url.isBlank()) throw HttpException(HttpErrorKind.NOT_CONFIGURED, "URL not configured")
        val parsed = try {
            URL(url)
        } catch (e: MalformedURLException) {
            throw HttpException(HttpErrorKind.BAD_URL, "Malformed URL", cause = e)
        }
        if (parsed.protocol != "https") throw HttpException(HttpErrorKind.INSECURE_URL, "Only https is allowed")

        val connection = try {
            parsed.openConnection() as HttpURLConnection
        } catch (e: IOException) {
            throw HttpException(HttpErrorKind.NETWORK, e.message ?: "Connection error", cause = e)
        }
        try {
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", userAgent)
            connection.setRequestProperty("Accept", "application/json, application/octet-stream, */*")
            val code = connection.responseCode
            if (code !in 200..299) {
                throw HttpException(HttpErrorKind.HTTP_STATUS, "HTTP $code", statusCode = code)
            }
            return body(connection)
        } catch (e: UnknownHostException) {
            throw HttpException(HttpErrorKind.DNS, e.message ?: "DNS error", cause = e)
        } catch (e: SocketTimeoutException) {
            throw HttpException(HttpErrorKind.TIMEOUT, "Timeout", cause = e)
        } catch (e: IOException) {
            throw HttpException(HttpErrorKind.NETWORK, e.message ?: "Network error", cause = e)
        } finally {
            connection.disconnect()
        }
    }

    private fun readLimited(input: InputStream, maxBytes: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) throw HttpException(HttpErrorKind.TOO_LARGE, "Response too large")
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }
}
