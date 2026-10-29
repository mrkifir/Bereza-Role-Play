package ua.bereza.launcher.ui.news

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ua.bereza.launcher.AppContainer
import ua.bereza.launcher.R
import ua.bereza.launcher.data.ErrorMessages
import ua.bereza.launcher.data.LoadState
import ua.bereza.launcher.data.NewsItem
import ua.bereza.launcher.data.asString
import ua.bereza.launcher.ui.components.BerezaCard
import ua.bereza.launcher.ui.components.Chip
import ua.bereza.launcher.ui.components.EmptyNote
import ua.bereza.launcher.ui.components.SecondaryButton
import ua.bereza.launcher.ui.components.ScreenScaffold
import ua.bereza.launcher.ui.components.berezaViewModel
import ua.bereza.launcher.ui.components.openUrl
import ua.bereza.launcher.ui.theme.BerezaColors

class NewsViewModel(private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow<LoadState<List<NewsItem>>>(LoadState.Loading)
    val state: StateFlow<LoadState<List<NewsItem>>> = _state.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        if (!c.news.isConfigured) {
            _state.value = LoadState.NotConfigured
            return
        }
        viewModelScope.launch {
            _state.value = LoadState.Loading
            _state.value = try {
                LoadState.Success(c.news.load())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LoadState.Error(ErrorMessages.from(e))
            }
        }
    }
}

@Composable
fun NewsScreen() {
    val vm = berezaViewModel { NewsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()

    ScreenScaffold(
        title = stringResource(R.string.nav_news),
        actions = {
            IconButton(onClick = vm::reload) {
                Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.action_refresh))
            }
        },
    ) {
        when (val s = state) {
            LoadState.Loading -> item { BerezaCard(Modifier.fillMaxWidth()) { EmptyNote(stringResource(R.string.state_loading)) } }
            LoadState.NotConfigured -> item {
                BerezaCard(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.news_not_configured), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    EmptyNote(stringResource(R.string.news_setup_hint))
                }
            }
            is LoadState.Error -> item {
                BerezaCard(Modifier.fillMaxWidth()) {
                    Text(s.message.asString(), color = BerezaColors.Error, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                    SecondaryButton(stringResource(R.string.action_retry), vm::reload)
                }
            }
            is LoadState.Success -> {
                if (s.data.isEmpty()) {
                    item { BerezaCard(Modifier.fillMaxWidth()) { EmptyNote(stringResource(R.string.news_empty)) } }
                } else {
                    items(s.data) { item -> NewsCard(item) }
                }
            }
        }
    }
}

@Composable
private fun NewsCard(item: NewsItem) {
    val context = LocalContext.current
    var expanded by rememberSaveable(item.id) { mutableStateOf(false) }
    BerezaCard(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        onClick = { expanded = !expanded },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(item.date, style = MaterialTheme.typography.bodySmall, color = BerezaColors.TextFaint, modifier = Modifier.weight(1f))
            item.tag?.let { Chip(it) }
        }
        Spacer(Modifier.height(6.dp))
        Text(item.title, style = MaterialTheme.typography.titleMedium)
        if (item.body.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                item.body,
                style = MaterialTheme.typography.bodyMedium,
                color = BerezaColors.TextMuted,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val url = item.url
        if (expanded && url != null) {
            Spacer(Modifier.height(10.dp))
            SecondaryButton(
                text = stringResource(R.string.action_open_link),
                onClick = { openUrl(context, url) },
                icon = Icons.AutoMirrored.Rounded.OpenInNew,
            )
        }
    }
}
