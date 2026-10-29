package ua.bereza.launcher.install

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/** Потокова перевірка SHA-256 із реальним прогресом (байти). */
object FileVerifier {

    private const val BUF = 256 * 1024

    suspend fun sha256(file: File, onBytes: (Long) -> Unit = {}): String =
        file.inputStream().use { sha256(it, onBytes) }

    suspend fun sha256(input: InputStream, onBytes: (Long) -> Unit = {}): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(BUF)
        while (true) {
            currentCoroutineContext().ensureActive()
            val n = input.read(buf)
            if (n < 0) break
            md.update(buf, 0, n)
            onBytes(n.toLong())
        }
        return md.digest().toHex()
    }

    suspend fun matches(file: File, expectedSha: String, expectedSize: Long, onBytes: (Long) -> Unit = {}): Boolean {
        if (!file.isFile) return false
        if (expectedSize > 0 && file.length() != expectedSize) return false
        return sha256(file, onBytes).equals(expectedSha, ignoreCase = true)
    }

    fun ByteArray.toHex(): String {
        val sb = StringBuilder(size * 2)
        for (b in this) {
            val v = b.toInt() and 0xFF
            sb.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
        }
        return sb.toString()
    }

    private val HEX = "0123456789abcdef".toCharArray()
}
