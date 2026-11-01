package com.example.rehabianex.data.repository

import com.example.rehabianex.core.error.ErrorMapper
import com.example.rehabianex.core.session.SessionManager
import com.example.rehabianex.core.session.SessionRefreshCoordinator
import com.example.rehabianex.core.session.SessionRefreshResult
import com.example.rehabianex.core.session.UserSession
import com.example.rehabianex.data.model.AccountDeletionReceipt
import com.example.rehabianex.data.model.AccountDeletionRequest
import com.example.rehabianex.data.model.AuthResponse
import com.example.rehabianex.data.model.DataControlCategory
import com.example.rehabianex.data.model.DataControlSummary
import com.example.rehabianex.data.model.DataControlSummaryResponse
import com.example.rehabianex.data.model.LoginRequest
import com.example.rehabianex.data.remote.ApiErrorParser
import com.example.rehabianex.data.remote.ApiService
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import retrofit2.Response

sealed interface DataControlResult<out T> {
    data class Success<T>(val data: T) : DataControlResult<T>
    data class Failure(
        val message: String,
        val statusCode: Int? = null,
        val fieldErrors: Map<String, String> = emptyMap(),
    ) : DataControlResult<Nothing>
}

class DataControlRepository(
    private val apiService: ApiService,
    private val sessionManager: SessionManager,
    private val clearLocalUserData: suspend (uid: String?, role: String?) -> Unit,
    private val suspendFcmForCurrentAccount: suspend () -> Unit = {},
    private val markLocalCleanupPending: (uid: String, role: String) -> Unit = { _, _ -> },
    private val clearLocalCleanupPending: () -> Unit = {},
    private val sessionRefreshCoordinator: SessionRefreshCoordinator =
        SessionRefreshCoordinator(apiService, sessionManager),
) {
    suspend fun loadSummary(): DataControlResult<DataControlSummary> = safeRequest {
        val response = authorizedCall(apiService::dataControlSummary)
        if (!response.isSuccessful) throw DataControlFailure(response.toFailure())
        response.body()?.toSummary()
            ?: throw DataControlFailure(DataControlResult.Failure("El servidor no devolvió el inventario de datos."))
    }

    suspend fun reauthenticate(password: String): DataControlResult<Unit> = safeRequest {
        val uid = sessionManager.getUid().safeCredential(MAX_UID_LENGTH)
            ?: throw DataControlFailure(DataControlResult.Failure("La sesión local está incompleta.", 401))
        val role = sessionManager.getRole()?.takeIf { it in VALID_ROLES }
            ?: throw DataControlFailure(DataControlResult.Failure("La sesión local está incompleta.", 401))
        val email = sessionManager.getEmail()?.trim()?.takeIf { it.isNotBlank() }
            ?: throw DataControlFailure(
                DataControlResult.Failure(
                    "Esta cuenta no tiene un correo reutilizable. Solicita atención manual para continuar.",
                    422,
                ),
            )
        if (password.isBlank() || password.length > MAX_PASSWORD_LENGTH || password.any(Char::isISOControl)) {
            throw DataControlFailure(
                DataControlResult.Failure(
                    "Ingresa tu contraseña actual.",
                    422,
                    mapOf("password" to "Ingresa una contraseña válida."),
                ),
            )
        }

        val response = apiService.login(LoginRequest(email = email, password = password))
        if (!response.isSuccessful) {
            val parsed = ApiErrorParser.parse(response.errorBody(), response.code())
            throw DataControlFailure(
                DataControlResult.Failure(
                    message = if (response.code() == 401) {
                        "No fue posible confirmar tu identidad. Revisa la contraseña."
                    } else {
                        ErrorMapper.safeBackendMessage(response.code(), parsed.message)
                            ?: ErrorMapper.fromHttpCode(response.code()).message
                    },
                    statusCode = response.code(),
                    fieldErrors = parsed.fieldErrors,
                ),
            )
        }

        val body = response.body()
        val auth = body?.resolvedAuth()
        val profile = body?.resolvedProfile()
        val returnedUid = auth?.uid.safeCredential(MAX_UID_LENGTH)
        val returnedRole = auth?.role ?: profile?.role
        val tokens = body?.resolvedTokens()
        val idToken = tokens?.idToken.safeCredential(MAX_TOKEN_LENGTH)
        val refreshToken = tokens?.refreshToken.safeCredential(MAX_TOKEN_LENGTH)
        if (returnedUid != uid || returnedRole != role || idToken == null) {
            throw DataControlFailure(
                DataControlResult.Failure("La reautenticación no corresponde a la cuenta activa.", 401),
            )
        }

        sessionManager.saveSession(
            UserSession(
                uid = uid,
                role = role,
                token = idToken,
                refreshToken = refreshToken ?: sessionManager.getRefreshToken(),
                email = sessionManager.getEmail(),
                displayName = sessionManager.getDisplayName(),
            ),
        )
    }

    suspend fun requestAccountDeletion(
        confirmation: String,
        acknowledgeRetentionLimits: Boolean,
    ): DataControlResult<AccountDeletionReceipt> = safeRequest {
        val uid = sessionManager.getUid()
        val role = sessionManager.getRole()
        val response = apiService.requestAccountDeactivation(
            AccountDeletionRequest(
                confirmation = confirmation,
                acknowledgeRetentionLimits = acknowledgeRetentionLimits,
            ),
        )
        if (response.code() != 202) throw DataControlFailure(response.toFailure())
        val body = response.body()
            ?: throw DataControlFailure(DataControlResult.Failure("El servidor no confirmó la solicitud."))
        val request = body.request
            ?: throw DataControlFailure(DataControlResult.Failure("El servidor no confirmó la solicitud."))
        val requestId = request.requestId.cleanText(MAX_REQUEST_ID_LENGTH)
            ?: throw DataControlFailure(DataControlResult.Failure("El servidor no confirmó la solicitud."))
        if (body.syncBlocked != true || body.localCleanupRequired != true) {
            throw DataControlFailure(DataControlResult.Failure("El servidor no confirmó el bloqueo de sincronización."))
        }
        val cleanupStatus = body.cleanupStatus.orEmpty()
            .mapNotNull { (key, value) ->
                val safeKey = key.cleanText(80) ?: return@mapNotNull null
                val safeValue = value.cleanText(80) ?: return@mapNotNull null
                safeKey to safeValue
            }
            .toMap()
        if (cleanupStatus["remote_access"] != "blocked") {
            throw DataControlFailure(DataControlResult.Failure("El servidor no confirmó el bloqueo de acceso remoto."))
        }
        val manualAttentionRequired = body.manualAttentionRequired
            ?: throw DataControlFailure(DataControlResult.Failure("El servidor devolvió un resultado incompleto."))
        val requestedAt = request.requestedAt.cleanText(80)
            ?: throw DataControlFailure(DataControlResult.Failure("El servidor no confirmó la fecha de solicitud."))
        val deactivatedAt = request.deactivatedAt.cleanText(80)
            ?: throw DataControlFailure(DataControlResult.Failure("El servidor no confirmó la fecha de desactivación."))
        val retentionNotice = body.retentionNotice.cleanText(600)
            ?: throw DataControlFailure(DataControlResult.Failure("El servidor no informó los límites de conservación."))
        val receipt = AccountDeletionReceipt(
            requestId = requestId,
            status = request.status.cleanText(60)
                ?: throw DataControlFailure(DataControlResult.Failure("El servidor no confirmó el estado de la solicitud.")),
            requestedAt = requestedAt,
            deactivatedAt = deactivatedAt,
            manualAttentionRequired = manualAttentionRequired,
            retentionNotice = retentionNotice,
            cleanupStatus = cleanupStatus,
        )

        if (sessionManager.getUid() != uid || sessionManager.getRole() != role) {
            throw DataControlFailure(
                DataControlResult.Failure(
                    "La cuenta activa cambió durante la solicitud. La cuenta anterior puede haber quedado desactivada; la sesión actual se conservó.",
                    409,
                ),
            )
        }

        withContext(NonCancellable) {
            if (uid.isNullOrBlank() || role.isNullOrBlank()) {
                sessionManager.clear()
                throw DataControlFailure(
                    DataControlResult.Failure(
                        "La cuenta fue bloqueada, pero faltan datos locales para completar la limpieza. Reinicia la aplicación.",
                    ),
                )
            }
            markLocalCleanupPending(uid, role)
            runCatching { suspendFcmForCurrentAccount() }
            try {
                clearLocalUserData(uid, role)
                clearLocalCleanupPending()
            } finally {
                sessionManager.clear()
            }
        }
        receipt
    }

    private suspend fun <T> authorizedCall(call: suspend () -> Response<T>): Response<T> {
        val rejectedToken = sessionManager.getIdToken()
        val first = call()
        if (first.code() != 401) return first

        return when (val refresh = sessionRefreshCoordinator.refreshAfterUnauthorized(rejectedToken)) {
            SessionRefreshResult.Success -> call()
            is SessionRefreshResult.Failure -> throw DataControlFailure(
                DataControlResult.Failure(refresh.message, refresh.statusCode),
            )
        }
    }

    private suspend fun <T> safeRequest(block: suspend () -> T): DataControlResult<T> {
        return try {
            DataControlResult.Success(block())
        } catch (error: CancellationException) {
            throw error
        } catch (failure: DataControlFailure) {
            failure.result
        } catch (error: Exception) {
            DataControlResult.Failure(
                ErrorMapper.fromThrowable(
                    error,
                    timeoutMessage = "La operación de datos tardó demasiado.",
                    connectionMessage = "Esta operación requiere conexión. Verifica tu red.",
                ).message,
            )
        }
    }

    private fun Response<*>.toFailure(): DataControlResult.Failure {
        val parsed = ApiErrorParser.parse(errorBody(), code())
        val message = when (code()) {
            401 -> "La sesión expiró. Inicia sesión nuevamente antes de continuar."
            403 -> "La desactivación requiere una autenticación reciente o atención manual."
            409 -> "La cuenta ya está desactivada o tiene una solicitud incompatible en curso."
            422 -> parsed.message?.takeIf(String::isNotBlank)
                ?: "Revisa la confirmación antes de desactivar la cuenta."
            423 -> "La cuenta ya tiene una eliminación en curso."
            else -> ErrorMapper.safeBackendMessage(code(), parsed.message)
                ?: ErrorMapper.fromHttpCode(code()).message
        }
        return DataControlResult.Failure(message, code(), parsed.fieldErrors)
    }

    private fun DataControlSummaryResponse.toSummary(): DataControlSummary {
        val safeRole = role?.takeIf { it in VALID_ROLES } ?: sessionManager.getRole().orEmpty()
        val profileRectification = rectification?.profile
        val deactivation = accountDeactivation ?: accountDeletion
        val permanentDeletion = actions?.requestPermanentDeletion
        return DataControlSummary(
            role = safeRole,
            deletionStatus = deletionStatus.cleanText(60) ?: "none",
            categories = categories.orEmpty().mapNotNull { category ->
                val code = category.code.cleanText(80) ?: return@mapNotNull null
                DataControlCategory(
                    code = code,
                    label = category.label.cleanText(120) ?: code,
                    location = category.location.cleanText(120) ?: "No disponible",
                    deletionMode = category.deletionMode.cleanText(120) ?: "manual_review",
                )
            }.take(MAX_CATEGORIES),
            editableFields = (profileRectification?.editableFields ?: editableFields).orEmpty()
                .mapNotNull { it.cleanText(80) }
                .distinct()
                .take(MAX_EDITABLE_FIELDS),
            rectificationRequiresConnection = profileRectification?.offlineQueueAllowed != true,
            automatedAccountDeletion = deactivation?.automated == true,
            recentAuthMaxAgeSeconds = deactivation?.recentAuthMaxAgeSeconds
                ?.coerceIn(60, 3_600)
                ?: 300,
            confirmationPhrase = deactivation?.confirmationPhrase.cleanText(80)
                ?: DEFAULT_CONFIRMATION,
            retentionNotice = retentionNotice.cleanText(600)
                ?: "La conservación aplicable requiere confirmación por soporte.",
            manualRequestNotice = manualRequestNotice.cleanText(400)
                ?: "Algunas solicitudes requieren atención manual al 33 2535 6657.",
            permanentDeletionEmail = permanentDeletion?.address
                ?.validEmailOrNull()
                ?: DEFAULT_PERMANENT_DELETION_EMAIL,
            permanentDeletionSubject = permanentDeletion?.subject.cleanText(120)
                ?: DEFAULT_PERMANENT_DELETION_SUBJECT,
        )
    }

    private fun String?.safeCredential(maxLength: Int): String? = this
        ?.takeIf { it.isNotBlank() && it.length <= maxLength }
        ?.takeIf { value -> value.none(Char::isWhitespace) && value.none(Char::isISOControl) }

    private fun String?.cleanText(maxLength: Int): String? = this
        ?.filterNot(Char::isISOControl)
        ?.trim()
        ?.take(maxLength)
        ?.takeIf(String::isNotBlank)

    private fun String?.validEmailOrNull(): String? = this
        ?.trim()
        ?.takeIf { it.length in 3..254 }
        ?.takeIf { it.count { char -> char == '@' } == 1 }
        ?.takeIf { it.none(Char::isWhitespace) && it.none(Char::isISOControl) }

    private class DataControlFailure(
        val result: DataControlResult.Failure,
    ) : IOException(result.message)

    private companion object {
        const val MAX_TOKEN_LENGTH = 16_384
        const val MAX_UID_LENGTH = 128
        const val MAX_PASSWORD_LENGTH = 4_096
        const val MAX_REQUEST_ID_LENGTH = 180
        const val MAX_CATEGORIES = 20
        const val MAX_EDITABLE_FIELDS = 40
        const val DEFAULT_CONFIRMATION = "DESACTIVAR MI CUENTA"
        const val DEFAULT_PERMANENT_DELETION_EMAIL = "rehabianex@gmail.com"
        const val DEFAULT_PERMANENT_DELETION_SUBJECT = "Solicitud de eliminación de cuenta RehabiAnex"
        val VALID_ROLES = setOf("patient", "supervisor", "admin")
    }
}
