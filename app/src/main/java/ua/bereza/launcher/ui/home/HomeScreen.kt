package ua.bereza.launcher.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ua.bereza.launcher.R
import ua.bereza.launcher.config.AppConfig
import ua.bereza.launcher.data.LauncherUpdateInfo
import ua.bereza.launcher.data.LoadState
import ua.bereza.launcher.data.NewsItem
import ua.bereza.launcher.data.ServerSnapshot
import ua.bereza.launcher.data.ServerState
import ua.bereza.launcher.data.asString
import ua.bereza.launcher.launcher.ClientAvailability
import ua.bereza.launcher.navigation.Routes
import ua.bereza.launcher.ui.components.BerezaCard
import ua.bereza.launcher.ui.components.BerezaWordmark
import ua.bereza.launcher.ui.components.Chip
import ua.bereza.launcher.ui.components.CollectMessages
import ua.bereza.launcher.ui.components.EmptyNote
import ua.bereza.launcher.ui.components.IconBadge
import ua.bereza.launcher.ui.components.LocalSnackbar
import ua.bereza.launcher.ui.components.PrimaryActionButton
import ua.bereza.launcher.ui.components.SecondaryButton
import ua.bereza.launcher.ui.components.SectionTitle
import ua.bereza.launcher.ui.components.StatusDot
import ua.bereza.launcher.ui.components.berezaViewModel
import ua.bereza.launcher.ui.components.copyToClipboard
import ua.bereza.launcher.ui.components.openUrl
import ua.bereza.launcher.ui.server.serverStatusColor
import ua.bereza.launcher.ui.server.serverStatusLabel
import ua.bereza.launcher.ui.theme.BerezaColors

