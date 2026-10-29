package ua.bereza.launcher.install

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import ua.bereza.launcher.core.AppLog
import ua.bereza.launcher.core.ErrorCode
import ua.bereza.launcher.core.LauncherException
import ua.bereza.launcher.data.model.FileSource
import ua.bereza.launcher.data.model.ManifestFile
import java.io.File

/** Отримання ігрового ресурсу з дозволеного джерела в тимчасовий файл [dest]. */
interface GameAssetsProvider {
    fun supports(file: ManifestFile): Boolean
    suspend fun fetch(file: ManifestFile, dest: File, onBytes: (Long) -> Unit)
}

/** Файли проєкту Bereza RP з вашого HTTPS-сервера (моди, текстури, конфіги — те, на що у вас є права). */
class RemoteAssetsProvider(private val downloader: DownloadManager) : GameAssetsProvider {
    override fun supports(file: ManifestFile) = file.source == FileSource.REMOTE
    override suspend fun fetch(file: ManifestFile, dest: File, onBytes: (Long) -> Unit) {
        downloader.download(file.url, dest, file.size, onBytes)
    }
}

/**
 * Файли оригінальної гри, які гравець має законно (власна куплена копія).
 * Гравець один раз обирає папку через системний діалог (SAF), лаунчер копіює звідти потрібні файли
 * і перевіряє їх SHA-256 за маніфестом. Лаунчер нічого не завантажує з неофіційних джерел.
 */
class UserOwnedAssetsProvider(
    private val context: Context,
    private val treeUriProvider: suspend () -> String,
) : GameAssetsProvider {

    private var indexedUri: String? = null
    private var index: Map<String, DocumentFile> = emptyMap()

    override fun supports(file: ManifestFile) = file.source == FileSource.USER

    override suspend fun fetch(file: ManifestFile, dest: File, onBytes: (Long) -> Unit) = withContext(Dispatchers.IO) {
        val uriStr = treeUriProvider()
        if (uriStr.isBlank()) {
            throw LauncherException(
                ErrorCode.USER_ASSETS,
                "Потрібні файли вашої копії GTA San Andreas. Вкажіть папку з ними: Налаштування → Файли гри.",
            )
        }
        ensureIndex(uriStr)
        val wanted = file.userFileName.ifBlank { file.path.substringAfterLast('/') }.lowercase()
        val doc = index[wanted] ?: throw LauncherException(
            ErrorCode.USER_ASSETS,
            "У вибраній папці не знайдено файл «$wanted». Перевірте, що вказали папку вашої копії гри.",
        )
        dest.parentFile?.mkdirs()
        if (dest.exists()) { onBytes(-dest.length()); dest.delete() }
        val input = context.contentResolver.openInputStream(doc.uri)
            ?: throw LauncherException(ErrorCode.USER_ASSETS, "Немає доступу до файлу «$wanted»")
        input.use { inp ->
            dest.outputStream().use { out ->
                val buf = ByteArray(256 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = inp.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    onBytes(n.toLong())
                }
                out.fd.sync()
            }
        }
    }

    fun invalidate() { indexedUri = null; index = emptyMap() }

    private fun ensureIndex(uriStr: String) {
        if (indexedUri == uriStr) return
        val root = DocumentFile.fromTreeUri(context, Uri.parse(uriStr))
            ?: throw LauncherException(ErrorCode.USER_ASSETS, "Вибрана папка недоступна. Оберіть її знову.")
        if (!root.canRead()) throw LauncherException(ErrorCode.USER_ASSETS, "Доступ до вибраної папки втрачено. Оберіть її знову.")
        val map = HashMap<String, DocumentFile>()
        fun walk(dir: DocumentFile, depth: Int) {
            if (depth > 6) return
            for (child in dir.listFiles()) {
                val name = child.name ?: continue
                if (child.isDirectory) walk(child, depth + 1)
                else map.putIfAbsent(name.lowercase(), child)
            }
        }
        walk(root, 0)
        AppLog.i("Папка гравця проіндексована: ${map.size} файлів")
        index = map
        indexedUri = uriStr
    }
}
