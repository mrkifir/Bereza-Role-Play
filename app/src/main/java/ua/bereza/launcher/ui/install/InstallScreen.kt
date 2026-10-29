package ua.bereza.launcher.ui.install

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ua.bereza.launcher.R
import ua.bereza.launcher.config.AppConfig
import ua.bereza.launcher.data.LoadState
import ua.bereza.launcher.data.asString
import ua.bereza.launcher.navigation.Routes
import ua.bereza.launcher.ui.components.BerezaCard
import ua.bereza.launcher.ui.components.Chip
import ua.bereza.launcher.ui.components.CollectMessages
import ua.bereza.launcher.ui.components.EmptyNote
import ua.bereza.launcher.ui.components.InfoRow
import ua.bereza.launcher.ui.components.LocalSnackbar
import ua.bereza.launcher.ui.components.PrimaryActionButton
import ua.bereza.launcher.ui.components.ScreenScaffold
import ua.bereza.launcher.ui.components.SecondaryButton
import ua.bereza.launcher.ui.components.SectionTitle
import ua.bereza.launcher.ui.components.berezaViewModel
import ua.bereza.launcher.ui.components.formatBytes
import ua.bereza.launcher.ui.theme.BerezaColors

@Composable
fun InstallScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val vm = berezaViewModel { InstallViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val noInstallerText = stringResource(R.string.err_no_installer)
    val permissionText = stringResource(R.string.msg_install_permission)
    CollectMessages(vm.messages)

    LaunchedEffect(Unit) { vm.loadTarget() }

    val pickSource = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { vm.onSourcePicked(it) }

    ScreenScaffold(title = stringResource(R.string.nav_install), onBack = onBack) {
        item(key = "legal") {
            BerezaCard(Modifier.fillMaxWidth()) {
                SectionTitle(stringResource(R.string.section_legal))
                Spacer(Modifier.height(6.dp))
                EmptyNote(stringResource(R.string.install_legal_note, AppConfig.clientName))
                Spacer(Modifier.height(8.dp))
                SecondaryButton(
                    text = stringResource(R.string.nav_compat),
                    onClick = { onNavigate(Routes.COMPAT) },
                    icon = Icons.Rounded.Verified,
                )
            }
        }
        item(key = "copy") {
            BerezaCard(Modifier.fillMaxWidth()) {
                SectionTitle(stringResource(R.string.section_copy_files))
                Spacer(Modifier.height(6.dp))
                InfoRow(stringResource(R.string.label_source), state.sourceName ?: stringResource(R.string.not_selected))
                InfoRow(
                    stringResource(R.string.label_target),
                    state.targetName ?: stringResource(R.string.not_selected),
                    if (state.targetUri == null) BerezaColors.Warning else BerezaColors.TextPrimary,
                )
                if (state.targetUri == null) {
                    EmptyNote(stringResource(R.string.install_target_hint))
                    TextButton(onClick = { onNavigate(Routes.FILES) }) {
                        Text(stringResource(R.string.action_pick_folder), color = BerezaColors.Green)
                    }
                }
                Spacer(Modifier.height(8.dp))
                SecondaryButton(
                    text = stringResource(R.string.action_pick_source),
                    onClick = { pickSource.launch(null) },
                    icon = Icons.Rounded.FolderOpen,
                    enabled = !state.copying,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.overwrite_title), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.overwrite_hint), style = MaterialTheme.typography.bodySmall, color = BerezaColors.TextMuted)
                    }
                    Switch(
                        checked = state.overwrite,
                        onCheckedChange = vm::setOverwrite,
                        enabled = !state.copying,
                        colors = SwitchDefaults.colors(checkedTrackColor = BerezaColors.Green, checkedThumbColor = BerezaColors.OnGreen),
                    )
                }
                Spacer(Modifier.height(12.dp))
                val progress = state.progress
                if (state.copying) {
                    LinearProgressIndicator(
                        progress = { progress?.fraction ?: 0f },
                        modifier = Modifier.fillMaxWidth(),
                        color = BerezaColors.Green,
                        trackColor = BerezaColors.SurfaceHighest,
                    )
                    Spacer(Modifier.height(6.dp))
                    if (progress != null) {
                        Text(
                            stringResource(
                                R.string.progress_copy_fmt,
                                formatBytes(progress.copiedBytes),
                                formatBytes(progress.totalBytes),
                                progress.filesDone,
                                progress.filesTotal,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(progress.currentFile, style = MaterialTheme.typography.bodySmall, color = BerezaColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    } else {
                        Text(stringResource(R.string.state_preparing), style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = vm::cancelCopy) { Text(stringResource(R.string.action_cancel), color = BerezaColors.Error) }
                } else {
                    PrimaryActionButton(
                        text = stringResource(R.string.action_start_copy),
                        onClick = vm::startCopy,
                        enabled = state.sourceUri != null && state.targetUri != null,
                        icon = Icons.Rounded.ContentCopy,
                        height = 52.dp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                state.report?.let { report ->
                    Spacer(Modifier.height(10.dp))
                    InfoRow(stringResource(R.string.label_copied), report.copied.toString())
                    InfoRow(stringResource(R.string.label_skipped), report.skipped.toString())
                    InfoRow(
                        stringResource(R.string.label_failed),
                        report.failed.size.toString(),
                        if (report.failed.isEmpty()) BerezaColors.Green else BerezaColors.Error,
                    )
                    report.failed.take(10).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = BerezaColors.Error) }
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = { onNavigate(Routes.FILES) }) {
                        Text(stringResource(R.string.action_verify_after_copy), color = BerezaColors.Green)
                    }
                }
            }
        }
        item(key = "launcher-update") {
            BerezaCard(Modifier.fillMaxWidth()) {
                SectionTitle(stringResource(R.string.section_launcher_update)) {
                    TextButton(onClick = vm::checkUpdate) { Text(stringResource(R.string.action_check), color = BerezaColors.Green) }
                }
                InfoRow(stringResource(R.string.label_current_version), "${AppConfig.versionName} (${AppConfig.versionCode})")
                when (val u = state.update) {
                    LoadState.Loading -> EmptyNote(stringResource(R.string.state_loading))
                    LoadState.NotConfigured -> EmptyNote(stringResource(R.string.updates_not_configured))
                    is LoadState.Error -> EmptyNote(u.message.asString())
                    is LoadState.Success -> {
                        val info = u.data
                        val newer = vm.isNewer(info)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.update_version_fmt, "${info.versionName} (${info.versionCode})"),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                            )
                            Chip(
                                stringResource(if (newer) R.string.update_available else R.string.update_current),
                                color = if (newer) BerezaColors.Warning else BerezaColors.Green,
                            )
                        }
                        info.changelog.forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium) }
                        if (newer) {
                            Spacer(Modifier.height(10.dp))
                            when {
                                state.downloading -> {
                                    val fraction = if (state.downloadTotal > 0) state.downloadedBytes.toFloat() / state.downloadTotal else 0f
                                    LinearProgressIndicator(
                                        progress = { fraction.coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth(),
                                        color = BerezaColors.Green,
                                        trackColor = BerezaColors.SurfaceHighest,
                                    )
                                    Text(
                                        "${formatBytes(state.downloadedBytes)} / ${formatBytes(state.downloadTotal)}",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    TextButton(onClick = vm::cancelDownload) { Text(stringResource(R.string.action_cancel), color = BerezaColors.Error) }
                                }
                                state.readyApk != null -> {
                                    PrimaryActionButton(
                                        text = stringResource(R.string.action_install_update),
                                        onClick = {
                                            if (!vm.canRequestInstall()) {
                                                scope.launch { snackbar.showSnackbar(permissionText) }
                                                try {
                                                    context.startActivity(vm.installPermissionIntent())
                                                } catch (e: ActivityNotFoundException) {
                                                    scope.launch { snackbar.showSnackbar(noInstallerText) }
                                                }
                                            } else {
                                                val intent = vm.installIntent()
                                                if (intent != null) {
                                                    try {
                                                        context.startActivity(intent)
                                                    } catch (e: ActivityNotFoundException) {
                                                        scope.launch { snackbar.showSnackbar(noInstallerText) }
                                                    }
                                                }
                                            }
                                        },
                                        icon = Icons.Rounded.InstallMobile,
                                        height = 52.dp,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                else -> {
                                    PrimaryActionButton(
                                        text = stringResource(R.string.action_download_update),
                                        onClick = vm::downloadUpdate,
                                        enabled = info.canBeInstalled,
                                        icon = Icons.Rounded.Download,
                                        height = 52.dp,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    if (!info.canBeInstalled) {
                                        EmptyNote(stringResource(R.string.err_update_unsigned))
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                EmptyNote(stringResource(R.string.update_signature_note))
            }
        }
    }
}
