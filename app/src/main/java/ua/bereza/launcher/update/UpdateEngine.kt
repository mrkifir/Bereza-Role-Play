package ua.bereza.launcher.update

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import ua.bereza.launcher.BuildConfig
import ua.bereza.launcher.files.Downloader
import ua.bereza.launcher.files.Sha256
import ua.bereza.launcher.files.ZipExtractor
import ua.bereza.launcher.files.formatBytes
import ua.bereza.launcher.model.InstallState
import ua.bereza.launcher.model.InstalledFile
import ua.bereza.launcher.model.LauncherRelease
import ua.bereza.launcher.model.Manifest
import ua.bereza.launcher.model.ManifestFile
import ua.bereza.launcher.net.Urls
import java.io.File
import java.io.IOException

enum class Stage { CHECK_LOCAL, DOWNLOAD, VERIFY, EXTRACT, CLEANUP }

sealed interface EngineState {
    data object Idle : EngineState
    data object Checking : EngineState
    data class Ready(val version: String, val offline: Boolean, val launcherUpdate: LauncherRelease?) : EngineState
    data class NeedsUpdate(val plan: UpdatePlan, val firstInstall: Boolean, val launcherUpdate: LauncherRelease?) : EngineState
    data class Working(
        val stage: Stage,
        val fileName: String,
        val fileIndex: Int,
        val fileCount: Int,
        val fileDone: Long,
        val fileTotal: Long,
        val overallDone: Long,
        val overallTotal: Long,
        val speedBps: Long,
    ) : EngineState {
        val fraction: Float get() = if (overallTotal > 0) (overallDone.toFloat() / overallTotal).coerceIn(0f, 1f) else 0f
    }
    data class Failed(val message: String, val canRetry: Boolean = true) : EngineState
    /** Маніфест вимагає новішого лаунчера — грати не можна, доки не оновиться. */
    data class LauncherTooOld(val release: LauncherRelease?) : EngineState
}

