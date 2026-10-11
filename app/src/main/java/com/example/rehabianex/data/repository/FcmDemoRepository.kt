package com.example.rehabianex.data.repository

import com.example.rehabianex.core.error.ErrorMapper
import com.example.rehabianex.data.model.FcmDemoRequest
import com.example.rehabianex.data.remote.ApiErrorParser
import com.example.rehabianex.data.remote.ApiService
import kotlinx.coroutines.CancellationException

sealed interface FcmDemoResult {
    data class Success(val type: String) : FcmDemoResult
    data class Failure(val message: String) : FcmDemoResult
}

class FcmDemoRepository(
    private val apiService: ApiService,
) {
    suspend fun send(type: String): FcmDemoResult = try {
        val response = apiService.sendFcmDemo(FcmDemoRequest(type = type))
        val body = response.body()
        if (response.isSuccessful && body?.ok == true && body.sent) {
            FcmDemoResult.Success(body.type ?: type)
        } else {
            val parsed = ApiErrorParser.parse(response.errorBody(), response.code())
            val fallback = when (response.code()) {
                403 -> "Este tipo de notificación no corresponde a tu rol."
                404, 409 -> "Registra primero este dispositivo para recibir notificaciones."
                422 -> "El tipo de demostración no es válido."
                429 -> "Espera un momento antes de enviar otra demostración."
                else -> ErrorMapper.fromHttpCode(response.code()).message
            }
            FcmDemoResult.Failure(
                ErrorMapper.safeBackendMessage(response.code(), parsed.message) ?: fallback,
            )
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        FcmDemoResult.Failure(ErrorMapper.fromThrowable(error).message)
    }
}
