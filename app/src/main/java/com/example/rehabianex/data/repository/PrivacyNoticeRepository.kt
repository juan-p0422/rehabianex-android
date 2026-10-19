package com.example.rehabianex.data.repository

import com.example.rehabianex.data.model.PRIVACY_NOTICE_VERSION
import com.example.rehabianex.data.model.PrivacyNoticeDto
import com.example.rehabianex.data.model.PrivacyNoticeReadRequest
import com.example.rehabianex.data.remote.ApiErrorParser
import com.example.rehabianex.data.remote.ApiService
import kotlinx.coroutines.CancellationException

data class PrivacyNoticeLoadResult(
    val notice: PrivacyNoticeDto,
    val currentReadConfirmedByServer: Boolean,
    val serverStatusChecked: Boolean,
    val warning: String? = null,
)

sealed interface PrivacyNoticeConfirmResult {
    data object Success : PrivacyNoticeConfirmResult
    data class Failure(val message: String) : PrivacyNoticeConfirmResult
}

class PrivacyNoticeRepository(
    private val apiService: ApiService,
    private val fallbackContent: () -> String,
) {
    suspend fun load(requireServerStatus: Boolean): PrivacyNoticeLoadResult {
        val remoteNotice = try {
            apiService.privacyNotice().takeIf { it.isSuccessful }?.body()?.notice
                ?.takeIf { !it.version.isNullOrBlank() && !it.content.isNullOrBlank() }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
        val notice = remoteNotice ?: fallbackNotice()

        if (!requireServerStatus) {
            return PrivacyNoticeLoadResult(
                notice = notice,
                currentReadConfirmedByServer = false,
                serverStatusChecked = remoteNotice != null,
                warning = if (remoteNotice == null) OFFLINE_NOTICE_WARNING else null,
            )
        }

        return try {
            val response = apiService.me()
            if (!response.isSuccessful) {
                PrivacyNoticeLoadResult(notice, false, false, STATUS_ERROR)
            } else {
                val read = response.body()?.profile?.privacyNoticeRead
                PrivacyNoticeLoadResult(
                    notice = notice,
                    currentReadConfirmedByServer = read?.status == "confirmed" &&
                        read.isCurrent == true &&
                        read.privacyNoticeVersion == notice.version,
                    serverStatusChecked = true,
                    warning = if (remoteNotice == null) OFFLINE_NOTICE_WARNING else null,
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            PrivacyNoticeLoadResult(notice, false, false, STATUS_ERROR)
        }
    }

    suspend fun confirmRead(version: String): PrivacyNoticeConfirmResult = try {
        val response = apiService.confirmPrivacyNoticeRead(
            PrivacyNoticeReadRequest(version = version, confirmedRead = true),
        )
        val confirmation = response.body()?.confirmation
        if (response.isSuccessful &&
            confirmation?.status == "confirmed" &&
            confirmation.privacyNoticeVersion == version
        ) {
            PrivacyNoticeConfirmResult.Success
        } else {
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

    private fun fallbackNotice(): PrivacyNoticeDto {
        val content = fallbackContent()
        return PrivacyNoticeDto(
            title = "Aviso de Privacidad de RehabiAnex",
            version = PRIVACY_NOTICE_VERSION,
            publishedOn = "2026-10-18",
            content = content,
            responsibleStatus = "pending_confirmation",
            contactStatus = "pending_confirmation",
            sensitiveDataConsentSeparate = true,
        )
    }

    private companion object {
        const val OFFLINE_NOTICE_WARNING =
            "Se muestra la copia incluida en la aplicación. Conéctate para validar la versión vigente."
        const val STATUS_ERROR =
            "No fue posible validar tu confirmación con el servidor. Reintenta o cierra sesión."
    }
}
