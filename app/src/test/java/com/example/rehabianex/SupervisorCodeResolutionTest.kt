package com.example.rehabianex

import com.example.rehabianex.core.session.SessionManager
import com.example.rehabianex.core.session.UserSession
import com.example.rehabianex.data.model.ConsentDto
import com.example.rehabianex.data.model.CreateSupervisionRequest
import com.example.rehabianex.data.model.MeResponse
import com.example.rehabianex.data.model.MutationResponse
import com.example.rehabianex.data.model.ResourceListResponse
import com.example.rehabianex.data.model.SessionProfileDto
import com.example.rehabianex.data.model.SupervisionRequestDto
import com.example.rehabianex.data.model.SupervisorResolveResponse
import com.example.rehabianex.data.remote.ApiService
import com.example.rehabianex.data.repository.PatientRepository
import com.example.rehabianex.ui.screens.patient.SupervisorLinkUiState
import com.example.rehabianex.ui.screens.patient.SupervisorLinkViewModel
import java.io.IOException
import java.lang.reflect.Proxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class SupervisorCodeResolutionTest {
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun resolveSuccess_showsPreviewBeforeSubmittingResolvedSupervisorUid() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val scenario = ResolutionScenario(
            resolveResponse = Response.success(
                SupervisorResolveResponse(
                    ok = true,
                    uid = SUPERVISOR_UID,
                    fullName = "Dra. Laura Martínez",
                    supervisorType = "clinical_psychologist",
                    available = true,
                ),
            ),
        )
        val viewModel = SupervisorLinkViewModel(scenario.repository)

        viewModel.requestLink(SUPERVISOR_CODE)
        advanceUntilIdle()

        val preview = viewModel.uiState.value as SupervisorLinkUiState.Preview
        assertEquals("Dra. Laura Martínez", preview.supervisor.fullName)
        assertEquals("Psicología clínica", preview.supervisor.supervisorType)
        assertTrue(preview.supervisor.available)
        assertFalse("createSupervisionRequest" in scenario.calls)

        viewModel.requestLink(SUPERVISOR_CODE)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is SupervisorLinkUiState.Success)
        assertEquals(SUPERVISOR_UID, scenario.createdRequests.single().supervisorUid)
        assertEquals("link", scenario.createdRequests.single().type)
        assertNoLegacyResolutionCalls(scenario.calls)
    }

    @Test
    fun codeNotFound_doesNotSubmitLinkRequest() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val scenario = ResolutionScenario(
            resolveResponse = errorResponse(404, "{}"),
        )
        val viewModel = SupervisorLinkViewModel(scenario.repository)

        viewModel.requestLink(SUPERVISOR_CODE)
        advanceUntilIdle()

        val error = viewModel.uiState.value as SupervisorLinkUiState.Error
        assertEquals("Supervisor no encontrado o no disponible.", error.message)
        assertTrue(scenario.createdRequests.isEmpty())
        assertNoLegacyResolutionCalls(scenario.calls)
    }

    @Test
    fun unavailableSupervisor_isRejectedBeforeCreatingRequest() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val scenario = ResolutionScenario(
            resolveResponse = Response.success(
                SupervisorResolveResponse(
                    ok = true,
                    uid = SUPERVISOR_UID,
                    fullName = "Supervisor pendiente",
                    supervisorType = "support_sponsor",
                    available = false,
                ),
            ),
        )
        val viewModel = SupervisorLinkViewModel(scenario.repository)

        viewModel.requestLink(SUPERVISOR_CODE)
        advanceUntilIdle()

        val error = viewModel.uiState.value as SupervisorLinkUiState.Error
        assertEquals("Supervisor no disponible.", error.message)
        assertTrue(scenario.createdRequests.isEmpty())
    }

    @Test
    fun connectionFailure_showsFriendlyMessage() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val scenario = ResolutionScenario(
            resolveResponse = errorResponse(500, "{}"),
            resolveFailure = IOException("offline"),
        )
        val viewModel = SupervisorLinkViewModel(scenario.repository)

        viewModel.requestLink(SUPERVISOR_CODE)
        advanceUntilIdle()

        val error = viewModel.uiState.value as SupervisorLinkUiState.Error
        assertEquals("Sin conexión. Revisa tu red e inténtalo nuevamente.", error.message)
        assertTrue(scenario.createdRequests.isEmpty())
    }

    @Test
    fun invalidCode_isRejectedWithoutResolutionOrMutationCalls() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val scenario = ResolutionScenario(
            resolveResponse = errorResponse(500, "{}"),
        )
        val viewModel = SupervisorLinkViewModel(scenario.repository)

        viewModel.requestLink("RA-INVALIDO")
        advanceUntilIdle()

        val error = viewModel.uiState.value as SupervisorLinkUiState.Error
        assertEquals("El código debe usar el formato RA-XXXXXXXX.", error.message)
        assertFalse("resolveSupervisorCode" in scenario.calls)
        assertFalse("createSupervisionRequest" in scenario.calls)
    }

    private class ResolutionScenario(
        resolveResponse: Response<SupervisorResolveResponse>,
        resolveFailure: IOException? = null,
        createResponse: Response<MutationResponse<Unit>> = Response.success(
            MutationResponse(message = "Solicitud enviada."),
        ),
    ) {
        val calls = mutableListOf<String>()
        val createdRequests = mutableListOf<CreateSupervisionRequest>()

        private val apiService = Proxy.newProxyInstance(
            ApiService::class.java.classLoader,
            arrayOf(ApiService::class.java),
        ) { _, method, arguments ->
            calls += method.name
            when (method.name) {
                "resolveSupervisorCode" -> resolveFailure?.let { throw it } ?: resolveResponse
                "me" -> Response.success(
                    MeResponse(
                        profile = SessionProfileDto(
                            uid = PATIENT_UID,
                            role = "patient",
                        ),
                    ),
                )
                "supervisionRequests" -> Response.success(
                    ResourceListResponse<SupervisionRequestDto>(data = emptyList()),
                )
                "createSupervisionRequest" -> {
                    arguments.orEmpty()
                        .filterIsInstance<CreateSupervisionRequest>()
                        .single()
                        .also(createdRequests::add)
                    createResponse
                }
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

        override fun getDisplayName(): String = "Paciente"

        override fun clear() = Unit
    }

    private fun assertNoLegacyResolutionCalls(calls: List<String>) {
        assertFalse("supervisorById" in calls)
        assertFalse("supervisors" in calls)
    }

    private companion object {
        const val PATIENT_UID = "patient-001"
        const val SUPERVISOR_UID = "supervisor-001"
        const val SUPERVISOR_CODE = "RA-12AB34CD"

        fun errorResponse(
            statusCode: Int,
            body: String,
        ): Response<SupervisorResolveResponse> {
            return Response.error(
                statusCode,
                body.toResponseBody("application/json".toMediaType()),
            )
        }
    }
}
