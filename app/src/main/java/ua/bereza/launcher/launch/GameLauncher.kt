package ua.bereza.launcher.launch

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ua.bereza.launcher.client.ClientProvider
import ua.bereza.launcher.client.ClientStatus
import ua.bereza.launcher.core.AppLog
import ua.bereza.launcher.core.ErrorCode
import ua.bereza.launcher.core.LauncherException
import ua.bereza.launcher.data.SafePath
import ua.bereza.launcher.data.model.ClientSpec
import ua.bereza.launcher.data.model.LaunchMode
import ua.bereza.launcher.data.model.LaunchSpec
import java.io.File

data class ConnectParams(
    val host: String,
    val port: Int,
    val nickname: String,
    val serverName: String,
    val password: String = "",
)

data class LaunchResult(
    /** Параметри сервера передано клієнту одним із налаштованих способів. */
    val autoConnect: Boolean,
    /** Адресу скопійовано в буфер обміну (якщо клієнт не приймає параметри). */
    val addressCopied: Boolean,
)

/** Запуск сумісного Android-клієнта SA-MP. */
interface GameLauncher {
    fun clientStatus(spec: ClientSpec?): ClientStatus
    suspend fun launch(context: Context, spec: ClientSpec?, launch: LaunchSpec, gameDir: File, params: ConnectParams): LaunchResult
}

/**
 * Передає клієнту адресу, порт і нік. Нічого не вигадує: способи передачі (ключі extras,
 * URI-схема, файл налаштувань) беруться з маніфесту, який заповнюється за документацією клієнта.
 */
class ServerConnector {

    fun render(template: String, p: ConnectParams): String = template
        .replace("{host}", p.host)
        .replace("{port}", p.port.toString())
        .replace("{nick}", p.nickname)
        .replace("{password}", p.password)
        .replace("{server_name}", p.serverName)

    /** Записує файл налаштувань клієнта, якщо так передбачено маніфестом. @return true, якщо записано. */
    fun writeConfigFile(launch: LaunchSpec, gameDir: File, p: ConnectParams): Boolean {
        val cf = launch.configFile ?: return false
        if (cf.path.isBlank() || cf.template.isBlank()) return false
        val target = SafePath.resolveInside(gameDir, cf.path)
        target.parentFile?.mkdirs()
        val tmp = File(target.path + ".tmp")
        tmp.writeText(render(cf.template, p), Charsets.UTF_8)
        if (target.exists()) target.delete()
        if (!tmp.renameTo(target)) throw LauncherException(ErrorCode.STORAGE, "Не вдалося записати ${cf.path}")
        AppLog.i("Записано файл налаштувань клієнта: ${cf.path}")
        return true
    }

    fun applyExtras(intent: Intent, launch: LaunchSpec, p: ConnectParams): Boolean {
        if (launch.mode != LaunchMode.INTENT_EXTRAS || launch.extras.isEmpty()) return false
        for ((key, tpl) in launch.extras) {
            if (tpl.startsWith("int:")) {
                val v = render(tpl.removePrefix("int:"), p).toIntOrNull()
                    ?: throw LauncherException(ErrorCode.CONFIG, "Extra $key має бути числом")
                intent.putExtra(key, v)
            } else {
                intent.putExtra(key, render(tpl, p))
            }
        }
        return true
    }

    fun buildUri(launch: LaunchSpec, p: ConnectParams): Uri? {
        if (launch.mode != LaunchMode.URI || launch.uriTemplate.isBlank()) return null
        // Значення кодуються, щоб нік чи пароль не зламали URI.
        val encoded = p.copy(
            nickname = Uri.encode(p.nickname),
            password = Uri.encode(p.password),
            serverName = Uri.encode(p.serverName),
        )
        return Uri.parse(render(launch.uriTemplate, encoded))
    }
}

class ConfigurableClientLauncher(
    private val clients: ClientProvider,
    private val connector: ServerConnector,
) : GameLauncher {

    override fun clientStatus(spec: ClientSpec?): ClientStatus = clients.status(spec)

    override suspend fun launch(
        context: Context,
        spec: ClientSpec?,
        launch: LaunchSpec,
        gameDir: File,
        params: ConnectParams,
    ): LaunchResult {
        when (val st = clients.status(spec)) {
            ClientStatus.NotConfigured -> throw LauncherException(
                ErrorCode.CLIENT_MISSING,
                "Сумісний клієнт SA-MP ще не налаштовано адміністрацією сервера (manifest → client.packageName).",
            )
            is ClientStatus.NotInstalled -> throw LauncherException(ErrorCode.CLIENT_MISSING, "Клієнт гри не встановлено")
            is ClientStatus.Outdated -> throw LauncherException(ErrorCode.CLIENT_MISSING, "Клієнт застарів (версія ${st.installedVersionCode}, потрібна ${st.required})")
            ClientStatus.SignatureMismatch -> throw LauncherException(ErrorCode.CLIENT_SIGNATURE, "Встановлений клієнт має інший підпис — можлива підробка. Видаліть його й встановіть через лаунчер.")
            is ClientStatus.Ready -> Unit
        }
        val pkg = spec!!.packageName

        val wroteConfig = withContext(Dispatchers.IO) { connector.writeConfigFile(launch, gameDir, params) }

        val intent: Intent = when {
            launch.mode == LaunchMode.URI -> {
                val uri = connector.buildUri(launch, params)
                    ?: throw LauncherException(ErrorCode.CONFIG, "Не задано uriTemplate для режиму uri")
                Intent(Intent.ACTION_VIEW, uri).setPackage(pkg)
            }
            launch.activity.isNotBlank() -> Intent(Intent.ACTION_MAIN).setComponent(ComponentName(pkg, launch.activity))
            else -> clients.launchIntent(pkg)
                ?: throw LauncherException(ErrorCode.LAUNCH, "Клієнт не має екрана запуску")
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        val extras = connector.applyExtras(intent, launch, params)

        val auto = extras || wroteConfig || launch.mode == LaunchMode.URI
        var copied = false
        if (!auto) {
            val cm = context.getSystemService(ClipboardManager::class.java)
            cm?.setPrimaryClip(ClipData.newPlainText("Bereza RP", "${params.host}:${params.port}"))
            copied = cm != null
        }
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            throw LauncherException(ErrorCode.LAUNCH, "Клієнт не приймає такий спосіб запуску. Перевірте блок launch у маніфесті.", e)
        } catch (e: SecurityException) {
            throw LauncherException(ErrorCode.LAUNCH, "Клієнт заборонив запуск ззовні (Activity не експортована)", e)
        }
        AppLog.i("Клієнт запущено: $pkg, автопідключення=$auto")
        return LaunchResult(autoConnect = auto, addressCopied = copied)
    }
}
