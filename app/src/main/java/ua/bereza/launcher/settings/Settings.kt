package ua.bereza.launcher.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class UserSettings(
    val nickname: String = "",
    val wifiOnly: Boolean = false,
    val autoCheck: Boolean = true,
    val manifestUrlOverride: String = "",
    val newsUrlOverride: String = "",
)

private val Context.dataStore by preferencesDataStore("settings")

class SettingsRepository(private val context: Context) {
    private object K {
        val nick = stringPreferencesKey("nickname")
        val wifi = booleanPreferencesKey("wifi_only")
        val auto = booleanPreferencesKey("auto_check")
        val manifest = stringPreferencesKey("manifest_url")
        val news = stringPreferencesKey("news_url")
    }

    val flow: Flow<UserSettings> = context.dataStore.data.map { p ->
        UserSettings(
            nickname = p[K.nick].orEmpty(),
            wifiOnly = p[K.wifi] ?: false,
            autoCheck = p[K.auto] ?: true,
            manifestUrlOverride = p[K.manifest].orEmpty(),
            newsUrlOverride = p[K.news].orEmpty(),
        )
    }

    suspend fun current(): UserSettings = flow.first()

    suspend fun setNickname(v: String) = context.dataStore.edit { it[K.nick] = v.trim() }
    suspend fun setWifiOnly(v: Boolean) = context.dataStore.edit { it[K.wifi] = v }
    suspend fun setAutoCheck(v: Boolean) = context.dataStore.edit { it[K.auto] = v }
    suspend fun setManifestOverride(v: String) = context.dataStore.edit { it[K.manifest] = v.trim() }
    suspend fun setNewsOverride(v: String) = context.dataStore.edit { it[K.news] = v.trim() }
}

object Nickname {
    /** Символи, які приймає клієнт SA-MP 0.3.7 у ніку. */
    private val allowed = Regex("^[A-Za-z0-9_\\[\\]()$@.=]+$")
    private val rp = Regex("^[A-Z][a-z]+_[A-Z][a-z]+$")

    sealed interface Check {
        data object Ok : Check
        data class Warning(val message: String) : Check
        data class Error(val message: String) : Check
    }

    fun check(nick: String): Check {
        val n = nick.trim()
        return when {
            n.isEmpty() -> Check.Error("Введи нік")
            n.length < 3 -> Check.Error("Мінімум 3 символи")
            n.length > 20 -> Check.Error("Максимум 20 символів")
            !allowed.matches(n) -> Check.Error("Лише латиниця, цифри та _ [ ] ( ) $ @ . =")
            !rp.matches(n) -> Check.Warning("Для RP потрібен формат Ім'я_Прізвище, напр. Ivan_Bereza")
            else -> Check.Ok
        }
    }
}
