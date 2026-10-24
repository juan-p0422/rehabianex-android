package com.example.rehabianex.data.repository

import com.example.rehabianex.core.error.ErrorMapper
import com.example.rehabianex.core.session.SessionManager
import com.example.rehabianex.core.session.UserSession
import com.example.rehabianex.data.model.AuthResponse
import com.example.rehabianex.data.model.AuthSession
import com.example.rehabianex.data.model.LoginRequest
import com.example.rehabianex.data.model.PRIVACY_NOTICE_VERSION
import com.example.rehabianex.data.model.RegisterRequest
import com.example.rehabianex.data.model.SessionProfileDto
import com.example.rehabianex.data.model.isPatientPrivacyRestricted
import com.example.rehabianex.data.model.resolveAuthSessionDisplayName
import com.example.rehabianex.data.remote.ApiErrorParser
import com.example.rehabianex.data.remote.ApiService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import retrofit2.Response

sealed interface AuthResult {
    data class Success(
        val session: AuthSession,
        val message: String,
    ) : AuthResult

    data class AccountCreated(
        val uid: String,
        val role: String,
        val message: String,
    ) : AuthResult

    data class Failure(
        val message: String,
        val fieldErrors: Map<String, String> = emptyMap(),
        val statusCode: Int? = null,
    ) : AuthResult
}

data class LogoutResult(
    val remoteLogoutConfirmed: Boolean,
    val message: String? = null,
    val requiresPendingNotesConfirmation: Boolean = false,
    val pendingNotesCount: Int = 0,
)

