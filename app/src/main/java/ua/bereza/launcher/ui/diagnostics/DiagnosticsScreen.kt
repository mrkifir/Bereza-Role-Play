package ua.bereza.launcher.ui.diagnostics

import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.bereza.launcher.AppContainer
import ua.bereza.launcher.R
import ua.bereza.launcher.diagnostics.DiagnosticsLog
import ua.bereza.launcher.ui.components.BerezaCard
import ua.bereza.launcher.ui.components.EmptyNote
import ua.bereza.launcher.ui.components.LocalSnackbar
import ua.bereza.launcher.ui.components.ScreenScaffold
import ua.bereza.launcher.ui.components.SecondaryButton
import ua.bereza.launcher.ui.components.SectionTitle
import ua.bereza.launcher.ui.components.berezaViewModel
import ua.bereza.launcher.ui.components.copyToClipboard
import ua.bereza.launcher.ui.theme.BerezaColors

class DiagnosticsViewModel(private val c: AppContainer) : ViewModel() {
    val log: StateFlow<List<String>> = DiagnosticsLog.entries

    private val _report = MutableStateFlow("")
    val report: StateFlow<String> = _report.asStateFlow()

    private val _testing = MutableStateFlow(false)
    val testing: StateFlow<Boolean> = _testing.asStateFlow()

    fun rebuild() {
        viewModelScope.launch {
            val settings = c.settings.current()
            val client = withContext(Dispatchers.IO) { c.clientLauncher(settings.clientPackage).availability() }
            _report.value = withContext(Dispatchers.IO) {
                c.diagnostics.buildReport(c.serverMonitor.snapshot.value.state, client, settings.gameTreeUri, settings.nickname)
            }
        }
    }

    fun testServer() {
        if (_testing.value) return
        viewModelScope.launch {
            _testing.value = true
            DiagnosticsLog.i("diag", "server test started")
            c.serverMonitor.refresh(withRules = true)
            DiagnosticsLog.i("diag", "server test finished: ${c.serverMonitor.snapshot.value.state::class.simpleName}")
            _testing.value = false
            rebuild()
        }
    }

    fun clearLog() {
        DiagnosticsLog.clear()
        rebuild()
    }
}

@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val vm = berezaViewModel { DiagnosticsViewModel(it) }
    val report by vm.report.collectAsStateWithLifecycle()
    val log by vm.log.collectAsStateWithLifecycle()
    val testing by vm.testing.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val copiedText = stringResource(R.string.msg_report_copied)
    val shareTitle = stringResource(R.string.action_share_report)

    LaunchedEffect(Unit) { vm.rebuild() }

    ScreenScaffold(title = stringResource(R.string.nav_diagnostics), onBack = onBack) {
        item(key = "actions") {
            BerezaCard(Modifier.fillMaxWidth()) {
                SectionTitle(stringResource(R.string.section_tools))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton(
                        text = stringResource(if (testing) R.string.state_loading else R.string.action_test_server),
                        onClick = vm::testServer,
                        enabled = !testing,
                        icon = Icons.Rounded.NetworkCheck,
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryButton(
                        text = stringResource(R.string.action_clear_log),
                        onClick = vm::clearLog,
                        icon = Icons.Rounded.DeleteSweep,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton(
                        text = stringResource(R.string.action_copy_report),
                        onClick = {
                            copyToClipboard(context, "Bereza diagnostics", report)
                            scope.launch { snackbar.showSnackbar(copiedText) }
                        },
                        enabled = report.isNotEmpty(),
                        icon = Icons.Rounded.ContentCopy,
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryButton(
                        text = stringResource(R.string.action_share),
                        onClick = {
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report)
                            context.startActivity(Intent.createChooser(send, shareTitle))
                        },
                        enabled = report.isNotEmpty(),
                        icon = Icons.Rounded.Share,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        item(key = "report") {
            BerezaCard(Modifier.fillMaxWidth()) {
                SectionTitle(stringResource(R.string.section_report))
                Spacer(Modifier.height(6.dp))
                if (report.isEmpty()) {
                    EmptyNote(stringResource(R.string.state_loading))
                } else {
                    Text(
                        report.substringBefore("--- "),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                        color = BerezaColors.TextPrimary,
                    )
                }
            }
        }
        item(key = "log") {
            BerezaCard(Modifier.fillMaxWidth()) {
                SectionTitle(stringResource(R.string.section_log, log.size))
                Spacer(Modifier.height(6.dp))
                if (log.isEmpty()) {
                    EmptyNote(stringResource(R.string.log_empty))
                } else {
                    Text(
                        log.takeLast(120).reversed().joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                        color = BerezaColors.TextMuted,
                        softWrap = false,
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    )
                }
            }
        }
    }
}
