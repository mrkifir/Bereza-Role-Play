package ua.bereza.launcher.ui.server

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ua.bereza.launcher.AppContainer
import ua.bereza.launcher.R
import ua.bereza.launcher.config.AppConfig
import ua.bereza.launcher.data.ServerSnapshot
import ua.bereza.launcher.data.ServerState
import ua.bereza.launcher.data.asString
import ua.bereza.launcher.ui.components.BerezaCard
import ua.bereza.launcher.ui.components.EmptyNote
import ua.bereza.launcher.ui.components.InfoRow
import ua.bereza.launcher.ui.components.LocalSnackbar
import ua.bereza.launcher.ui.components.ScreenScaffold
import ua.bereza.launcher.ui.components.SecondaryButton
import ua.bereza.launcher.ui.components.SectionTitle
import ua.bereza.launcher.ui.components.StatusDot
import ua.bereza.launcher.ui.components.berezaViewModel
import ua.bereza.launcher.ui.components.copyToClipboard
import ua.bereza.launcher.ui.theme.BerezaColors
import java.text.DateFormat
import java.util.Date

class ServerInfoViewModel(private val c: AppContainer) : ViewModel() {
    val server: StateFlow<ServerSnapshot> = c.serverMonitor.snapshot

    fun refresh() {
        viewModelScope.launch { c.serverMonitor.refresh(withRules = true) }
    }
}

@Composable
fun ServerInfoScreen(onBack: () -> Unit) {
    val vm = berezaViewModel { ServerInfoViewModel(it) }
    val snapshot by vm.server.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val copiedText = stringResource(R.string.msg_address_copied)

    LaunchedEffect(Unit) { vm.refresh() }

    ScreenScaffold(
        title = stringResource(R.string.nav_server),
        onBack = onBack,
        actions = {
            if (snapshot.refreshing) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = vm::refresh) {
                    Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.action_refresh))
                }
            }
        },
    ) {
        item(key = "status") {
            val state = snapshot.state
            BerezaCard(Modifier.fillMaxWidth(), highlighted = state is ServerState.Online) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(serverStatusColor(state), pulsing = state is ServerState.Online)
                    Spacer(Modifier.width(6.dp))
                    Text(serverStatusLabel(state), style = MaterialTheme.typography.titleMedium, color = serverStatusColor(state))
                }
                Spacer(Modifier.height(8.dp))
                InfoRow(stringResource(R.string.label_address), AppConfig.serverAddress)
                when (state) {
                    is ServerState.Online -> {
                        val info = state.info
                        InfoRow(stringResource(R.string.label_hostname), info.hostname.ifBlank { "—" })
                        InfoRow(stringResource(R.string.label_players), stringResource(R.string.players_fmt, info.players, info.maxPlayers))
                        InfoRow(stringResource(R.string.label_ping), stringResource(R.string.ping_fmt, info.pingMs))
                        InfoRow(stringResource(R.string.label_gamemode), info.gamemode.ifBlank { "—" })
                        InfoRow(stringResource(R.string.label_language), info.language.ifBlank { "—" })
                        InfoRow(
                            stringResource(R.string.label_password),
                            if (info.passworded) stringResource(R.string.yes) else stringResource(R.string.no),
                        )
                        InfoRow(
                            stringResource(R.string.label_checked_at),
                            DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(state.checkedAt)),
                        )
                    }
                    is ServerState.Offline -> {
                        InfoRow(stringResource(R.string.label_error), state.reason.asString(), BerezaColors.Error)
                    }
                    else -> Unit
                }
                Spacer(Modifier.height(10.dp))
                SecondaryButton(
                    text = stringResource(R.string.action_copy_address),
                    onClick = {
                        copyToClipboard(context, "Bereza RP", AppConfig.serverAddress)
                        scope.launch { snackbar.showSnackbar(copiedText) }
                    },
                    icon = Icons.Rounded.ContentCopy,
                )
            }
        }
        item(key = "rules") {
            BerezaCard(Modifier.fillMaxWidth()) {
                SectionTitle(stringResource(R.string.section_server_rules))
                Spacer(Modifier.height(6.dp))
                if (snapshot.rules.isEmpty()) {
                    EmptyNote(stringResource(R.string.server_rules_empty))
                } else {
                    snapshot.rules.forEach { (name, value) -> InfoRow(name, value.ifBlank { "—" }) }
                }
            }
        }
        item(key = "protocol") {
            BerezaCard(Modifier.fillMaxWidth()) {
                SectionTitle(stringResource(R.string.section_how_it_works))
                Spacer(Modifier.height(6.dp))
                EmptyNote(stringResource(R.string.server_protocol_note))
            }
        }
    }
}
