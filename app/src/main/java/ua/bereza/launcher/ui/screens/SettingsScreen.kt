package ua.bereza.launcher.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ua.bereza.launcher.BuildConfig
import ua.bereza.launcher.data.StorageMode
import ua.bereza.launcher.install.InstallMode
import ua.bereza.launcher.ui.MainViewModel
import ua.bereza.launcher.ui.components.Banner
import ua.bereza.launcher.ui.components.BarkDivider
import ua.bereza.launcher.ui.components.BzCard
import ua.bereza.launcher.ui.components.BzTextField
import ua.bereza.launcher.ui.components.BzTopBar
import ua.bereza.launcher.ui.components.GhostButton
import ua.bereza.launcher.ui.components.Glyph
import ua.bereza.launcher.ui.components.KeyValue
import ua.bereza.launcher.ui.components.NavRow
import ua.bereza.launcher.ui.components.SectionLabel
import ua.bereza.launcher.ui.components.SwitchRow
import ua.bereza.launcher.ui.theme.Bz
import ua.bereza.launcher.ui.theme.BzDims
import ua.bereza.launcher.ui.theme.BzText

@Composable
fun SettingsScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenServer: () -> Unit,
    onOpenNews: () -> Unit,
) {
    val s by vm.settings.collectAsState()
    val ctx = LocalContext.current

    var nick by remember { mutableStateOf("") }
    LaunchedEffect(s.nickname) { if (nick.isEmpty()) nick = s.nickname }
    val nickError = if (nick.isEmpty()) null else vm.nicknameError(nick)

    var folder by remember(s.sharedFolder) { mutableStateOf(s.sharedFolder) }
    var hasAccess by remember { mutableStateOf(vm.hasStorageAccess()) }

    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            vm.setUserAssetsTree(uri.toString())
        }
    }
    val legacyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasAccess = vm.hasStorageAccess()
    }
    val allFiles = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        hasAccess = vm.hasStorageAccess()
    }

    Column(Modifier.fillMaxSize()) {
        BzTopBar("Налаштування", onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = BzDims.gutter)
                .widthIn(max = 640.dp).align(Alignment.CenterHorizontally).navigationBarsPadding(),
        ) {
            /* ---------- Гравець ---------- */
            SectionLabel("Гравець")
            Spacer(Modifier.height(10.dp))
            BzTextField(
                value = nick,
                onValueChange = { v ->
                    nick = v.filter { !it.isWhitespace() }.take(20)
                    if (vm.nicknameError(nick) == null) vm.setNickname(nick)
                },
                label = "Нікнейм (Ім'я_Прізвище)",
                error = nickError,
            )
            Spacer(Modifier.height(6.dp))
            Text("Передається клієнту під час запуску, якщо клієнт це підтримує.", style = BzText.t.small)

            /* ---------- Файли гри ---------- */
            Spacer(Modifier.height(28.dp))
            SectionLabel("Файли оригінальної гри")
            Spacer(Modifier.height(10.dp))
            BzCard {
                Text(
                    "Якщо збірка потребує файлів GTA San Andreas, лаунчер бере їх лише з вашої власної копії гри. " +
                        "Вкажіть папку, де вони лежать, — файли буде перевірено за контрольними сумами.",
                    style = BzText.t.small,
                )
                Spacer(Modifier.height(10.dp))
                KeyValue("Папка", if (s.userAssetsTreeUri.isBlank()) "не вибрано" else Uri.parse(s.userAssetsTreeUri).lastPathSegment ?: "вибрано")
                Spacer(Modifier.height(8.dp))
                GhostButton(if (s.userAssetsTreeUri.isBlank()) "Вибрати папку" else "Змінити папку", glyph = Glyph.Folder, modifier = Modifier.fillMaxWidth()) {
                    treePicker.launch(null)
                }
            }

            /* ---------- Сховище ---------- */
            Spacer(Modifier.height(28.dp))
            SectionLabel("Де зберігати гру")
            Spacer(Modifier.height(10.dp))
            ModeOption(
                selected = s.storageMode == StorageMode.PRIVATE,
                title = "Папка лаунчера",
                text = "Без додаткових дозволів. Підходить, якщо клієнт отримує файли від лаунчера.",
            ) { vm.setStorageMode(StorageMode.PRIVATE) }
            Spacer(Modifier.height(10.dp))
            ModeOption(
                selected = s.storageMode == StorageMode.SHARED,
                title = "Спільна папка",
                text = "Внутрішня пам'ять / ${s.sharedFolder}. Потрібна, якщо клієнт читає файли зі спільного сховища.",
            ) { vm.setStorageMode(StorageMode.SHARED) }
            if (s.storageMode == StorageMode.SHARED) {
                Spacer(Modifier.height(12.dp))
                BzTextField(folder, { folder = it.filter { c -> c.isLetterOrDigit() || c in "_-/." } }, "Назва папки", null)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton("Зберегти назву", modifier = Modifier.weight(1f), enabled = folder.isNotBlank() && folder != s.sharedFolder) {
                        vm.setSharedFolder(folder)
                    }
                    GhostButton(
                        if (hasAccess) "Доступ є" else "Надати доступ",
                        glyph = if (hasAccess) Glyph.Check else Glyph.Shield,
                        color = if (hasAccess) Bz.Leaf else Bz.Amber,
                        modifier = Modifier.weight(1f),
                        enabled = !hasAccess,
                    ) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val i = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                            runCatching { allFiles.launch(i) }.onFailure { allFiles.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
                        } else {
                            legacyPermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        }
                    }
                }
                if (!hasAccess) {
                    Spacer(Modifier.height(10.dp))
                    Banner("Без доступу встановлення в спільну папку неможливе. Дозвіл надається вручну в системному вікні.", Bz.Amber)
                }
            }

            /* ---------- Завантаження ---------- */
            Spacer(Modifier.height(28.dp))
            SectionLabel("Завантаження та запуск")
            Spacer(Modifier.height(4.dp))
            SwitchRow("Лише через Wi-Fi", "Не витрачати мобільний трафік", s.wifiOnly) { vm.setWifiOnly(it) }
            SwitchRow("Перевіряти файли перед грою", "Повна перевірка SHA-256 при кожному запуску (довше)", s.verifyBeforeLaunch) { vm.setVerifyBeforeLaunch(it) }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton("Перевірити файли", glyph = Glyph.Shield, modifier = Modifier.weight(1f)) {
                    vm.startInstall(ctx, InstallMode.VERIFY)
                }
                GhostButton("Відновити", glyph = Glyph.Refresh, modifier = Modifier.weight(1f)) {
                    vm.startInstall(ctx, InstallMode.REPAIR)
                }
            }
            Spacer(Modifier.height(10.dp))
            GhostButton("Очистити кеш завантажень", glyph = Glyph.Close, color = Bz.BarkDim, modifier = Modifier.fillMaxWidth()) { vm.clearDownloadCache() }

            /* ---------- Інше ---------- */
            Spacer(Modifier.height(28.dp))
            SectionLabel("Інформація")
            Spacer(Modifier.height(4.dp))
            NavRow("Сервер", "Онлайн, пінг, адреса", Glyph.Server, onOpenServer)
            BarkDivider()
            NavRow("Новини", null, Glyph.News, onOpenNews)
            BarkDivider()
            NavRow("Діагностика", "Стан файлів, мережі та клієнта", Glyph.Pulse, onOpenDiagnostics)
            Spacer(Modifier.height(20.dp))
            BzCard {
                KeyValue("Лаунчер", "${BuildConfig.VERSION_NAME} · ${BuildConfig.VERSION_CODE}")
                KeyValue("Версія клієнта SA-MP", vm.effectiveServer.clientVersion.ifBlank { "—" })
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ModeOption(selected: Boolean, title: String, text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(BzDims.radius)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (selected) Bz.Raised else Bz.Graphite)
            .border(BorderStroke(1.dp, if (selected) Bz.Leaf else Bz.Line), shape)
            .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier.padding(top = 3.dp).size(16.dp).clip(CircleShape)
                .border(BorderStroke(1.5.dp, if (selected) Bz.Leaf else Bz.Muted), CircleShape),
            contentAlignment = Alignment.Center,
        ) { if (selected) Box(Modifier.size(8.dp).clip(CircleShape).background(Bz.Leaf)) }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = BzText.t.body.copy(color = Bz.Bark))
            Text(text, style = BzText.t.small)
        }
    }
}
