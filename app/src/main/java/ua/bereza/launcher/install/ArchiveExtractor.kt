package ua.bereza.launcher.install

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import ua.bereza.launcher.core.ErrorCode
import ua.bereza.launcher.core.LauncherException
import ua.bereza.launcher.data.SafePath
import java.io.File
import java.util.zip.ZipException
import java.util.zip.ZipFile

/**
 * Безпечне розпакування ZIP:
 *  - захист від Zip Slip (кожен шлях перевіряється canonical-шляхом усередині цільової папки);
 *  - заборона абсолютних шляхів, "..", двокрапок;
 *  - обмеження сумарного розміру (захист від zip-бомб);
 *  - кожен файл спочатку пишеться у *.tmp і лише потім перейменовується.
 */
object ArchiveExtractor {

    private const val MAX_ENTRIES = 50_000

    /** @return розпаковані файли: шлях відносно gameDir → розмір */
    suspend fun extract(
        archive: File,
        gameDir: File,
        extractTo: String,
        declaredUnpackedSize: Long,
        onBytes: (Long) -> Unit,
    ): Map<String, Long> {
        val prefix = extractTo.trim('/').let { if (it.isEmpty()) "" else "$it/" }
        val limit = if (declaredUnpackedSize > 0) (declaredUnpackedSize * 11 / 10) + 1_048_576 else 16L shl 30
        val result = LinkedHashMap<String, Long>()
        var total = 0L

        val zip = try { ZipFile(archive) } catch (e: ZipException) {
            throw LauncherException(ErrorCode.ARCHIVE, "Архів ${archive.name} пошкоджено", e)
        }
        zip.use { z ->
            val entries = z.entries().toList()
            if (entries.size > MAX_ENTRIES) throw LauncherException(ErrorCode.ARCHIVE, "Забагато файлів в архіві")
            for (entry in entries) {
                currentCoroutineContext().ensureActive()
                val name = entry.name.replace('\\', '/')
                val clean = name.trimEnd('/')
                if (clean.isEmpty()) continue
                if (!SafePath.isSafeRelative(clean)) {
                    throw LauncherException(ErrorCode.ARCHIVE, "Архів містить небезпечний шлях: $name")
                }
                val rel = prefix + clean
                val target = SafePath.resolveInside(gameDir, rel)
                if (entry.isDirectory) { target.mkdirs(); continue }

                target.parentFile?.mkdirs()
                val tmp = File(target.path + ".tmp")
                var written = 0L
                z.getInputStream(entry).use { input ->
                    tmp.outputStream().use { out ->
                        val buf = ByteArray(128 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            written += n
                            total += n
                            if (total > limit) {
                                tmp.delete()
                                throw LauncherException(ErrorCode.ARCHIVE, "Розпакований розмір перевищує заявлений у маніфесті")
                            }
                            out.write(buf, 0, n)
                            onBytes(n.toLong())
                        }
                        out.fd.sync()
                    }
                }
                if (target.exists()) target.delete()
                if (!tmp.renameTo(target)) {
                    tmp.delete()
                    throw LauncherException(ErrorCode.STORAGE, "Не вдалося записати ${target.name}")
                }
                result[rel] = written
            }
        }
        return result
    }
}
