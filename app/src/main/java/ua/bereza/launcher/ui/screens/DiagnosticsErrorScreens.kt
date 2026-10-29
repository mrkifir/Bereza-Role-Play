package ua.bereza.launcher.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ua.bereza.launcher.core.AppLog
import ua.bereza.launcher.core.ErrorCode
import ua.bereza.launcher.install.InstallMode
import ua.bereza.launcher.ui.MainViewModel
import ua.bereza.launcher.ui.components.BzCard
import ua.bereza.launcher.ui.components.BzGlyph
import ua.bereza.launcher.ui.components.BzTextField
import ua.bereza.launcher.ui.components.BzTopBar
import ua.bereza.launcher.ui.components.GhostButton
import ua.bereza.launcher.ui.components.Glyph
import ua.bereza.launcher.ui.components.IconTap
import ua.bereza.launcher.ui.components.KeyValue
import ua.bereza.launcher.ui.components.PlayButton
import ua.bereza.launcher.ui.components.PlayVariant
import ua.bereza.launcher.ui.components.SectionLabel
import ua.bereza.launcher.ui.theme.Bz
import ua.bereza.launcher.ui.theme.BzDims
import ua.bereza.launcher.ui.theme.BzText

/* ------------------------------------------------------------------ */
/*  Діагностика                                                        */
/* ------------------------------------------------------------------ */

