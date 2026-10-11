package com.example.rehabianex

import com.example.rehabianex.core.session.SessionManager
import com.example.rehabianex.core.session.UserSession
import com.example.rehabianex.data.model.MeResponse
import com.example.rehabianex.data.model.ResourceListResponse
import com.example.rehabianex.data.model.SessionProfileDto
import com.example.rehabianex.data.model.SupervisedPatientsResponse
import com.example.rehabianex.data.model.SupervisorChatEligibilityResponse
import com.example.rehabianex.data.model.SupervisorPatientDto
import com.example.rehabianex.data.model.SupervisionRequestDto
import com.example.rehabianex.data.remote.ApiService
import com.example.rehabianex.data.repository.SupervisorDataResult
import com.example.rehabianex.data.repository.SupervisorRepository
import java.lang.reflect.Proxy
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class SupervisorAccessFlowTest {
    @Test
    fun supervisorHome_usesLightweightPatientListWithoutDownloadingNotes() = runTest {
        val calls = mutableListOf<String>()
        val patient = SupervisorPatientDto(
            uid = "patient-001",
            displayName = "Paciente autorizado",
            supervisorUid = SUPERVISOR_UID,
            supervisionStatus = "active",
            consentActive = true,
            explicitConsent = true,
            consentStatus = "active",
            status = "active",
        )
        val repository = SupervisorRepository(
            apiService = supervisorApi(
                profile = AUTHORIZED_PROFILE,
                calls = calls,
                patients = listOf(patient),
            ),
            sessionManager = SupervisorSessionManager(),
        )

        val home = (repository.loadHomeData() as SupervisorDataResult.Success).data

        assertEquals(1, home.patientsCount)
        assertEquals(1, calls.count { it == "supervisedPatients" })
        assertFalse("patientNotesByPatient" in calls)
    }

    @Test
    fun authorizedSupervisor_canLoadSensitiveSupervisorData() = runTest {
        val calls = mutableListOf<String>()
        val repository = SupervisorRepository(
            apiService = supervisorApi(
                profile = AUTHORIZED_PROFILE,
                calls = calls,
            ),
            sessionManager = SupervisorSessionManager(),
        )

        val home = repository.loadHomeData()
        val chat = repository.loadChatData()
        val homeData = (home as SupervisorDataResult.Success).data

        assertTrue(homeData.authorized)
        assertTrue(homeData.supervisorCode.startsWith("RA-"))
        assertTrue((chat as SupervisorDataResult.Success).data.authorized)
        assertTrue("supervisionRequests" in calls)
        assertTrue("supervisedPatients" in calls)
        assertTrue("supervisorChatEligibility" in calls)
        assertFalse("consents" in calls)
    }

    @Test
    fun pendingSupervisor_keepsSessionButReceivesLimitedDataAndNoChat() = runTest {
        val calls = mutableListOf<String>()
        val session = SupervisorSessionManager()
        val repository = SupervisorRepository(
            apiService = supervisorApi(
                profile = PENDING_PROFILE,
                calls = calls,
            ),
            sessionManager = session,
        )

        val home = (repository.loadHomeData() as SupervisorDataResult.Success).data
        val patients = (repository.loadPatientsData() as SupervisorDataResult.Success).data
        val chat = (repository.loadChatData() as SupervisorDataResult.Success).data
        val chatRequest = repository.askSupervisorChat("Resume el estado de mis pacientes")

        assertFalse(home.authorized)
        assertTrue(home.supervisorCode.isBlank())
        assertTrue(home.patients.isEmpty())
        assertTrue(home.pendingRequests.isEmpty())
        assertFalse(patients.authorized)
        assertTrue(patients.patients.isEmpty())
        assertFalse(chat.authorized)
        assertEquals(403, (chatRequest as SupervisorDataResult.Failure).statusCode)
        assertFalse("supervisionRequests" in calls)
        assertFalse("supervisedPatients" in calls)
        assertFalse("supervisorChat" in calls)
        assertFalse(session.cleared)
    }

    @Test
    fun supervisorEndpoint403_isShownInsteadOfBecomingEmptyData() = runTest {
        val calls = mutableListOf<String>()
        val backendMessage = "Forbidden by supervisor guard."
        val repository = SupervisorRepository(
            apiService = supervisorApi(
                profile = AUTHORIZED_PROFILE,
                calls = calls,
                pendingRequestsResponse = Response.error(
                    403,
                    """{"message":"$backendMessage"}"""
                        .toResponseBody("application/json".toMediaType()),
                ),
            ),
            sessionManager = SupervisorSessionManager(),
        )

        val result = repository.loadHomeData()

        val failure = result as SupervisorDataResult.Failure
        assertEquals(403, failure.statusCode)
        assertEquals(
            "Tu cuenta de supervisor está pendiente de autorización. " +
                "Un administrador revisará tu información antes de habilitar el acceso " +
                "a pacientes, solicitudes y Chat IA.",
            failure.message,
        )
    }

    private fun supervisorApi(
        profile: SessionProfileDto,
        calls: MutableList<String>,
        patients: List<SupervisorPatientDto> = emptyList(),
        pendingRequestsResponse: Response<ResourceListResponse<SupervisionRequestDto>> =
            Response.success(ResourceListResponse(data = emptyList())),
    ): ApiService {
        return Proxy.newProxyInstance(
            ApiService::class.java.classLoader,
            arrayOf(ApiService::class.java),
        ) { _, method, _ ->
            calls += method.name
            when (method.name) {
                "me" -> Response.success(MeResponse(profile = profile))
                "supervisionRequests" -> pendingRequestsResponse
                "supervisedPatients" -> Response.success(
                    SupervisedPatientsResponse(
                        supervisorUid = SUPERVISOR_UID,
                        patients = patients,
                    ),
                )
                "supervisorChatEligibility" -> Response.success(
                    SupervisorChatEligibilityResponse(
                        ok = true,
                        eligible = false,
                        eligiblePatientsCount = 0,
                        requiredScope = "ai_chat_summary",
                    ),
                )
                else -> error("Unexpected ApiService call: ${method.name}")
            }
        } as ApiService
    }

    private class SupervisorSessionManager : SessionManager {
        var cleared: Boolean = false

        override fun saveSession(session: UserSession) = Unit

        override fun getIdToken(): String = "header.payload.signature"

        override fun getRefreshToken(): String = "refresh-token"

        override fun getUid(): String = SUPERVISOR_UID

        override fun getEmail(): String = "supervisor@example.com"

        override fun getRole(): String = "supervisor"

        override fun getDisplayName(): String = "Supervisor Prueba"

        override fun clear() {
            cleared = true
        }
    }

    private companion object {
        const val SUPERVISOR_UID = "supervisor-001"
        val AUTHORIZED_PROFILE = SessionProfileDto(
            uid = SUPERVISOR_UID,
            role = "supervisor",
            fullName = "Supervisor Autorizado",
            status = "active",
            authorized = true,
            verified = true,
        )
        val PENDING_PROFILE = SessionProfileDto(
            uid = SUPERVISOR_UID,
            role = "supervisor",
            fullName = "Supervisor Pendiente",
            status = "pending",
            authorized = false,
            verified = false,
        )
    }
}
