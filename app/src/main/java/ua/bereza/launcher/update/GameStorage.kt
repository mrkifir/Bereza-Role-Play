package ua.bereza.launcher.update

import android.content.Context
import kotlinx.serialization.json.Json
import ua.bereza.launcher.files.SafePath
import ua.bereza.launcher.model.InstallState
import java.io.File

/**
 * Власна папка лаунчера: Android/data/<пакет>/files/game.
 * Дозволи на сховище не потрібні; видаляється разом із лаунчером.
 */
class GameStorage(context: Context) {
    private val base: File = context.getExternalFilesDir(null) ?: context.filesDir
    val gameDir: File = File(base, "game").apply { mkdirs() }
    val downloadDir: File = File(base, "download").apply { mkdirs() }
    private val stateFile = File(context.filesDir, "install_state.json")

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    @Synchronized
    fun loadState(): InstallState = runCatching {
        if (stateFile.exists()) json.decodeFromString(InstallState.serializer(), stateFile.readText()) else InstallState()
    }.getOrElse { InstallState() }

    @Synchronized
    fun saveState(state: InstallState) {
        val tmp = File(stateFile.path + ".tmp")
        tmp.writeText(json.encodeToString(InstallState.serializer(), state))
        if (!tmp.renameTo(stateFile)) { stateFile.delete(); tmp.renameTo(stateFile) }
    }

    fun resolve(path: String): File = SafePath.resolve(gameDir, path)

    fun partFile(path: String): File = File(downloadDir, path.replace('/', '_').replace('\\', '_') + ".part")

    fun usedBytes(): Long = gameDir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    fun freeBytes(): Long = gameDir.usableSpace

    fun wipe() {
        gameDir.deleteRecursively(); gameDir.mkdirs()
        downloadDir.deleteRecursively(); downloadDir.mkdirs()
        stateFile.delete()
    }
}
