package ua.bereza.launcher.data

import ua.bereza.launcher.network.SampServerInfo

/** Загальний стан завантаження даних із мережі. */
sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data object NotConfigured : LoadState<Nothing>
    data class Success<T>(val data: T) : LoadState<T>
    data class Error(val message: UiText) : LoadState<Nothing>
}

data class NewsItem(
    val id: String,
    val title: String,
    val date: String,
    val body: String,
    val url: String?,
    val tag: String?,
)

data class LauncherUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val sizeBytes: Long,
    val date: String,
    val changelog: List<String>,
) {
    val canBeInstalled: Boolean get() = apkUrl.startsWith("https://") && sha256.length == 64
}

sealed interface ServerState {
    data object Unknown : ServerState
    data object NoInternet : ServerState
    data object NotConfigured : ServerState
    data class Online(val info: SampServerInfo, val checkedAt: Long) : ServerState
    data class Offline(val reason: UiText, val checkedAt: Long) : ServerState
}

data class ServerSnapshot(
    val state: ServerState = ServerState.Unknown,
    val refreshing: Boolean = false,
    val rules: Map<String, String> = emptyMap(),
)
