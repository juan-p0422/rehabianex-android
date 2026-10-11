package com.example.rehabianex

import com.example.rehabianex.core.session.SessionManager
import com.example.rehabianex.core.session.UserSession
import com.example.rehabianex.data.model.AuthResponse
import com.example.rehabianex.data.model.MeResponse
import com.example.rehabianex.data.model.NotificationSettingsDto
import com.example.rehabianex.data.model.ResourceListResponse
import com.example.rehabianex.data.model.TokenData
import com.example.rehabianex.data.remote.ApiService
import com.example.rehabianex.data.repository.PatientDataResult
import com.example.rehabianex.data.repository.PatientRepository
import com.example.rehabianex.data.repository.SupervisorDataResult
import com.example.rehabianex.data.repository.SupervisorRepository
import java.lang.reflect.Proxy
import java.net.SocketTimeoutException
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class SessionRefreshErrorTest {
    @Test
    fun patientRefresh_mapsTemporaryErrorsWithoutClearingSession() = runTest {
        val expectedMessages = mapOf(
            422 to "Revisa los datos marcados e inténtalo nuevamente.",
            429 to "Has realizado demasiadas solicitudes. Espera un momento antes de continuar.",
            500 to "El servicio presenta un problema temporal. Inténtalo más tarde.",
            502 to "Un servicio externo no está disponible temporalmente. Inténtalo nuevamente.",
        )

        expectedMessages.forEach { (statusCode, expectedMessage) ->
            val session = TrackingSessionManager(role = "patient")
            val repository = PatientRepository(
                apiService = patientApi(refreshResponse(statusCode)),
                sessionManager = session,
            )

            val result = repository.loadNotificationSettings()

            val failure = result as PatientDataResult.Failure
            assertEquals(statusCode, failure.statusCode)
            assertEquals(expectedMessage, failure.message)
            assertFalse(session.cleared)
        }
    }

    @Test
    fun patientRefresh_doesNotExposeBackendRefreshDetails() = runTest {
        val session = TrackingSessionManager(role = "patient")
        val repository = PatientRepository(
            apiService = patientApi(
                refreshResponse(
                    statusCode = 422,
                    body = """{"message":"El refresh token tiene un formato inválido."}""",
                ),
            ),
            sessionManager = session,
        )

        val result = repository.loadNotificationSettings()

        val failure = result as PatientDataResult.Failure
        assertEquals("Revisa los datos marcados e inténtalo nuevamente.", failure.message)
        assertFalse(session.cleared)
    }

    @Test
    fun patientRefresh_clearsSessionWhenBackendReturns401() = runTest {
        val session = TrackingSessionManager(role = "patient")
        val repository = PatientRepository(
            apiService = patientApi(refreshResponse(401)),
            sessionManager = session,
        )

        val result = repository.loadNotificationSettings()

        val failure = result as PatientDataResult.Failure
        assertEquals(401, failure.statusCode)
        assertTrue(session.cleared)
    }

    @Test
    fun supervisorRefresh_keepsSessionOnTemporaryProviderError() = runTest {
        val session = TrackingSessionManager(role = "supervisor")
        val apiService = apiService { method ->
            when (method) {
                "me" -> Response.error<MeResponse>(
                    401,
                    "{}".toResponseBody(JSON_MEDIA_TYPE),
                )
                "refresh" -> refreshResponse(502)
                else -> error("Unexpected ApiService call: $method")
            }
        }
        val repository = SupervisorRepository(apiService, session)

        val result = repository.loadChatData()

        val failure = result as SupervisorDataResult.Failure
        assertEquals(502, failure.statusCode)
        assertEquals(
            "Un servicio externo no está disponible temporalmente. Inténtalo nuevamente.",
            failure.message,
        )
        assertFalse(session.cleared)
    }

    @Test
    fun patientRefresh_timeoutDoesNotClearSession() = runTest {
        val session = TrackingSessionManager(role = "patient")
        val api = apiService { method ->
            when (method) {
                "notificationSettings" -> Response.error<ResourceListResponse<NotificationSettingsDto>>(
                    401,
                    "{}".toResponseBody(JSON_MEDIA_TYPE),
                )
                "refresh" -> throw SocketTimeoutException("simulated timeout")
                else -> error("Unexpected ApiService call: $method")
            }
        }

        val result = PatientRepository(api, session).loadNotificationSettings()

        val failure = result as PatientDataResult.Failure
        assertEquals("El servidor tardó demasiado en responder.", failure.message)
        assertFalse(session.cleared)
    }

    @Test
    fun patientRefresh_retriesOriginalRequestAndKeepsRenewedSession() = runTest {
        val session = TrackingSessionManager(role = "patient")
        var settingsCalls = 0
        val api = apiService { method ->
            when (method) {
                "notificationSettings" -> {
                    settingsCalls += 1
                    if (settingsCalls == 1) {
                        Response.error<ResourceListResponse<NotificationSettingsDto>>(
                            401,
                            "{}".toResponseBody(JSON_MEDIA_TYPE),
                        )
                    } else {
                        Response.success(ResourceListResponse(data = emptyList()))
                    }
                }
                "refresh" -> Response.success(
                    AuthResponse(
                        tokens = TokenData(
                            idToken = "renewed-token",
                            refreshToken = "renewed-refresh-token",
                        ),
                    ),
                )
                else -> error("Unexpected ApiService call: $method")
            }
        }

        val result = PatientRepository(api, session).loadNotificationSettings()

        assertTrue(result is PatientDataResult.Success)
        assertEquals(2, settingsCalls)
        assertFalse(session.cleared)
        assertEquals("renewed-token", session.savedSession?.token)
    }

    @Test
    fun patientRefresh_final401ClearsSession() = runTest {
        val session = TrackingSessionManager(role = "patient")
        val api = apiService { method ->
            when (method) {
                "notificationSettings" -> Response.error<ResourceListResponse<NotificationSettingsDto>>(
                    401,
                    "{}".toResponseBody(JSON_MEDIA_TYPE),
                )
                "refresh" -> Response.success(
                    AuthResponse(
                        tokens = TokenData(idToken = "renewed-token"),
                    ),
                )
                else -> error("Unexpected ApiService call: $method")
            }
        }

        val result = PatientRepository(api, session).loadNotificationSettings()

        val failure = result as PatientDataResult.Failure
        assertEquals(401, failure.statusCode)
        assertTrue(session.cleared)
    }

    private fun patientApi(
        refreshResponse: Response<AuthResponse>,
    ): ApiService {
        return apiService { method ->
            when (method) {
                "notificationSettings" -> Response.error<ResourceListResponse<NotificationSettingsDto>>(
                    401,
                    "{}".toResponseBody(JSON_MEDIA_TYPE),
                )
                "refresh" -> refreshResponse
                else -> error("Unexpected ApiService call: $method")
            }
        }
    }

    private fun apiService(
        handler: (String) -> Any,
    ): ApiService {
        return Proxy.newProxyInstance(
            ApiService::class.java.classLoader,
            arrayOf(ApiService::class.java),
        ) { _, method, _ ->
            handler(method.name)
        } as ApiService
    }

    private class TrackingSessionManager(
        private val role: String,
    ) : SessionManager {
        var cleared: Boolean = false
        var savedSession: UserSession? = null

        override fun saveSession(session: UserSession) {
            savedSession = session
        }

        override fun getIdToken(): String = "expired-token"

        override fun getRefreshToken(): String = "refresh-token"

        override fun getUid(): String = "user-001"

        override fun getEmail(): String = "$role@example.com"

        override fun getRole(): String = role

        override fun getDisplayName(): String = "Usuario"

        override fun clear() {
            cleared = true
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()

        fun refreshResponse(
            statusCode: Int,
            body: String = "{}",
        ): Response<AuthResponse> {
            return Response.error(
                statusCode,
                body.toResponseBody(JSON_MEDIA_TYPE),
            )
        }
    }
}
