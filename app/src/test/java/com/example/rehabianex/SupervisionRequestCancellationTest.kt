package com.example.rehabianex

import com.example.rehabianex.core.session.SessionManager
import com.example.rehabianex.core.session.UserSession
import com.example.rehabianex.data.model.ConsentDto
import com.example.rehabianex.data.model.MeResponse
import com.example.rehabianex.data.model.MutationResponse
import com.example.rehabianex.data.model.ResourceListResponse
import com.example.rehabianex.data.model.SessionProfileDto
import com.example.rehabianex.data.model.SupervisionRequestDto
import com.example.rehabianex.data.model.UpdateSupervisionRequest
import com.example.rehabianex.data.remote.ApiService
import com.example.rehabianex.data.repository.PatientDataResult
import com.example.rehabianex.data.repository.PatientRepository
import java.lang.reflect.Proxy
import java.util.ArrayDeque
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class SupervisionRequestCancellationTest {
    @Test
    fun requestLink_cancelsOldRequestWithPatchAndRefreshesBeforeCreating() = runTest {
        val scenario = CancellationScenario(Response.success(MutationResponse()))

        val result = scenario.repository.requestSupervisorLink(TARGET_SUPERVISOR_UID)

        assertTrue(result is PatientDataResult.Success)
        assertEquals("cancelled", scenario.cancellationBodies.single().status)
        assertFalse("deleteSupervisionRequest" in scenario.calls)
        assertTrue(
            scenario.calls.indexOf("updateSupervisionRequest") <
                scenario.calls.lastIndexOf("supervisionRequests"),
        )
        assertTrue("createSupervisionRequest" in scenario.calls)
    }

    @Test
    fun requestLink_treatsMissingOldRequestAsAlreadyCancelled() = runTest {
        val scenario = CancellationScenario(errorResponse(404))

        val result = scenario.repository.requestSupervisorLink(TARGET_SUPERVISOR_UID)

        assertTrue(result is PatientDataResult.Success)
        assertTrue("createSupervisionRequest" in scenario.calls)
        assertTrue(scenario.calls.count { it == "supervisionRequests" } >= 3)
    }

    @Test
    fun requestLink_handlesCancellationErrorsWithFriendlyMessages() = runTest {
        val expectedMessages = mapOf(
            403 to "No tienes permiso para cancelar esta solicitud.",
            409 to "La solicitud ya cambió de estado. Actualiza la información e inténtalo nuevamente.",
            422 to "No fue posible cancelar la solicitud pendiente. Revisa su estado e inténtalo nuevamente.",
        )

        expectedMessages.forEach { (statusCode, expectedMessage) ->
            val scenario = CancellationScenario(errorResponse(statusCode))

            val result = scenario.repository.requestSupervisorLink(TARGET_SUPERVISOR_UID)

            val failure = result as PatientDataResult.Failure
            assertEquals(statusCode, failure.statusCode)
            assertEquals(expectedMessage, failure.message)
            assertFalse("createSupervisionRequest" in scenario.calls)
            assertTrue(scenario.calls.count { it == "supervisionRequests" } >= 2)
        }
    }

    private class CancellationScenario(
        cancellationResponse: Response<MutationResponse<SupervisionRequestDto>>,
    ) {
        val calls = mutableListOf<String>()
        val cancellationBodies = mutableListOf<UpdateSupervisionRequest>()
        private val pendingResponses = ArrayDeque(
            listOf(
                Response.success(ResourceListResponse(data = listOf(STALE_REQUEST))),
                Response.success(ResourceListResponse<SupervisionRequestDto>(data = emptyList())),
                Response.success(ResourceListResponse<SupervisionRequestDto>(data = emptyList())),
                Response.success(ResourceListResponse<SupervisionRequestDto>(data = emptyList())),
            ),
        )

        private val apiService = Proxy.newProxyInstance(
            ApiService::class.java.classLoader,
            arrayOf(ApiService::class.java),
        ) { _, method, arguments ->
            calls += method.name
            when (method.name) {
                "supervisorById" -> Response.success(
                    MutationResponse(
                        data = SessionProfileDto(
                            uid = TARGET_SUPERVISOR_UID,
                            role = "supervisor",
                        ),
                    ),
                )
                "me" -> Response.success(
                    MeResponse(
                        profile = SessionProfileDto(
                            uid = PATIENT_UID,
                            role = "patient",
                        ),
                    ),
                )
                "supervisionRequests" -> pendingResponses.removeFirst()
                "updateSupervisionRequest" -> {
                    arguments.orEmpty()
                        .filterIsInstance<UpdateSupervisionRequest>()
                        .single()
                        .also(cancellationBodies::add)
                    cancellationResponse
                }
                "createSupervisionRequest" -> Response.success(
                    MutationResponse<Unit>(
                        message = "Solicitud enviada. Quedará pendiente hasta que el supervisor la acepte.",
                    ),
                )
                "consents" -> Response.success(
                    ResourceListResponse<ConsentDto>(data = emptyList()),
                )
                "createConsent" -> Response.success(MutationResponse<Unit>())
                else -> error("Unexpected ApiService call: ${method.name}")
            }
        } as ApiService

        val repository = PatientRepository(
            apiService = apiService,
            sessionManager = PatientSessionManager(),
        )
    }

    private class PatientSessionManager : SessionManager {
        override fun saveSession(session: UserSession) = Unit

        override fun getIdToken(): String = "header.payload.signature"

        override fun getRefreshToken(): String = "refresh-token"

        override fun getUid(): String = PATIENT_UID

        override fun getEmail(): String = "patient@example.com"

        override fun getRole(): String = "patient"

        override fun getDisplayName(): String = "Paciente Prueba"

        override fun clear() = Unit
    }

    private companion object {
        const val PATIENT_UID = "patient-001"
        const val TARGET_SUPERVISOR_UID = "supervisor-new"
        val STALE_REQUEST = SupervisionRequestDto(
            documentId = "request-old",
            patientUid = PATIENT_UID,
            supervisorUid = "supervisor-old",
            status = "pending",
            type = "link",
            message = "Solicito apoyo para seguimiento autorizado en RehabiAnex.",
        )

        fun errorResponse(
            statusCode: Int,
        ): Response<MutationResponse<SupervisionRequestDto>> {
            return Response.error(
                statusCode,
                "{}".toResponseBody("application/json".toMediaType()),
            )
        }
    }
}
