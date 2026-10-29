package ua.bereza.launcher.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ua.bereza.launcher.server.ServerState
import ua.bereza.launcher.ui.MainViewModel
import ua.bereza.launcher.ui.components.BarkProgress
import ua.bereza.launcher.ui.components.BzCard
import ua.bereza.launcher.ui.components.BzTopBar
import ua.bereza.launcher.ui.components.GhostButton
import ua.bereza.launcher.ui.components.Glyph
import ua.bereza.launcher.ui.components.IconTap
import ua.bereza.launcher.ui.components.KeyValue
import ua.bereza.launcher.ui.components.SectionLabel
import ua.bereza.launcher.ui.components.StatusDot
import ua.bereza.launcher.ui.theme.Bz
import ua.bereza.launcher.ui.theme.BzDims
import ua.bereza.launcher.ui.theme.BzText

@Composable
fun ServerScreen(vm: MainViewModel, onBack: () -> Unit) {
    val state by vm.server.collectAsState()
    val ctx = LocalContext.current
    val srv = vm.effectiveServer
    val cfg = vm.config

    Column(Modifier.fillMaxSize()) {
        BzTopBar("Сервер", onBack) { IconTap(Glyph.Refresh, "Оновити") { vm.startServerPolling() } }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = BzDims.gutter)
                .widthIn(max = 640.dp).align(Alignment.CenterHorizontally).navigationBarsPadding(),
        ) {
            Text(srv.name, style = BzText.t.display)
            Spacer(Modifier.height(20.dp))

            when (val s = state) {
                is ServerState.Online -> {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${s.players}", style = BzText.t.display.copy(fontSize = 64.sp, lineHeight = 64.sp, color = Bz.Leaf))
                        Spacer(Modifier.width(8.dp))
                        Text("/ ${s.maxPlayers}", style = BzText.t.title.copy(color = Bz.BarkDim), modifier = Modifier.padding(bottom = 10.dp))
                    }
                    Text("гравців онлайн зараз", style = BzText.t.small)
                    Spacer(Modifier.height(14.dp))
                    BarkProgress(if (s.maxPlayers > 0) s.players.toFloat() / s.maxPlayers else null, height = 10.dp)
                    Spacer(Modifier.height(22.dp))
                    BzCard {
                        KeyValue("Назва в SA-MP", s.hostname.ifBlank { "—" })
                        KeyValue("Режим", s.gamemode.ifBlank { "—" })
                        KeyValue("Мова", s.language.ifBlank { "—" })
                        KeyValue("Пінг", "${s.pingMs} мс", valueColor = when { s.pingMs < 80 -> Bz.Leaf; s.pingMs < 160 -> Bz.Amber; else -> Bz.Ember })
                        KeyValue("Пароль", if (s.passworded) "так" else "ні")
                        KeyValue("Джерело даних", s.source)
                    }
                }
                ServerState.Loading -> { BarkProgress(null, height = 6.dp, color = Bz.Muted); Spacer(Modifier.height(8.dp)); Text("Запит до сервера…", style = BzText.t.body) }
                ServerState.NotConfigured -> Text("Адресу сервера ще не налаштовано в лаунчері.", style = BzText.t.body)
                is ServerState.Unreachable -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(Bz.Ember); Spacer(Modifier.width(10.dp))
                        Text("Немає відповіді", style = BzText.t.title)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("${s.reason}. Кількість гравців не показується, доки сервер не відповість.", style = BzText.t.body)
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionLabel("Підключення")
            Spacer(Modifier.height(8.dp))
            BzCard {
                KeyValue("Адреса", if (srv.isPlaceholder) "не налаштовано" else "${srv.host}:${srv.port}")
                if (srv.clientVersion.isNotBlank()) KeyValue("Версія клієнта", srv.clientVersion)
                if (!srv.isPlaceholder) {
                    Spacer(Modifier.height(8.dp))
                    GhostButton("Скопіювати адресу", glyph = Glyph.Copy, modifier = Modifier.fillMaxWidth()) {
                        ctx.getSystemService(ClipboardManager::class.java)
                            ?.setPrimaryClip(ClipData.newPlainText("Bereza RP", "${srv.host}:${srv.port}"))
                    }
                }
            }

            val links = listOf("Сайт" to cfg.siteUrl, "Discord" to cfg.discordUrl, "Telegram" to cfg.telegramUrl).filter { it.second.isNotBlank() }
            if (links.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                SectionLabel("Спільнота")
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    links.forEach { (name, url) -> GhostButton(name, glyph = Glyph.ArrowRight, modifier = Modifier.fillMaxWidth()) { vm.openUrl(url) } }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