@Composable
fun HomeScreen(onNavigate: (String) -> Unit) {
    val vm = berezaViewModel { HomeViewModel(it) }
    val server by vm.server.collectAsStateWithLifecycle()
    val news by vm.news.collectAsStateWithLifecycle()
    val update by vm.update.collectAsStateWithLifecycle()
    val client by vm.client.collectAsStateWithLifecycle()
    val nickname by vm.nickname.collectAsStateWithLifecycle()
    val nickError by vm.nickError.collectAsStateWithLifecycle()
    val launching by vm.launching.collectAsStateWithLifecycle()

    CollectMessages(vm.messages)

    // Оновлення статусу сервера і клієнта лише поки екран видимий.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            vm.refreshClient()
            while (true) {
                vm.refreshServer()
                delay(AppConfig.SERVER_POLL_INTERVAL_MS)
            }
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "brand") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) { BerezaWordmark() }
                    IconButton(onClick = vm::reloadAll) {
                        Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.action_refresh))
                    }
                }
            }
            item(key = "server") {
                ServerCard(server = server, onOpen = { onNavigate(Routes.SERVER) })
            }
            item(key = "play") {
                PlayCard(
                    nickname = nickname,
                    nickError = nickError?.asString(),
                    client = client,
                    launching = launching,
                    onNickChange = vm::onNicknameChange,
                    onPlay = vm::play,
                    onCompat = { onNavigate(Routes.COMPAT) },
                )
            }
            item(key = "actions") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        QuickAction(stringResource(R.string.nav_install), Icons.Rounded.Download, Modifier.weight(1f)) { onNavigate(Routes.INSTALL) }
                        QuickAction(stringResource(R.string.nav_compat), Icons.Rounded.Verified, Modifier.weight(1f)) { onNavigate(Routes.COMPAT) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        QuickAction(stringResource(R.string.nav_diagnostics), Icons.Rounded.BugReport, Modifier.weight(1f)) { onNavigate(Routes.DIAGNOSTICS) }
                        QuickAction(stringResource(R.string.nav_about), Icons.Rounded.Info, Modifier.weight(1f)) { onNavigate(Routes.ABOUT) }
                    }
                }
            }
            item(key = "updates") { UpdatesCard(update = update, onOpen = { onNavigate(Routes.INSTALL) }) }
            item(key = "news") { NewsPreviewCard(news = news, onAll = { onNavigate(Routes.NEWS) }) }
            if (AppConfig.hasAnyLink) {
                item(key = "links") { LinksCard() }
            }
            item(key = "version") {
                Text(
                    text = stringResource(R.string.launcher_version_fmt, AppConfig.versionName, AppConfig.versionCode),
                    style = MaterialTheme.typography.bodySmall,
                    color = BerezaColors.TextFaint,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun ServerCard(server: ServerSnapshot, onOpen: () -> Unit) {
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val state = server.state
    val copiedText = stringResource(R.string.msg_address_copied)
    val scope = rememberCoroutineScope()
    BerezaCard(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        onClick = onOpen,
        highlighted = state is ServerState.Online,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(color = serverStatusColor(state), pulsing = state is ServerState.Online)
            Spacer(Modifier.width(6.dp))
            Text(
                text = serverStatusLabel(state),
                style = MaterialTheme.typography.titleMedium,
                color = serverStatusColor(state),
                modifier = Modifier.weight(1f),
            )
            if (server.refreshing) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = BerezaColors.TextMuted)
            } else if (state is ServerState.Online) {
                Chip(stringResource(R.string.ping_fmt, state.info.pingMs))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = if (state is ServerState.Online && state.info.hostname.isNotBlank()) state.info.hostname else AppConfig.serverName,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        when (state) {
            is ServerState.Online -> {
                val info = state.info
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Person, contentDescription = null, tint = BerezaColors.Green, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.players_fmt, info.players, info.maxPlayers),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { if (info.maxPlayers > 0) info.players.toFloat() / info.maxPlayers else 0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = BerezaColors.Green,
                    trackColor = BerezaColors.SurfaceHighest,
                )
                if (info.passworded) {
                    Spacer(Modifier.height(8.dp))
                    Chip(stringResource(R.string.server_passworded), color = BerezaColors.Warning)
                }
            }
            is ServerState.Offline -> {
                Spacer(Modifier.height(6.dp))
                Text(state.reason.asString(), style = MaterialTheme.typography.bodyMedium, color = BerezaColors.TextMuted)
            }
            else -> Unit
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = AppConfig.serverAddress,
                style = MaterialTheme.typography.bodyMedium,
                color = BerezaColors.TextMuted,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = {
                copyToClipboard(context, "Bereza RP", AppConfig.serverAddress)
                scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    snackbar.showSnackbar(copiedText)
                }
            }) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.action_copy_address), tint = BerezaColors.Green)
            }
        }
    }
}

@Composable
private fun PlayCard(
    nickname: String,
    nickError: String?,
    client: ClientAvailability,
    launching: Boolean,
    onNickChange: (String) -> Unit,
    onPlay: () -> Unit,
    onCompat: () -> Unit,
) {
    val focus = LocalFocusManager.current
    BerezaCard(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = nickname,
            onValueChange = onNickChange,
            label = { Text(stringResource(R.string.nickname_label)) },
            placeholder = { Text(stringResource(R.string.nickname_placeholder)) },
            supportingText = {
                Text(
                    nickError ?: if (AppConfig.nickRpFormat) stringResource(R.string.nickname_hint_rp) else stringResource(R.string.nickname_hint)
                )
            },
            isError = nickError != null,
            singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(onGo = { focus.clearFocus(); onPlay() }),
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BerezaColors.Green,
                unfocusedBorderColor = BerezaColors.Outline,
                focusedLabelColor = BerezaColors.Green,
                cursorColor = BerezaColors.Green,
                focusedLeadingIconColor = BerezaColors.Green,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        val installed = client is ClientAvailability.Installed && client.launchable
        PrimaryActionButton(
            text = stringResource(R.string.action_play),
            onClick = { focus.clearFocus(); onPlay() },
            loading = launching,
            enabled = installed,
            icon = Icons.Rounded.PlayArrow,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val (text, color) = when (client) {
                is ClientAvailability.Installed ->
                    if (client.launchable) {
                        stringResource(R.string.client_installed_fmt, client.label, client.versionName) to BerezaColors.Green
                    } else {
                        stringResource(R.string.client_no_activity_short) to BerezaColors.Warning
                    }
                is ClientAvailability.NotInstalled ->
                    stringResource(R.string.client_not_installed_fmt, AppConfig.clientName) to BerezaColors.Warning
                ClientAvailability.NotConfigured ->
                    stringResource(R.string.client_not_configured_short) to BerezaColors.Warning
            }
            Text(text, style = MaterialTheme.typography.bodySmall, color = color, modifier = Modifier.weight(1f))
            AnimatedVisibility(visible = !installed) {
                SecondaryButton(text = stringResource(R.string.action_check), onClick = onCompat)
            }
        }
    }
}

