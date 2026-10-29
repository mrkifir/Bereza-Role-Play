package ua.bereza.launcher.navigation

import androidx.annotation.StringRes
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ua.bereza.launcher.R
import ua.bereza.launcher.ui.about.AboutScreen
import ua.bereza.launcher.ui.compat.CompatScreen
import ua.bereza.launcher.ui.components.LocalSnackbar
import ua.bereza.launcher.ui.diagnostics.DiagnosticsScreen
import ua.bereza.launcher.ui.files.FilesScreen
import ua.bereza.launcher.ui.home.HomeScreen
import ua.bereza.launcher.ui.install.InstallScreen
import ua.bereza.launcher.ui.news.NewsScreen
import ua.bereza.launcher.ui.server.ServerInfoScreen
import ua.bereza.launcher.ui.settings.SettingsScreen
import ua.bereza.launcher.ui.theme.BerezaColors

object Routes {
    const val HOME = "home"
    const val NEWS = "news"
    const val FILES = "files"
    const val SETTINGS = "settings"
    const val SERVER = "server"
    const val INSTALL = "install"
    const val COMPAT = "compat"
    const val DIAGNOSTICS = "diagnostics"
    const val ABOUT = "about"
}

private data class TopLevel(val route: String, @StringRes val label: Int, val icon: ImageVector)

private val topLevel = listOf(
    TopLevel(Routes.HOME, R.string.nav_home, Icons.Rounded.Home),
    TopLevel(Routes.NEWS, R.string.nav_news, Icons.Rounded.Newspaper),
    TopLevel(Routes.FILES, R.string.nav_files, Icons.Rounded.FolderOpen),
    TopLevel(Routes.SETTINGS, R.string.nav_settings, Icons.Rounded.Settings),
)

private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun BerezaRoot() {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val isWide = LocalConfiguration.current.screenWidthDp >= 600

    fun isSelected(route: String): Boolean = destination?.hierarchy?.any { it.route == route } == true

    CompositionLocalProvider(LocalSnackbar provides snackbar) {
        Scaffold(
            containerColor = BerezaColors.Black,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (!isWide) {
                    NavigationBar(containerColor = BerezaColors.Graphite, tonalElevation = 0.dp) {
                        topLevel.forEach { item ->
                            NavigationBarItem(
                                selected = isSelected(item.route),
                                onClick = { nav.navigateTopLevel(item.route) },
                                icon = { Icon(item.icon, contentDescription = null) },
                                label = { Text(stringResource(item.label)) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = BerezaColors.OnGreen,
                                    selectedTextColor = BerezaColors.Green,
                                    indicatorColor = BerezaColors.Green,
                                    unselectedIconColor = BerezaColors.TextMuted,
                                    unselectedTextColor = BerezaColors.TextMuted,
                                ),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (isWide) {
                    NavigationRail(containerColor = BerezaColors.Graphite) {
                        topLevel.forEach { item ->
                            NavigationRailItem(
                                selected = isSelected(item.route),
                                onClick = { nav.navigateTopLevel(item.route) },
                                icon = { Icon(item.icon, contentDescription = null) },
                                label = { Text(stringResource(item.label)) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = BerezaColors.OnGreen,
                                    selectedTextColor = BerezaColors.Green,
                                    indicatorColor = BerezaColors.Green,
                                    unselectedIconColor = BerezaColors.TextMuted,
                                    unselectedTextColor = BerezaColors.TextMuted,
                                ),
                            )
                        }
                    }
                }
                BerezaNavHost(nav, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BerezaNavHost(nav: NavHostController, modifier: Modifier) {
    val back: () -> Unit = { nav.popBackStack() }
    NavHost(
        navController = nav,
        startDestination = Routes.HOME,
        modifier = modifier,
        enterTransition = { fadeIn(tween(240)) + slideInHorizontally(tween(260)) { it / 10 } },
        exitTransition = { fadeOut(tween(160)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { fadeOut(tween(160)) + slideOutHorizontally(tween(220)) { it / 10 } },
    ) {
        composable(Routes.HOME) {
            HomeScreen(onNavigate = { route -> nav.navigate(route) })
        }
        composable(Routes.NEWS) { NewsScreen() }
        composable(Routes.FILES) { FilesScreen(onNavigate = { route -> nav.navigate(route) }) }
        composable(Routes.SETTINGS) { SettingsScreen(onNavigate = { route -> nav.navigate(route) }) }
        composable(Routes.SERVER) { ServerInfoScreen(onBack = back) }
        composable(Routes.INSTALL) { InstallScreen(onBack = back, onNavigate = { route -> nav.navigate(route) }) }
        composable(Routes.COMPAT) { CompatScreen(onBack = back, onNavigate = { route -> nav.navigate(route) }) }
        composable(Routes.DIAGNOSTICS) { DiagnosticsScreen(onBack = back) }
        composable(Routes.ABOUT) { AboutScreen(onBack = back) }
    }
}