@Composable
fun DiagnosticsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val log by AppLog.lines.collectAsState()
    var tick by remember { mutableIntStateOf(0) }
    val diag = remember(tick, log.size) { vm.diagnostics() }

    Column(Modifier.fillMaxSize()) {
        BzTopBar("Діагностика", onBack) {
            IconTap(Glyph.Refresh, "Оновити") { vm.refreshAll(); tick++ }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = BzDims.gutter)
                .widthIn(max = 720.dp).align(Alignment.CenterHorizontally).navigationBarsPadding(),
        ) {
            BzCard { diag.lines.forEach { (k, v) -> KeyValue(k, v) } }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton("Копіювати звіт", glyph = Glyph.Copy, modifier = Modifier.weight(1f)) {
                    ctx.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText("Bereza diagnostics", vm.diagnosticsText()))
                }
                GhostButton("Поділитися", glyph = Glyph.ArrowRight, modifier = Modifier.weight(1f)) {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, vm.diagnosticsText())
                    ctx.startActivity(Intent.createChooser(send, "Звіт Bereza RP"))
                }
            }
            Spacer(Modifier.height(10.dp))
            GhostButton("Повна перевірка файлів", glyph = Glyph.Shield, modifier = Modifier.fillMaxWidth()) {
                vm.startInstall(ctx, InstallMode.VERIFY)
            }
            Spacer(Modifier.height(24.dp))
            SectionLabel("Журнал")
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(BzDims.radius)).background(Bz.Graphite)
                    .horizontalScroll(rememberScrollState()).padding(12.dp),
            ) {
                if (log.isEmpty()) Text("Порожньо", style = BzText.t.small)
                log.takeLast(150).reversed().forEach { line ->
                    Text(
                        line,
                        style = BzText.t.data.copy(
                            fontSize = 11.sp, lineHeight = 15.sp,
                            color = when { " E " in line -> Bz.Ember; " W " in line -> Bz.Amber; else -> Bz.BarkDim },
                        ),
                        softWrap = false,
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

/* ------------------------------------------------------------------ */
/*  Екран помилки                                                      */
/* ------------------------------------------------------------------ */

@Composable
fun ErrorScreen(vm: MainViewModel, onBack: () -> Unit, onOpenDiagnostics: () -> Unit, onOpenSettings: () -> Unit) {
    val err by vm.error.collectAsState()
    val ctx = LocalContext.current
    val e = err

    val hint: String = when (e?.error) {
        ErrorCode.NETWORK -> "Перевірте інтернет. Завантаження продовжиться з місця зупинки."
        ErrorCode.NO_SPACE -> "Звільніть місце на пристрої та повторіть."
        ErrorCode.CHECKSUM -> "Файл пошкоджено під час передачі. Повторіть — лаунчер завантажить його знову. Якщо повторюється — повідомте адміністрацію."
        ErrorCode.USER_ASSETS -> "Вкажіть у налаштуваннях папку з вашою копією GTA San Andreas потрібної версії."
        ErrorCode.STORAGE -> "Перевірте дозвіл на доступ до файлів у налаштуваннях лаунчера."
        ErrorCode.CLIENT_MISSING -> "Для гри потрібен сумісний Android-клієнт SA-MP, який визначає адміністрація сервера."
        ErrorCode.CLIENT_SIGNATURE -> "Встановлений клієнт не збігається з офіційним. Видаліть його та встановіть через лаунчер."
        ErrorCode.CLIENT_INSTALL -> "Дозвольте лаунчеру встановлення застосунків і повторіть."
        ErrorCode.LAUNCHER_OUTDATED -> "Завантажте нову версію лаунчера з офіційного сайту проєкту."
        ErrorCode.CONFIG, ErrorCode.MANIFEST -> "Це помилка налаштування сервера оновлень. Повідомте адміністрацію, додавши звіт діагностики."
        ErrorCode.LAUNCH -> "Клієнт не вдалося запустити. Спробуйте відкрити його вручну або повідомте адміністрацію."
        else -> "Спробуйте ще раз. Якщо помилка повторюється — надішліть звіт діагностики."
    }

    Column(Modifier.fillMaxSize()) {
        BzTopBar("Помилка", onBack = { vm.clearError(); onBack() })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = BzDims.gutter)
                .widthIn(max = 640.dp).align(Alignment.CenterHorizontally),
        ) {
            Spacer(Modifier.height(24.dp))
            BzGlyph(Glyph.Alert, size = 44.dp, color = Bz.Ember)
            Spacer(Modifier.height(16.dp))
            Text(e?.error?.code ?: "BZ-ERR", style = BzText.t.label.copy(color = Bz.Ember))
            Spacer(Modifier.height(6.dp))
            Text(e?.message ?: "Невідома помилка", style = BzText.t.title.copy(fontSize = 22.sp, lineHeight = 28.sp))
            Spacer(Modifier.height(14.dp))
            Text(hint, style = BzText.t.body)
            Spacer(Modifier.height(24.dp))
            BzCard {
                KeyValue("Код", e?.error?.code ?: "—")
                e?.cause?.let { KeyValue("Причина", it.javaClass.simpleName) }
            }
        }
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = BzDims.gutter, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (e?.error) {
                ErrorCode.CLIENT_MISSING -> {
                    val url = vm.clientInfoUrl()
                    if (url.isNotBlank()) PlayButton("Отримати клієнт", PlayVariant.Install) { vm.openUrl(url) }
                }
                ErrorCode.CLIENT_INSTALL -> PlayButton("Дозволити встановлення", PlayVariant.Install) {
                    ctx.startActivity(vm.unknownSourcesIntent())
                }
                ErrorCode.USER_ASSETS, ErrorCode.STORAGE -> PlayButton("Відкрити налаштування", PlayVariant.Install) { vm.clearError(); onOpenSettings() }
                ErrorCode.LAUNCHER_OUTDATED -> if (vm.config.siteUrl.isNotBlank()) PlayButton("Завантажити лаунчер", PlayVariant.Update) { vm.openUrl(vm.config.siteUrl) }
                else -> PlayButton("Спробувати ще", PlayVariant.Update) { vm.clearError(); vm.refreshGame(); onBack() }
            }
            GhostButton("Діагностика", glyph = Glyph.Pulse, modifier = Modifier.fillMaxWidth(), onClick = onOpenDiagnostics)
        }
    }
}

/* ------------------------------------------------------------------ */
/*  Діалог нікнейму перед першою грою                                  */
/* ------------------------------------------------------------------ */

@Composable
fun NicknameDialog(vm: MainViewModel, onDismiss: () -> Unit, onConfirmed: (String) -> Unit) {
    val s by vm.settings.collectAsState()
    var nick by remember { mutableStateOf(s.nickname) }
    val err = vm.nicknameError(nick)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Bz.Graphite,
        shape = RoundedCornerShape(BzDims.radius),
        title = { Text("ВАШ НІКНЕЙМ", style = BzText.t.title) },
        text = {
            Column {
                Text("Під цим ім'ям вас бачитимуть на сервері.", style = BzText.t.small)
                Spacer(Modifier.height(12.dp))
                BzTextField(nick, { nick = it.filter { c -> !c.isWhitespace() }.take(20) }, "Ім'я_Прізвище", if (nick.isEmpty()) null else err)
            }
        },
        confirmButton = {
            GhostButton("Грати", glyph = Glyph.ArrowRight, color = Bz.Leaf, enabled = err == null) {
                onConfirmed(nick)
            }
        },
        dismissButton = { GhostButton("Скасувати", color = Bz.BarkDim, onClick = onDismiss) },
    )
}
