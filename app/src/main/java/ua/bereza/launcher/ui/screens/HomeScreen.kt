package ua.bereza.launcher.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ua.bereza.launcher.client.ClientStatus
import ua.bereza.launcher.core.Format
import ua.bereza.launcher.install.GameStatus
import ua.bereza.launcher.install.InstallProgress
import ua.bereza.launcher.install.Phase
import ua.bereza.launcher.server.ServerState
import ua.bereza.launcher.ui.MainViewModel
import ua.bereza.launcher.ui.NewsUi
import ua.bereza.launcher.ui.components.Banner
import ua.bereza.launcher.ui.components.BarkDivider
import ua.bereza.launcher.ui.components.BarkProgress
import ua.bereza.launcher.ui.components.BerezaLogo
import ua.bereza.launcher.ui.components.BirchBackdrop
import ua.bereza.launcher.ui.components.BzCard
import ua.bereza.launcher.ui.components.BzGlyph
import ua.bereza.launcher.ui.components.Glyph
import ua.bereza.launcher.ui.components.GhostButton
import ua.bereza.launcher.ui.components.IconTap
import ua.bereza.launcher.ui.components.KeyValue
import ua.bereza.launcher.ui.components.PlayButton
import ua.bereza.launcher.ui.components.PlayVariant
import ua.bereza.launcher.ui.components.SectionLabel
import ua.bereza.launcher.ui.components.StatusDot
import ua.bereza.launcher.ui.theme.Bz
import ua.bereza.launcher.ui.theme.BzDims
import ua.bereza.launcher.ui.theme.BzText

@Composable
fun HomeScreen(
    vm: MainViewModel,
    onOpenSettings: () -> Unit,
    onOpenNews: () -> Unit,
    onOpenServer: () -> Unit,
    onOpenInstall: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    val game by vm.game.collectAsState()
    val server by vm.server.collectAsState()
    val install by vm.install.collectAsState()
    val news by vm.news.collectAsState()
    val settings by vm.settings.collectAsState()
    val client by vm.client.collectAsState()
    val checking by vm.checking.collectAsState()
    val ctx = LocalContext.current

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 720.dp
        BirchBackdrop(Modifier.fillMaxSize())

        val playBlock: @Composable () -> Unit = {
            PrimaryArea(vm, game, install, checking, settings.nickname, onPlay = {
                if (install.phase.isRunning || install.phase == Phase.AWAITING_CLIENT) onOpenInstall() else vm.primaryAction(ctx)
            })
        }

        if (wide) {
            Row(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(BzDims.gutter)) {
                Column(Modifier.weight(1.1f).fillMaxHeight()) {
                    Header(onOpenSettings)
                    Spacer(Modifier.weight(1f))
                    Hero(vm.effectiveServer.name, server, onOpenServer)
                    Spacer(Modifier.height(24.dp))
                    Box(Modifier.widthIn(max = 520.dp)) { playBlock() }
                }
                Spacer(Modifier.width(28.dp))
                Column(Modifier.weight(0.9f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                    Spacer(Modifier.height(56.dp))
                    StatusCards(vm, game, client, onOpenDiagnostics)
                    Spacer(Modifier.height(20.dp))
                    NewsPreview(news, onOpenNews, limit = 4)
                }
            }
        } else {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Header(onOpenSettings, Modifier.padding(horizontal = BzDims.gutter))
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = BzDims.gutter),
                ) {
                    Spacer(Modifier.height(36.dp))
                    Hero(vm.effectiveServer.name, server, onOpenServer)
                    Spacer(Modifier.height(28.dp))
                    StatusCards(vm, game, client, onOpenDiagnostics)
                    Spacer(Modifier.height(24.dp))
                    NewsPreview(news, onOpenNews, limit = 2)
                    Spacer(Modifier.height(24.dp))
                }
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = BzDims.gutter, vertical = 14.dp)) {
                    playBlock()
                }
            }
        }
    }
}

@Composable
private fun Header(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        BerezaLogo(Modifier.weight(1f))
        IconTap(Glyph.Settings, "Налаштування", onClick = onOpenSettings)
    }
}

