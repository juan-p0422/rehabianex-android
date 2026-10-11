package com.example.rehabianex

import com.example.rehabianex.core.session.SessionManager
import com.example.rehabianex.core.session.UserSession
import com.example.rehabianex.data.model.AuthResponse
import com.example.rehabianex.data.model.AdminAuthorizeSupervisorRequest
import com.example.rehabianex.data.model.AdminSupervisorDto
import com.example.rehabianex.data.model.AdminSupervisorReasonRequest
import com.example.rehabianex.data.model.AdminSupervisorResponse
import com.example.rehabianex.data.model.AdminSupervisorsResponse
import com.example.rehabianex.data.remote.ApiService
import com.example.rehabianex.data.repository.AdminDataResult
import com.example.rehabianex.data.repository.AdminRepository
import java.lang.reflect.Proxy
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class AdminRepositoryTest {
    @Test
    fun listSupervisors_readsOfficialDataEnvelope() = runTest {
        val expected = AdminSupervisorDto(
            uid = "supervisor-001",
            fullName = "Dra. Laura Martínez",
            status = "pending_review",
        )
        val repository = AdminRepository(
            apiService = apiService { method, _ ->
                when (method) {
                    "adminSupervisors" -> Response.success(
                        AdminSupervisorsResponse(
                            ok = true,
                            count = 1,
                            data = listOf(expected),
                        ),
                    )

                    else -> error("Unexpected ApiService call: $method")
                }
            },
        )

        val result = repository.supervisors(status = "pending_review")

        val success = result as AdminDataResult.Success
        assertEquals(listOf(expected), success.data)
    }

    @Test
    fun transitionMethods_sendOfficialBodiesAndReturnSupervisor() = runTest {
        val receivedRequests = mutableMapOf<String, Any?>()
        val supervisor = AdminSupervisorDto(
            uid = "supervisor-001",
            authorized = true,
            verified = true,
            status = "active",
        )
        val repository = AdminRepository(
            apiService = apiService { method, arguments ->
                receivedRequests[method] = arguments.firstOrNull {
                    it is AdminAuthorizeSupervisorRequest ||
                        it is AdminSupervisorReasonRequest
                }
                Response.success(
                    AdminSupervisorResponse(
                        ok = true,
                        message = "Operación completada.",
                        data = supervisor,
                    ),
                )
            },
        )

        assertTrue(repository.authorize("supervisor-001", "Documentación revisada") is AdminDataResult.Success)
        assertTrue(repository.reject("supervisor-001", "Documentación incompleta") is AdminDataResult.Success)
        assertTrue(repository.suspend("supervisor-001", "Revisión administrativa") is AdminDataResult.Success)
        assertTrue(repository.reactivate("supervisor-001") is AdminDataResult.Success)

        assertEquals(
            "Documentación revisada",
            (receivedRequests["authorizeAdminSupervisor"] as AdminAuthorizeSupervisorRequest).notes,
        )
        assertEquals(
            "Documentación incompleta",
            (receivedRequests["rejectAdminSupervisor"] as AdminSupervisorReasonRequest).reason,
        )
        assertEquals(
            "Revisión administrativa",
            (receivedRequests["suspendAdminSupervisor"] as AdminSupervisorReasonRequest).reason,
        )
    }

    @Test
    fun officialAdminErrors_keepStatusAndReadableMessage() = runTest {
        val expectations = mapOf(
            401 to "Tu sesión expiró. Inicia sesión nuevamente.",
            403 to "Tu cuenta no tiene permisos administrativos.",
            404 to "Supervisor no encontrado.",
            409 to "El estado actual no permite esta operación.",
            422 to "Los datos enviados no son válidos.",
            500 to "El servicio presenta un problema temporal. Inténtalo más tarde.",
        )

        expectations.forEach { (statusCode, expectedMessage) ->
            val backendMessage = when (statusCode) {
                404 -> "Supervisor no encontrado."
                409 -> "El estado actual no permite esta operación."
                422 -> "Los datos enviados no son válidos."
                else -> "Mensaje interno que no debe reemplazar el mensaje seguro."
            }
            val repository = AdminRepository(
                apiService = apiService { method, _ ->
                    when (method) {
                        "adminSupervisorByUid" -> Response.error<AdminSupervisorResponse>(
                            statusCode,
                            """
                            {
                              "ok": false,
                              "message": "$backendMessage",
                              "errors": {
                                "reason": ["El motivo es obligatorio."]
                              }
                            }
                            """.trimIndent().toResponseBody(JSON_MEDIA_TYPE),
                        )

                        else -> error("Unexpected ApiService call: $method")
                    }
                },
            )

            val failure = repository.supervisor("supervisor-001") as AdminDataResult.Failure

            assertEquals(statusCode, failure.statusCode)
            assertEquals(expectedMessage, failure.message)
            if (statusCode == 422) {
                assertEquals("El motivo es obligatorio.", failure.fieldErrors["reason"])
            }
        }
    }

    @Test
    fun admin401AttemptsRefreshAndKeepsSessionOnServerFailure() = runTest {
        val session = TrackingAdminSessionManager()
        val calls = mutableListOf<String>()
        val repository = AdminRepository(
            apiService = apiService { method, _ ->
                calls += method
                when (method) {
                    "adminSupervisors" -> Response.error<AdminSupervisorsResponse>(
                        401,
                        "{}".toResponseBody(JSON_MEDIA_TYPE),
                    )
                    "refresh" -> Response.error<AuthResponse>(
                        500,
                        """{"message":"Internal server error: stack trace"}"""
                            .toResponseBody(JSON_MEDIA_TYPE),
                    )
                    else -> error("Unexpected ApiService call: $method")
                }
            },
            sessionManager = session,
        )

        val failure = repository.supervisors() as AdminDataResult.Failure

        assertEquals(listOf("adminSupervisors", "refresh"), calls)
        assertEquals(500, failure.statusCode)
        assertEquals(
            "El servicio presenta un problema temporal. Inténtalo más tarde.",
            failure.message,
        )
        assertFalse(session.cleared)
    }

    @Test
    fun adminRefresh401ClearsSession() = runTest {
        val session = TrackingAdminSessionManager()
        val repository = AdminRepository(
            apiService = apiService { method, _ ->
                when (method) {
                    "adminSupervisors" -> Response.error<AdminSupervisorsResponse>(
                        401,
                        "{}".toResponseBody(JSON_MEDIA_TYPE),
                    )
                    "refresh" -> Response.error<AuthResponse>(
                        401,
                        "{}".toResponseBody(JSON_MEDIA_TYPE),
                    )
                    else -> error("Unexpected ApiService call: $method")
                }
            },
            sessionManager = session,
        )

        val failure = repository.supervisors() as AdminDataResult.Failure

        assertEquals(401, failure.statusCode)
        assertTrue(session.cleared)
    }

    private fun apiService(
        response: (method: String, arguments: List<Any?>) -> Any,
    ): ApiService {
        return Proxy.newProxyInstance(
            ApiService::class.java.classLoader,
            arrayOf(ApiService::class.java),
        ) { _, method, arguments ->
            response(method.name, arguments?.toList().orEmpty())
        } as ApiService
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }

    private class TrackingAdminSessionManager : SessionManager {
        var cleared = false

        override fun saveSession(session: UserSession) = Unit
        override fun getIdToken(): String = "expired-token"
        override fun getRefreshToken(): String = "refresh-token"
        override fun getUid(): String = "admin-001"
        override fun getEmail(): String = "admin@example.com"
        override fun getRole(): String = "admin"
        override fun getDisplayName(): String = "Administrador"
        override fun clear() {
            cleared = true
        }
    }
}
