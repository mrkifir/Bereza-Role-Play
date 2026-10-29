package ua.bereza.launcher.ui.compat

import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.bereza.launcher.AppContainer
import ua.bereza.launcher.R
import ua.bereza.launcher.config.AppConfig
import ua.bereza.launcher.data.UiText
import ua.bereza.launcher.data.asString
import ua.bereza.launcher.installer.SafTree
import ua.bereza.launcher.launcher.ClientAvailability
import ua.bereza.launcher.navigation.Routes
import ua.bereza.launcher.ui.components.BerezaCard
import ua.bereza.launcher.ui.components.EmptyNote
import ua.bereza.launcher.ui.components.ScreenScaffold
import ua.bereza.launcher.ui.components.SecondaryButton
import ua.bereza.launcher.ui.components.SectionTitle
import ua.bereza.launcher.ui.components.berezaViewModel
import ua.bereza.launcher.ui.theme.BerezaColors

enum class CheckStatus { OK, WARN, FAIL, INFO }

data class CompatCheck(val title: UiText, val detail: UiText, val status: CheckStatus)

class CompatViewModel(private val c: AppContainer) : ViewModel() {
    private val _checks = MutableStateFlow<List<CompatCheck>>(emptyList())
    val checks: StateFlow<List<CompatCheck>> = _checks.asStateFlow()

    fun run() {
        viewModelScope.launch {
            val settings = c.settings.current()
            val context = c.appContext
            val result = withContext(Dispatchers.IO) {
                buildList {
                    add(
                        CompatCheck(
                            UiText.Res(R.string.compat_android),
                            UiText.Res(R.string.compat_android_detail, Build.VERSION.RELEASE, Build.VERSION.SDK_INT),
                            CheckStatus.OK,
                        )
                    )
                    val abis = Build.SUPPORTED_ABIS.joinToString()
                    add(CompatCheck(UiText.Res(R.string.compat_abi), UiText.Raw(abis), CheckStatus.INFO))

                    add(
                        CompatCheck(
                            UiText.Res(R.string.compat_internet),
                            UiText.Res(if (c.network.isOnline()) R.string.compat_internet_ok else R.string.compat_internet_fail),
                            if (c.network.isOnline()) CheckStatus.OK else CheckStatus.FAIL,
                        )
                    )

                    when (val client = c.clientLauncher(settings.clientPackage).availability()) {
                        ClientAvailability.NotConfigured -> add(
                            CompatCheck(UiText.Res(R.string.compat_client), UiText.Res(R.string.client_not_configured_short), CheckStatus.FAIL)
                        )
                        is ClientAvailability.NotInstalled -> add(
                            CompatCheck(
                                UiText.Res(R.string.compat_client),
                                UiText.Res(R.string.compat_client_missing, AppConfig.clientName, client.packageName),
                                CheckStatus.FAIL,
                            )
                        )
                        is ClientAvailability.Installed -> {
                            add(
                                CompatCheck(
                                    UiText.Res(R.string.compat_client),
                                    UiText.Res(R.string.compat_client_ok, client.label, client.versionName, client.packageName),
                                    if (client.launchable) CheckStatus.OK else CheckStatus.WARN,
                                )
                            )
                            if (!client.launchable) {
                                add(CompatCheck(UiText.Res(R.string.compat_launch), UiText.Res(R.string.client_no_activity_short), CheckStatus.FAIL))
                            }
                        }
                    }

                    add(
                        CompatCheck(
                            UiText.Res(R.string.compat_direct_connect),
                            UiText.Res(R.string.compat_direct_connect_detail),
                            CheckStatus.INFO,
                        )
                    )

                    val tree = settings.gameTreeUri
                    if (tree == null) {
                        add(CompatCheck(UiText.Res(R.string.compat_folder), UiText.Res(R.string.folder_not_selected), CheckStatus.WARN))
                    } else {
                        val uri = Uri.parse(tree)
                        val ok = SafTree.hasPersistedPermission(context, uri, write = false) && SafTree.root(context, uri) != null
                        add(
                            CompatCheck(
                                UiText.Res(R.string.compat_folder),
                                if (ok) UiText.Raw(SafTree.displayName(context, uri)) else UiText.Res(R.string.access_lost),
                                if (ok) CheckStatus.OK else CheckStatus.FAIL,
                            )
                        )
                    }

                    val manifest = c.activeManifest(settings.customManifestJson)
                    add(
                        CompatCheck(
                            UiText.Res(R.string.compat_manifest),
                            if (manifest.isEmpty) UiText.Res(R.string.manifest_empty_hint)
                            else UiText.Res(R.string.compat_manifest_ok, manifest.entries.size),
                            if (manifest.isEmpty) CheckStatus.WARN else CheckStatus.OK,
                        )
                    )
                }
            }
            _checks.value = result
        }
    }
}

@Composable
fun CompatScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val vm = berezaViewModel { CompatViewModel(it) }
    val checks by vm.checks.collectAsStateWithLifecycle()

    // Повторна перевірка при поверненні на екран (наприклад, після встановлення клієнта).
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) { vm.run() }
    }

    ScreenScaffold(
        title = stringResource(R.string.nav_compat),
        onBack = onBack,
        actions = {
            IconButton(onClick = vm::run) {
                Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.action_refresh))
            }
        },
    ) {
        items(checks) { check -> CheckRow(check) }
        item(key = "what-to-install") {
            BerezaCard(Modifier.fillMaxWidth()) {
                SectionTitle(stringResource(R.string.section_what_to_install))
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.compat_requirements, AppConfig.clientName), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                SecondaryButton(
                    text = stringResource(R.string.nav_files),
                    onClick = { onNavigate(Routes.FILES) },
                    icon = Icons.Rounded.FolderOpen,
                )
            }
        }
    }
}

@Composable
private fun CheckRow(check: CompatCheck) {
    val (icon, color) = when (check.status) {
        CheckStatus.OK -> Icons.Rounded.CheckCircle to BerezaColors.Green
        CheckStatus.WARN -> Icons.Rounded.Warning to BerezaColors.Warning
        CheckStatus.FAIL -> Icons.Rounded.Error to BerezaColors.Error
        CheckStatus.INFO -> Icons.Rounded.Info to BerezaColors.TextMuted
    }
    BerezaCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp).padding(top = 1.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(check.title.asString(), style = MaterialTheme.typography.titleSmall)
                Text(check.detail.asString(), style = MaterialTheme.typography.bodySmall, color = BerezaColors.TextMuted)
            }
        }
    }
}
