package ua.bereza.launcher.install

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import ua.bereza.launcher.BuildConfig
import ua.bereza.launcher.client.ClientProvider
import ua.bereza.launcher.client.ClientStatus
import ua.bereza.launcher.core.AppLog
import ua.bereza.launcher.core.ErrorCode
import ua.bereza.launcher.core.Format
import ua.bereza.launcher.core.LauncherException
import ua.bereza.launcher.data.InstalledStateStore
import ua.bereza.launcher.data.ManifestRepository
import ua.bereza.launcher.data.SafePath
import ua.bereza.launcher.data.SettingsRepository
import ua.bereza.launcher.data.StorageLocations
import ua.bereza.launcher.data.model.FileSource
import ua.bereza.launcher.data.model.GameManifest
import ua.bereza.launcher.data.model.InstalledFile
import ua.bereza.launcher.data.model.InstalledState
import ua.bereza.launcher.data.model.ManifestFile
import java.io.File
import java.io.IOException

/**
 * Встановлення / оновлення / перевірка ресурсів гри.
 *
 * Алгоритм:
 *  1. Маніфест → список файлів із розмірами та SHA-256.
 *  2. Перевірка: що вже встановлено й цілим (швидко за станом, або повним хешуванням).
 *  3. Перевірка вільного місця.
 *  4. Отримання лише відсутніх/змінених файлів у .bereza_staging (*.part, з докачуванням).
 *  5. SHA-256 кожного файлу → пошкоджений перезавантажується один раз автоматично.
 *  6. Атомарне переміщення / безпечне розпакування, запис стану після КОЖНОГО файлу
 *     (переривання в будь-який момент → наступний запуск продовжить з того ж місця).
 *  7. Видалення файлів, яких більше немає в маніфесті.
 *  8. Отримання клієнта (якщо дозволено маніфестом).
 */
