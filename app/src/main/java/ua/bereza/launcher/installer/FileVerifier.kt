package ua.bereza.launcher.installer

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ua.bereza.launcher.diagnostics.DiagnosticsLog

sealed interface FileCheckResult {
    val entry: ManifestEntry

    /** Файл є, контрольна сума (або розмір) збігається. */
    data class Ok(override val entry: ManifestEntry) : FileCheckResult

    /** Файл є, але контрольну суму не задано — перевірено лише наявність. */
    data class Present(override val entry: ManifestEntry) : FileCheckResult

    data class Missing(override val entry: ManifestEntry) : FileCheckResult
    data class SizeMismatch(override val entry: ManifestEntry, val actual: Long) : FileCheckResult
    data class HashMismatch(override val entry: ManifestEntry, val actual: String) : FileCheckResult
    data class Unreadable(override val entry: ManifestEntry, val reason: String) : FileCheckResult
}

val FileCheckResult.isProblem: Boolean
    get() = when (this) {
        is FileCheckResult.Ok, is FileCheckResult.Present -> false
        is FileCheckResult.Missing -> entry.required
        else -> true
    }

data class VerifyProgress(val index: Int, val total: Int, val path: String)

interface FileVerifier {
    /** Перевіряє файли з маніфесту в обраній папці. */
    suspend fun verify(
        rootUri: Uri,
        manifest: GameFilesManifest,
        onProgress: (VerifyProgress) -> Unit,
    ): List<FileCheckResult>

    /** Сканує еталонну папку і будує маніфест із розмірами та SHA-256 усіх файлів. */
    suspend fun buildManifest(
        rootUri: Uri,
        name: String,
        onProgress: (VerifyProgress) -> Unit,
    ): GameFilesManifest
}

class SafFileVerifier(private val context: Context) : FileVerifier {

    override suspend fun verify(
        rootUri: Uri,
        manifest: GameFilesManifest,
        onProgress: (VerifyProgress) -> Unit,
    ): List<FileCheckResult> = withContext(Dispatchers.IO) {
        val root = SafTree.root(context, rootUri)
            ?: throw IllegalStateException("folder is not accessible")
        val resolver = context.contentResolver
        manifest.entries.mapIndexed { index, entry ->
            onProgress(VerifyProgress(index + 1, manifest.entries.size, entry.path))
            val file = SafTree.resolve(root, entry.path)
            when {
                file == null || !file.isFile -> FileCheckResult.Missing(entry)
                entry.sizeBytes != null && file.length() != entry.sizeBytes ->
                    FileCheckResult.SizeMismatch(entry, file.length())
                entry.sha256 == null -> FileCheckResult.Present(entry)
                else -> try {
                    val actual = SafTree.sha256(resolver, file.uri)
                    if (actual == entry.sha256) FileCheckResult.Ok(entry)
                    else FileCheckResult.HashMismatch(entry, actual)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    DiagnosticsLog.e("verify", "cannot read ${entry.path}", e)
                    FileCheckResult.Unreadable(entry, e.message ?: e.javaClass.simpleName)
                }
            }
        }.also { results ->
            val problems = results.count { it.isProblem }
            DiagnosticsLog.i("verify", "checked ${results.size} files, problems: $problems")
        }
    }

    override suspend fun buildManifest(
        rootUri: Uri,
        name: String,
        onProgress: (VerifyProgress) -> Unit,
    ): GameFilesManifest = withContext(Dispatchers.IO) {
        val root = SafTree.root(context, rootUri)
            ?: throw IllegalStateException("folder is not accessible")
        val files = SafTree.listFilesRecursive(root)
        val resolver = context.contentResolver
        val entries = files.mapIndexed { index, (path, file) ->
            onProgress(VerifyProgress(index + 1, files.size, path))
            ManifestEntry(
                path = path,
                required = true,
                sizeBytes = file.length(),
                sha256 = SafTree.sha256(resolver, file.uri),
            )
        }
        DiagnosticsLog.i("verify", "manifest built: ${entries.size} files")
        GameFilesManifest(name, entries)
    }
}
