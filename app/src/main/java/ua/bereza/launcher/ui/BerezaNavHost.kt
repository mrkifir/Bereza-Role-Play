package ua.bereza.launcher.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ua.bereza.launcher.ui.screens.DiagnosticsScreen
import ua.bereza.launcher.ui.screens.ErrorScreen
import ua.bereza.launcher.ui.screens.HomeScreen
import ua.bereza.launcher.ui.screens.InstallScreen
import ua.bereza.launcher.ui.screens.NewsScreen
import ua.bereza.launcher.ui.screens.NicknameDialog
import ua.bereza.launcher.ui.screens.ServerScreen
import ua.bereza.launcher.ui.screens.SettingsScreen
import ua.bereza.launcher.ui.theme.Bz

object Routes {
    const val HOME = "home"
    const val INSTALL = "install"
    const val SETTINGS = "settings"
    const val NEWS = "news"
    const val SERVER = "server"
    const val DIAGNOSTICS = "diagnostics"
    const val ERROR = "error"
}

@Composable
fun BerezaNavHost(vm: MainViewModel) {
    val nav = rememberNavController()
    val ctx = LocalContext.current
    var askNick by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.events.collect { ev ->
            when (ev) {
                UiEvent.OpenInstall -> nav.go(Routes.INSTALL)
                UiEvent.OpenError -> nav.go(Routes.ERROR)
                UiEvent.AskNickname -> askNick = true
                is UiEvent.Toast -> Toast.makeText(ctx, ev.text, Toast.LENGTH_LONG).show()
                is UiEvent.OpenUrl -> {
                    val uri = Uri.parse(ev.url)
                    if (uri.scheme == "https") {
                        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (_: ActivityNotFoundException) { }
                    }
                }
                UiEvent.OpenUnknownSourcesSettings -> {
                    Toast.makeText(ctx, "Дозвольте Bereza RP встановлювати застосунки", Toast.LENGTH_LONG).show()
                    ctx.startActivity(vm.unknownSourcesIntent())
                }
            }
        }
    }

    val duration = 280
    Box(Modifier.fillMaxSize().background(Bz.Soot)) {
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            enterTransition = {
                fadeIn(tween(duration)) + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(duration)) { it / 6 }
            },
            exitTransition = { fadeOut(tween(duration / 2)) },
            popEnterTransition = { fadeIn(tween(duration)) },
            popExitTransition = {
                fadeOut(tween(duration / 2)) + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(duration)) { it / 6 }
            },
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    vm,
                    onOpenSettings = { nav.go(Routes.SETTINGS) },
                    onOpenNews = { nav.go(Routes.NEWS) },
                    onOpenServer = { nav.go(Routes.SERVER) },
                    onOpenInstall = { nav.go(Routes.INSTALL) },
                    onOpenDiagnostics = { nav.go(Routes.DIAGNOSTICS) },
                )
            }
            composable(Routes.INSTALL) {
                InstallScreen(vm, onBack = { nav.popBackStack() }, onOpenError = { nav.go(Routes.ERROR) })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    vm,
                    onBack = { nav.popBackStack() },
                    onOpenDiagnostics = { nav.go(Routes.DIAGNOSTICS) },
                    onOpenServer = { nav.go(Routes.SERVER) },
                    onOpenNews = { nav.go(Routes.NEWS) },
                )
            }
            composable(Routes.NEWS) { NewsScreen(vm, onBack = { nav.popBackStack() }) }
            composable(Routes.SERVER) { ServerScreen(vm, onBack = { nav.popBackStack() }) }
            composable(Routes.DIAGNOSTICS) { DiagnosticsScreen(vm, onBack = { nav.popBackStack() }) }
            composable(Routes.ERROR) {
                ErrorScreen(
                    vm,
                    onBack = { nav.popBackStack() },
                    onOpenDiagnostics = { nav.go(Routes.DIAGNOSTICS) },
                    onOpenSettings = { nav.popBackStack(); nav.go(Routes.SETTINGS) },
                )
            }
        }
        if (askNick) {
            NicknameDialog(
                vm,
                onDismiss = { askNick = false },
                onConfirmed = { nick -> askNick = false; vm.saveNicknameAndPlay(nick, ctx) },
            )
        }
    }
}

/** Навігація без дублювання екрана поверх самого себе. */
private fun NavHostController.go(route: String) {
    if (currentDestination?.route == route) return
    navigate(route) { launchSingleTop = true }
}
