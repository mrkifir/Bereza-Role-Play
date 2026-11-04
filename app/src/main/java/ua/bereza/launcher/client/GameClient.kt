package ua.bereza.launcher.client

import android.app.Activity
import android.content.Context
import android.os.Build
import java.io.File

/**
 * Точка інтеграції мультиплеєр-клієнта.
 *
 * Лаунчер нічого не знає про внутрішню будову клієнта — лише цей контракт.
 * Реалізація з'явиться, коли буде клієнт із підтвердженою ліцензією (див. docs/CLIENT_INTEGRATION.md).
 * Поки [ClientRegistry.clients] порожній, кнопка «ГРАТИ» вимкнена і чесно показує причину.
 */
interface GameClient {
    val id: String
    val displayName: String
    /** ABI, під які зібрано нативну частину клієнта (arm64-v8a, armeabi-v7a). */
    val supportedAbis: Set<String>
    /** Чим підтверджено право на використання (ліцензія / письмовий дозвіл). */
    val licenseNote: String

    fun availability(context: Context, gameDir: File): Availability

    /** Запуск і підключення. Викликається лише коли [availability] == Ready. */
    fun launch(activity: Activity, params: LaunchParams): LaunchResult
}

data class LaunchParams(
    val host: String,
    val port: Int,
    val nickname: String,
    val password: String? = null,
    val gameDir: File,
)

sealed interface Availability {
    data object Ready : Availability
    data class Unavailable(val reason: String, val details: List<String> = emptyList()) : Availability
}

sealed interface LaunchResult {
    data object Started : LaunchResult
    data class Error(val message: String) : LaunchResult
}

object ClientRegistry {
    /**
     * Зареєстровані клієнти. Свідомо порожньо: жодного клієнта з підтвердженою
     * ліцензією поки немає, а підставляти чужий код без дозволу не можна.
     */
    val clients: List<GameClient> = emptyList()

    fun deviceAbis(): List<String> = Build.SUPPORTED_ABIS.toList()

    /** Перший клієнт, що підтримує ABI пристрою. */
    fun select(): GameClient? = clients.firstOrNull { c -> deviceAbis().any { it in c.supportedAbis } }

    fun availability(context: Context, gameDir: File): Availability {
        if (clients.isEmpty()) return Availability.Unavailable(
            "Клієнт SA-MP ще не інтегровано",
            listOf(
                "Лаунчер уже вміє завантажувати й перевіряти файли Bereza RP та показувати статус сервера.",
                "Запуск гри з'явиться після підключення клієнта з легальною ліцензією.",
            ),
        )
        val c = select() ?: return Availability.Unavailable(
            "Пристрій не підтримується",
            listOf("ABI пристрою: ${deviceAbis().joinToString()}"),
        )
        return c.availability(context, gameDir)
    }
}
