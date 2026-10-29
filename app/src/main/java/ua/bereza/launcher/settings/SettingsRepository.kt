package ua.bereza.launcher.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import ua.bereza.launcher.config.AppConfig
import java.io.IOException

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "bereza_settings")

data class LauncherSettings(
    val nickname: String = "",
    /** URI папки з даними гри (SAF tree), обраної користувачем. */
    val gameTreeUri: String? = null,
    val clientPackage: String = AppConfig.defaultClientPackage,
    val copyAddressOnLaunch: Boolean = true,
    val checkUpdatesOnStart: Boolean = true,
    /** Імпортований користувачем маніфест файлів (JSON) або null — вбудований. */
    val customManifestJson: String? = null,
)

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.settingsStore

    val settings: Flow<LauncherSettings> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            LauncherSettings(
                nickname = p[KEY_NICK] ?: "",
                gameTreeUri = p[KEY_GAME_TREE],
                clientPackage = p[KEY_CLIENT_PKG]?.takeIf { it.isNotBlank() } ?: AppConfig.defaultClientPackage,
                copyAddressOnLaunch = p[KEY_COPY_ADDR] ?: true,
                checkUpdatesOnStart = p[KEY_CHECK_UPDATES] ?: true,
                customManifestJson = p[KEY_MANIFEST],
            )
        }

    suspend fun current(): LauncherSettings = settings.first()

    suspend fun setNickname(value: String) = store.edit { it[KEY_NICK] = value }

    suspend fun setGameTreeUri(value: String?) = store.edit { prefs ->
        if (value == null) {
            prefs.remove(KEY_GAME_TREE)
        } else {
            prefs[KEY_GAME_TREE] = value
        }
    }

    suspend fun setClientPackage(value: String?) = store.edit { prefs ->
        if (value.isNullOrBlank()) {
            prefs.remove(KEY_CLIENT_PKG)
        } else {
            prefs[KEY_CLIENT_PKG] = value.trim()
        }
    }

    suspend fun setCopyAddressOnLaunch(value: Boolean) = store.edit { it[KEY_COPY_ADDR] = value }

    suspend fun setCheckUpdatesOnStart(value: Boolean) = store.edit { it[KEY_CHECK_UPDATES] = value }

    suspend fun setCustomManifest(json: String?) = store.edit { prefs ->
        if (json == null) {
            prefs.remove(KEY_MANIFEST)
        } else {
            prefs[KEY_MANIFEST] = json
        }
    }

    private companion object {
        val KEY_NICK = stringPreferencesKey("nickname")
        val KEY_GAME_TREE = stringPreferencesKey("game_tree_uri")
        val KEY_CLIENT_PKG = stringPreferencesKey("client_package")
        val KEY_COPY_ADDR = booleanPreferencesKey("copy_address_on_launch")
        val KEY_CHECK_UPDATES = booleanPreferencesKey("check_updates_on_start")
        val KEY_MANIFEST = stringPreferencesKey("custom_manifest_json")
    }
}
