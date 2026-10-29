package ua.bereza.launcher.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ua.bereza.launcher.R
import ua.bereza.launcher.config.AppConfig
import ua.bereza.launcher.ui.components.BerezaCard
import ua.bereza.launcher.ui.components.BerezaLogoMark
import ua.bereza.launcher.ui.components.EmptyNote
import ua.bereza.launcher.ui.components.InfoRow
import ua.bereza.launcher.ui.components.ScreenScaffold
import ua.bereza.launcher.ui.components.SecondaryButton
import ua.bereza.launcher.ui.components.SectionTitle
import ua.bereza.launcher.ui.components.openUrl
import ua.bereza.launcher.ui.theme.BerezaColors

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    ScreenScaffold(title = stringResource(R.string.nav_about), onBack = onBack) {
        item(key = "brand") {
            BerezaCard(Modifier.fillMaxWidth(), highlighted = true) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    BerezaLogoMark(size = 88.dp)
                    Spacer(Modifier.height(12.dp))
                    Text(AppConfig.serverName, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        stringResource(R.string.about_tagline),
                        style = MaterialTheme.typography.bodyMedium,
                        color = BerezaColors.TextMuted,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        item(key = "info") {
            BerezaCard(Modifier.fillMaxWidth()) {
                SectionTitle(stringResource(R.string.section_about_launcher))
                Spacer(Modifier.height(6.dp))
                InfoRow(stringResource(R.string.label_current_version), "${AppConfig.versionName} (${AppConfig.versionCode})")
                InfoRow(stringResource(R.string.label_address), AppConfig.serverAddress)
                InfoRow(stringResource(R.string.label_client), AppConfig.clientName)
            }
        }
        item(key = "legal") {
            BerezaCard(Modifier.fillMaxWidth()) {
                SectionTitle(stringResource(R.string.section_legal))
                Spacer(Modifier.height(6.dp))
                EmptyNote(stringResource(R.string.about_legal))
            }
        }
        if (AppConfig.hasAnyLink) {
            item(key = "links") {
                BerezaCard(Modifier.fillMaxWidth()) {
                    SectionTitle(stringResource(R.string.section_links))
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (AppConfig.discordUrl.isNotBlank()) {
                            SecondaryButton("Discord", { openUrl(context, AppConfig.discordUrl) }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Forum)
                        }
                        if (AppConfig.telegramUrl.isNotBlank()) {
                            SecondaryButton("Telegram", { openUrl(context, AppConfig.telegramUrl) }, Modifier.fillMaxWidth(), icon = Icons.AutoMirrored.Rounded.Send)
                        }
                        if (AppConfig.siteUrl.isNotBlank()) {
                            SecondaryButton(stringResource(R.string.link_site), { openUrl(context, AppConfig.siteUrl) }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Language)
                        }
                    }
                }
            }
        }
    }
}
