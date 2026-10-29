package ua.bereza.launcher.data

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/** Текст для UI, що може бути ресурсом (локалізація) або готовим рядком. */
sealed interface UiText {
    data class Raw(val value: String) : UiText

    class Res(@StringRes val id: Int, vararg val args: Any) : UiText
}

@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Raw -> value
    is UiText.Res -> stringResource(id, *args)
}

fun UiText.asString(context: Context): String = when (this) {
    is UiText.Raw -> value
    is UiText.Res -> context.getString(id, *args)
}
