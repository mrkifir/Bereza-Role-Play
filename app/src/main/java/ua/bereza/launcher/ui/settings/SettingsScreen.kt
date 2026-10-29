package ua.bereza.launcher.ui.settings

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.bereza.launcher.AppContainer
import ua.bereza.launcher.R
import ua.bereza.launcher.config.AppConfig
import ua.bereza.launcher.data.UiText
import ua.bereza.launcher.installer.SafTree
import ua.bereza.launcher.navigation.Routes
import ua.bereza.launcher.settings.LauncherSettings
import ua.bereza.launcher.settings.NicknameValidator
import ua.bereza.launcher.ui.components.BerezaCard
import ua.bereza.launcher.ui.components.CollectMessages
import ua.bereza.launcher.ui.components.EmptyNote
import ua.bereza.launcher.ui.components.InfoRow
import ua.bereza.launcher.ui.components.ScreenScaffold
import ua.bereza.launcher.ui.components.SecondaryButton
import ua.bereza.launcher.ui.components.SectionTitle
import ua.bereza.launcher.ui.components.berezaViewModel
import ua.bereza.launcher.ui.theme.BerezaColors

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val settings: StateFlow<LauncherSettings?> = c.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _messages = Channel<UiText>(Channel.BUFFERED)
    val messages: Flow<UiText> = _messages.receiveAsFlow()

    fun saveNickname(value: String) {
        viewModelScope.launch {
            val error = NicknameValidator.validate(value, AppConfig.nickRpFormat)
            if (error != null) {
                _messages.send(error)
            } else {
                c.settings.setNickname(value.trim())
                _messages.send(UiText.Res(R.string.msg_saved))
            }
        }
    }

    fun saveClientPackage(value: String) {
        viewModelScope.launch {
            val pkg = value.trim()
            if (pkg.isNotEmpty() && !PACKAGE_REGEX.matches(pkg)) {
                _messages.send(UiText.Res(R.string.err_package_name))
                return@launch
            }
            c.settings.setClientPackage(pkg.ifEmpty { null })
            _messages.send(UiText.Res(R.string.msg_saved))
        }
    }

    fun resetClientPackage() {
        viewModelScope.launch {
            c.settings.setClientPackage(null)
            _messages.send(UiText.Res(R.string.msg_saved))
        }
    }

    fun setCopyAddress(value: Boolean) {
        viewModelScope.launch { c.settings.setCopyAddressOnLaunch(value) }
    }

    fun setCheckUpdates(value: Boolean) {
        viewModelScope.launch { c.settings.setCheckUpdatesOnStart(value) }
    }

    fun forgetFolder() {
        viewModelScope.launch {
            val uri = c.settings.current().gameTreeUri ?: return@launch
            SafTree.releasePermission(c.appContext, Uri.parse(uri))
            c.settings.setGameTreeUri(null)
            _messages.send(UiText.Res(R.string.msg_folder_forgotten))
        }
    }

    fun folderName(uri: String): String = SafTree.displayName(c.appContext, Uri.parse(uri))

    private companion object {
        val PACKAGE_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")
    }
}

