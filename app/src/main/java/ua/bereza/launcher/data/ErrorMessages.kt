package ua.bereza.launcher.data

import ua.bereza.launcher.R
import ua.bereza.launcher.network.HttpErrorKind
import ua.bereza.launcher.network.HttpException
import ua.bereza.launcher.network.QueryErrorKind
import ua.bereza.launcher.network.SampQueryException

/** Перетворення технічних помилок у зрозумілі повідомлення. */
object ErrorMessages {
    fun from(error: Throwable): UiText = when (error) {
        is SampQueryException -> when (error.kind) {
            QueryErrorKind.DNS -> UiText.Res(R.string.err_dns)
            QueryErrorKind.NO_IPV4 -> UiText.Res(R.string.err_no_ipv4)
            QueryErrorKind.TIMEOUT -> UiText.Res(R.string.err_server_timeout)
            QueryErrorKind.MALFORMED -> UiText.Res(R.string.err_server_malformed)
            QueryErrorKind.NETWORK -> UiText.Res(R.string.err_network)
        }
        is HttpException -> when (error.kind) {
            HttpErrorKind.NOT_CONFIGURED -> UiText.Res(R.string.err_not_configured)
            HttpErrorKind.INSECURE_URL -> UiText.Res(R.string.err_insecure_url)
            HttpErrorKind.BAD_URL -> UiText.Res(R.string.err_bad_url)
            HttpErrorKind.DNS -> UiText.Res(R.string.err_dns)
            HttpErrorKind.TIMEOUT -> UiText.Res(R.string.err_timeout)
            HttpErrorKind.NETWORK -> UiText.Res(R.string.err_network)
            HttpErrorKind.HTTP_STATUS -> UiText.Res(R.string.err_http_status, error.statusCode)
            HttpErrorKind.TOO_LARGE -> UiText.Res(R.string.err_too_large)
            HttpErrorKind.BAD_JSON -> UiText.Res(R.string.err_bad_json)
        }
        is InvalidDataException -> UiText.Res(R.string.err_invalid_data, error.message ?: "")
        else -> UiText.Res(R.string.err_unexpected, error.message ?: error.javaClass.simpleName)
    }
}

/** Відповідь сервера коректна як JSON, але не відповідає очікуваному формату. */
class InvalidDataException(message: String) : Exception(message)
