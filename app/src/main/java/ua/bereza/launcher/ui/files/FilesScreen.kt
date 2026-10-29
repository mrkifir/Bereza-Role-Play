package ua.bereza.launcher.ui.files

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ua.bereza.launcher.R
import ua.bereza.launcher.installer.FileCheckResult
import ua.bereza.launcher.installer.isProblem
import ua.bereza.launcher.navigation.Routes
import ua.bereza.launcher.ui.components.BerezaCard
import ua.bereza.launcher.ui.components.CollectMessages
import ua.bereza.launcher.ui.components.EmptyNote
import ua.bereza.launcher.ui.components.InfoRow
import ua.bereza.launcher.ui.components.PrimaryActionButton
import ua.bereza.launcher.ui.components.ScreenScaffold
import ua.bereza.launcher.ui.components.SecondaryButton
import ua.bereza.launcher.ui.components.SectionTitle
import ua.bereza.launcher.ui.components.berezaViewModel
import ua.bereza.launcher.ui.components.formatBytes
import ua.bereza.launcher.ui.theme.BerezaColors

@Composable
fun FilesScreen(onNavigate: (String) -> Unit) {
    val vm = berezaViewModel { FilesViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    CollectMessages(vm.messages)

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { vm.onFolderPicked(it) }
    val createManifest = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { vm.buildManifest(it) }
    val importManifest = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { vm.importManifest(it) }

    ScreenScaffold(title = stringResource(R.string.nav_files)) {
        item(key = "folder") {
            BerezaCard(Modifier.fillMaxWidth()) {
                SectionTitle(stringResource(R.string.section_game_folder))
                Spacer(Modifier.height(6.dp))
                if (state.folderUri == null) {
                    EmptyNote(stringResource(R.string.folder_not_selected))
                } else {
                    InfoRow(stringResource(R.string.label_folder), state.folderName ?: "—")
                    InfoRow(
                        stringResource(R.string.label_access),
                        if (state.folderAccessible) stringResource(R.string.access_ok) else stringResource(R.string.access_lost),
                        if (state.folderAccessible) BerezaColors.Green else BerezaColors.Error,
                    )
                }
                Spacer(Modifier.height(6.dp))
                EmptyNote(stringResource(R.string.folder_pick_hint))
                Spacer(Modifier.height(8.dp))
                SecondaryButton(
                    text = stringResource(if (state.folderUri == null) R.string.action_pick_folder else R.string.action_change_folder),
                    onClick = { pickFolder.launch(null) },
                    icon = Icons.Rounded.FolderOpen,
                    enabled = !state.running,
                )
            }
        }
        item(key = "manifest") {
            BerezaCard(Modifier.fillMaxWidth()) {
                SectionTitle(stringResource(R.string.section_manifest))
                Spacer(Modifier.height(6.dp))
                InfoRow(stringResource(R.string.label_manifest), state.manifestName.ifBlank { "—" })
                InfoRow(stringResource(R.string.label_files_count), state.manifestCount.toString())
                InfoRow(
                    stringResource(R.string.label_source),
                    stringResource(if (state.manifestIsCustom) R.string.manifest_custom else R.string.manifest_bundled),
                )
                if (state.manifestCount == 0) {
                    Spacer(Modifier.height(4.dp))
                    EmptyNote(stringResource(R.string.manifest_empty_hint))
                }
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton(
                        text = stringResource(R.string.action_build_manifest),
                        onClick = { createManifest.launch("bereza_manifest.json") },
                        icon = Icons.Rounded.Save,
                        enabled = !state.running && state.folderAccessible,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SecondaryButton(
                        text = stringResource(R.string.action_import_manifest),
                        onClick = { importManifest.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                        icon = Icons.Rounded.FileOpen,
                        enabled = !state.running,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (state.manifestIsCustom) {
                        SecondaryButton(
                            text = stringResource(R.string.action_reset_manifest),
                            onClick = vm::resetManifest,
                            icon = Icons.Rounded.Restore,
                            enabled = !state.running,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
        item(key = "verify") {
            BerezaCard(Modifier.fillMaxWidth(), highlighted = state.results != null && state.problems == 0) {
                SectionTitle(stringResource(R.string.section_verify))
                Spacer(Modifier.height(8.dp))
                val progress = state.progress
                if (state.running) {
                    LinearProgressIndicator(
                        progress = { if (progress == null || progress.total == 0) 0f else progress.index.toFloat() / progress.total },
                        modifier = Modifier.fillMaxWidth(),
                        color = BerezaColors.Green,
                        trackColor = BerezaColors.SurfaceHighest,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (progress == null) stringResource(R.string.state_preparing)
                        else stringResource(R.string.progress_files_fmt, progress.index, progress.total, progress.path),
                        style = MaterialTheme.typography.bodySmall,
                        color = BerezaColors.TextMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = vm::cancel) { Text(stringResource(R.string.action_cancel), color = BerezaColors.Error) }
                } else {
                    val results = state.results
                    if (results == null) {
                        EmptyNote(stringResource(R.string.verify_not_run))
                    } else {
                        Text(
                            text = if (state.problems == 0) stringResource(R.string.msg_verify_ok, results.size)
                            else stringResource(R.string.msg_verify_problems, state.problems),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (state.problems == 0) BerezaColors.Green else BerezaColors.Warning,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    PrimaryActionButton(
                        text = stringResource(R.string.action_verify),
                        onClick = vm::verify,
                        enabled = state.folderAccessible && state.manifestCount > 0,
                        icon = Icons.Rounded.FactCheck,
                        height = 52.dp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (state.problems > 0) {
                        Spacer(Modifier.height(8.dp))
                        SecondaryButton(
                            text = stringResource(R.string.action_go_install),
                            onClick = { onNavigate(Routes.INSTALL) },
                            icon = Icons.Rounded.Download,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
        val results = state.results
        if (results != null && results.isNotEmpty()) {
            val sorted = results.sortedByDescending { it.isProblem }
            items(sorted, key = { it.entry.path }) { result -> ResultRow(result) }
        }
    }
}

@Composable
private fun ResultRow(result: FileCheckResult) {
    val (icon, color, text) = describe(result)
    BerezaCard(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(result.entry.path, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(text, style = MaterialTheme.typography.bodySmall, color = color, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

@Composable
private fun describe(result: FileCheckResult): Triple<ImageVector, Color, String> = when (result) {
    is FileCheckResult.Ok -> Triple(Icons.Rounded.CheckCircle, BerezaColors.Green, stringResource(R.string.check_ok))
    is FileCheckResult.Present -> Triple(Icons.Rounded.CheckCircle, BerezaColors.Green, stringResource(R.string.check_present))
    is FileCheckResult.Missing -> if (result.entry.required) {
        Triple(Icons.Rounded.Error, BerezaColors.Error, stringResource(R.string.check_missing))
    } else {
        Triple(Icons.Rounded.HelpOutline, BerezaColors.TextMuted, stringResource(R.string.check_missing_optional))
    }
    is FileCheckResult.SizeMismatch -> Triple(
        Icons.Rounded.Warning, BerezaColors.Warning,
        stringResource(R.string.check_size_fmt, formatBytes(result.actual), formatBytes(result.entry.sizeBytes ?: -1)),
    )
    is FileCheckResult.HashMismatch -> Triple(Icons.Rounded.Warning, BerezaColors.Warning, stringResource(R.string.check_hash))
    is FileCheckResult.Unreadable -> Triple(Icons.Rounded.Error, BerezaColors.Error, stringResource(R.string.check_unreadable_fmt, result.reason))
}
