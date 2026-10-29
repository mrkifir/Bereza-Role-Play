package ua.bereza.launcher.launcher

/** Чи встановлено сумісний ігровий клієнт. */
sealed interface ClientAvailability {
    data object NotConfigured : ClientAvailability
    data class NotInstalled(val packageName: String) : ClientAvailability
    data class Installed(
        val packageName: String,
        val label: String,
        val versionName: String,
        val versionCode: Long,
        val launchable: Boolean,
    ) : ClientAvailability
}

data class LaunchRequest(
    val host: String,
    val port: Int,
    val nickname: String,
    /** Скопіювати адресу сервера в буфер обміну перед запуском. */
    val copyAddress: Boolean,
)

sealed interface LaunchResult {
    /** Клієнт відкрито. [directConnect] = true лише якщо адаптер реально передав сервер/нік клієнту. */
    data class Started(val directConnect: Boolean, val addressCopied: Boolean) : LaunchResult
    data object NotConfigured : LaunchResult
    data object NotInstalled : LaunchResult
    data object NoLaunchActivity : LaunchResult
    data class Failed(val reason: String) : LaunchResult
}

/**
 * Адаптер запуску зовнішнього клієнта SA-MP.
 * Нова інтеграція (наприклад, клієнт із документованим intent для підключення до сервера)
 * — це окрема реалізація цього інтерфейсу. Див. README → «Інтеграція клієнта».
 */
interface GameClientLauncher {
    /** Чи вміє адаптер передати клієнту адресу сервера та нікнейм напряму. */
    val supportsDirectConnect: Boolean

    fun availability(): ClientAvailability

    fun launch(request: LaunchRequest): LaunchResult
}
