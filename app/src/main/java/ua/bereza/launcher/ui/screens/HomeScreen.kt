package ua.bereza.launcher.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ua.bereza.launcher.client.Availability
import ua.bereza.launcher.files.formatBytes
import ua.bereza.launcher.server.ServerStatus
import ua.bereza.launcher.settings.Nickname
import ua.bereza.launcher.ui.MainViewModel
import ua.bereza.launcher.ui.components.BCard
import ua.bereza.launcher.ui.components.BerezaLogo
import ua.bereza.launcher.ui.components.Glyph
import ua.bereza.launcher.ui.components.Kicker
import ua.bereza.launcher.ui.components.LeafProgress
import ua.bereza.launcher.ui.components.Pill
import ua.bereza.launcher.ui.components.PlayButton
import ua.bereza.launcher.ui.components.SecondaryButton
import ua.bereza.launcher.ui.components.StatusDot
import ua.bereza.launcher.ui.theme.B
import ua.bereza.launcher.update.EngineState
import ua.bereza.launcher.update.LauncherUpdater
import ua.bereza.launcher.update.Stage

@Composable
fun HomeScreen(vm: MainViewModel, openNews: () -> Unit, openProfile: () -> Unit) {
    val server by vm.server.collectAsStateWithLifecycle()
    val engine by vm.engine.collectAsStateWithLifecycle()
    val client by vm.client.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val news by vm.news.collectAsStateWithLifecycle()
    var explain by remember { mutableStateOf<Pair<String, List<String>>?>(null) }

    DisposableEffect(Unit) { vm.startPolling(); onDispose { vm.stopPolling() } }
    LaunchedEffect(engine is EngineState.Ready) { if (engine is EngineState.Ready) vm.refreshStorage() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BerezaLogo(Modifier.weight(1f))
            IconButton(onClick = { vm.refreshServer(); vm.checkUpdates() }) {
                Icon(Glyph.Refresh, "Оновити", tint = B.Muted)
            }
        }

        ServerCard(server, vm.serverAddress, vm.config.server.name) { vm.refreshServer() }

        FilesCard(engine, vm)

        LauncherUpdateCard(engine, vm)

        // --- Кнопка ГРАТИ: стан визначається реальними перевірками, не прикрасами ---
        val nickCheck = Nickname.check(settings.nickname)
        val filesReady = engine is EngineState.Ready
        val blocker: Pair<String, List<String>>? = when {
            client is Availability.Unavailable -> (client as Availability.Unavailable).let { it.reason to it.details }
            !filesReady -> "Спершу завантаж файли Bereza RP" to emptyList()
            nickCheck is Nickname.Check.Error -> "Вкажи нік у профілі" to listOf(nickCheck.message)
            else -> null
        }
        PlayButton(
            label = "ГРАТИ",
            sublabel = blocker?.first ?: "Підключення до ${vm.serverAddress}",
            enabled = blocker == null,
            icon = if (blocker == null) null else Glyph.Lock,
            onClick = {
                if (blocker != null) explain = blocker
                // Коли з'явиться клієнт: ClientRegistry.select()?.launch(activity, LaunchParams(...))
            },
        )
        if (nickCheck is Nickname.Check.Error || settings.nickname.isBlank()) {
            SecondaryButton("Вказати нік", Glyph.Person, openProfile, Modifier.fillMaxWidth())
        }

        // --- Новини ---
        val items = news.result?.items.orEmpty().take(2)
        if (items.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Kicker("Новини проєкту", B.Muted)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = openNews) { Text("Усі", color = B.Leaf, style = MaterialTheme.typography.labelMedium) }
            }
            items.forEach { n ->
                BCard(onClick = openNews) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (n.tag.isNotBlank()) { Pill(n.tag); Spacer(Modifier.width(8.dp)) }
                        Text(n.date, style = MaterialTheme.typography.bodySmall, color = B.Faint)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(n.title, style = MaterialTheme.typography.titleMedium, color = B.Bark)
                    Text(n.body, style = MaterialTheme.typography.bodyMedium, color = B.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    explain?.let { (title, details) ->
        AlertDialog(
            onDismissRequest = { explain = null },
            containerColor = B.Card,
            title = { Text(title, style = MaterialTheme.typography.headlineSmall, color = B.Bark) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    details.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = B.Muted) }
                    if (details.isEmpty()) Text("Виконай цей крок і кнопка стане активною.", color = B.Muted)
                }
            },
            confirmButton = { TextButton(onClick = { explain = null }) { Text("Зрозуміло", color = B.Leaf) } },
        )
    }
}