@Composable
fun SettingsScreen(onNavigate: (String) -> Unit) {
    val vm = berezaViewModel { SettingsViewModel(it) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    CollectMessages(vm.messages)

    ScreenScaffold(title = stringResource(R.string.nav_settings)) {
        val s = settings
        if (s == null) {
            item { EmptyNote(stringResource(R.string.state_loading)) }
            return@ScreenScaffold
        }
        item(key = "player") {
            var nick by rememberSaveable { mutableStateOf(s.nickname) }
            LaunchedEffect(s.nickname) { nick = s.nickname }
            BerezaCard(Modifier.fillMaxWidth()) {
                SectionTitle(stringResource(R.string.section_player))
                Spacer(Modifier.height(8.dp))
                SettingsField(nick, { nick = it }, stringResource(R.string.nickname_label))
                Spacer(Modifier.height(8.dp))
                SecondaryButton(stringResource(R.string.action_save), { vm.saveNickname(nick) }, icon = Icons.Rounded.Save)
            }
        }
        item(key = "client") {
            var pkg by rememberSaveable { mutableStateOf(s.clientPackage) }
            LaunchedEffect(s.clientPackage) { pkg = s.clientPackage }
            BerezaCard(Modifier.fillMaxWidth()) {
                SectionTitle(stringResource(R.string.section_client))
                Spacer(Modifier.height(8.dp))
                SettingsField(pkg, { pkg = it }, stringResource(R.string.label_client_package))
                EmptyNote(stringResource(R.string.client_package_hint, AppConfig.defaultClientPackage.ifBlank { "—" }))
                Spacer(Modifier.height(8.dp))
                Row {
                    SecondaryButton(stringResource(R.string.action_save), { vm.saveClientPackage(pkg) }, Modifier.weight(1f), icon = Icons.Rounded.Save)
                    Spacer(Modifier.padding(4.dp))
                    SecondaryButton(stringResource(R.string.action_reset), vm::resetClientPackage, Modifier.weight(1f), icon = Icons.Rounded.Restore)
                }
                Spacer(Modifier.height(8.dp))
                ToggleRow(
                    title = stringResource(R.string.toggle_copy_address),
                    hint = stringResource(R.string.toggle_copy_address_hint),
                    checked = s.copyAddressOnLaunch,
                    onChange = vm::setCopyAddress,
                )
            }
        }
        item(key = "launcher") {
            BerezaCard(Modifier.fillMaxWidth()) {
                SectionTitle(stringResource(R.string.section_launcher))
                ToggleRow(
                    title = stringResource(R.string.toggle_check_updates),
                    hint = stringResource(R.string.toggle_check_updates_hint),
                    checked = s.checkUpdatesOnStart,
                    onChange = vm::setCheckUpdates,
                )
                val tree = s.gameTreeUri
                val folderName by produceState<String?>(null, tree) {
                    value = tree?.let { t -> withContext(Dispatchers.IO) { vm.folderName(t) } }
                }
                InfoRow(stringResource(R.string.label_folder), folderName ?: stringResource(R.string.not_selected))
                if (tree != null) {
                    SecondaryButton(stringResource(R.string.action_forget_folder), vm::forgetFolder, icon = Icons.Rounded.LinkOff)
                }
            }
        }
        item(key = "server") {
            BerezaCard(Modifier.fillMaxWidth()) {
                SectionTitle(stringResource(R.string.section_server_config))
                Spacer(Modifier.height(6.dp))
                InfoRow(stringResource(R.string.label_server_name), AppConfig.serverName)
                InfoRow(stringResource(R.string.label_address), AppConfig.serverAddress)
                InfoRow(stringResource(R.string.label_news_url), AppConfig.newsUrl.ifBlank { stringResource(R.string.not_set) })
                InfoRow(stringResource(R.string.label_update_url), AppConfig.updateUrl.ifBlank { stringResource(R.string.not_set) })
                EmptyNote(stringResource(R.string.server_config_hint))
            }
        }
        item(key = "more") {
            Row {
                SecondaryButton(stringResource(R.string.nav_diagnostics), { onNavigate(Routes.DIAGNOSTICS) }, Modifier.weight(1f), icon = Icons.Rounded.BugReport)
                Spacer(Modifier.padding(4.dp))
                SecondaryButton(stringResource(R.string.nav_about), { onNavigate(Routes.ABOUT) }, Modifier.weight(1f), icon = Icons.Rounded.Info)
            }
        }
    }
}

@Composable
private fun SettingsField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = BerezaColors.Green,
            unfocusedBorderColor = BerezaColors.Outline,
            focusedLabelColor = BerezaColors.Green,
            cursorColor = BerezaColors.Green,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ToggleRow(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = BerezaColors.TextMuted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = BerezaColors.Green, checkedThumbColor = BerezaColors.OnGreen),
        )
    }
}
