package ua.bereza.launcher.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ua.bereza.launcher.data.model.NewsItem
import ua.bereza.launcher.ui.MainViewModel
import ua.bereza.launcher.ui.NewsUi
import ua.bereza.launcher.ui.components.BarkDivider
import ua.bereza.launcher.ui.components.BarkProgress
import ua.bereza.launcher.ui.components.BzCard
import ua.bereza.launcher.ui.components.BzTopBar
import ua.bereza.launcher.ui.components.GhostButton
import ua.bereza.launcher.ui.components.Glyph
import ua.bereza.launcher.ui.components.IconTap
import ua.bereza.launcher.ui.theme.Bz
import ua.bereza.launcher.ui.theme.BzDims
import ua.bereza.launcher.ui.theme.BzText

@Composable
fun NewsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val news by vm.news.collectAsState()
    Column(Modifier.fillMaxSize()) {
        BzTopBar("Новини", onBack) {
            IconTap(Glyph.Refresh, "Оновити") { vm.refreshNews() }
        }
        when (val n = news) {
            NewsUi.NotConfigured -> Empty("Стрічку новин не підключено", "Адміністрація ще не вказала newsUrl у конфігурації лаунчера.")
            NewsUi.Loading -> Column(Modifier.padding(BzDims.gutter)) { BarkProgress(null, height = 6.dp, color = Bz.Muted) }
            is NewsUi.Failed -> Empty("Не вдалося завантажити", n.message)
            is NewsUi.Items -> if (n.items.isEmpty()) Empty("Поки тихо", "Новин ще немає.") else LazyColumn(
                Modifier.fillMaxWidth().widthIn(max = 720.dp).align(Alignment.CenterHorizontally),
                contentPadding = PaddingValues(start = BzDims.gutter, end = BzDims.gutter, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (n.stale) item { Text("Офлайн — показано збережені новини", style = BzText.t.small) }
                items(n.items, key = { it.id }) { NewsCard(it, vm::openUrl) }
                item { Spacer(Modifier.navigationBarsPadding()) }
            }
        }
    }
}

@Composable
private fun NewsCard(item: NewsItem, openUrl: (String) -> Unit) {
    var expanded by rememberSaveable(item.id) { mutableStateOf(false) }
    BzCard(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        accent = if (item.pinned) Bz.Leaf else null,
        onClick = { expanded = !expanded },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (item.pinned) { Text("ЗАКРІПЛЕНО", style = BzText.t.label.copy(color = Bz.Leaf)); Spacer(Modifier.width(10.dp)) }
            if (item.tag.isNotBlank()) { Text(item.tag.uppercase(), style = BzText.t.label.copy(color = Bz.BarkDim)); Spacer(Modifier.width(10.dp)) }
            Spacer(Modifier.weight(1f))
            Text(item.date, style = BzText.t.data.copy(color = Bz.Muted))
        }
        Spacer(Modifier.height(8.dp))
        Text(item.title, style = BzText.t.title)
        if (item.body.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                item.body, style = BzText.t.body,
                maxLines = if (expanded) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis,
            )
        }
        if (expanded && item.url.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            BarkDivider()
            Spacer(Modifier.height(12.dp))
            GhostButton("Відкрити повністю", glyph = Glyph.ArrowRight, color = Bz.Leaf) { openUrl(item.url) }
        }
    }
}

@Composable
fun Empty(title: String, text: String) {
    Column(Modifier.fillMaxWidth().padding(BzDims.gutter)) {
        Spacer(Modifier.height(40.dp))
        Text(title, style = BzText.t.title)
        Spacer(Modifier.height(8.dp))
        Text(text, style = BzText.t.body)
    }
}