@Composable
private fun ServerCard(status: ServerStatus, address: String, fallbackName: String, onRefresh: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    BCard(highlight = status is ServerStatus.Online, onClick = { if (status is ServerStatus.Online) expanded = !expanded else onRefresh() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val (color, label) = when (status) {
                is ServerStatus.Online -> B.Leaf to "ОНЛАЙН"
                is ServerStatus.Offline -> B.Danger to "ОФЛАЙН"
                else -> B.Amber to "ПЕРЕВІРКА"
            }
            StatusDot(color, pulse = status is ServerStatus.Online)
            Spacer(Modifier.width(6.dp))
            Kicker(label, color)
            Spacer(Modifier.weight(1f))
            Text(address, style = MaterialTheme.typography.bodySmall, color = B.Faint)
        }
        Spacer(Modifier.height(10.dp))
        when (status) {
            is ServerStatus.Online -> {
                val i = status.info
                Text(i.hostname.ifBlank { fallbackName }, style = MaterialTheme.typography.titleMedium, color = B.Bark, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${i.players}", style = MaterialTheme.typography.displaySmall, color = B.Leaf)
                    Text(" / ${i.maxPlayers} гравців", style = MaterialTheme.typography.bodyMedium, color = B.Muted, modifier = Modifier.padding(bottom = 5.dp))
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${status.pingMs} мс", style = MaterialTheme.typography.titleSmall, color = if (status.pingMs < 120) B.Leaf else B.Amber)
                        Text("пінг", style = MaterialTheme.typography.bodySmall, color = B.Faint)
                    }
                }
                Spacer(Modifier.height(8.dp))
                LeafProgress(if (i.maxPlayers > 0) i.players.toFloat() / i.maxPlayers else 0f)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (i.gamemode.isNotBlank()) Pill(i.gamemode, B.Muted)
                    status.version?.let { Pill(it, B.Muted) }
                    if (i.passworded) Pill("пароль", B.Amber)
                }
                AnimatedVisibility(expanded) {
                    Column(Modifier.padding(top = 12.dp)) {
                        when {
                            status.players == null -> Text("Список гравців недоступний (понад 100 онлайн)", style = MaterialTheme.typography.bodySmall, color = B.Faint)
                            status.players.isEmpty() -> Text("Зараз на сервері нікого", style = MaterialTheme.typography.bodySmall, color = B.Faint)
                            else -> status.players.sortedByDescending { it.score }.take(30).forEach { p ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                    Text(p.name, style = MaterialTheme.typography.bodyMedium, color = B.Bark, modifier = Modifier.weight(1f))
                                    Text("${p.score}", style = MaterialTheme.typography.bodyMedium, color = B.Muted)
                                }
                            }
                        }
                    }
                }
            }
            is ServerStatus.Offline -> {
                Text(fallbackName, style = MaterialTheme.typography.titleMedium, color = B.Bark)
                Text("${status.reason}. Натисни, щоб перевірити ще раз", style = MaterialTheme.typography.bodySmall, color = B.Muted)
            }
            else -> {
                Text(fallbackName, style = MaterialTheme.typography.titleMedium, color = B.Bark)
                Spacer(Modifier.height(10.dp))
                LeafProgress(0f, indeterminate = true)
            }
        }
    }
}

