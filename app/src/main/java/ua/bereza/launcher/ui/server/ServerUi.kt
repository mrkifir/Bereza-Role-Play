package ua.bereza.launcher.ui.server

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import ua.bereza.launcher.R
import ua.bereza.launcher.data.ServerState
import ua.bereza.launcher.ui.theme.BerezaColors

fun serverStatusColor(state: ServerState): Color = when (state) {
    is ServerState.Online -> BerezaColors.Green
    is ServerState.Offline -> BerezaColors.Error
    ServerState.NoInternet -> BerezaColors.Warning
    ServerState.NotConfigured -> BerezaColors.Warning
    ServerState.Unknown -> BerezaColors.TextFaint
}

@Composable
fun serverStatusLabel(state: ServerState): String = when (state) {
    is ServerState.Online -> stringResource(R.string.server_online)
    is ServerState.Offline -> stringResource(R.string.server_offline)
    ServerState.NoInternet -> stringResource(R.string.server_no_internet)
    ServerState.NotConfigured -> stringResource(R.string.server_not_configured)
    ServerState.Unknown -> stringResource(R.string.server_checking)
}
