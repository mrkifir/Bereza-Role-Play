package ua.bereza.launcher.installer

import org.json.JSONArray
import org.json.JSONObject
import ua.bereza.launcher.data.InvalidDataException

/**
 * Маніфест очікуваних файлів клієнта (шляхи відносно обраної папки гри).
 * {
 *   "name": "...",
 *   "files": [ { "path": "models/gta3.img", "required": true, "size": 123, "sha256": "..." } ]
 * }
 * size і sha256 необов'язкові: без них перевіряється лише наявність файлу.
 */
data class ManifestEntry(
    val path: String,
    val required: Boolean,
    val sizeBytes: Long?,
    val sha256: String?,
)

data class GameFilesManifest(
    val name: String,
    val entries: List<ManifestEntry>,
) {
    val isEmpty: Boolean get() = entries.isEmpty()

    fun toJson(): String {
        val files = JSONArray()
        entries.forEach { e ->
            files.put(
                JSONObject().apply {
                    put("path", e.path)
                    put("required", e.required)
                    e.sizeBytes?.let { put("size", it) }
                    e.sha256?.let { put("sha256", it) }
                }
            )
        }
        return JSONObject().apply {
            put("name", name)
            put("files", files)
        }.toString(2)
    }

    companion object {
        private val SHA_REGEX = Regex("^[0-9a-f]{64}$")
        private const val MAX_ENTRIES = 20_000

        fun parse(json: String): GameFilesManifest {
            val root = try {
                JSONObject(json)
            } catch (e: Exception) {
                throw InvalidDataException("manifest is not valid JSON")
            }
            val files = root.optJSONArray("files") ?: throw InvalidDataException("manifest: missing \"files\"")
            if (files.length() > MAX_ENTRIES) throw InvalidDataException("manifest: too many entries")
            val entries = ArrayList<ManifestEntry>(files.length())
            for (i in 0 until files.length()) {
                val obj = files.optJSONObject(i) ?: throw InvalidDataException("manifest: entry #$i")
                val path = normalizePath(obj.optString("path"))
                    ?: throw InvalidDataException("manifest: bad path in entry #$i")
                val sha = obj.optString("sha256").trim().lowercase().takeIf { it.isNotEmpty() }
                if (sha != null && !SHA_REGEX.matches(sha)) {
                    throw InvalidDataException("manifest: bad sha256 for $path")
                }
                val size = if (obj.has("size")) obj.optLong("size", -1L).takeIf { it >= 0 } else null
                entries += ManifestEntry(path, obj.optBoolean("required", true), size, sha)
            }
            return GameFilesManifest(root.optString("name", "manifest"), entries.distinctBy { it.path })
        }

        /** Відносний шлях без "..", без початкового "/", з прямими слешами. */
        fun normalizePath(raw: String): String? {
            val parts = raw.trim().replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." }
            if (parts.isEmpty() || parts.any { it == ".." }) return null
            return parts.joinToString("/")
        }
    }
}
