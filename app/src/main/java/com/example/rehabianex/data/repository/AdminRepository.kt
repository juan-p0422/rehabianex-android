package com.example.rehabianex.data.repository

import com.example.rehabianex.core.error.ErrorMapper
import com.example.rehabianex.core.session.SessionInvalidationBus
import com.example.rehabianex.core.session.SessionManager
import com.example.rehabianex.core.session.UserSession
import com.example.rehabianex.data.model.AdminAuthorizeSupervisorRequest
import com.example.rehabianex.data.model.AdminSupervisorDto
import com.example.rehabianex.data.model.AdminSupervisorReasonRequest
import com.example.rehabianex.data.model.RefreshRequest
import com.example.rehabianex.data.local.NetworkStatusProvider
import com.example.rehabianex.data.local.db.AdminDashboardSummaryDao
import com.example.rehabianex.data.local.db.AdminDashboardSummaryEntity
import com.example.rehabianex.data.local.db.AdminProfileSnapshotDao
import com.example.rehabianex.data.local.db.AdminProfileSnapshotEntity
import com.example.rehabianex.data.remote.ApiErrorParser
import com.example.rehabianex.data.remote.ApiService
import java.io.IOException
import kotlinx.coroutines.CancellationException
import retrofit2.Response

sealed interface AdminDataResult<out T> {
    data class Success<T>(val data: T) : AdminDataResult<T>

    data class Failure(
        val message: String,
        val fieldErrors: Map<String, String> = emptyMap(),
        val statusCode: Int? = null,
    ) : AdminDataResult<Nothing>
}

data class AdminDashboardCacheData(
    val displayName: String,
    val email: String?,
    val status: String,
    val pendingSupervisors: Int,
    val activeSupervisors: Int,
    val suspendedSupervisors: Int,
    val lastSyncedAt: Long,
)

