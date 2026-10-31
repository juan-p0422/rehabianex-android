package com.example.rehabianex.data.repository

import com.example.rehabianex.core.session.SessionManager
import com.example.rehabianex.data.local.PrivacyNoticeReadStore
import com.example.rehabianex.data.model.MeResponse
import com.example.rehabianex.data.model.PRIVACY_NOTICE_VERSION
import com.example.rehabianex.data.model.PrivacyNoticeDto
import com.example.rehabianex.data.model.PrivacyNoticeReadRequest
import com.example.rehabianex.data.remote.ApiErrorParser
import com.example.rehabianex.data.remote.ApiService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import retrofit2.Response

data class PrivacyNoticeLoadResult(
    val notice: PrivacyNoticeDto,
    val currentReadConfirmedByServer: Boolean,
    val serverStatusChecked: Boolean,
    val warning: String? = null,
    val currentReadConfirmedByLocalEvidence: Boolean = false,
) {
    val accessGranted: Boolean
        get() = currentReadConfirmedByServer || currentReadConfirmedByLocalEvidence
}

sealed interface PrivacyNoticeConfirmResult {
    data object Success : PrivacyNoticeConfirmResult
    data class Failure(val message: String) : PrivacyNoticeConfirmResult
}

class PrivacyNoticeRepository(
    private val apiService: ApiService,
    private val sessionManager: SessionManager,
    private val readStore: PrivacyNoticeReadStore,
    private val fallbackContent: () -> String,
) {
    suspend fun load(
        requireServerStatus: Boolean,
        authenticatedStatusChecked: Boolean = false,
        authenticatedConfirmedVersion: String? = null,
    ): PrivacyNoticeLoadResult = coroutineScope {
        val uid = sessionManager.getUid()?.takeIf(String::isNotBlank)
        if (requireServerStatus &&
            authenticatedStatusChecked &&
            authenticatedConfirmedVersion == PRIVACY_NOTICE_VERSION &&
            uid != null
        ) {
            readStore.saveConfirmedVersion(uid, PRIVACY_NOTICE_VERSION)
            return@coroutineScope confirmedResult(fallbackNotice())
        }

        val preloadedProfile = requireServerStatus &&
            !authenticatedStatusChecked &&
            uid != null &&
            readStore.confirmedVersion(uid) == PRIVACY_NOTICE_VERSION
        val preloadedProfileResponse = if (preloadedProfile) loadProfileResponse() else null
        if (preloadedProfileResponse?.isSuccessful == true &&
            sessionManager.getUid()?.takeIf(String::isNotBlank) == uid
        ) {
            val read = preloadedProfileResponse.body()?.profile?.privacyNoticeRead
            if (read?.status == "confirmed" &&
                read.isCurrent == true &&
                read.privacyNoticeVersion == PRIVACY_NOTICE_VERSION
            ) {
                readStore.saveConfirmedVersion(requireNotNull(uid), PRIVACY_NOTICE_VERSION)
                return@coroutineScope confirmedResult(fallbackNotice())
            }
        }

        val remoteNoticeRequest = async { loadRemoteNotice() }
        val profileRequest = if (
            requireServerStatus &&
            !authenticatedStatusChecked &&
            !preloadedProfile
        ) {
            async { loadProfileResponse() }
        } else {
            null
        }
        val remoteNotice = remoteNoticeRequest.await()
        val notice = remoteNotice ?: fallbackNotice()
        if (uid != null && remoteNotice != null) {
            readStore.confirmedVersion(uid)
                ?.takeIf { it != notice.version }
                ?.let { readStore.clearOwner(uid) }
        }

        if (!requireServerStatus) {
            return@coroutineScope PrivacyNoticeLoadResult(
                notice = notice,
                currentReadConfirmedByServer = false,
                serverStatusChecked = remoteNotice != null,
                warning = if (remoteNotice == null) OFFLINE_NOTICE_WARNING else null,
            )
        }

        if (authenticatedStatusChecked) {
            val confirmed = uid != null &&
                authenticatedConfirmedVersion != null &&
                authenticatedConfirmedVersion == notice.version
            if (uid != null) {
                if (confirmed) {
                    readStore.saveConfirmedVersion(uid, requireNotNull(notice.version))
                } else {
                    readStore.clearOwner(uid)
                }
            }
            return@coroutineScope PrivacyNoticeLoadResult(
                notice = notice,
                currentReadConfirmedByServer = confirmed,
                serverStatusChecked = true,
                warning = if (remoteNotice == null) OFFLINE_NOTICE_WARNING else null,
            )
        }

        val response = if (preloadedProfile) {
            preloadedProfileResponse
        } else {
            profileRequest?.await()
        } ?: return@coroutineScope transientFailureResult(notice, uid)
        return@coroutineScope try {
            if (sessionManager.getUid()?.takeIf(String::isNotBlank) != uid) {
                return@coroutineScope PrivacyNoticeLoadResult(notice, false, true, STATUS_ERROR)
            }
            if (response.isSuccessful) {
                val read = response.body()?.profile?.privacyNoticeRead
                val confirmed = read?.status == "confirmed" &&
                    read.isCurrent == true &&
                    read.privacyNoticeVersion == notice.version
                if (uid != null) {
                    if (confirmed) {
                        readStore.saveConfirmedVersion(uid, requireNotNull(notice.version))
                    } else {
                        readStore.clearOwner(uid)
                    }
                }
                PrivacyNoticeLoadResult(
                    notice = notice,
                    currentReadConfirmedByServer = confirmed,
                    serverStatusChecked = true,
                    warning = if (remoteNotice == null) OFFLINE_NOTICE_WARNING else null,
                )
            } else if (isTransientStatus(response.code())) {
                transientFailureResult(notice, uid)
            } else {
                uid?.let(readStore::clearOwner)
                PrivacyNoticeLoadResult(notice, false, true, STATUS_ERROR)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            transientFailureResult(notice, uid)
        }
    }

    private suspend fun loadRemoteNotice(): PrivacyNoticeDto? = try {
        apiService.privacyNotice().takeIf { it.isSuccessful }?.body()?.notice
            ?.takeIf { !it.version.isNullOrBlank() && !it.content.isNullOrBlank() }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    private suspend fun loadProfileResponse(): Response<MeResponse>? = try {
        apiService.me()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    private fun confirmedResult(notice: PrivacyNoticeDto) = PrivacyNoticeLoadResult(
        notice = notice,
        currentReadConfirmedByServer = true,
        serverStatusChecked = true,
    )

    suspend fun confirmRead(version: String): PrivacyNoticeConfirmResult {
        return try {
            val uid = sessionManager.getUid()?.takeIf(String::isNotBlank)
            val response = apiService.confirmPrivacyNoticeRead(
                PrivacyNoticeReadRequest(version = version, confirmedRead = true),
            )
            if (uid == null || sessionManager.getUid()?.takeIf(String::isNotBlank) != uid) {
                return PrivacyNoticeConfirmResult.Failure(
                    "La sesión cambió durante la confirmación. Vuelve a intentarlo.",
                )
            }
            val confirmation = response.body()?.confirmation
            if (response.isSuccessful &&
                confirmation?.status == "confirmed" &&
                confirmation.privacyNoticeVersion == version
            ) {
                readStore.saveConfirmedVersion(uid, version)
                PrivacyNoticeConfirmResult.Success
            } else {
                if (!isTransientStatus(response.code())) {
                    readStore.clearOwner(uid)
                }
                val parsed = ApiErrorParser.parse(response.errorBody(), response.code())
                PrivacyNoticeConfirmResult.Failure(
                    parsed.message ?: "No fue posible guardar la confirmación. Intenta nuevamente.",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            PrivacyNoticeConfirmResult.Failure(
                "Sin conexión: no se guardó la confirmación. Conservamos esta pantalla para que reintentes.",
            )
        }
    }

    private fun transientFailureResult(
        notice: PrivacyNoticeDto,
        uid: String?,
    ): PrivacyNoticeLoadResult {
        val version = notice.version?.takeIf(String::isNotBlank)
        val confirmedLocally = uid != null &&
            sessionManager.getUid()?.takeIf(String::isNotBlank) == uid &&
            version != null &&
            readStore.confirmedVersion(uid) == version
        return PrivacyNoticeLoadResult(
            notice = notice,
            currentReadConfirmedByServer = false,
            serverStatusChecked = false,
            warning = if (confirmedLocally) OFFLINE_ACCESS_WARNING else STATUS_ERROR,
            currentReadConfirmedByLocalEvidence = confirmedLocally,
        )
    }

    private fun fallbackNotice(): PrivacyNoticeDto {
        val content = fallbackContent()
        return PrivacyNoticeDto(
            title = "Aviso de Privacidad de RehabiAnex",
            version = PRIVACY_NOTICE_VERSION,
            publishedOn = "2026-10-30",
            content = content,
            responsibleStatus = "pending_confirmation",
            contactStatus = "pending_confirmation",
            sensitiveDataConsentSeparate = true,
        )
    }

    private fun isTransientStatus(statusCode: Int): Boolean =
        statusCode == 408 || statusCode == 425 || statusCode == 429 || statusCode in 500..599

    private companion object {
        const val OFFLINE_NOTICE_WARNING =
            "Se muestra la copia incluida en la aplicación. Conéctate para validar la versión vigente."
        const val STATUS_ERROR =
            "No fue posible validar tu confirmación con el servidor. Reintenta o cierra sesión."
        const val OFFLINE_ACCESS_WARNING =
            "Sin conexión. Se usó la confirmación guardada para esta versión del aviso."
    }
}
