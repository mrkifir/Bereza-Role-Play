package ua.bereza.launcher.files

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

object Sha256 {
    fun of(file: File, onBytes: (Long) -> Unit = {}): String =
        file.inputStream().buffered(1 shl 18).use { of(it, onBytes) }

    fun of(input: InputStream, onBytes: (Long) -> Unit = {}): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(1 shl 18)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            md.update(buf, 0, n)
            total += n
            onBytes(total)
        }
        return md.digest().toHex()
    }

    fun of(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    private fun ByteArray.toHex(): String {
        val hex = "0123456789abcdef"
        val out = CharArray(size * 2)
        forEachIndexed { i, b ->
            val v = b.toInt() and 0xFF
            out[i * 2] = hex[v ushr 4]; out[i * 2 + 1] = hex[v and 0x0F]
        }
        return String(out)
    }

    fun matches(actual: String, expected: String) = actual.equals(expected.trim(), ignoreCase = true)
}

object SafePath {
    /**
     * Перетворює шлях з маніфесту/архіву на файл усередині [root].
     * Відхиляє абсолютні шляхи, "..", диски Windows — захист від zip-slip.
     */
    fun resolve(root: File, relative: String): File {
        val norm = relative.replace('\\', '/').trim()
        if (norm.isEmpty() || norm.startsWith("/") || norm.contains(':')) {
            throw SecurityException("Недозволений шлях: $relative")
        }
        val parts = norm.split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.any { it == ".." }) throw SecurityException("Недозволений шлях: $relative")
        val target = File(root, parts.joinToString(File.separator))
        val rootPath = root.canonicalFile.path + File.separator
        if (!target.canonicalFile.path.startsWith(rootPath)) {
            throw SecurityException("Шлях виходить за межі папки: $relative")
        }
        return target
    }

    fun joinRelative(dir: String, name: String): String {
        val d = dir.replace('\\', '/').trim('/')
        val n = name.replace('\\', '/').trimStart('/')
        return if (d.isEmpty()) n else "$d/$n"
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes Б"
    val units = listOf("КБ", "МБ", "ГБ")
    var v = bytes / 1024.0
    var i = 0
    while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
    return if (v >= 100) "%.0f %s".format(v, units[i]) else "%.1f %s".format(v, units[i])
}
