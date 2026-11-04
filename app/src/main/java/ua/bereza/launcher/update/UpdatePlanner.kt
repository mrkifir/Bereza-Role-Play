package ua.bereza.launcher.update

import ua.bereza.launcher.model.InstallState
import ua.bereza.launcher.model.Manifest
import ua.bereza.launcher.model.ManifestFile

/** Що треба зробити, щоб локальна папка відповідала маніфесту. */
data class UpdatePlan(
    val manifest: Manifest,
    val toDownload: List<ManifestFile>,
    val toRemove: List<String>,
) {
    val downloadBytes: Long get() = toDownload.sumOf { it.size }
    val isEmpty: Boolean get() = toDownload.isEmpty() && toRemove.isEmpty()
}

object UpdatePlanner {
    /**
     * @param isValid чи файл з маніфесту вже встановлений і цілий
     *                (швидка перевірка за станом або повна — за SHA-256, вирішує виклик).
     * @param exists  чи існує шлях у папці гри (для списку видалення).
     */
    fun plan(
        manifest: Manifest,
        state: InstallState,
        isValid: (ManifestFile, InstallState) -> Boolean,
        exists: (String) -> Boolean,
    ): UpdatePlan {
        val download = manifest.files.filter { !isValid(it, state) }
        val remove = manifest.remove.filter(exists)
        return UpdatePlan(manifest, download, remove)
    }

    /** Перевірка маніфесту до будь-яких дій із файлами. */
    fun validate(manifest: Manifest): List<String> = buildList {
        if (manifest.schema != 2) add("Непідтримувана схема маніфесту: ${manifest.schema}")
        if (manifest.version.isBlank()) add("Порожня версія маніфесту")
        val seen = HashSet<String>()
        manifest.files.forEach { f ->
            if (!seen.add(f.path.lowercase())) add("Дубль шляху: ${f.path}")
            if (!Regex("^[0-9a-fA-F]{64}$").matches(f.sha256)) add("Неправильний SHA-256 у ${f.path}")
            if (f.size <= 0) add("Не вказано розмір ${f.path}")
            if (f.url.isBlank()) add("Немає посилання для ${f.path}")
        }
    }
}
