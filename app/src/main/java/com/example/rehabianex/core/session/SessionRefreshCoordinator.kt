package com.example.rehabianex.core.session

import com.example.rehabianex.core.error.ErrorMapper
import com.example.rehabianex.data.model.AuthResponse
import com.example.rehabianex.data.model.RefreshRequest
import com.example.rehabianex.data.remote.ApiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.Response

sealed interface SessionRefreshResult {
    data object Success : SessionRefreshResult

    data class Failure(
        val message: String,
        val statusCode: Int?,
        val invalidatesSession: Boolean,
    ) : SessionRefreshResult
}

/**
 * Serializa la renovación de sesión para que peticiones concurrentes que reciben 401
 * compartan una única llamada. Firebase sigue siendo la autoridad de los tokens; este
 * coordinador solo consume el contrato de renovación expuesto por el backend.
 */
class SessionRefreshCoordinator private constructor(
    private val sessionManager: SessionManager,
    private val refreshCall: suspend (RefreshRequest) -> Response<AuthResponse>,
) {
    constructor(
        apiService: ApiService,
        sessionManager: SessionManager,
    ) : this(sessionManager, apiService::refresh)

    internal constructor(
        sessionManager: SessionManager,
        refreshCall: suspend (RefreshRequest) -> Response<AuthResponse>,
        @Suppress("UNUSED_PARAMETER") testOnly: Unit,
    ) : this(sessionManager, refreshCall)

    private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlightMutex = Mutex()
    private var inFlight: Deferred<SessionRefreshResult>? = null

    suspend fun refreshAfterUnauthorized(rejectedIdToken: String?): SessionRefreshResult {
        if (tokenWasAlreadyRenewed(rejectedIdToken)) {
            return SessionRefreshResult.Success
        }

        val deferred = inFlightMutex.withLock {
            if (tokenWasAlreadyRenewed(rejectedIdToken)) {
                return@withLock null
            }
            inFlight ?: refreshScope.async { performRefresh() }.also { inFlight = it }
        } ?: return SessionRefreshResult.Success

        return try {
            deferred.await()
        } finally {
            inFlightMutex.withLock {
                if (inFlight === deferred && deferred.isCompleted) {
                    inFlight = null
                }
            }
        }
    }

    private fun tokenWasAlreadyRenewed(rejectedIdToken: String?): Boolean {
        if (rejectedIdToken.isNullOrBlank()) return false
        val currentToken = sessionManager.getIdToken()
        return !currentToken.isNullOrBlank() && currentToken != rejectedIdToken
    }

    private suspend fun performRefresh(): SessionRefreshResult {
        val refreshToken = sessionManager.getRefreshToken().validatedToken()
            ?: return invalidSession("No hay un token de renovación válido.")
        val uid = sessionManager.getUid()?.takeIf { it.isNotBlank() }
            ?: return invalidSession("La sesión local está incompleta.")
        val role = sessionManager.getRole()?.takeIf { it in VALID_ROLES }
            ?: return invalidSession("La sesión local está incompleta.")
        val email = sessionManager.getEmail()
        val displayName = sessionManager.getDisplayName()

        val response = refreshCall(RefreshRequest(refreshToken))
        if (!response.isSuccessful) {
            val statusCode = response.code()
            return SessionRefreshResult.Failure(
                message = ErrorMapper.fromHttpCode(statusCode).message,
                statusCode = statusCode,
                invalidatesSession = statusCode == HTTP_UNAUTHORIZED,
            )
        }

        val tokens = response.body()?.resolvedTokens()
        val idToken = tokens?.idToken.validatedToken()
            ?: return SessionRefreshResult.Failure(
                message = "El servidor no devolvió una sesión renovada válida.",
                statusCode = null,
                invalidatesSession = false,
            )
        val rotatedRefreshToken = tokens?.refreshToken.validatedToken() ?: refreshToken

        // Un cierre de sesión o un cambio de usuario ocurrido mientras se renovaba no debe
        // ser revertido por una respuesta tardía.
        if (
            sessionManager.getUid() != uid ||
            sessionManager.getRole() != role ||
            sessionManager.getRefreshToken() != refreshToken
        ) {
            return SessionRefreshResult.Failure(
                message = "La sesión cambió durante la renovación.",
                statusCode = HTTP_UNAUTHORIZED,
                invalidatesSession = false,
            )
        }

        sessionManager.saveSession(
            UserSession(
                uid = uid,
                role = role,
                token = idToken,
                refreshToken = rotatedRefreshToken,
                email = email,
                displayName = displayName,
            ),
        )
        return SessionRefreshResult.Success
    }

    private fun invalidSession(message: String) = SessionRefreshResult.Failure(
        message = message,
        statusCode = HTTP_UNAUTHORIZED,
        invalidatesSession = true,
    )

    private fun String?.validatedToken(): String? = this
        ?.takeIf { token ->
            token.isNotBlank() &&
                token.length <= MAX_TOKEN_LENGTH &&
                token.none(Char::isWhitespace) &&
                token.none(Char::isISOControl)
        }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
        const val MAX_TOKEN_LENGTH = 16_384
        val VALID_ROLES = setOf("patient", "supervisor", "admin")
    }
}
