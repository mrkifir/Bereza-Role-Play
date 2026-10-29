package ua.bereza.launcher.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import ua.bereza.launcher.core.AppJson
import ua.bereza.launcher.core.AppLog
import ua.bereza.launcher.core.ErrorCode
import ua.bereza.launcher.core.Http
import ua.bereza.launcher.core.LauncherException
import ua.bereza.launcher.data.model.FileSource
import ua.bereza.launcher.data.model.GameManifest
import java.io.File
import java.io.IOException

/** Отримує маніфест файлів та версій із manifestUrl, перевіряє його й кешує. */
class ManifestRepository(
    private val context: Context,
    private val configRepository: ConfigRepository,
) {
    private val cacheFile get() = File(context.filesDir, "manifest_cache.json")

    @Volatile
    var lastManifest: GameManifest? = null
        private set

    suspend fun fetch(): GameManifest = withContext(Dispatchers.IO) {
        val url = configRepository.config.manifestUrl
        if (url.isBlank() || url.contains("CHANGE_ME")) {
            throw LauncherException(ErrorCode.CONFIG, "Адресу маніфесту не налаштовано (launcher_config.json → manifestUrl)")
        }
        if (!url.startsWith("https://")) throw LauncherException(ErrorCode.CONFIG, "Маніфест має завантажуватись лише через HTTPS")

        val request = Request.Builder().url(url).header("Cache-Control", "no-cache").build()
        val body = try {
            Http.client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw LauncherException(ErrorCode.MANIFEST, "Сервер маніфесту відповів HTTP ${resp.code}")
                resp.body?.string() ?: throw LauncherException(ErrorCode.MANIFEST, "Порожня відповідь маніфесту")
            }
        } catch (e: LauncherException) {
            throw e
        } catch (e: IOException) {
            throw LauncherException(ErrorCode.NETWORK, "Немає з'єднання з сервером оновлень", e)
        }

        val manifest = try {
            AppJson.decodeFromString(GameManifest.serializer(), body)
        } catch (t: Throwable) {
            throw LauncherException(ErrorCode.MANIFEST, "Маніфест має неправильний формат: ${t.message}", t)
        }
        val resolved = resolveUrls(manifest, url)
        validate(resolved)
        runCatching { cacheFile.writeText(body) }
        lastManifest = resolved
        AppLog.i("Маніфест: версія ${resolved.gameVersion}, файлів ${resolved.files.size}")
        resolved
    }

    /** Останній збережений маніфест (для роботи без інтернету). */
    fun cached(): GameManifest? {
        lastManifest?.let { return it }
        val f = cacheFile
        if (!f.exists()) return null
        return runCatching {
            resolveUrls(AppJson.decodeFromString(GameManifest.serializer(), f.readText()), configRepository.config.manifestUrl)
        }.getOrNull()?.also { lastManifest = it }
    }

    private fun resolveUrls(m: GameManifest, base: String): GameManifest {
        val baseUrl = base.toHttpUrlOrNull() ?: return m
        fun res(u: String) = if (u.isBlank()) u else (baseUrl.resolve(u)?.toString() ?: u)
        return m.copy(
            files = m.files.map { it.copy(url = res(it.url)) },
            client = m.client?.let { it.copy(apkUrl = res(it.apkUrl)) },
        )
    }

    private fun validate(m: GameManifest) {
        val seen = HashSet<String>()
        for (f in m.files) {
            if (!SafePath.isSafeRelative(f.path)) fail("Небезпечний шлях у маніфесті: ${f.path}")
            if (f.extract && f.extractTo.isNotEmpty() && !SafePath.isSafeRelative(f.extractTo.trimEnd('/')))
                fail("Небезпечний extractTo: ${f.extractTo}")
            if (!seen.add(f.path.lowercase())) fail("Дубль шляху: ${f.path}")
            if (f.size < 0) fail("Від'ємний розмір: ${f.path}")
            val needHash = !(f.source == FileSource.USER && f.acceptAnyHash)
            if (needHash && !SHA_RE.matches(f.sha256)) fail("Некоректний SHA-256 для ${f.path}")
            if (f.source == FileSource.REMOTE && !f.url.startsWith("https://")) fail("Файл ${f.path} має бути на HTTPS")
        }
        m.client?.let { c ->
            if (c.apkUrl.isNotBlank()) {
                if (!c.apkUrl.startsWith("https://")) fail("APK клієнта має бути на HTTPS")
                if (!SHA_RE.matches(c.apkSha256)) fail("Для APK клієнта потрібен apkSha256")
            }
        }
        m.launch.configFile?.let { if (!SafePath.isSafeRelative(it.path)) fail("Небезпечний шлях configFile") }
    }

    private fun fail(msg: String): Nothing = throw LauncherException(ErrorCode.MANIFEST, msg)

    companion object {
        val SHA_RE = Regex("^[0-9a-fA-F]{64}$")
    }
}

object SafePath {
    /** Відносний шлях без "..", без абсолютних частин і керувальних символів. */
    fun isSafeRelative(p: String): Boolean {
        if (p.isBlank() || p.startsWith("/") || p.startsWith("\\") || p.contains('\u0000')) return false
        if (p.contains(':')) return false
        val parts = p.replace('\\', '/').split('/')
        return parts.none { it == ".." || it == "." || it.isEmpty() }
    }

    /** Повертає файл усередині root або кидає виняток (захист від виходу за межі директорії). */
    fun resolveInside(root: File, relative: String): File {
        val rootCanon = root.canonicalFile
        val target = File(rootCanon, relative.replace('\\', '/')).canonicalFile
        val rootPath = rootCanon.path + File.separator
        if (!target.path.startsWith(rootPath)) {
            throw LauncherException(ErrorCode.STORAGE, "Шлях виходить за межі папки гри: $relative")
        }
        return target
    }
}
