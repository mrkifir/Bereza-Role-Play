package ua.bereza.launcher

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ua.bereza.launcher.ui.MainViewModel
import ua.bereza.launcher.ui.components.BarkBackground
import ua.bereza.launcher.ui.components.Glyph
import ua.bereza.launcher.ui.screens.HomeScreen
import ua.bereza.launcher.ui.screens.NewsScreen
import ua.bereza.launcher.ui.screens.ProfileScreen
import ua.bereza.launcher.ui.screens.SettingsScreen
import ua.bereza.launcher.ui.theme.B
import ua.bereza.launcher.ui.theme.BerezaTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(B.Ink.toArgb()),
        )
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)

        setContent { BerezaTheme { App(vm) } }
    }
}

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Головна", Glyph.Home),
    News("news", "Новини", Glyph.News),
    Profile("profile", "Профіль", Glyph.Person),
    Settings("settings", "Налашт.", Glyph.Tune),
}

@Composable
private fun App(vm: MainViewModel) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val message by vm.message.collectAsStateWithLifecycle()
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.consumeMessage() } }

    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route
    fun go(tab: Tab) = nav.navigate(tab.route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true; restoreState = true
    }

    BarkBackground {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = { BottomBar(current) { go(it) } },
        ) { pad ->
            NavHost(nav, Tab.Home.route, Modifier.padding(bottom = pad.calculateBottomPadding())) {
                composable(Tab.Home.route) { HomeScreen(vm, openNews = { go(Tab.News) }, openProfile = { go(Tab.Profile) }) }
                composable(Tab.News.route) { NewsScreen(vm) }
                composable(Tab.Profile.route) { ProfileScreen(vm) }
                composable(Tab.Settings.route) { SettingsScreen(vm) }
            }
        }
    }
}

@Composable
private fun BottomBar(current: String?, onSelect: (Tab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(B.Surface).navigationBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Tab.entries.forEach { tab ->
            val selected = current == tab.route
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                    .clickable(remember { MutableInteractionSource() }, indication = null) { onSelect(tab) }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(if (selected) B.Leaf.copy(alpha = 0.16f) else Color.Transparent)
                        .padding(horizontal = 18.dp, vertical = 5.dp),
                ) { Icon(tab.icon, tab.label, tint = if (selected) B.Leaf else B.Muted, modifier = Modifier.size(22.dp)) }
                Spacer(Modifier.height(3.dp))
                Text(tab.label, style = MaterialTheme.typography.labelMedium, color = if (selected) B.Bark else B.Muted)
            }
        }
    }
}