class GameInstaller(
    private val context: Context,
    private val settings: SettingsRepository,
    private val storage: StorageLocations,
    private val stateStore: InstalledStateStore,
    private val manifests: ManifestRepository,
    private val providers: List<GameAssetsProvider>,
    private val clients: ClientProvider,
) {

    suspend fun run(mode: InstallMode, out: MutableStateFlow<InstallProgress>) = withContext(Dispatchers.IO) {
        out.value = InstallProgress(mode = mode, phase = Phase.PREPARING)
        try {
            val manifest = manifests.fetch()
            if (manifest.minLauncherVersionCode > BuildConfig.VERSION_CODE) {
                throw LauncherException(ErrorCode.LAUNCHER_OUTDATED, "Потрібна новіша версія лаунчера. Завантажте оновлення лаунчера.")
            }
            val userSettings = settings.current()
            val gameDir = storage.ensureGameDir(userSettings)
            val staging = storage.stagingDir(gameDir)
            out.update { it.copy(targetVersion = manifest.gameVersion) }

            var state = stateStore.load(gameDir)
            state = state.copy(pendingVersion = manifest.gameVersion)
            stateStore.save(gameDir, state)

            // ---- 2. Перевірка наявних файлів ----
            val deep = mode == InstallMode.VERIFY || mode == InstallMode.REPAIR
            val check = verifyExisting(manifest, gameDir, state, deep, out)
            state = check.state
            stateStore.save(gameDir, state)

            val needed = check.needed
            AppLog.i("Перевірено: ${check.okCount} цілих, потрібно отримати ${needed.size}")

            // ---- 3. Вільне місце ----
            out.update { it.copy(phase = Phase.CHECKING_SPACE) }
            val alreadyPartial = needed.sumOf { partFile(staging, it).let { p -> if (p.exists()) p.length().coerceAtMost(it.size) else 0L } }
            val downloadTotal = needed.sumOf { it.size }
            val required = (downloadTotal - alreadyPartial) + needed.filter { it.extract }.sumOf { it.unpackedSize } + SAFETY_MARGIN
            val free = storage.freeBytes(gameDir)
            out.update { it.copy(freeBytes = free, requiredBytes = required) }
            if (needed.isNotEmpty() && free in 0 until required) {
                throw LauncherException(
                    ErrorCode.NO_SPACE,
                    "Недостатньо місця: потрібно ${Format.bytes(required)}, вільно ${Format.bytes(free)}",
                )
            }

            // ---- 4–6. Отримання, перевірка, встановлення ----
            if (needed.any { it.source == FileSource.REMOTE }) ensureNetworkAllowed(userSettings.wifiOnly)
            val speed = SpeedMeter()
            out.update {
                it.copy(
                    phase = Phase.DOWNLOADING, fileCount = needed.size, fileIndex = 0,
                    bytesDone = alreadyPartial, bytesTotal = downloadTotal,
                )
            }
            needed.forEachIndexed { i, file ->
                out.update { it.copy(phase = Phase.DOWNLOADING, fileIndex = i + 1, currentFile = file.path) }
                state = installOne(file, gameDir, staging, state, out, speed)
                stateStore.save(gameDir, state)
            }

            // ---- 7. Видалення застарілих файлів ----
            out.update { it.copy(phase = Phase.FINALIZING, currentFile = null) }
            state = removeObsolete(manifest, gameDir, state)
            state = state.copy(gameVersion = manifest.gameVersion, pendingVersion = null, lastVerifiedAt = System.currentTimeMillis())
            stateStore.save(gameDir, state)
            staging.deleteRecursively()

            // ---- 8. Клієнт ----
            val spec = manifest.client
            val cs = clients.status(spec)
            val needClient = (cs is ClientStatus.NotInstalled && cs.canAutoInstall) || (cs is ClientStatus.Outdated && cs.canAutoInstall)
            if (spec != null && needClient) {
                out.update { it.copy(phase = Phase.CLIENT_DOWNLOAD, currentFile = spec.displayName.ifBlank { spec.packageName }, bytesDone = 0, bytesTotal = spec.apkSize) }
                val meter = SpeedMeter()
                clients.downloadApk(spec) { d -> progressBytes(out, d, meter) }
                out.update { it.copy(phase = Phase.AWAITING_CLIENT) }
                AppLog.i("APK клієнта готовий до встановлення")
            } else {
                out.update { it.copy(phase = Phase.DONE) }
                AppLog.i("Встановлення завершено: ${manifest.gameVersion}")
            }
        } catch (e: CancellationException) {
            out.update { it.copy(phase = Phase.CANCELLED, error = null) }
            AppLog.i("Процес скасовано — завантажені частини збережено")
            throw e
        } catch (e: LauncherException) {
            AppLog.e("Помилка встановлення [${e.error.code}]: ${e.message}", e.cause)
            out.update { it.copy(phase = Phase.FAILED, error = e) }
        } catch (e: IOException) {
            val le = LauncherException(ErrorCode.STORAGE, "Помилка запису файлів: ${e.message}", e)
            AppLog.e(le.message, e)
            out.update { it.copy(phase = Phase.FAILED, error = le) }
        } catch (e: Exception) {
            val le = LauncherException(ErrorCode.UNKNOWN, e.message ?: e.javaClass.simpleName, e)
            AppLog.e("Неочікувана помилка", e)
            out.update { it.copy(phase = Phase.FAILED, error = le) }
        }
    }

    /* ---------------- перевірка ---------------- */

    private class CheckResult(val needed: List<ManifestFile>, val state: InstalledState, val okCount: Int)

    private suspend fun verifyExisting(
        manifest: GameManifest,
        gameDir: File,
        initial: InstalledState,
        deep: Boolean,
        out: MutableStateFlow<InstallProgress>,
    ): CheckResult {
        var state = initial
        val needed = ArrayList<ManifestFile>()
        var ok = 0
        val toHashTotal = manifest.files.sumOf { f -> if (f.extract) 0L else f.size }
        out.update { it.copy(phase = Phase.VERIFYING, stageDone = 0, stageTotal = toHashTotal, fileCount = manifest.files.size, verifiedFiles = 0, badFiles = 0) }

        manifest.files.forEachIndexed { i, f ->
            out.update { it.copy(currentFile = f.path, fileIndex = i + 1) }
            val rec = state.files[f.path]
            val good: Boolean = if (f.extract) {
                rec != null && rec.sha256.equals(f.sha256, true) && rec.extracted.isNotEmpty() &&
                    rec.extracted.all { (p, size) -> runCatching { SafePath.resolveInside(gameDir, p) }.getOrNull()?.let { it.isFile && it.length() == size } == true }
            } else {
                val target = SafePath.resolveInside(gameDir, f.path)
                val quickOk = !deep && rec != null && target.isFile && target.length() == rec.size &&
                    target.lastModified() == rec.lastModified && hashAccepted(f, rec.sha256) &&
                    (f.size <= 0 || rec.size == f.size)
                if (quickOk) {
                    out.update { it.copy(stageDone = it.stageDone + f.size) }
                    true
                } else if (target.isFile && (f.size <= 0 || target.length() == f.size)) {
                    val sha = FileVerifier.sha256(target) { n -> out.update { it.copy(stageDone = it.stageDone + n) } }
                    if (hashAccepted(f, sha)) {
                        state = state.copy(files = state.files + (f.path to InstalledFile(sha, target.length(), lastModified = target.lastModified())))
                        true
                    } else false
                } else {
                    out.update { it.copy(stageDone = it.stageDone + f.size) }
                    false
                }
            }
            if (good) ok++ else needed += f
            out.update { it.copy(verifiedFiles = i + 1, badFiles = needed.size) }
        }
        return CheckResult(needed, state, ok)
    }

    private fun hashAccepted(f: ManifestFile, sha: String) =
        (f.source == FileSource.USER && f.acceptAnyHash) || sha.equals(f.sha256, ignoreCase = true)

    /* ---------------- встановлення одного файлу ---------------- */

    private suspend fun installOne(
        file: ManifestFile,
        gameDir: File,
        staging: File,
        initial: InstalledState,
        out: MutableStateFlow<InstallProgress>,
        speed: SpeedMeter,
    ): InstalledState {
        val provider = providers.firstOrNull { it.supports(file) }
            ?: throw LauncherException(ErrorCode.CONFIG, "Немає джерела для ${file.path}")
        val part = partFile(staging, file)
        var attempt = 0
        var sha = ""
        while (true) {
            try {
                provider.fetch(file, part) { d -> progressBytes(out, d, speed) }
            } catch (e: LauncherException) {
                if (file.optional) {
                    AppLog.w("Необов'язковий файл ${file.path} пропущено: ${e.message}")
                    return initial
                }
                throw e
            }
            if (file.size > 0 && part.length() != file.size) {
                if (file.source == FileSource.USER && file.acceptAnyHash) {
                    // розмір користувацької копії може відрізнятися — приймаємо
                } else {
                    out.update { it.copy(bytesDone = (it.bytesDone - part.length()).coerceAtLeast(0)) }
                    part.delete()
                    if (++attempt > 1) throw LauncherException(ErrorCode.CHECKSUM, "Розмір файлу ${file.path} не відповідає маніфесту")
                    continue
                }
            }
            sha = FileVerifier.sha256(part)
            if (hashAccepted(file, sha)) break
            AppLog.w("SHA-256 не збігся для ${file.path} (спроба ${attempt + 1})")
            out.update { it.copy(bytesDone = (it.bytesDone - part.length()).coerceAtLeast(0)) }
            part.delete()
            if (file.source == FileSource.USER) {
                throw LauncherException(ErrorCode.USER_ASSETS, "Файл «${file.path.substringAfterLast('/')}» з вашої папки має іншу версію. Потрібна сумісна копія гри.")
            }
            if (++attempt > 1) throw LauncherException(ErrorCode.CHECKSUM, "Файл ${file.path} пошкоджено на сервері (SHA-256 не збігається)")
        }

        return if (file.extract) {
            out.update { it.copy(phase = Phase.EXTRACTING, stageDone = 0, stageTotal = file.unpackedSize) }
            // Видаляємо файли попередньої версії цього архіву
            initial.files[file.path]?.extracted?.keys?.forEach { p ->
                runCatching { SafePath.resolveInside(gameDir, p).delete() }
            }
            val extracted = ArchiveExtractor.extract(part, gameDir, file.extractTo, file.unpackedSize) { n ->
                out.update { it.copy(stageDone = it.stageDone + n) }
            }
            part.delete()
            out.update { it.copy(phase = Phase.DOWNLOADING) }
            initial.copy(files = initial.files + (file.path to InstalledFile(sha, file.size, extracted)))
        } else {
            val target = SafePath.resolveInside(gameDir, file.path)
            moveInto(part, target)
            initial.copy(files = initial.files + (file.path to InstalledFile(sha, target.length(), lastModified = target.lastModified())))
        }
    }

    private fun moveInto(src: File, target: File) {
        target.parentFile?.mkdirs()
        if (target.exists() && !target.delete()) throw LauncherException(ErrorCode.STORAGE, "Не вдалося замінити ${target.name}")
        if (!src.renameTo(target)) {
            src.copyTo(target, overwrite = true)
            src.delete()
        }
    }

    private fun removeObsolete(manifest: GameManifest, gameDir: File, state: InstalledState): InstalledState {
        val current = manifest.files.map { it.path }.toSet()
        val obsolete = state.files.keys - current
        if (obsolete.isEmpty()) return state
        for (p in obsolete) {
            val rec = state.files[p] ?: continue
            runCatching {
                if (rec.extracted.isNotEmpty()) rec.extracted.keys.forEach { SafePath.resolveInside(gameDir, it).delete() }
                else SafePath.resolveInside(gameDir, p).delete()
            }
            AppLog.i("Видалено застарілий файл: $p")
        }
        return state.copy(files = state.files - obsolete)
    }

    private fun partFile(staging: File, f: ManifestFile): File {
        val key = f.sha256.ifBlank { Integer.toHexString(f.path.hashCode()) }.take(24)
        return File(staging, "$key.part")
    }

    private fun progressBytes(out: MutableStateFlow<InstallProgress>, delta: Long, meter: SpeedMeter) {
        meter.add(delta)
        out.update {
            val done = (it.bytesDone + delta).coerceAtLeast(0)
            val bps = meter.bytesPerSecond()
            val eta = if (bps > 0 && it.bytesTotal > 0) (it.bytesTotal - done).coerceAtLeast(0) / bps else -1
            it.copy(bytesDone = done, speedBps = bps, etaSeconds = eta)
        }
    }

    private fun ensureNetworkAllowed(wifiOnly: Boolean) {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
            ?: throw LauncherException(ErrorCode.NETWORK, "Немає підключення до інтернету")
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            throw LauncherException(ErrorCode.NETWORK, "Немає підключення до інтернету")
        }
        if (wifiOnly && !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) {
            throw LauncherException(ErrorCode.NETWORK, "Увімкнено «Лише Wi-Fi». Підключіться до Wi-Fi або змініть налаштування.")
        }
    }

    /** Швидке визначення стану без хешування — для головного екрана. */
    suspend fun quickStatus(): GameStatus = withContext(Dispatchers.IO) {
        val userSettings = settings.current()
        if (!storage.hasAccess(userSettings)) {
            return@withContext GameStatus.Blocked(LauncherException(ErrorCode.STORAGE, "Немає доступу до папки гри"))
        }
        val gameDir = storage.gameDir(userSettings)
        val state = stateStore.load(gameDir)
        var offline = false
        val manifest = try {
            manifests.fetch()
        } catch (e: LauncherException) {
            if (e.error == ErrorCode.CONFIG || e.error == ErrorCode.MANIFEST) return@withContext GameStatus.Blocked(e)
            offline = true
            manifests.cached()
        }
        if (manifest != null && manifest.minLauncherVersionCode > BuildConfig.VERSION_CODE) {
            return@withContext GameStatus.Blocked(LauncherException(ErrorCode.LAUNCHER_OUTDATED, "Потрібна новіша версія лаунчера"))
        }
        val installed = state.gameVersion
        if (state.pendingVersion != null) return@withContext GameStatus.Interrupted(state.pendingVersion)
        if (installed == null) return@withContext if (manifest == null) GameStatus.Unknown else GameStatus.NotInstalled
        if (manifest == null) return@withContext GameStatus.Installed(installed, offline = true)

        var damaged = 0
        var damagedBytes = 0L
        var changedBytes = 0L
        for (f in manifest.files) {
            val rec = state.files[f.path]
            val sameHash = rec != null && hashAccepted(f, rec.sha256)
            if (!sameHash) { changedBytes += f.size; continue }
            val present = if (f.extract) {
                rec!!.extracted.all { (p, s) -> runCatching { SafePath.resolveInside(gameDir, p) }.getOrNull()?.let { it.isFile && it.length() == s } == true }
            } else {
                runCatching { SafePath.resolveInside(gameDir, f.path) }.getOrNull()?.let { it.isFile && it.length() == rec!!.size } == true
            }
            if (!present) { damaged++; damagedBytes += f.size }
        }
        when {
            installed != manifest.gameVersion || changedBytes > 0 ->
                GameStatus.UpdateAvailable(installed, manifest.gameVersion, changedBytes + damagedBytes)
            damaged > 0 -> GameStatus.Damaged(damaged)
            else -> GameStatus.Installed(installed, offline)
        }
    }

    companion object {
        private const val SAFETY_MARGIN = 64L * 1024 * 1024
    }
}

/** Ковзне вікно 3 с для реальної швидкості завантаження. */
class SpeedMeter {
    private val samples = ArrayDeque<Pair<Long, Long>>()
    private var sum = 0L

    @Synchronized
    fun add(bytes: Long) {
        if (bytes <= 0) return
        val now = SystemClock.elapsedRealtime()
        samples.addLast(now to bytes); sum += bytes
        while (samples.isNotEmpty() && now - samples.first().first > 3000) sum -= samples.removeFirst().second
    }

    @Synchronized
    fun bytesPerSecond(): Long {
        if (samples.isEmpty()) return 0
        val now = SystemClock.elapsedRealtime()
        val span = (now - samples.first().first).coerceAtLeast(500)
        return sum * 1000 / span
    }
}
