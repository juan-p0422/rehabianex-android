package com.example.rehabianex.core.error

import java.io.IOException
import java.net.SocketTimeoutException
import retrofit2.HttpException

object ErrorMapper {
    fun fromThrowable(
        error: Throwable,
        timeoutMessage: String = "El servidor tardó demasiado en responder.",
        connectionMessage: String = "Sin conexión a internet.",
        unexpectedMessage: String = "Ocurrió un error inesperado.",
    ): ErrorInfo {
        val causes = generateSequence<Throwable>(error) { it.cause }.toList()
        val httpError = causes.filterIsInstance<HttpException>().firstOrNull()
        return when {
            causes.any { it is SocketTimeoutException } -> ErrorInfo(
                message = timeoutMessage,
                throwable = error,
            )
            causes.any { it is IOException } -> ErrorInfo(
                message = connectionMessage,
                throwable = error,
            )
            httpError != null -> fromHttpCode(
                code = httpError.code(),
                throwable = error,
            )
            else -> ErrorInfo(
                message = unexpectedMessage,
                throwable = error,
            )
        }
    }

    fun fromHttpCode(
        code: Int?,
        apiMessage: String? = null,
        throwable: Throwable? = null,
    ): ErrorInfo {
        val defaultMessage = when (code) {
            400 -> "La solicitud contiene datos inválidos. Revisa la información e inténtalo nuevamente."
            401 -> "Tu sesión expiró. Inicia sesión nuevamente."
            403 -> "No tienes permiso para realizar esta acción."
            404 -> "No se encontró la información solicitada."
            409 -> "La operación entra en conflicto con el estado actual. Actualiza la información e inténtalo nuevamente."
            422 -> "Revisa los datos marcados e inténtalo nuevamente."
            429 -> "Has realizado demasiadas solicitudes. Espera un momento antes de continuar."
            502 -> "Un servicio externo no está disponible temporalmente. Inténtalo nuevamente."
            in 500..599 -> "El servicio presenta un problema temporal. Inténtalo más tarde."
            else -> "No fue posible completar la solicitud."
        }

        return ErrorInfo(
            message = safeBackendMessage(code, apiMessage) ?: defaultMessage,
            code = code,
            throwable = throwable,
        )
    }

    fun safeBackendMessage(code: Int?, message: String?): String? {
        if (code !in BACKEND_MESSAGE_CODES) return null
        val rawMessage = message
            ?.filterNot { it.isISOControl() }
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null

        if (TECHNICAL_MARKERS.any { marker ->
                rawMessage.contains(marker, ignoreCase = true)
            }
        ) {
            return null
        }

        val safeMessage = rawMessage
            .replace(HTML_TAG_REGEX, " ")
            .replace("<", "")
            .replace(">", "")
            .replace(Regex("\\s+"), " ")
            .take(MAX_MESSAGE_LENGTH)
            .trim()
            .takeIf { it.isNotBlank() }
            ?: return null

        return safeMessage
    }

    private const val MAX_MESSAGE_LENGTH = 180
    private val BACKEND_MESSAGE_CODES = setOf(400, 403, 404, 409, 422, 429)
    private val TECHNICAL_MARKERS = setOf(
        "exception",
        "stack trace",
        "traceback",
        "nullpointer",
        "firebase",
        "firestore",
        "retrofit",
        "okhttp",
        "java.",
        "kotlin.",
        "sql",
        "queryexception",
        "database",
        "laravel",
        "pdoexception",
        "syntax error",
        "undefined",
        "internal server error",
        "mensaje interno",
        " at com.",
        "stacktrace",
        "vendor/",
        "/var/",
        "/app/",
        "storage/framework",
        "debug:",
    )
    private val HTML_TAG_REGEX = Regex("<[^>]*>")
}

data class ErrorInfo(
    val message: String,
    val code: Int? = null,
    val throwable: Throwable? = null,
)