@Composable
private fun Hero(serverName: String, server: ServerState, onOpenServer: () -> Unit) {
    Column {
        Text("СЕРВЕР", style = BzText.t.label)
        Spacer(Modifier.height(6.dp))
        Text(serverName, style = BzText.t.display, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (server) {
                is ServerState.Online -> {
                    StatusDot(Bz.Leaf, pulse = true)
                    Spacer(Modifier.width(10.dp))
                    Text("${server.players}", style = BzText.t.title.copy(color = Bz.Bark))
                    Text(" / ${server.maxPlayers} гравців", style = BzText.t.body)
                    Spacer(Modifier.width(12.dp))
                    Text("${server.pingMs} мс", style = BzText.t.data.copy(color = Bz.Muted))
                }
                ServerState.Loading -> { StatusDot(Bz.Muted, pulse = true); Spacer(Modifier.width(10.dp)); Text("Перевірка сервера…", style = BzText.t.body) }
                ServerState.NotConfigured -> { StatusDot(Bz.Muted); Spacer(Modifier.width(10.dp)); Text("Адресу сервера не налаштовано", style = BzText.t.body) }
                is ServerState.Unreachable -> { StatusDot(Bz.Ember); Spacer(Modifier.width(10.dp)); Text("Офлайн · ${server.reason}", style = BzText.t.body, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            Spacer(Modifier.weight(1f))
            IconTap(Glyph.ArrowRight, "Про сервер", tint = Bz.Muted, onClick = onOpenServer)
        }
    }
}

@Composable
private fun StatusCards(vm: MainViewModel, game: GameStatus, client: ClientStatus, onOpenDiagnostics: () -> Unit) {
    SectionLabel("Стан гри", trailing = {
        Text("ДІАГНОСТИКА", style = BzText.t.label.copy(color = Bz.BarkDim), modifier = Modifier.padding(start = 8.dp))
        IconTap(Glyph.Pulse, "Діагностика", tint = Bz.BarkDim, onClick = onOpenDiagnostics)
    })
    Spacer(Modifier.height(8.dp))
    BzCard(accent = gameAccent(game)) {
        val (title, sub) = gameText(game)
        Text(title, style = BzText.t.title)
        Spacer(Modifier.height(4.dp))
        Text(sub, style = BzText.t.small)
        Spacer(Modifier.height(12.dp))
        BarkDivider()
        Spacer(Modifier.height(6.dp))
        KeyValue("Клієнт SA-MP", clientText(client), valueColor = if (client is ClientStatus.Ready) Bz.Leaf else Bz.BarkDim)
        KeyValue("Лаунчер", ua.bereza.launcher.BuildConfig.VERSION_NAME)
    }
    if (vm.configProblems.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Banner("Режим налаштування: " + vm.configProblems.joinToString("; "), Bz.Amber)
    }
}

private fun gameAccent(g: GameStatus) = when (g) {
    is GameStatus.Installed -> Bz.Leaf
    is GameStatus.UpdateAvailable, is GameStatus.Damaged, is GameStatus.Interrupted -> Bz.Amber
    is GameStatus.Blocked -> Bz.Ember
    else -> Bz.Muted
}

private fun gameText(g: GameStatus): Pair<String, String> = when (g) {
    GameStatus.Unknown -> "Немає даних" to "Немає зв'язку з сервером оновлень і локальної інсталяції"
    GameStatus.NotInstalled -> "Гру не встановлено" to "Натисніть «Встановити гру» — лаунчер завантажить і перевірить усі файли"
    is GameStatus.Interrupted -> "Встановлення перервано" to "Версія ${g.version}: продовжимо з місця зупинки"
    is GameStatus.UpdateAvailable -> "Доступне оновлення" to "${g.installed} → ${g.available} · ${Format.bytes(g.downloadBytes)}"
    is GameStatus.Damaged -> "Пошкоджено файлів: ${g.missing}" to "Лаунчер завантажить лише пошкоджені файли"
    is GameStatus.Installed -> "Версія ${g.version}" to if (g.offline) "Офлайн — оновлення не перевірено" else "Усі файли на місці"
    is GameStatus.Blocked -> "Потрібна дія" to (g.error.message)
}

private fun clientText(c: ClientStatus) = when (c) {
    ClientStatus.NotConfigured -> "не налаштовано"
    is ClientStatus.NotInstalled -> if (c.canAutoInstall) "буде встановлено" else "не встановлено"
    is ClientStatus.Outdated -> "потрібне оновлення"
    ClientStatus.SignatureMismatch -> "невірний підпис"
    is ClientStatus.Ready -> c.versionName
}

@Composable
private fun PrimaryArea(
    vm: MainViewModel,
    game: GameStatus,
    install: InstallProgress,
    checking: Boolean,
    nickname: String,
    onPlay: () -> Unit,
) {
    Column {
        if (install.phase.isRunning) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(phaseShort(install.phase), style = BzText.t.label.copy(color = Bz.Bark), modifier = Modifier.weight(1f))
                if (install.bytesTotal > 0 && install.phase == Phase.DOWNLOADING) {
                    Text("${Format.bytes(install.bytesDone)} / ${Format.bytes(install.bytesTotal)}", style = BzText.t.data.copy(color = Bz.Muted))
                }
            }
            Spacer(Modifier.height(8.dp))
            val frac = when (install.phase) {
                Phase.DOWNLOADING, Phase.CLIENT_DOWNLOAD -> if (install.bytesTotal > 0) install.downloadFraction else null
                Phase.VERIFYING, Phase.EXTRACTING -> if (install.stageTotal > 0) install.stageFraction else null
                else -> null
            }
            BarkProgress(frac)
            Spacer(Modifier.height(12.dp))
        } else if (game is GameStatus.Installed) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BzGlyph(Glyph.Users, size = 16.dp, color = Bz.Muted)
                Spacer(Modifier.width(8.dp))
                Text(if (nickname.isBlank()) "Нікнейм не задано" else nickname, style = BzText.t.data.copy(color = if (nickname.isBlank()) Bz.Amber else Bz.BarkDim))
            }
            Spacer(Modifier.height(10.dp))
        }

        val (label, variant, caption) = when {
            install.phase.isRunning -> Triple(
                phaseShort(install.phase) + (if (install.phase == Phase.DOWNLOADING && install.bytesTotal > 0) " ${(install.downloadFraction * 100).toInt()}%" else ""),
                PlayVariant.Busy, "Натисніть, щоб переглянути деталі",
            )
            install.phase == Phase.AWAITING_CLIENT -> Triple("Встановити клієнт", PlayVariant.Install, "Останній крок")
            checking && game == GameStatus.Unknown -> Triple("Перевірка", PlayVariant.Busy, null)
            else -> when (game) {
                GameStatus.Unknown -> Triple("Повторити", PlayVariant.Update, "Немає зв'язку")
                GameStatus.NotInstalled -> Triple(
                    "Встановити гру", PlayVariant.Install,
                    vm.fullInstallSize().takeIf { it > 0 }?.let { "Розмір: ${Format.bytes(it)}" },
                )
                is GameStatus.Interrupted -> Triple("Продовжити", PlayVariant.Install, "Встановлення версії ${game.version}")
                is GameStatus.UpdateAvailable -> Triple("Оновити", PlayVariant.Update, "${game.available} · ${Format.bytes(game.downloadBytes)}")
                is GameStatus.Damaged -> Triple("Відновити", PlayVariant.Update, "Завантажити пошкоджені файли")
                is GameStatus.Installed -> Triple("Грати", PlayVariant.Play, null)
                is GameStatus.Blocked -> Triple("Що сталося?", PlayVariant.Update, game.error.error.code)
            }
        }
        AnimatedContent(targetState = label, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "play") { l ->
            PlayButton(l, variant, caption = caption, onClick = onPlay)
        }
    }
}

