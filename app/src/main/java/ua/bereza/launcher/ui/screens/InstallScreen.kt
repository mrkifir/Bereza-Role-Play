package ua.bereza.launcher.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ua.bereza.launcher.core.Format
import ua.bereza.launcher.install.InstallMode
import ua.bereza.launcher.install.InstallProgress
import ua.bereza.launcher.install.Phase
import ua.bereza.launcher.ui.MainViewModel
import ua.bereza.launcher.ui.components.Banner
import ua.bereza.launcher.ui.components.BarkDivider
import ua.bereza.launcher.ui.components.BarkProgress
import ua.bereza.launcher.ui.components.BzCard
import ua.bereza.launcher.ui.components.BzGlyph
import ua.bereza.launcher.ui.components.BzTopBar
import ua.bereza.launcher.ui.components.GhostButton
import ua.bereza.launcher.ui.components.Glyph
import ua.bereza.launcher.ui.components.KeyValue
import ua.bereza.launcher.ui.components.PlayButton
import ua.bereza.launcher.ui.components.PlayVariant
import ua.bereza.launcher.ui.theme.Bz
import ua.bereza.launcher.ui.theme.BzDims
import ua.bereza.launcher.ui.theme.BzText

/**
 * Єдиний екран процесу: встановлення, завантаження, перевірка, оновлення, клієнт.
 * Вигляд змінюється за фазою; усі цифри — з реального процесу.
 */
@Composable
fun InstallScreen(vm: MainViewModel, onBack: () -> Unit, onOpenError: () -> Unit) {
    val p by vm.install.collectAsState()
    val ctx = LocalContext.current

    val title = when (p.mode) {
        InstallMode.INSTALL -> "Встановлення"
        InstallMode.UPDATE -> "Оновлення"
        InstallMode.VERIFY -> "Перевірка файлів"
        InstallMode.REPAIR -> "Відновлення"
    }

    Column(Modifier.fillMaxSize()) {
        BzTopBar(title, onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = BzDims.gutter).widthIn(max = 640.dp).align(Alignment.CenterHorizontally),
        ) {
            Spacer(Modifier.height(8.dp))
            p.targetVersion?.let {
                Text("ЗБІРКА", style = BzText.t.label)
                Text(it, style = BzText.t.display.copy(fontSize = 40.sp))
                Spacer(Modifier.height(20.dp))
            }

            MainMeter(p)
            Spacer(Modifier.height(24.dp))
            Steps(p)
            Spacer(Modifier.height(20.dp))
            Details(p)

            when (p.phase) {
                Phase.FAILED -> {
                    Spacer(Modifier.height(16.dp))
                    Banner("[${p.error?.error?.code}] ${p.error?.message ?: "Невідома помилка"}", Bz.Ember)
                }
                Phase.CANCELLED -> {
                    Spacer(Modifier.height(16.dp))
                    Banner("Скасовано. Уже завантажені частини збережено — наступного разу завантаження продовжиться.", Bz.Amber)
                }
                Phase.AWAITING_CLIENT -> {
                    Spacer(Modifier.height(16.dp))
                    Banner(
                        "Файли гри готові. Залишилось встановити клієнт SA-MP: Android покаже системне вікно підтвердження — натисніть «Встановити».",
                        Bz.Leaf, Glyph.Shield,
                    )
                }
                else -> Unit
            }
            Spacer(Modifier.height(24.dp))
        }

        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = BzDims.gutter, vertical = 14.dp)) {
            when (p.phase) {
                Phase.DONE -> PlayButton("Грати", PlayVariant.Play, caption = "Усе встановлено й перевірено") {
                    vm.acknowledgeInstall(); onBack(); vm.play(ctx)
                }
                Phase.AWAITING_CLIENT -> PlayButton("Встановити клієнт", PlayVariant.Install) { vm.installClient() }
                Phase.FAILED -> {
                    PlayButton("Повторити", PlayVariant.Update, caption = "Продовжити з місця зупинки") { vm.startInstall(ctx, p.mode) }
                    Spacer(Modifier.height(10.dp))
                    GhostButton("Деталі помилки", glyph = Glyph.Alert, modifier = Modifier.fillMaxWidth()) {
                        p.error?.let { vm.showError(it) }; onOpenError()
                    }
                }
                Phase.CANCELLED -> PlayButton("Продовжити", PlayVariant.Install) { vm.startInstall(ctx, p.mode) }
                Phase.IDLE -> PlayButton("На головну", PlayVariant.Busy, onClick = onBack)
                else -> GhostButton("Скасувати", glyph = Glyph.Close, color = Bz.Ember, modifier = Modifier.fillMaxWidth()) { vm.cancelInstall() }
            }
        }
    }
}

