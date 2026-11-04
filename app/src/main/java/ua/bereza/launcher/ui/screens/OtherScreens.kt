package ua.bereza.launcher.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ua.bereza.launcher.BuildConfig
import ua.bereza.launcher.client.Availability
import ua.bereza.launcher.files.formatBytes
import ua.bereza.launcher.settings.Nickname
import ua.bereza.launcher.ui.MainViewModel
import ua.bereza.launcher.ui.components.BCard
import ua.bereza.launcher.ui.components.BSwitch
import ua.bereza.launcher.ui.components.Glyph
import ua.bereza.launcher.ui.components.KeyValue
import ua.bereza.launcher.ui.components.Kicker
import ua.bereza.launcher.ui.components.Pill
import ua.bereza.launcher.ui.components.SecondaryButton
import ua.bereza.launcher.ui.components.SettingRow
import ua.bereza.launcher.ui.theme.B
import ua.bereza.launcher.ui.theme.Display
import ua.bereza.launcher.update.EngineState

@Composable
private fun ScreenTitle(title: String, kicker: String) {
    Column(Modifier.statusBarsPadding().padding(top = 18.dp, bottom = 6.dp)) {
        Kicker(kicker, B.Leaf)
        Text(title, style = MaterialTheme.typography.headlineSmall, color = B.Bark)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsScreen(vm: MainViewModel) {
    val news by vm.news.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    PullToRefreshBox(isRefreshing = news.loading, onRefresh = { vm.refreshNews() }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ScreenTitle("Новини", "Bereza RP") }
            val r = news.result
            if (r?.error != null) item {
                Text(if (r.items.isEmpty()) "Не вдалося завантажити новини: ${r.error}" else "Показано збережені новини (немає з'єднання)",
                    style = MaterialTheme.typography.bodySmall, color = B.Amber)
            }
            if (r != null && r.items.isEmpty() && r.error == null) item {
                Text("Поки що новин немає", style = MaterialTheme.typography.bodyMedium, color = B.Muted)
            }
            items(r?.items.orEmpty(), key = { it.id }) { n ->
                BCard(highlight = n.pinned, onClick = if (n.url.isNotBlank()) ({
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(n.url))) }
                    Unit
                }) else null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (n.pinned) { Pill("Закріплено", B.Amber); Spacer(Modifier.width(6.dp)) }
                        if (n.tag.isNotBlank()) { Pill(n.tag); Spacer(Modifier.width(8.dp)) }
                        Spacer(Modifier.weight(1f))
                        Text(n.date, style = MaterialTheme.typography.bodySmall, color = B.Faint)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(n.title, style = MaterialTheme.typography.titleMedium, color = B.Bark)
                    Spacer(Modifier.height(4.dp))
                    Text(n.body, style = MaterialTheme.typography.bodyMedium, color = B.Muted)
                    if (n.url.isNotBlank()) Text("Детальніше →", style = MaterialTheme.typography.labelMedium, color = B.Leaf, modifier = Modifier.padding(top = 8.dp))
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = B.Leaf, unfocusedBorderColor = B.Line, cursorColor = B.Leaf,
    focusedLabelColor = B.Leaf, unfocusedLabelColor = B.Muted, focusedTextColor = B.Bark, unfocusedTextColor = B.Bark,
)

@Composable
fun ProfileScreen(vm: MainViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val storage by vm.storage.collectAsStateWithLifecycle()
    val engine by vm.engine.collectAsStateWithLifecycle()
    val client by vm.client.collectAsStateWithLifecycle()
    var nick by remember(settings.nickname) { mutableStateOf(settings.nickname) }
    LaunchedEffect(Unit) { vm.refreshStorage() }
    val check = Nickname.check(nick)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenTitle("Профіль", "Гравець")
        BCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).clip(CircleShape).background(B.Leaf.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    val initials = settings.nickname.split('_').mapNotNull { it.firstOrNull()?.uppercaseChar() }.take(2).joinToString("")
                    Text(initials.ifBlank { "?" }, fontFamily = Display, fontSize = 20.sp, color = B.Leaf)
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(settings.nickname.ifBlank { "Нік не вказано" }, style = MaterialTheme.typography.titleMedium, color = B.Bark)
                    Text(vm.config.server.name, style = MaterialTheme.typography.bodySmall, color = B.Muted)
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = nick, onValueChange = { nick = it.filterNot { c -> c.isWhitespace() }.take(20) },
                label = { Text("Нік (Ім'я_Прізвище)") }, singleLine = true, colors = fieldColors(),
                shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                supportingText = {
                    when (check) {
                        is Nickname.Check.Error -> Text(check.message, color = B.Danger)
                        is Nickname.Check.Warning -> Text(check.message, color = B.Amber)
                        Nickname.Check.Ok -> Text("Нік підходить", color = B.Leaf)
                    }
                },
            )
            Spacer(Modifier.height(6.dp))
            SecondaryButton("Зберегти", Glyph.Check, { vm.setNickname(nick) }, Modifier.fillMaxWidth(), color = B.Leaf,
                enabled = check !is Nickname.Check.Error && nick != settings.nickname)
        }
        BCard {
            Kicker("Гра на цьому пристрої")
            Spacer(Modifier.height(6.dp))
            KeyValue("Файли Bereza RP", when (val e = engine) {
                is EngineState.Ready -> "версія ${e.version}"
                is EngineState.NeedsUpdate -> if (e.firstInstall) "не встановлено" else "є оновлення"
                is EngineState.Working -> "встановлюються…"
                else -> "—"
            })
            KeyValue("Займають", formatBytes(storage.used))
            KeyValue("Вільно", formatBytes(storage.free))
            KeyValue("Клієнт SA-MP", if (client is Availability.Ready) "готовий" else "не інтегровано",
                if (client is Availability.Ready) B.Leaf else B.Amber)
        }
        BCard {
            Kicker("Пристрій")
            Spacer(Modifier.height(6.dp))
            KeyValue("Модель", "${Build.MANUFACTURER} ${Build.MODEL}")
            KeyValue("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            KeyValue("Архітектура", vm.deviceAbis.joinToString())
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val storage by vm.storage.collectAsStateWithLifecycle()
    var confirmWipe by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(false) }
    var manifestUrl by remember(settings.manifestUrlOverride) { mutableStateOf(settings.manifestUrlOverride) }
    var newsUrl by remember(settings.newsUrlOverride) { mutableStateOf(settings.newsUrlOverride) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenTitle("Налаштування", "Лаунчер")
        BCard(padding = 16.dp) {
            Kicker("Завантаження")
            SettingRow("Лише через Wi-Fi", "Не качати файли через мобільний інтернет") { BSwitch(settings.wifiOnly, vm::setWifiOnly) }
            HorizontalDivider(color = B.Line)
            SettingRow("Перевіряти оновлення при запуску") { BSwitch(settings.autoCheck, vm::setAutoCheck) }
        }
        BCard {
            Kicker("Файли гри")
            SettingRow("Перевірити цілісність", "Повна перевірка SHA-256 усіх файлів", onClick = { vm.checkUpdates(full = true) })
            HorizontalDivider(color = B.Line)
            SettingRow("Видалити файли гри", "Займають ${formatBytes(storage.used)}", onClick = { confirmWipe = true })
            HorizontalDivider(color = B.Line)
            Text(storage.path, style = MaterialTheme.typography.bodySmall, color = B.Faint, modifier = Modifier.padding(top = 10.dp))
        }
        BCard {
            SettingRow("Для розробника", if (advanced) "Сховати" else "Власні адреси маніфесту й новин", onClick = { advanced = !advanced })
            if (advanced) {
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(manifestUrl, { manifestUrl = it }, label = { Text("URL маніфесту") }, placeholder = { Text(vm.config.manifestUrl, color = B.Faint) },
                    singleLine = true, colors = fieldColors(), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(newsUrl, { newsUrl = it }, label = { Text("URL новин") }, placeholder = { Text(vm.config.newsUrl, color = B.Faint) },
                    singleLine = true, colors = fieldColors(), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("Застосувати", Glyph.Check, { vm.setManifestOverride(manifestUrl); vm.setNewsOverride(newsUrl) }, color = B.Leaf)
                    SecondaryButton("Скинути", Glyph.Close, { manifestUrl = ""; newsUrl = ""; vm.setManifestOverride(""); vm.setNewsOverride("") })
                }
                Text("Посилання Dropbox можна вставляти як є — лаунчер сам перетворить їх на пряме завантаження (dl=1).",
                    style = MaterialTheme.typography.bodySmall, color = B.Faint, modifier = Modifier.padding(top = 8.dp))
            }
        }
        BCard {
            Kicker("Про застосунок")
            Spacer(Modifier.height(6.dp))
            KeyValue("Версія", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            KeyValue("Сервер", vm.serverAddress)
            Spacer(Modifier.height(8.dp))
            Text("Шрифти Unbounded і Manrope — SIL Open Font License 1.1. Bereza RP не пов'язаний із Rockstar Games, Take-Two або командою SA-MP.",
                style = MaterialTheme.typography.bodySmall, color = B.Faint)
        }
        Spacer(Modifier.height(16.dp))
    }

    if (confirmWipe) AlertDialog(
        onDismissRequest = { confirmWipe = false }, containerColor = B.Card,
        title = { Text("Видалити файли гри?", color = B.Bark) },
        text = { Text("Усі завантажені файли Bereza RP буде видалено. Нік і налаштування залишаться.", color = B.Muted) },
        confirmButton = { TextButton(onClick = { confirmWipe = false; vm.wipeGameFiles() }) { Text("Видалити", color = B.Danger) } },
        dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text("Скасувати", color = B.Muted) } },
    )
}