fun phaseShort(p: Phase) = when (p) {
    Phase.PREPARING -> "Підготовка"
    Phase.VERIFYING -> "Перевірка файлів"
    Phase.CHECKING_SPACE -> "Перевірка місця"
    Phase.DOWNLOADING -> "Завантаження"
    Phase.EXTRACTING -> "Розпакування"
    Phase.CLIENT_DOWNLOAD -> "Клієнт"
    Phase.FINALIZING -> "Завершення"
    Phase.AWAITING_CLIENT -> "Встановлення клієнта"
    Phase.DONE -> "Готово"
    Phase.FAILED -> "Помилка"
    Phase.CANCELLED -> "Скасовано"
    Phase.IDLE -> ""
}

@Composable
fun NewsPreview(news: NewsUi, onOpenNews: () -> Unit, limit: Int) {
    SectionLabel("Новини", trailing = {
        if (news is NewsUi.Items && news.items.size > limit) {
            Text("ВСІ", style = BzText.t.label.copy(color = Bz.Leaf), modifier = Modifier.padding(start = 8.dp))
            IconTap(Glyph.ArrowRight, "Всі новини", tint = Bz.Leaf, onClick = onOpenNews)
        }
    })
    Spacer(Modifier.height(8.dp))
    when (news) {
        NewsUi.NotConfigured -> Text("Стрічку новин ще не підключено.", style = BzText.t.small)
        NewsUi.Loading -> BarkProgress(null, height = 4.dp, color = Bz.Muted)
        is NewsUi.Failed -> Text(news.message, style = BzText.t.small)
        is NewsUi.Items -> if (news.items.isEmpty()) Text("Поки немає новин.", style = BzText.t.small) else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                news.items.take(limit).forEach { item ->
                    BzCard(onClick = onOpenNews, accent = if (item.pinned) Bz.Leaf else null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (item.tag.isNotBlank()) {
                                Text(item.tag.uppercase(), style = BzText.t.label.copy(color = Bz.Leaf))
                                Spacer(Modifier.width(10.dp))
                            }
                            Text(item.date, style = BzText.t.data.copy(color = Bz.Muted))
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(item.title, style = BzText.t.title.copy(fontSize = BzText.t.body.fontSize), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (item.body.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(item.body, style = BzText.t.small, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (news.stale) Text("Показано збережені новини", style = BzText.t.small)
            }
        }
    }
    if (news is NewsUi.Items && news.items.size <= limit && news.items.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        GhostButton("Всі новини", glyph = Glyph.News, modifier = Modifier.fillMaxWidth(), onClick = onOpenNews)
    }
}