@Composable
private fun MainMeter(p: InstallProgress) {
    val (big, small, frac) = when (p.phase) {
        Phase.DOWNLOADING, Phase.CLIENT_DOWNLOAD -> Triple(
            if (p.bytesTotal > 0) "${(p.downloadFraction * 100).toInt()}%" else "—",
            "${Format.bytes(p.bytesDone)} з ${Format.bytes(p.bytesTotal)}",
            if (p.bytesTotal > 0) p.downloadFraction else null,
        )
        Phase.VERIFYING, Phase.EXTRACTING -> Triple(
            if (p.stageTotal > 0) "${(p.stageFraction * 100).toInt()}%" else "—",
            if (p.phase == Phase.VERIFYING) "Перевірено ${p.verifiedFiles} з ${p.fileCount} файлів" else "${Format.bytes(p.stageDone)} розпаковано",
            if (p.stageTotal > 0) p.stageFraction else null,
        )
        Phase.DONE, Phase.AWAITING_CLIENT -> Triple("100%", "Файли готові", 1f)
        Phase.FAILED -> Triple("Зупинено", "Сталася помилка", if (p.bytesTotal > 0) p.downloadFraction else 0f)
        Phase.CANCELLED -> Triple("Пауза", "${Format.bytes(p.bytesDone)} збережено", if (p.bytesTotal > 0) p.downloadFraction else 0f)
        else -> Triple("…", phaseShort(p.phase), null)
    }
    Row(verticalAlignment = Alignment.Bottom) {
        Text(big, style = BzText.t.display.copy(fontSize = 56.sp, lineHeight = 56.sp, color = if (p.phase == Phase.FAILED) Bz.Ember else Bz.Bark))
        Spacer(Modifier.weight(1f))
        if (p.phase == Phase.DOWNLOADING || p.phase == Phase.CLIENT_DOWNLOAD) {
            Column(horizontalAlignment = Alignment.End) {
                Text(Format.speed(p.speedBps), style = BzText.t.data)
                Text("залишилось ${Format.eta(p.etaSeconds)}", style = BzText.t.small)
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(small, style = BzText.t.body)
    Spacer(Modifier.height(14.dp))
    BarkProgress(frac, height = 14.dp, color = if (p.phase == Phase.FAILED) Bz.Ember else Bz.Leaf)
    p.currentFile?.let {
        if (p.phase.isRunning) {
            Spacer(Modifier.height(10.dp))
            Text(
                (if (p.fileCount > 0) "${p.fileIndex}/${p.fileCount}  " else "") + it,
                style = BzText.t.data.copy(color = Bz.Muted), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private enum class StepState { Pending, Active, Done, Failed }

@Composable
private fun Steps(p: InstallProgress) {
    val order = listOf(
        Phase.PREPARING to "Маніфест і версії",
        Phase.VERIFYING to "Перевірка наявних файлів",
        Phase.CHECKING_SPACE to "Вільне місце",
        Phase.DOWNLOADING to "Завантаження змінених файлів",
        Phase.EXTRACTING to "Безпечне розпакування",
        Phase.FINALIZING to "Збереження стану",
        Phase.CLIENT_DOWNLOAD to "Клієнт SA-MP",
    )
    val rank = order.mapIndexed { i, pair -> pair.first to i }.toMap()
    val currentRank = when (p.phase) {
        Phase.DONE, Phase.AWAITING_CLIENT -> 99
        Phase.FAILED, Phase.CANCELLED -> lastRankBeforeFail(p)
        else -> rank[p.phase] ?: -1
    }
    BzCard(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        order.forEach { (phase, label) ->
            val r = rank[phase] ?: 0
            val st = when {
                p.phase == Phase.FAILED && r == lastRankBeforeFail(p) -> StepState.Failed
                r < currentRank -> StepState.Done
                p.phase == Phase.CANCELLED && r == currentRank -> StepState.Pending
                r == currentRank || (phase == Phase.DOWNLOADING && p.phase == Phase.EXTRACTING) -> StepState.Active
                else -> StepState.Pending
            }
            StepRow(label, st)
        }
    }
}

private fun lastRankBeforeFail(p: InstallProgress): Int = when {
    p.bytesTotal > 0 && p.bytesDone > 0 -> 3
    p.verifiedFiles > 0 -> 1
    else -> 0
}

@Composable
private fun StepRow(label: String, st: StepState) {
    val color by animateColorAsState(
        when (st) { StepState.Done -> Bz.Leaf; StepState.Active -> Bz.Bark; StepState.Failed -> Bz.Ember; StepState.Pending -> Bz.Notch },
        label = "step",
    )
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 14.dp, height = 3.dp).clip(RoundedCornerShape(1.dp)).background(color))
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            style = BzText.t.body.copy(color = if (st == StepState.Pending) Bz.Muted else Bz.Bark),
            modifier = Modifier.weight(1f),
        )
        when (st) {
            StepState.Done -> BzGlyph(Glyph.Check, size = 16.dp, color = Bz.Leaf)
            StepState.Failed -> BzGlyph(Glyph.Alert, size = 16.dp, color = Bz.Ember)
            StepState.Active -> Text("•••", style = BzText.t.data.copy(color = Bz.Leaf))
            StepState.Pending -> Unit
        }
    }
}

@Composable
private fun Details(p: InstallProgress) {
    if (p.fileCount == 0 && p.freeBytes < 0) return
    BzCard {
        if (p.fileCount > 0) KeyValue("Файлів у роботі", "${p.fileCount}")
        if (p.bytesTotal > 0) KeyValue("Обсяг завантаження", Format.bytes(p.bytesTotal))
        if (p.badFiles > 0 || p.phase == Phase.VERIFYING) KeyValue("Відсутні / змінені", "${p.badFiles}", valueColor = if (p.badFiles > 0) Bz.Amber else Bz.Bark)
        if (p.requiredBytes > 0) KeyValue("Потрібно місця", Format.bytes(p.requiredBytes))
        if (p.freeBytes >= 0) {
            BarkDivider(Modifier.padding(vertical = 6.dp))
            KeyValue("Вільно на пристрої", Format.bytes(p.freeBytes), valueColor = if (p.freeBytes in 0 until p.requiredBytes) Bz.Ember else Bz.Bark)
        }
    }
}
