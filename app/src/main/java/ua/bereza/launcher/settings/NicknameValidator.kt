package ua.bereza.launcher.settings

import ua.bereza.launcher.R
import ua.bereza.launcher.data.UiText

/**
 * Правила SA-MP для імені гравця: 3–20 символів, лише A-Z a-z 0-9 [ ] ( ) $ @ . _ =
 * Для RP-формату додатково: Ім'я_Прізвище латиницею з великої літери.
 */
object NicknameValidator {
    const val MIN_LENGTH = 3
    const val MAX_LENGTH = 20

    private val allowed = Regex("^[A-Za-z0-9_\\[\\]()\$@.=]+\$")
    private val rpFormat = Regex("^[A-Z][a-z]+_[A-Z][a-z]+\$")

    fun validate(nickname: String, requireRp: Boolean): UiText? {
        val nick = nickname.trim()
        return when {
            nick.isEmpty() -> UiText.Res(R.string.err_nick_empty)
            nick.length !in MIN_LENGTH..MAX_LENGTH -> UiText.Res(R.string.err_nick_length, MIN_LENGTH, MAX_LENGTH)
            !allowed.matches(nick) -> UiText.Res(R.string.err_nick_chars)
            requireRp && !rpFormat.matches(nick) -> UiText.Res(R.string.err_nick_rp)
            else -> null
        }
    }
}
