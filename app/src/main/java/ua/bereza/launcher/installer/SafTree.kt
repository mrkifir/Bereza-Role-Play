package ua.bereza.launcher.installer

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.InputStream
import java.security.MessageDigest

/** Допоміжні функції для роботи з папками через Storage Access Framework. */
object SafTree {

    fun takePersistablePermission(context: Context, uri: Uri, write: Boolean) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            (if (write) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
        context.contentResolver.takePersistableUriPermission(uri, flags)
    }

    fun releasePermission(context: Context, uri: Uri) {
        try {
            context.contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Дозволу вже немає — нічого не робимо.
        }
    }

    fun hasPersistedPermission(context: Context, uri: Uri, write: Boolean): Boolean =
        context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission && (!write || it.isWritePermission)
        }

    fun root(context: Context, uri: Uri): DocumentFile? =
        DocumentFile.fromTreeUri(context, uri)?.takeIf { it.exists() && it.isDirectory }

    fun displayName(context: Context, uri: Uri): String =
        DocumentFile.fromTreeUri(context, uri)?.name ?: uri.lastPathSegment ?: uri.toString()

    fun resolve(root: DocumentFile, path: String): DocumentFile? {
        var current: DocumentFile = root
        for (segment in path.split('/').filter { it.isNotEmpty() }) {
            current = current.findFile(segment) ?: return null
        }
        return current
    }

    /** Рекурсивно збирає всі файли: відносний шлях -> DocumentFile. */
    suspend fun listFilesRecursive(root: DocumentFile, maxFiles: Int = 20_000): List<Pair<String, DocumentFile>> {
        val result = ArrayList<Pair<String, DocumentFile>>()
        suspend fun walk(dir: DocumentFile, prefix: String) {
            for (child in dir.listFiles()) {
                currentCoroutineContext().ensureActive()
                val name = child.name ?: continue
                val path = if (prefix.isEmpty()) name else "$prefix/$name"
                if (child.isDirectory) {
                    walk(child, path)
                } else if (child.isFile) {
                    result += path to child
                    if (result.size > maxFiles) throw IllegalStateException("too many files (> $maxFiles)")
                }
            }
        }
        walk(root, "")
        return result.sortedBy { it.first }
    }

    suspend fun sha256(resolver: ContentResolver, uri: Uri, onBytes: (Long) -> Unit = {}): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val stream: InputStream = resolver.openInputStream(uri)
            ?: throw java.io.IOException("cannot open $uri")
        stream.use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
                onBytes(read.toLong())
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun sha256OfFile(file: java.io.File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
