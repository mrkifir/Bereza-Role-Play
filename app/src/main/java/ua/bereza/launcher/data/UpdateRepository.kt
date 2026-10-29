package ua.bereza.launcher.data

import ua.bereza.launcher.network.HttpJsonClient

/**
 * Інформація про останню версію лаунчера з JSON за адресою update.url:
 * {
 *   "versionCode": 2, "versionName": "1.1.0", "date": "2026-11-01",
 *   "apkUrl": "https://.../BerezaLauncher.apk",
 *   "sha256": "64 hex-символи", "size": 12345678,
 *   "changelog": ["Пункт 1", "Пункт 2"]
 * }
 */
class UpdateRepository(
    private val http: HttpJsonClient,
    private val url: String,
) {
    val isConfigured: Boolean get() = url.isNotBlank()

    suspend fun load(): LauncherUpdateInfo {
        val obj = http.getJson(url)
        val versionCode = obj.optInt("versionCode", -1)
        if (versionCode <= 0) throw InvalidDataException("versionCode")
        val versionName = obj.optString("versionName").trim()
        if (versionName.isEmpty()) throw InvalidDataException("versionName")
        val sha = obj.optString("sha256").trim().lowercase()
        val changelogArray = obj.optJSONArray("changelog")
        val changelog = buildList {
            if (changelogArray != null) {
                for (i in 0 until minOf(changelogArray.length(), 30)) {
                    val line = changelogArray.optString(i).trim()
                    if (line.isNotEmpty()) add(line.take(300))
                }
            }
        }
        return LauncherUpdateInfo(
            versionCode = versionCode,
            versionName = versionName.take(40),
            apkUrl = obj.optString("apkUrl").trim(),
            sha256 = if (SHA_REGEX.matches(sha)) sha else "",
            sizeBytes = obj.optLong("size", -1L),
            date = obj.optString("date").trim().take(40),
            changelog = changelog,
        )
    }

    private companion object {
        val SHA_REGEX = Regex("^[0-9a-f]{64}$")
    }
}