class AuthRepository(
    private val apiService: ApiService,
    private val sessionManager: SessionManager,
    private val clearLocalUserData: suspend (uid: String?, role: String?) -> Unit = { _, _ -> },
    private val pendingLocalNotesCount: suspend (uid: String?) -> Int = { 0 },
    private val clearPreviousUserData: suspend (uid: String?, role: String?) -> Unit = { _, _ -> },
    private val restrictPrivatePatientIdentity: suspend (
        uid: String,
        profile: SessionProfileDto?,
        safeDisplayName: String,
        isAnonymous: Boolean,
    ) -> Unit = { _, _, _, _ -> },
    private val registerFcmToken: suspend () -> Unit = {},
    private val revokeFcmToken: suspend () -> Unit = {},
) {
    suspend fun login(request: LoginRequest): AuthResult = executeRequest {
        apiService.login(request)
    }

    suspend fun register(request: RegisterRequest): AuthResult = executeRequest(
        allowTokenlessAccount = true,
    ) {
        apiService.register(request)
    }

    suspend fun registerNotificationsAfterPrivacyGate() {
        runNonCriticalFcmOperation(registerFcmToken)
    }

    /**
     * El cierre local es obligatorio incluso cuando el backend no responde. Los
     * tokens se eliminan al final y no dependen del resultado de la limpieza de caché.
     */
    suspend fun logout(confirmPendingNotesDeletion: Boolean = false): LogoutResult {
        val uid = sessionManager.getUid()
        val role = sessionManager.getRole()
        val pendingNotes = runCatching { pendingLocalNotesCount(uid) }.getOrElse { UNKNOWN_PENDING_COUNT }
        if (pendingNotes != 0 && !confirmPendingNotesDeletion) {
            return LogoutResult(
                remoteLogoutConfirmed = false,
                message = if (pendingNotes > 0) {
                    "Hay registros emocionales pendientes de sincronizar."
                } else {
                    "No fue posible comprobar si hay registros pendientes."
                },
                requiresPendingNotesConfirmation = true,
                pendingNotesCount = pendingNotes,
            )
        }
        var result = LogoutResult(remoteLogoutConfirmed = false)

        try {
            runNonCriticalFcmOperation(revokeFcmToken)
            val response = apiService.logout()
            result = if (response.isSuccessful || response.code() == 401) {
                LogoutResult(remoteLogoutConfirmed = response.isSuccessful)
            } else {
                LogoutResult(
                    remoteLogoutConfirmed = false,
                    message = LOCAL_LOGOUT_WARNING,
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            result = LogoutResult(
                remoteLogoutConfirmed = false,
                message = LOCAL_LOGOUT_WARNING,
            )
        } finally {
            withContext(NonCancellable) {
                runCatching { clearLocalUserData(uid, role) }
                sessionManager.clear()
            }
        }

        return result
    }

    private suspend fun executeRequest(
        allowTokenlessAccount: Boolean = false,
        request: suspend () -> Response<AuthResponse>,
    ): AuthResult {
        return try {
            val response = request()
            if (response.isSuccessful) {
                readSuccessfulResponse(response, allowTokenlessAccount)
            } else {
                readErrorResponse(response)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AuthResult.Failure(ErrorMapper.fromThrowable(error).message)
        }
    }

    private suspend fun readSuccessfulResponse(
        response: Response<AuthResponse>,
        allowTokenlessAccount: Boolean,
    ): AuthResult {
        val body = response.body()
        val tokens = body?.resolvedTokens()
        val auth = body?.resolvedAuth()
        val profile = body?.resolvedProfile()
        val idToken = tokens?.idToken?.takeIf {
            it.isSafeCredential(MAX_TOKEN_LENGTH)
        }
        val refreshToken = tokens?.refreshToken?.takeIf { it.isNotBlank() }
        val uid = auth?.uid?.takeIf {
            it.isSafeCredential(MAX_UID_LENGTH)
        }
        val role = (auth?.role ?: profile?.role)?.takeIf { it in VALID_ROLES }
        val email = auth?.email?.toSafeProfileText()
        val displayName = resolveAuthSessionDisplayName(role, auth, profile)
            ?.toSafeProfileText()
        val privacyNoticeConfirmedVersion = profile?.privacyNoticeRead
            ?.takeIf {
                it.status == "confirmed" &&
                    it.isCurrent == true &&
                    it.privacyNoticeVersion == PRIVACY_NOTICE_VERSION
            }
            ?.privacyNoticeVersion

        if (
            allowTokenlessAccount &&
            tokens?.idToken.isNullOrBlank() &&
            uid != null &&
            role != null
        ) {
            return AuthResult.AccountCreated(
                uid = uid,
                role = role,
                message = "Cuenta creada correctamente. Ahora inicia sesión.",
            )
        }

        if (
            idToken == null ||
            uid == null ||
            role == null ||
            (refreshToken != null && !refreshToken.isSafeCredential(MAX_TOKEN_LENGTH))
        ) {
            return AuthResult.Failure(
                "El servidor devolvió una sesión incompleta. Contacta a soporte.",
            )
        }

        val session = AuthSession(
            idToken = idToken,
            refreshToken = refreshToken,
            uid = uid,
            role = role,
            email = email,
            displayName = displayName,
            privacyNoticeStatusChecked = profile != null,
            privacyNoticeConfirmedVersion = privacyNoticeConfirmedVersion,
        )

        return try {
            val previousUid = sessionManager.getUid()
            val previousRole = sessionManager.getRole()
            if (
                !previousUid.isNullOrBlank() &&
                (previousUid != session.uid || previousRole != session.role)
            ) {
                runNonCriticalFcmOperation(revokeFcmToken)
                clearPreviousUserData(previousUid, previousRole)
            }
            val privacyRestricted = session.role == "patient" && (
                profile?.isPatientPrivacyRestricted() == true ||
                    auth.isPatientPrivacyRestricted()
                )
            if (privacyRestricted) {
                restrictPrivatePatientIdentity(
                    session.uid,
                    profile,
                    session.displayName ?: "Paciente anónimo",
                    profile?.isAnonymous == true || auth.isAnonymous == true,
                )
            }
            sessionManager.saveSession(
                UserSession(
                    uid = session.uid,
                    email = session.email,
                    role = session.role,
                    token = session.idToken,
                    refreshToken = session.refreshToken,
                    displayName = session.displayName,
                ),
            )
            AuthResult.Success(
                session = session,
                message = "Autenticación completada.",
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AuthResult.Failure(
                "No fue posible proteger la sesión en este dispositivo.",
            )
        }
    }

    private fun readErrorResponse(
        response: Response<AuthResponse>,
    ): AuthResult.Failure {
        val parsedError = ApiErrorParser.parse(response.errorBody(), response.code())
        val defaultMessage = when (response.code()) {
            401 -> "El correo o la contraseña son incorrectos."
            403 -> "Tu cuenta no tiene permiso para iniciar sesión."
            404 -> "El servicio de autenticación no está disponible."
            409 -> "Ya existe una cuenta con estos datos."
            422 -> "Revisa los datos marcados en el formulario."
            else -> ErrorMapper.fromHttpCode(response.code()).message
        }

        return AuthResult.Failure(
            message = ErrorMapper.safeBackendMessage(
                response.code(),
                parsedError.message,
            ) ?: defaultMessage,
            fieldErrors = parsedError.fieldErrors,
            statusCode = response.code(),
        )
    }

    private suspend fun runNonCriticalFcmOperation(operation: suspend () -> Unit) {
        try {
            operation()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // FCM availability must never block authentication or local logout cleanup.
        }
    }

    private companion object {
        val VALID_ROLES = setOf("patient", "supervisor", "admin")
        const val MAX_TOKEN_LENGTH = 16_384
        const val MAX_UID_LENGTH = 128
        const val UNKNOWN_PENDING_COUNT = -1
        const val LOCAL_LOGOUT_WARNING =
            "La sesión se cerró en este dispositivo. No fue posible confirmar el cierre remoto."
    }
}

private fun String.isSafeCredential(maxLength: Int): Boolean {
    return isNotBlank() &&
        length <= maxLength &&
        none { it.isWhitespace() || it.isISOControl() }
}

private fun String.toSafeProfileText(): String? {
    return filterNot { it.isISOControl() }
        .replace("<", "")
        .replace(">", "")
        .trim()
        .take(180)
        .takeIf { it.isNotBlank() }
}
