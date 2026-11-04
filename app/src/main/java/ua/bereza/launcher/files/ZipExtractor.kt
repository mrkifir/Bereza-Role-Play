package ua.bereza.launcher.files

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import ua.bereza.launcher.model.ExtractedEntry
import java.io.File
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

object ZipExtractor {
    /**
     * Розпаковує [zip] у [root]/[subDir]. Кожен файл спершу пишеться у *.tmp,
     * хеш рахується під час запису, потім файл атомарно перейменовується.
     * Повертає список розпакованих файлів (шляхи відносно [root]).
     */
    suspend fun extract(
        zip: File,
        root: File,
        subDir: String,
        onEntry: (name: String, bytesDone: Long) -> Unit = { _, _ -> },
    ): List<ExtractedEntry> {
        val result = mutableListOf<ExtractedEntry>()
        var done = 0L
        ZipInputStream(zip.inputStream().buffered(1 shl 18)).use { zin ->
            val buf = ByteArray(1 shl 18)
            while (true) {
                val entry = zin.nextEntry ?: break
                currentCoroutineContext().ensureActive()
                val rel = SafePath.joinRelative(subDir, entry.name)
                val target = SafePath.resolve(root, rel)
                if (entry.isDirectory) { target.mkdirs(); continue }
                target.parentFile?.mkdirs()
                val tmp = File(target.path + ".tmp")
                val md = MessageDigest.getInstance("SHA-256")
                var size = 0L
                DigestOutputStream(tmp.outputStream().buffered(1 shl 18), md).use { out ->
                    while (true) {
                        val n = zin.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        size += n
                        done += n
                    }
                }
                if (target.exists() && !target.delete()) throw java.io.IOException("Не вдалося замінити ${target.name}")
                if (!tmp.renameTo(target)) throw java.io.IOException("Не вдалося записати ${target.name}")
                val hash = md.digest().joinToString("") { "%02x".format(it) }
                result += ExtractedEntry(rel, size, hash)
                onEntry(rel, done)
            }
        }
        return result
    }
}