class UpdateEngine(
    context: Context,
    private val storage: GameStorage,
    private val http: OkHttpClient,
    private val manifestUrl: () -> String,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val cacheFile = File(context.filesDir, "manifest_cache.json")
    private val downloader = Downloader(http)
    private val mutex = Mutex()

    private val _state = MutableStateFlow<EngineState>(EngineState.Idle)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    val isBusy: Boolean get() = mutex.isLocked

    private fun launcherUpdate(m: Manifest) = m.launcher?.takeIf { it.versionCode > BuildConfig.VERSION_CODE }

    suspend fun fetchManifest(): Manifest = withContext(Dispatchers.IO) {
        val url = Urls.resolve("", manifestUrl())
        val req = Request.Builder().url(url).header("Cache-Control", "no-cache").build()
        val text = http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Маніфест: HTTP ${r.code}")
            r.body?.string() ?: throw IOException("Маніфест порожній")
        }
        val m = try { json.decodeFromString(Manifest.serializer(), text) }
        catch (e: Exception) { throw IOException("Маніфест пошкоджений: ${e.message}") }
        val errors = UpdatePlanner.validate(m)
        if (errors.isNotEmpty()) throw IOException("Помилка в маніфесті: ${errors.first()}")
        cacheFile.writeText(text)
        m
    }

    private fun cachedManifest(): Manifest? = runCatching {
        json.decodeFromString(Manifest.serializer(), cacheFile.readText())
    }.getOrNull()

    /** Перевірити оновлення. [full] = повна перевірка SHA-256 усіх локальних файлів. */
    suspend fun check(full: Boolean = false) {
        if (!mutex.tryLock()) return
        try {
            _state.value = EngineState.Checking
            val manifest = try { fetchManifest() } catch (e: CancellationException) { throw e } catch (e: Exception) {
                val cached = cachedManifest()
                val local = storage.loadState()
                _state.value = if (cached != null && local.manifestVersion == cached.version && planFor(cached, false).toDownload.isEmpty())
                    EngineState.Ready(cached.version, offline = true, launcherUpdate = null)
                else EngineState.Failed("Не вдалося отримати маніфест: ${e.message}")
                return
            }
            if (manifest.minLauncherVersionCode > BuildConfig.VERSION_CODE) {
                _state.value = EngineState.LauncherTooOld(manifest.launcher); return
            }
            val plan = planFor(manifest, full)
            val local = storage.loadState()
            _state.value = if (plan.isEmpty) {
                if (local.manifestVersion != manifest.version) storage.saveState(local.copy(manifestVersion = manifest.version))
                EngineState.Ready(manifest.version, offline = false, launcherUpdate = launcherUpdate(manifest))
            } else EngineState.NeedsUpdate(plan, firstInstall = local.files.isEmpty(), launcherUpdate = launcherUpdate(manifest))
        } finally { mutex.unlock() }
    }

    private suspend fun planFor(manifest: Manifest, full: Boolean): UpdatePlan = withContext(Dispatchers.IO) {
        val state = storage.loadState()
        var i = 0
        UpdatePlanner.plan(
            manifest, state,
            isValid = { f, s ->
                i++
                if (full) _state.value = EngineState.Working(Stage.CHECK_LOCAL, f.title.ifBlank { f.path }, i, manifest.files.size, 0, f.size, i.toLong(), manifest.files.size.toLong(), 0)
                isInstalled(f, s, full)
            },
            exists = { runCatching { storage.resolve(it).exists() }.getOrDefault(false) },
        )
    }

    private fun isInstalled(f: ManifestFile, s: InstallState, full: Boolean): Boolean {
        val rec = s.files[f.path] ?: return false
        if (!Sha256.matches(rec.sha256, f.sha256)) return false
        return if (f.extractTo != null) {
            rec.extracted.isNotEmpty() && rec.extracted.all { e ->
                val file = runCatching { storage.resolve(e.path) }.getOrNull() ?: return@all false
                file.isFile && file.length() == e.size && (!full || Sha256.matches(Sha256.of(file), e.sha256))
            }
        } else {
            val file = runCatching { storage.resolve(f.path) }.getOrNull() ?: return false
            file.isFile && file.length() == f.size && (!full || Sha256.matches(Sha256.of(file), f.sha256))
        }
    }

    /** Завантажити й встановити план. Викликається з InstallService. */
    suspend fun install(plan: UpdatePlan) = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                runInstall(plan)
                _state.value = EngineState.Ready(plan.manifest.version, offline = false, launcherUpdate = launcherUpdate(plan.manifest))
            } catch (e: CancellationException) {
                _state.value = EngineState.Failed("Завантаження зупинено. Наступного разу продовжиться з того ж місця")
                throw e
            } catch (e: SecurityException) {
                _state.value = EngineState.Failed(e.message ?: "Небезпечний шлях у файлах", canRetry = false)
            } catch (e: Exception) {
                _state.value = EngineState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private suspend fun runInstall(plan: UpdatePlan) {
        val files = plan.toDownload
        val total = plan.downloadBytes
        val need = files.sumOf { it.size - storage.partFile(it.path).length().coerceAtMost(it.size) } +
            files.filter { it.extractTo != null }.sumOf { it.size } // запас під розпакування
        if (storage.freeBytes() < need) {
            throw IOException("Мало місця: потрібно ${formatBytes(need)}, вільно ${formatBytes(storage.freeBytes())}")
        }
        var finishedBytes = 0L
        val speed = SpeedMeter()
        files.forEachIndexed { index, f ->
            val name = f.title.ifBlank { f.path.substringAfterLast('/') }
            val url = Urls.resolve(plan.manifest.baseUrl, f.url)
            val part = storage.partFile(f.path)

            fun report(stage: Stage, done: Long, fileTotal: Long) {
                _state.value = EngineState.Working(stage, name, index + 1, files.size, done, fileTotal,
                    finishedBytes + done.coerceAtMost(f.size), total, speed.bps)
            }

            var verified = false
            for (attempt in 0..1) {
                downloader.download(url, part, f.size) { done, t -> speed.sample(finishedBytes + done); report(Stage.DOWNLOAD, done, t) }
                report(Stage.VERIFY, 0, f.size)
                val hash = Sha256.of(part) { report(Stage.VERIFY, it, f.size) }
                if (Sha256.matches(hash, f.sha256)) { verified = true; break }
                part.delete() // пошкоджений — качаємо ще раз з нуля
            }
            if (!verified) throw IOException("SHA-256 не збігається для $name після повторного завантаження. Перевір маніфест")

            val record = if (f.extractTo != null) {
                report(Stage.EXTRACT, 0, f.size)
                val old = storage.loadState().files[f.path]?.extracted.orEmpty()
                val entries = ZipExtractor.extract(part, storage.gameDir, f.extractTo) { _, d -> report(Stage.EXTRACT, d, f.size) }
                // Файли, що були в старій версії архіву, але зникли з нової.
                val now = entries.map { it.path }.toSet()
                old.filter { it.path !in now }.forEach { runCatching { storage.resolve(it.path).delete() } }
                part.delete()
                InstalledFile(f.sha256.lowercase(), f.size, entries)
            } else {
                val target = storage.resolve(f.path)
                target.parentFile?.mkdirs()
                if (target.exists() && !target.delete()) throw IOException("Не вдалося замінити $name")
                if (!part.renameTo(target)) { part.copyTo(target, overwrite = true); part.delete() }
                InstalledFile(f.sha256.lowercase(), f.size)
            }
            val st = storage.loadState()
            storage.saveState(st.copy(files = st.files + (f.path to record)))
            finishedBytes += f.size
        }

        if (plan.toRemove.isNotEmpty()) {
            _state.value = EngineState.Working(Stage.CLEANUP, "", files.size, files.size, 0, 0, total, total, 0)
            plan.toRemove.forEach { p -> runCatching { storage.resolve(p).deleteRecursively() } }
        }
        val st = storage.loadState()
        val keep = plan.manifest.files.map { it.path }.toSet()
        storage.saveState(st.copy(manifestVersion = plan.manifest.version, files = st.files.filterKeys { it in keep }))
    }

    fun reset() { if (!mutex.isLocked) _state.value = EngineState.Idle }

    private class SpeedMeter {
        private var lastT = System.nanoTime(); private var lastB = -1L
        var bps = 0L; private set
        fun sample(bytes: Long) {
            val t = System.nanoTime()
            if (lastB < 0) { lastB = bytes; lastT = t; return }
            val dt = t - lastT
            if (dt >= 1_000_000_000L) {
                val inst = ((bytes - lastB).coerceAtLeast(0) * 1_000_000_000L) / dt
                bps = if (bps == 0L) inst else (bps * 7 + inst * 3) / 10
                lastB = bytes; lastT = t
            }
        }
    }
}
