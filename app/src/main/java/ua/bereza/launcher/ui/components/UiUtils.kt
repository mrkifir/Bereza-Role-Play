package ua.bereza.launcher.ui.components

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.Flow
import ua.bereza.launcher.AppContainer
import ua.bereza.launcher.BerezaApplication
import ua.bereza.launcher.data.UiText
import ua.bereza.launcher.data.asString
import java.util.Locale

/** Створення ViewModel із доступом до AppContainer. */
@Composable
inline fun <reified VM : ViewModel> berezaViewModel(crossinline create: (AppContainer) -> VM): VM {
    val container = (LocalContext.current.applicationContext as BerezaApplication).container
    return viewModel<VM>(factory = viewModelFactory { initializer<VM> { create(container) } })
}

/** Показує повідомлення з ViewModel у глобальному Snackbar. */
@Composable
fun CollectMessages(messages: Flow<UiText>) {
    val snackbar = LocalSnackbar.current
    val context = LocalContext.current
    LaunchedEffect(messages) {
        messages.collect { text ->
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(text.asString(context))
        }
    }
}

fun openUrl(context: Context, url: String): Boolean {
    if (url.isBlank()) return false
    return try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}

fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

fun formatBytes(bytes: Long): String {
    if (bytes < 0) return "—"
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB")
    var value = bytes / 1024.0
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return String.format(Locale.US, "%.1f %s", value, units[unit])
}