@Composable
private fun FilesCard(engine: EngineState, vm: MainViewModel) {
    BCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Glyph.Folder, null, tint = B.Muted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Kicker("Файли гри")
        }
        Spacer(Modifier.height(10.dp))
        when (engine) {
            EngineState.Idle -> {
                Text("Оновлення ще не перевірялись", style = MaterialTheme.typography.bodyMedium, color = B.Muted)
                Spacer(Modifier.height(10.dp))
                SecondaryButton("Перевірити", Glyph.Refresh, { vm.checkUpdates() }, color = B.Leaf)
            }
            EngineState.Checking -> {
                Text("Перевіряю маніфест…", style = MaterialTheme.typography.bodyMedium, color = B.Bark)
                Spacer(Modifier.height(10.dp)); LeafProgress(0f, indeterminate = true)
            }
            is EngineState.Ready -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Glyph.Shield, null, tint = B.Leaf, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Файли актуальні", style = MaterialTheme.typography.titleMedium, color = B.Bark)
                    Text("Версія ${engine.version}" + if (engine.offline) " · офлайн" else "", style = MaterialTheme.typography.bodySmall, color = B.Muted)
                }
            }
            is EngineState.NeedsUpdate -> {
                val p = engine.plan
                Text(if (engine.firstInstall) "Потрібне встановлення" else "Доступне оновлення ${p.manifest.version}",
                    style = MaterialTheme.typography.titleMedium, color = B.Bark)
                Text("${p.toDownload.size} файл(ів) · ${formatBytes(p.downloadBytes)}" +
                    if (p.toRemove.isNotEmpty()) " · видалити ${p.toRemove.size}" else "",
                    style = MaterialTheme.typography.bodySmall, color = B.Muted)
                Spacer(Modifier.height(12.dp))
                SecondaryButton(if (engine.firstInstall) "Завантажити" else "Оновити", Glyph.Download, { vm.startInstall() },
                    Modifier.fillMaxWidth(), color = B.Leaf)
            }
            is EngineState.Working -> {
                val stage = when (engine.stage) {
                    Stage.CHECK_LOCAL -> "Перевірка файлів"
                    Stage.DOWNLOAD -> "Завантаження"
                    Stage.VERIFY -> "Перевірка SHA-256"
                    Stage.EXTRACT -> "Розпакування"
                    Stage.CLEANUP -> "Прибирання"
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stage, style = MaterialTheme.typography.titleMedium, color = B.Bark, modifier = Modifier.weight(1f))
                    Text("${(engine.fraction * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, color = B.Leaf)
                }
                Text("${engine.fileName} · ${engine.fileIndex}/${engine.fileCount}", style = MaterialTheme.typography.bodySmall, color = B.Muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(10.dp))
                LeafProgress(engine.fraction)
                Spacer(Modifier.height(8.dp))
                if (engine.stage != Stage.CHECK_LOCAL) Row {
                    Text("${formatBytes(engine.overallDone)} з ${formatBytes(engine.overallTotal)}", style = MaterialTheme.typography.bodySmall, color = B.Faint, modifier = Modifier.weight(1f))
                    if (engine.stage == Stage.DOWNLOAD && engine.speedBps > 0) {
                        val eta = (engine.overallTotal - engine.overallDone) / engine.speedBps
                        Text("${formatBytes(engine.speedBps)}/с · ${eta / 60}:${"%02d".format(eta % 60)}", style = MaterialTheme.typography.bodySmall, color = B.Faint)
                    }
                }
                if (engine.stage != Stage.CHECK_LOCAL) {
                    Spacer(Modifier.height(10.dp))
                    SecondaryButton("Зупинити", Glyph.Stop, { vm.cancelInstall() }, color = B.Danger)
                }
            }
            is EngineState.Failed -> {
                Text("Помилка", style = MaterialTheme.typography.titleMedium, color = B.Danger)
                Text(engine.message, style = MaterialTheme.typography.bodySmall, color = B.Muted)
                if (engine.canRetry) {
                    Spacer(Modifier.height(10.dp))
                    SecondaryButton("Спробувати ще", Glyph.Refresh, { vm.checkUpdates() }, color = B.Leaf)
                }
            }
            is EngineState.LauncherTooOld -> {
                Text("Потрібна новіша версія лаунчера", style = MaterialTheme.typography.titleMedium, color = B.Amber)
                Text("Онови лаунчер, щоб отримати файли.", style = MaterialTheme.typography.bodySmall, color = B.Muted)
            }
        }
    }
}

@Composable
private fun LauncherUpdateCard(engine: EngineState, vm: MainViewModel) {
    val release = when (engine) {
        is EngineState.Ready -> engine.launcherUpdate
        is EngineState.NeedsUpdate -> engine.launcherUpdate
        is EngineState.LauncherTooOld -> engine.release
        else -> null
    } ?: return
    val st by vm.launcherUpdate.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    BCard(highlight = true) {
        Kicker("Нова версія лаунчера", B.Leaf)
        Spacer(Modifier.height(6.dp))
        Text("Bereza RP ${release.versionName}", style = MaterialTheme.typography.titleMedium, color = B.Bark)
        if (release.notes.isNotBlank()) Text(release.notes, style = MaterialTheme.typography.bodySmall, color = B.Muted)
        Spacer(Modifier.height(10.dp))
        when (val s = st) {
            is LauncherUpdater.State.Downloading -> LeafProgress(s.fraction)
            is LauncherUpdater.State.ReadyToInstall -> SecondaryButton("Встановити", Glyph.Download, {
                val u = vm.launcherUpdater
                ctx.startActivity(if (u.canInstall()) u.installIntent(s.apk) else u.openUnknownSourcesSettings())
            }, color = B.Leaf)
            is LauncherUpdater.State.Failed -> {
                Text(s.message, style = MaterialTheme.typography.bodySmall, color = B.Danger)
                SecondaryButton("Ще раз", Glyph.Refresh, { vm.downloadLauncher(release) }, color = B.Leaf)
            }
            LauncherUpdater.State.Idle -> SecondaryButton("Завантажити (${formatBytes(release.size)})", Glyph.Download, { vm.downloadLauncher(release) }, color = B.Leaf)
        }
    }
}
