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
import com.google.gson.JsonParser
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
    private val sessionRefreshCoordinator: SessionRefreshCoordinator =
        SessionRefreshCoordinator(apiService, sessionManager),
) {
    suspend fun loadSummary(): DataControlResult<DataControlSummary> = safeRequest {
        val response = authorizedCall(apiService::dataControlSummary)
        if (!response.isSuccessful) throw DataControlFailure(response.toFailure())
        response.body()?.toSummary()
            ?: throw DataControlFailure(DataControlResult.Failure("El servidor no devolvió el inventario de datos."))
    }

    suspend fun exportOwnData(): DataControlResult<String> = safeRequest {
        val response = authorizedCall(apiService::exportOwnData)
        if (!response.isSuccessful) throw DataControlFailure(response.toFailure())
        val body = response.body()
            ?: throw DataControlFailure(DataControlResult.Failure("La exportación llegó vacía."))
        val contentLength = body.contentLength()
        if (contentLength > MAX_EXPORT_BYTES) {
            body.close()
            throw DataControlFailure(DataControlResult.Failure("La exportación supera el tamaño permitido en el dispositivo."))
        }
        val json = body.use {
            val source = it.source()
            source.request(MAX_EXPORT_BYTES.toLong() + 1L)
            if (source.buffer.size > MAX_EXPORT_BYTES) {
                throw DataControlFailure(DataControlResult.Failure("La exportación supera el tamaño permitido en el dispositivo."))
            }
            source.buffer.readUtf8()
        }
        if (!isStructuredExport(json)) {
            throw DataControlFailure(DataControlResult.Failure("El servidor devolvió una exportación no válida."))
        }
        json
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
        val response = authorizedCall {
            apiService.requestAccountDeletion(
                AccountDeletionRequest(
                    confirmation = confirmation,
                    acknowledgeRetentionLimits = acknowledgeRetentionLimits,
                ),
            )
        }
        if (!response.isSuccessful) throw DataControlFailure(response.toFailure())
        val body = response.body()
            ?: throw DataControlFailure(DataControlResult.Failure("El servidor no confirmó la solicitud."))
        val request = body.request
            ?: throw DataControlFailure(DataControlResult.Failure("El servidor no confirmó la solicitud."))
        val requestId = request.requestId.cleanText(MAX_REQUEST_ID_LENGTH)
            ?: throw DataControlFailure(DataControlResult.Failure("El servidor no confirmó la solicitud."))
        if (body.syncBlocked != true || body.localCleanupRequired != true) {
            throw DataControlFailure(DataControlResult.Failure("El servidor no confirmó el bloqueo de sincronización."))
        }
        val receipt = AccountDeletionReceipt(
            requestId = requestId,
            status = request.status.cleanText(60) ?: "pending_manual_review",
            requestedAt = request.requestedAt.cleanText(80),
            manualAttentionRequired = body.manualAttentionRequired == true,
            retentionNotice = body.retentionNotice.cleanText(600)
                ?: "La eliminación definitiva requiere revisión operativa.",
        )

        withContext(NonCancellable) {
            runCatching { clearLocalUserData(uid, role) }
            sessionManager.clear()
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
            401 -> "La sesión expiró o la eliminación requiere una autenticación reciente."
            403 -> "Esta acción requiere atención manual para tu tipo de cuenta."
            423 -> "La cuenta ya tiene una eliminación en curso."
            else -> ErrorMapper.safeBackendMessage(code(), parsed.message)
                ?: ErrorMapper.fromHttpCode(code()).message
        }
        return DataControlResult.Failure(message, code(), parsed.fieldErrors)
    }

    private fun DataControlSummaryResponse.toSummary(): DataControlSummary {
        val safeRole = role?.takeIf { it in VALID_ROLES } ?: sessionManager.getRole().orEmpty()
        val profileRectification = rectification?.profile
        return DataControlSummary(
            role = safeRole,
            deletionStatus = deletionStatus.cleanText(60) ?: "none",
            categories = categories.orEmpty().mapNotNull { category ->
                val code = category.code.cleanText(80) ?: return@mapNotNull null
                DataControlCategory(
                    code = code,
                    label = category.label.cleanText(120) ?: code,
                    location = category.location.cleanText(120) ?: "No disponible",
                    exported = category.exported == true,
                    deletionMode = category.deletionMode.cleanText(120) ?: "manual_review",
                )
            }.take(MAX_CATEGORIES),
            editableFields = (profileRectification?.editableFields ?: editableFields).orEmpty()
                .mapNotNull { it.cleanText(80) }
                .distinct()
                .take(MAX_EDITABLE_FIELDS),
            rectificationRequiresConnection = profileRectification?.offlineQueueAllowed != true,
            automatedAccountDeletion = accountDeletion?.automated == true,
            recentAuthMaxAgeSeconds = accountDeletion?.recentAuthMaxAgeSeconds
                ?.coerceIn(60, 3_600)
                ?: 300,
            confirmationPhrase = accountDeletion?.confirmationPhrase.cleanText(80)
                ?: DEFAULT_CONFIRMATION,
            retentionNotice = retentionNotice.cleanText(600)
                ?: "La conservación aplicable requiere confirmación por soporte.",
            manualRequestNotice = manualRequestNotice.cleanText(400)
                ?: "Algunas solicitudes requieren atención manual.",
        )
    }

    private fun isStructuredExport(json: String): Boolean = runCatching {
        val root = JsonParser.parseString(json).asJsonObject
        root.get("scope")?.asString == "authenticated_account_holder" &&
            root.has("identity") && root.has("profile") && root.has("records") &&
            root.get("sensitivity_notice")?.asString?.isNotBlank() == true
    }.getOrDefault(false)

    private fun String?.safeCredential(maxLength: Int): String? = this
        ?.takeIf { it.isNotBlank() && it.length <= maxLength }
        ?.takeIf { value -> value.none(Char::isWhitespace) && value.none(Char::isISOControl) }

    private fun String?.cleanText(maxLength: Int): String? = this
        ?.filterNot(Char::isISOControl)
        ?.trim()
        ?.take(maxLength)
        ?.takeIf(String::isNotBlank)

    private class DataControlFailure(
        val result: DataControlResult.Failure,
    ) : IOException(result.message)

    private companion object {
        const val MAX_EXPORT_BYTES = 5 * 1024 * 1024
        const val MAX_TOKEN_LENGTH = 16_384
        const val MAX_UID_LENGTH = 128
        const val MAX_PASSWORD_LENGTH = 4_096
        const val MAX_REQUEST_ID_LENGTH = 180
        const val MAX_CATEGORIES = 20
        const val MAX_EDITABLE_FIELDS = 40
        const val DEFAULT_CONFIRMATION = "ELIMINAR MI CUENTA"
        val VALID_ROLES = setOf("patient", "supervisor", "admin")
    }
}
