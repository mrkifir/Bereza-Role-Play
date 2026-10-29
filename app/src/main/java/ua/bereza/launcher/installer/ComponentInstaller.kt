package ua.bereza.launcher.installer

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import ua.bereza.launcher.diagnostics.DiagnosticsLog
import java.io.IOException

data class CopyProgress(
    val copiedBytes: Long,
    val totalBytes: Long,
    val filesDone: Int,
    val filesTotal: Int,
    val currentFile: String,
) {
    val fraction: Float get() = if (totalBytes <= 0) 0f else (copiedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
}

data class CopyReport(
    val copied: Int,
    val skipped: Int,
    val failed: List<String>,
    val totalBytes: Long,
)

interface ComponentInstaller {
    /**
     * Копіює вміст папки [source] у папку [target] (обидві обрані користувачем через SAF).
     * Файли з таким самим розміром пропускаються, якщо [overwrite] = false.
     */
    suspend fun copyTree(
        source: Uri,
        target: Uri,
        overwrite: Boolean,
        onProgress: (CopyProgress) -> Unit,
    ): CopyReport
}

class SafCopyInstaller(private val context: Context) : ComponentInstaller {

    override suspend fun copyTree(
        source: Uri,
        target: Uri,
        overwrite: Boolean,
        onProgress: (CopyProgress) -> Unit,
    ): CopyReport = withContext(Dispatchers.IO) {
        if (source == target) throw IllegalArgumentException("source and target are the same folder")
        val sourceRoot = SafTree.root(context, source) ?: throw IllegalStateException("source folder is not accessible")
        val targetRoot = SafTree.root(context, target) ?: throw IllegalStateException("target folder is not accessible")
        if (!targetRoot.canWrite()) throw IllegalStateException("no write access to target folder")

        val files = SafTree.listFilesRecursive(sourceRoot)
        val totalBytes = files.sumOf { it.second.length().coerceAtLeast(0) }
        DiagnosticsLog.i("install", "copy ${files.size} files, $totalBytes bytes")

        var copiedBytes = 0L
        var copied = 0
        var skipped = 0
        val failed = ArrayList<String>()
        val resolver = context.contentResolver
        val dirCache = HashMap<String, DocumentFile>()
        dirCache[""] = targetRoot

        files.forEachIndexed { index, (path, sourceFile) ->
            currentCoroutineContext().ensureActive()
            val size = sourceFile.length().coerceAtLeast(0)
            onProgress(CopyProgress(copiedBytes, totalBytes, index, files.size, path))
            try {
                val parentPath = path.substringBeforeLast('/', "")
                val fileName = path.substringAfterLast('/')
                val parent = ensureDir(targetRoot, parentPath, dirCache)
                val existing = parent.findFile(fileName)
                if (existing != null && existing.isFile && !overwrite && existing.length() == size) {
                    skipped++
                    copiedBytes += size
                } else {
                    val dest = when {
                        existing != null && existing.isFile -> existing
                        existing != null -> throw IOException("a folder with the same name exists")
                        else -> parent.createFile("application/octet-stream", fileName)
                            ?: throw IOException("cannot create file")
                    }
                    val baseBytes = copiedBytes
                    val input = resolver.openInputStream(sourceFile.uri) ?: throw IOException("cannot read source")
                    val output = resolver.openOutputStream(dest.uri, "wt") ?: throw IOException("cannot write target")
                    input.use { inp ->
                        output.use { out ->
                            val buffer = ByteArray(128 * 1024)
                            var written = 0L
                            var lastReport = 0L
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val read = inp.read(buffer)
                                if (read < 0) break
                                out.write(buffer, 0, read)
                                written += read
                                if (written - lastReport >= 512 * 1024) {
                                    lastReport = written
                                    onProgress(CopyProgress(baseBytes + written, totalBytes, index, files.size, path))
                                }
                            }
                        }
                    }
                    copiedBytes = baseBytes + size
                    copied++
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DiagnosticsLog.e("install", "failed: $path", e)
                failed += path
                copiedBytes += size
            }
        }
        onProgress(CopyProgress(totalBytes, totalBytes, files.size, files.size, ""))
        DiagnosticsLog.i("install", "done: copied=$copied skipped=$skipped failed=${failed.size}")
        CopyReport(copied, skipped, failed, totalBytes)
    }

    private fun ensureDir(root: DocumentFile, path: String, cache: MutableMap<String, DocumentFile>): DocumentFile {
        cache[path]?.let { return it }
        var current = root
        var currentPath = ""
        for (segment in path.split('/').filter { it.isNotEmpty() }) {
            currentPath = if (currentPath.isEmpty()) segment else "$currentPath/$segment"
            val cached = cache[currentPath]
            current = if (cached != null) {
                cached
            } else {
                val found = current.findFile(segment)
                val dir = when {
                    found != null && found.isDirectory -> found
                    found != null -> throw IOException("file blocks folder $currentPath")
                    else -> current.createDirectory(segment) ?: throw IOException("cannot create $currentPath")
                }
                cache[currentPath] = dir
                dir
            }
        }
        return current
    }
}
