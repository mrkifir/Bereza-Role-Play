package ua.bereza.launcher.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "bereza_settings")

enum class StorageMode {
    /** Android/data/ua.bereza.launcher/files/game — без дозволів, але інші застосунки не бачать. */
    PRIVATE,

    /** Спільна папка /storage/emulated/0/<folder> — потрібен доступ до файлів, якщо клієнт читає звідти. */
    SHARED,
}

data class UserSettings(
    val nickname: String = "",
    val storageMode: StorageMode = StorageMode.PRIVATE,
    val sharedFolder: String = "BerezaRP",
    val wifiOnly: Boolean = false,
    val verifyBeforeLaunch: Boolean = false,
    val userAssetsTreeUri: String = "",
)

class SettingsRepository(private val context: Context) {

    private object K {
        val NICK = stringPreferencesKey("nickname")
        val MODE = stringPreferencesKey("storage_mode")
        val FOLDER = stringPreferencesKey("shared_folder")
        val WIFI = booleanPreferencesKey("wifi_only")
        val VERIFY = booleanPreferencesKey("verify_before_launch")
        val TREE = stringPreferencesKey("user_assets_tree")
    }

    val settings: Flow<UserSettings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): UserSettings = settings.first()

    suspend fun setNickname(v: String) = edit { it[K.NICK] = v.trim() }
    suspend fun setStorageMode(v: StorageMode) = edit { it[K.MODE] = v.name }
    suspend fun setSharedFolder(v: String) = edit { it[K.FOLDER] = v.trim().trim('/') }
    suspend fun setWifiOnly(v: Boolean) = edit { it[K.WIFI] = v }
    suspend fun setVerifyBeforeLaunch(v: Boolean) = edit { it[K.VERIFY] = v }
    suspend fun setUserAssetsTree(v: String) = edit { it[K.TREE] = v }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }

    private fun Preferences.toSettings() = UserSettings(
        nickname = this[K.NICK] ?: "",
        storageMode = this[K.MODE]?.let { runCatching { StorageMode.valueOf(it) }.getOrNull() } ?: StorageMode.PRIVATE,
        sharedFolder = this[K.FOLDER]?.takeIf { it.isNotBlank() } ?: "BerezaRP",
        wifiOnly = this[K.WIFI] ?: false,
        verifyBeforeLaunch = this[K.VERIFY] ?: false,
        userAssetsTreeUri = this[K.TREE] ?: "",
    )
}

object Nickname {
    private val SAMP_ALLOWED = Regex("^[A-Za-z0-9_\\[\\]().\$@=]{3,20}$")
    private val RP_FORMAT = Regex("^[A-Z][a-z]{1,15}_[A-Z][a-z]{1,15}$")

    /** null — нік коректний; інакше текст помилки. */
    fun validate(nick: String, requireRp: Boolean): String? = when {
        nick.isBlank() -> "Введіть нікнейм"
        !SAMP_ALLOWED.matches(nick) -> "3–20 символів: латиниця, цифри, _ [ ] ( ) . \$ @ ="
        requireRp && !RP_FORMAT.matches(nick) -> "RP-формат: Ім'я_Прізвище латиницею, напр. Ivan_Kovalenko"
        else -> null
    }
}
