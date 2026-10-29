package ua.bereza.launcher.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.core.content.ContextCompat
import ua.bereza.launcher.core.AppJson
import ua.bereza.launcher.core.AppLog
import ua.bereza.launcher.core.ErrorCode
import ua.bereza.launcher.core.LauncherException
import ua.bereza.launcher.data.model.InstalledState
import java.io.File

class StorageLocations(private val context: Context) {

    @Suppress("DEPRECATION")
    fun gameDir(settings: UserSettings): File = when (settings.storageMode) {
        StorageMode.PRIVATE -> File(context.getExternalFilesDir(null) ?: context.filesDir, "game")
        StorageMode.SHARED -> File(Environment.getExternalStorageDirectory(), settings.sharedFolder.ifBlank { "BerezaRP" })
    }

    fun hasAccess(settings: UserSettings): Boolean = when (settings.storageMode) {
        StorageMode.PRIVATE -> true
        StorageMode.SHARED -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    fun ensureGameDir(settings: UserSettings): File {
        if (!hasAccess(settings)) {
            throw LauncherException(ErrorCode.STORAGE, "Немає доступу до спільної папки. Надайте дозвіл у налаштуваннях лаунчера.")
        }
        val dir = gameDir(settings)
        if (!dir.exists() && !dir.mkdirs()) {
            throw LauncherException(ErrorCode.STORAGE, "Не вдалося створити папку гри: ${dir.path}")
        }
        return dir
    }

    fun stagingDir(gameDir: File): File = File(gameDir, ".bereza_staging").apply { mkdirs() }

    fun freeBytes(dir: File): Long {
        var d: File? = dir
        while (d != null && !d.exists()) d = d.parentFile
        return runCatching { StatFs((d ?: dir).path).availableBytes }.getOrDefault(-1L)
    }
}

/** Зберігає стан встановлення поруч із файлами гри (.bereza_state.json), атомарно. */
class InstalledStateStore {

    fun load(gameDir: File): InstalledState {
        val f = File(gameDir, STATE)
        if (!f.exists()) return InstalledState()
        return runCatching { AppJson.decodeFromString(InstalledState.serializer(), f.readText()) }
            .onFailure { AppLog.w("Стан встановлення пошкоджено — буде виконано повну перевірку", it) }
            .getOrDefault(InstalledState())
    }

    @Synchronized
    fun save(gameDir: File, state: InstalledState) {
        gameDir.mkdirs()
        val tmp = File(gameDir, "$STATE.tmp")
        tmp.writeText(AppJson.encodeToString(InstalledState.serializer(), state))
        val target = File(gameDir, STATE)
        if (!tmp.renameTo(target)) {
            target.delete()
            if (!tmp.renameTo(target)) throw LauncherException(ErrorCode.STORAGE, "Не вдалося зберегти стан встановлення")
        }
    }

    companion object {
        const val STATE = ".bereza_state.json"
    }
}