class AdminRepository(
    private val apiService: ApiService,
    private val sessionManager: SessionManager? = null,
    private val profileSnapshotDao: AdminProfileSnapshotDao? = null,
    private val dashboardSummaryDao: AdminDashboardSummaryDao? = null,
    private val networkStatusProvider: NetworkStatusProvider? = null,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun cachedDashboard(): AdminDashboardCacheData? {
        val uid = sessionManager?.getUid()?.takeIf(String::isNotBlank) ?: return null
        val profile = profileSnapshotDao?.get(uid) ?: return null
        val summary = dashboardSummaryDao?.get(uid)
        return AdminDashboardCacheData(
            displayName = profile.displayName,
            email = profile.email,
            status = profile.status,
            pendingSupervisors = summary?.pendingSupervisors ?: 0,
            activeSupervisors = summary?.activeSupervisors ?: 0,
            suspendedSupervisors = summary?.suspendedSupervisors ?: 0,
            lastSyncedAt = maxOf(profile.lastSyncedAt, summary?.lastSyncedAt ?: 0L),
        )
    }

    suspend fun supervisors(
        status: String? = null,
        authorized: Boolean? = null,
        verified: Boolean? = null,
        limit: Int = DEFAULT_LIMIT,
    ): AdminDataResult<List<AdminSupervisorDto>> {
        if (limit !in 1..MAX_LIMIT) {
            return validationFailure(
                field = "limit",
                message = "El límite debe estar entre 1 y 100.",
            )
        }

        val result = execute(
            request = {
                apiService.adminSupervisors(
                    status = status,
                    authorized = authorized,
                    verified = verified,
                    limit = limit,
                )
            },
            transform = { response -> response.data.orEmpty() },
        )
        if (result is AdminDataResult.Success && status != null) {
            cacheAdminSummary(status, result.data.size)
        }
        return result
    }

    suspend fun supervisor(uid: String): AdminDataResult<AdminSupervisorDto> {
        val cleanUid = uid.validUid() ?: return invalidUidFailure()
        return execute(
            request = { apiService.adminSupervisorByUid(cleanUid) },
            transform = { response -> response.data },
        )
    }

    suspend fun authorize(
        uid: String,
        notes: String? = null,
    ): AdminDataResult<AdminSupervisorDto> {
        val cleanUid = uid.validUid() ?: return invalidUidFailure()
        offlineActionFailure()?.let { return it }
        val cleanNotes = notes.cleanOptionalText()
        return execute(
            request = {
                apiService.authorizeAdminSupervisor(
                    supervisorUid = cleanUid,
                    request = AdminAuthorizeSupervisorRequest(notes = cleanNotes),
                )
            },
            transform = { response -> response.data },
        )
    }

    suspend fun reject(
        uid: String,
        reason: String,
    ): AdminDataResult<AdminSupervisorDto> {
        return reasonAction(uid, reason, apiService::rejectAdminSupervisor)
    }

    suspend fun suspend(
        uid: String,
        reason: String,
    ): AdminDataResult<AdminSupervisorDto> {
        return reasonAction(uid, reason, apiService::suspendAdminSupervisor)
    }

    suspend fun reactivate(uid: String): AdminDataResult<AdminSupervisorDto> {
        val cleanUid = uid.validUid() ?: return invalidUidFailure()
        offlineActionFailure()?.let { return it }
        return execute(
            request = { apiService.reactivateAdminSupervisor(cleanUid) },
            transform = { response -> response.data },
        )
    }

    private suspend fun reasonAction(
        uid: String,
        reason: String,
        request: suspend (String, AdminSupervisorReasonRequest) ->
            Response<com.example.rehabianex.data.model.AdminSupervisorResponse>,
    ): AdminDataResult<AdminSupervisorDto> {
        val cleanUid = uid.validUid() ?: return invalidUidFailure()
        offlineActionFailure()?.let { return it }
        val cleanReason = reason.cleanRequiredText()
            ?: return validationFailure(
                field = "reason",
                message = "Indica el motivo de esta acción.",
            )

        return execute(
            request = {
                request(
                    cleanUid,
                    AdminSupervisorReasonRequest(reason = cleanReason),
                )
            },
            transform = { response -> response.data },
        )
    }

    private suspend fun <T, R> execute(
        request: suspend () -> Response<T>,
        transform: (T) -> R?,
    ): AdminDataResult<R> {
        val ownerUid = sessionManager?.getUid()
        return try {
            val response = authorizedCall(request)
            if (!response.isSuccessful) {
                response.toFailure().also { clearSnapshotsOnDenied(it.statusCode, ownerUid) }
            } else {
                val body = response.body()
                val data = body?.let(transform)
                if (data == null) {
                    AdminDataResult.Failure(
                        message = "El servidor devolvió una respuesta incompleta.",
                        statusCode = response.code(),
                    )
                } else {
                    AdminDataResult.Success(data)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: SessionRefreshException) {
            clearSnapshotsOnDenied(error.statusCode, ownerUid)
            AdminDataResult.Failure(
                message = ErrorMapper.fromHttpCode(error.statusCode).message,
                statusCode = error.statusCode,
            )
        } catch (error: Exception) {
            AdminDataResult.Failure(ErrorMapper.fromThrowable(error).message)
        }
    }

    private fun Response<*>.toFailure(): AdminDataResult.Failure {
        val parsedError = ApiErrorParser.parse(errorBody(), code())
        val defaultMessage = when (code()) {
            403 -> "Tu cuenta no tiene permisos administrativos."
            404 -> "No se encontró el supervisor solicitado."
            409 -> "El estado actual del supervisor no permite esta operación."
            422 -> "Revisa los datos enviados."
            else -> ErrorMapper.fromHttpCode(code()).message
        }

        return AdminDataResult.Failure(
            message = ErrorMapper.safeBackendMessage(code(), parsedError.message)
                ?: defaultMessage,
            fieldErrors = parsedError.fieldErrors,
            statusCode = code(),
        )
    }

    private fun validationFailure(
        field: String,
        message: String,
    ) = AdminDataResult.Failure(
        message = message,
        fieldErrors = mapOf(field to message),
        statusCode = 422,
    )

    private fun invalidUidFailure() = validationFailure(
        field = "uid",
        message = "El supervisor seleccionado no es válido.",
    )

    private fun offlineActionFailure(): AdminDataResult.Failure? {
        if (networkStatusProvider?.isOnline() != false) return null
        return AdminDataResult.Failure(
            message = "Esta acción requiere conexión. Revisa tu red e inténtalo nuevamente.",
        )
    }

    private suspend fun cacheAdminSummary(status: String, count: Int) {
        val manager = sessionManager ?: return
        val uid = manager.getUid()?.takeIf(String::isNotBlank) ?: return
        val syncedAt = now()
        profileSnapshotDao?.upsert(
            AdminProfileSnapshotEntity(
                ownerUid = uid,
                displayName = manager.getDisplayName()?.takeIf(String::isNotBlank) ?: "Administrador",
                email = manager.getEmail()?.takeIf(String::isNotBlank),
                status = "active",
                lastSyncedAt = syncedAt,
            ),
        )
        val current = dashboardSummaryDao?.get(uid)
        dashboardSummaryDao?.upsert(
            AdminDashboardSummaryEntity(
                ownerUid = uid,
                pendingSupervisors = if (status == "pending_review") count else current?.pendingSupervisors ?: 0,
                activeSupervisors = if (status == "active") count else current?.activeSupervisors ?: 0,
                suspendedSupervisors = if (status == "suspended") count else current?.suspendedSupervisors ?: 0,
                lastSyncedAt = syncedAt,
            ),
        )
    }

    private suspend fun clearSnapshotsOnDenied(statusCode: Int?, ownerUid: String?) {
        if (statusCode !in setOf(401, 403)) return
        val uid = ownerUid?.takeIf(String::isNotBlank) ?: return
        profileSnapshotDao?.clearOwner(uid)
        dashboardSummaryDao?.clearOwner(uid)
    }

    private suspend fun <T> authorizedCall(
        request: suspend () -> Response<T>,
    ): Response<T> {
        val manager = sessionManager ?: return request()
        val firstResponse = request()
        if (firstResponse.code() != 401) return firstResponse

        try {
            refreshSession(manager)
        } catch (error: CancellationException) {
            throw error
        } catch (error: SessionRefreshException) {
            if (error.invalidatesSession) {
                invalidateSession(manager)
            }
            throw error
        }

        return request().also { response ->
            if (response.code() == 401) {
                invalidateSession(manager)
            }
        }
    }

    private suspend fun refreshSession(manager: SessionManager) {
        val refreshToken = manager.getRefreshToken()?.takeIf { it.isNotBlank() }
            ?: throw SessionRefreshException(statusCode = 401, invalidatesSession = true)
        val uid = manager.getUid()?.takeIf { it.isNotBlank() }
            ?: throw SessionRefreshException(statusCode = 401, invalidatesSession = true)
        val role = manager.getRole()?.takeIf { it == "admin" }
            ?: throw SessionRefreshException(statusCode = 401, invalidatesSession = true)
        val response = apiService.refresh(RefreshRequest(refreshToken))
        if (!response.isSuccessful) {
            throw SessionRefreshException(
                statusCode = response.code(),
                invalidatesSession = response.code() == 401,
            )
        }
        val tokens = response.body()?.resolvedTokens()
        val idToken = tokens?.idToken?.takeIf { it.isNotBlank() }
            ?: throw SessionRefreshException()

        manager.saveSession(
            UserSession(
                uid = uid,
                email = manager.getEmail(),
                role = role,
                token = idToken,
                refreshToken = tokens.refreshToken?.takeIf { it.isNotBlank() } ?: refreshToken,
                displayName = manager.getDisplayName(),
            ),
        )
    }

    private fun invalidateSession(manager: SessionManager) {
        manager.clear()
        SessionInvalidationBus.notifySessionExpired()
    }

    private companion object {
        const val DEFAULT_LIMIT = 50
        const val MAX_LIMIT = 100
        const val MAX_UID_LENGTH = 128
        const val MAX_ACTION_TEXT_LENGTH = 1_000
    }

    private class SessionRefreshException(
        val statusCode: Int? = null,
        val invalidatesSession: Boolean = false,
    ) : IOException()

    private fun String.validUid(): String? = trim()
        .takeIf {
            it.isNotEmpty() &&
                it.length <= MAX_UID_LENGTH &&
                it.none(Char::isISOControl)
        }

    private fun String?.cleanOptionalText(): String? = this
        ?.filterNot(Char::isISOControl)
        ?.trim()
        ?.take(MAX_ACTION_TEXT_LENGTH)
        ?.takeIf(String::isNotEmpty)

    private fun String.cleanRequiredText(): String? = cleanOptionalText()
}