@Composable
private fun QuickAction(title: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    BerezaCard(modifier = modifier, onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        IconBadge(icon)
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun UpdatesCard(update: LoadState<LauncherUpdateInfo>, onOpen: () -> Unit) {
    BerezaCard(modifier = Modifier.fillMaxWidth(), onClick = onOpen) {
        SectionTitle(stringResource(R.string.section_updates))
        Spacer(Modifier.height(8.dp))
        when (update) {
            LoadState.Loading -> EmptyNote(stringResource(R.string.state_loading))
            LoadState.NotConfigured -> EmptyNote(stringResource(R.string.updates_not_configured))
            is LoadState.Error -> EmptyNote(update.message.asString())
            is LoadState.Success -> {
                val info = update.data
                val newer = info.versionCode > AppConfig.versionCode
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.update_version_fmt, info.versionName),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Chip(
                        if (newer) stringResource(R.string.update_available) else stringResource(R.string.update_current),
                        color = if (newer) BerezaColors.Warning else BerezaColors.Green,
                    )
                }
                if (info.date.isNotBlank()) {
                    Text(info.date, style = MaterialTheme.typography.bodySmall, color = BerezaColors.TextFaint)
                }
                if (info.changelog.isEmpty()) {
                    EmptyNote(stringResource(R.string.update_no_changelog))
                } else {
                    Spacer(Modifier.height(6.dp))
                    info.changelog.take(5).forEach { line ->
                        Text("•  $line", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun NewsPreviewCard(news: LoadState<List<NewsItem>>, onAll: () -> Unit) {
    BerezaCard(modifier = Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.section_news)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onAll) {
                    Text(stringResource(R.string.action_all_news), color = BerezaColors.Green)
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = BerezaColors.Green)
                }
            }
        }
        when (news) {
            LoadState.Loading -> EmptyNote(stringResource(R.string.state_loading))
            LoadState.NotConfigured -> EmptyNote(stringResource(R.string.news_not_configured))
            is LoadState.Error -> EmptyNote(news.message.asString())
            is LoadState.Success -> {
                if (news.data.isEmpty()) {
                    EmptyNote(stringResource(R.string.news_empty))
                } else {
                    news.data.take(3).forEachIndexed { index, item ->
                        if (index > 0) Spacer(Modifier.height(10.dp))
                        Column {
                            Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (item.date.isNotBlank()) {
                                Text(item.date, style = MaterialTheme.typography.bodySmall, color = BerezaColors.TextFaint)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LinksCard() {
    val context = LocalContext.current
    BerezaCard(modifier = Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.section_links))
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (AppConfig.discordUrl.isNotBlank()) {
                SecondaryButton("Discord", { openUrl(context, AppConfig.discordUrl) }, Modifier.weight(1f), icon = Icons.Rounded.Forum)
            }
            if (AppConfig.telegramUrl.isNotBlank()) {
                SecondaryButton("Telegram", { openUrl(context, AppConfig.telegramUrl) }, Modifier.weight(1f), icon = Icons.AutoMirrored.Rounded.Send)
            }
            if (AppConfig.siteUrl.isNotBlank()) {
                SecondaryButton(stringResource(R.string.link_site), { openUrl(context, AppConfig.siteUrl) }, Modifier.weight(1f), icon = Icons.Rounded.Language)
            }
        }
    }
}
