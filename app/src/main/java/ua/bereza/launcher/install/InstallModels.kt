package ua.bereza.launcher.install

import ua.bereza.launcher.core.LauncherException

enum class InstallMode { INSTALL, UPDATE, VERIFY, REPAIR }

enum class Phase {
    IDLE,
    PREPARING,
    VERIFYING,
    CHECKING_SPACE,
    DOWNLOADING,
    EXTRACTING,
    CLIENT_DOWNLOAD,
    FINALIZING,
    /** Файли встановлено, APK клієнта готовий — чекаємо, поки гравець підтвердить встановлення. */
    AWAITING_CLIENT,
    DONE,
    FAILED,
    CANCELLED;

    val isTerminal get() = this == DONE || this == FAILED || this == CANCELLED || this == AWAITING_CLIENT
    val isRunning get() = this != IDLE && !isTerminal
}

/** Реальний стан процесу. Усі числа беруться з фактичних операцій із файлами та мережею. */
data class InstallProgress(
    val mode: InstallMode = InstallMode.INSTALL,
    val phase: Phase = Phase.IDLE,
    val targetVersion: String? = null,
    val currentFile: String? = null,
    val fileIndex: Int = 0,
    val fileCount: Int = 0,
    /** Завантаження: прийнято байтів / всього треба завантажити. */
    val bytesDone: Long = 0,
    val bytesTotal: Long = 0,
    val speedBps: Long = 0,
    val etaSeconds: Long = -1,
    /** Поточна стадія (перевірка / розпакування): оброблено / всього байтів. */
    val stageDone: Long = 0,
    val stageTotal: Long = 0,
    /** Скільки файлів перевірено і скільки виявилися пошкодженими/відсутніми. */
    val verifiedFiles: Int = 0,
    val badFiles: Int = 0,
    val freeBytes: Long = -1,
    val requiredBytes: Long = 0,
    val error: LauncherException? = null,
) {
    val downloadFraction: Float get() = if (bytesTotal > 0) (bytesDone.toFloat() / bytesTotal).coerceIn(0f, 1f) else 0f
    val stageFraction: Float get() = if (stageTotal > 0) (stageDone.toFloat() / stageTotal).coerceIn(0f, 1f) else 0f
}

/** Швидкий підсумок для головного екрана. */
sealed interface GameStatus {
    data object Unknown : GameStatus
    data object NotInstalled : GameStatus
    data class Interrupted(val version: String) : GameStatus
    data class UpdateAvailable(val installed: String, val available: String, val downloadBytes: Long) : GameStatus
    data class Damaged(val missing: Int) : GameStatus
    data class Installed(val version: String, val offline: Boolean) : GameStatus
    data class Blocked(val error: LauncherException) : GameStatus
}
