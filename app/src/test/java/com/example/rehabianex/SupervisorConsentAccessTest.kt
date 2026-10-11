package com.example.rehabianex

import com.example.rehabianex.core.session.SessionManager
import com.example.rehabianex.core.session.UserSession
import com.example.rehabianex.data.model.ConsentDto
import com.example.rehabianex.data.model.MeResponse
import com.example.rehabianex.data.model.MutationResponse
import com.example.rehabianex.data.model.PatientNoteDto
import com.example.rehabianex.data.model.PatientNotesResponse
import com.example.rehabianex.data.model.ResourceListResponse
import com.example.rehabianex.data.model.SessionProfileDto
import com.example.rehabianex.data.model.SupervisedPatientsResponse
import com.example.rehabianex.data.model.SupervisionRequestDto
import com.example.rehabianex.data.model.SupervisorChatEligibilityResponse
import com.example.rehabianex.data.model.SupervisorPatientAccessState
import com.example.rehabianex.data.model.SupervisorPatientDto
import com.example.rehabianex.data.remote.ApiService
import com.example.rehabianex.data.repository.SupervisorDataResult
import com.example.rehabianex.data.repository.SupervisorRepository
import com.example.rehabianex.ui.screens.supervisor.PatientDetailViewModel
import com.example.rehabianex.ui.screens.supervisor.SupervisorPatientDetailUiState
import java.lang.reflect.Proxy
import java.util.ArrayDeque
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
class SupervisorConsentAccessTest {
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun detail403BlocksAllPatientContent() = runTest {
        val calls = mutableListOf<String>()
        val repository = repository { method ->
            calls += method
            when (method) {
                "me" -> activeSupervisorProfile()
                "patientNotesByPatient" -> forbiddenNotes()
                else -> error("Unexpected ApiService call: $method")
            }
        }

        val result = repository.loadPatientDetail(PATIENT_ACTIVE)

        val failure = result as SupervisorDataResult.Failure
        assertEquals(403, failure.statusCode)
        assertTrue(failure.message.contains("pausado o revocado"))
        assertFalse("supervisedPatients" in calls)
    }

    @Test
    fun patientListDifferentiatesAndSanitizesEveryAccessState() = runTest {
        val calls = mutableListOf<String>()
        val repository = repository(calls) { method ->
            when (method) {
                "me" -> activeSupervisorProfile()
                "supervisionRequests" -> emptyRequests()
                "supervisedPatients" -> Response.success(
                    SupervisedPatientsResponse(patients = listOf(ACTIVE_PATIENT)),
                )
                "patientNotesByPatient" -> Response.success(
                    PatientNotesResponse(notes = listOf(PatientNoteDto(anxietyLevel = 8))),
                )
                "consents" -> Response.success(
                    ResourceListResponse(
                        data = listOf(
                            consent(PATIENT_PAUSED, "paused"),
                            consent(PATIENT_NO_ACCESS, "revoked"),
                            consent(
                                PATIENT_UNLINKED,
                                "revoked",
                                revocationReason = "supervision_unlinked",
                            ),
                        ),
                    ),
                )
                "patientById" -> Response.error<MutationResponse<SessionProfileDto>>(
                    403,
                    "{}".toResponseBody("application/json".toMediaType()),
                )
                else -> error("Unexpected ApiService call: $method")
            }
        }

        val result = repository.loadPatientsData() as SupervisorDataResult.Success
        val byUid = result.data.patients.associateBy { it.uid }

        assertEquals(SupervisorPatientAccessState.Active, byUid[PATIENT_ACTIVE]?.accessState)
        assertEquals(SupervisorPatientAccessState.Paused, byUid[PATIENT_PAUSED]?.accessState)
        assertEquals(SupervisorPatientAccessState.NoAccess, byUid[PATIENT_NO_ACCESS]?.accessState)
        assertEquals(SupervisorPatientAccessState.Unlinked, byUid[PATIENT_UNLINKED]?.accessState)

        listOf(PATIENT_PAUSED, PATIENT_NO_ACCESS, PATIENT_UNLINKED).forEach { uid ->
            val patient = requireNotNull(byUid[uid])
            assertFalse(patient.canOpenDetails)
            assertEquals(0, patient.notesCount)
            assertEquals("—", patient.anxietyText)
            assertEquals("—", patient.soberDays)
        }
        assertFalse("patientById" in calls)
        assertEquals(1, calls.count { it == "patientNotesByPatient" })
    }

    @Test
    fun privacyProjectionUsesSafeReferenceAndIgnoresSensitiveFullName() = runTest {
        val protectedPatient = ACTIVE_PATIENT.copy(
            uid = "patient-protected",
            fullName = "Nombre sensible",
            patientRef = "Paciente #A17C",
            privacyMode = true,
        )
        val repository = repository { method ->
            when (method) {
                "me" -> activeSupervisorProfile()
                "supervisionRequests" -> emptyRequests()
                "supervisedPatients" -> Response.success(
                    SupervisedPatientsResponse(patients = listOf(protectedPatient)),
                )
                "patientNotesByPatient" -> Response.success(PatientNotesResponse(notes = emptyList()))
                "consents" -> Response.success(ResourceListResponse<ConsentDto>(data = emptyList()))
                else -> error("Unexpected ApiService call: $method")
            }
        }

        val result = repository.loadPatientsData() as SupervisorDataResult.Success

        assertEquals("Paciente #A17C", result.data.patients.single().name)
        assertFalse(result.data.patients.single().name.contains("Nombre sensible"))
    }

    @Test
    fun chatEligibilityUsesDedicatedEndpointWithoutReadingPatientNotes() = runTest {
        val calls = mutableListOf<String>()
        val repository = repository { method ->
            calls += method
            when (method) {
                "me" -> activeSupervisorProfile()
                "supervisorChatEligibility" -> Response.success(
                    SupervisorChatEligibilityResponse(
                        ok = true,
                        eligible = true,
                        eligiblePatientsCount = 1,
                        requiredScope = "ai_chat_summary",
                    ),
                )
                else -> error("Unexpected ApiService call: $method")
            }
        }

        val result = repository.loadChatData() as SupervisorDataResult.Success

        assertEquals(1, result.data.patientsCount)
        assertEquals(listOf("me", "supervisorChatEligibility"), calls)
        assertFalse("patientNotesByPatient" in calls)
        assertFalse("consents" in calls)
    }

    @Test
    fun detailReloadReplacesPreviousContentAfterConsentIsRevoked() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val notesResponses = ArrayDeque(
            listOf(
                Response.success(PatientNotesResponse(notes = listOf(PatientNoteDto(mood = "stable")))),
                forbiddenNotes(),
            ),
        )
        val repository = repository { method ->
            when (method) {
                "me" -> activeSupervisorProfile()
                "patientNotesByPatient" -> notesResponses.removeFirst()
                "supervisedPatients" -> Response.success(
                    SupervisedPatientsResponse(patients = listOf(ACTIVE_PATIENT)),
                )
                else -> error("Unexpected ApiService call: $method")
            }
        }
        val viewModel = PatientDetailViewModel(repository, PATIENT_ACTIVE)

        viewModel.load()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is SupervisorPatientDetailUiState.Content)

        viewModel.load()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is SupervisorPatientDetailUiState.Error)
        assertFalse(state is SupervisorPatientDetailUiState.Content)
    }

    private fun repository(
        calls: MutableList<String>? = null,
        handler: (String) -> Any,
    ): SupervisorRepository {
        val api = Proxy.newProxyInstance(
            ApiService::class.java.classLoader,
            arrayOf(ApiService::class.java),
        ) { _, method, _ ->
            calls?.add(method.name)
            handler(method.name)
        } as ApiService
        return SupervisorRepository(api, SupervisorSessionManager())
    }

    private fun activeSupervisorProfile(): Response<MeResponse> {
        return Response.success(
            MeResponse(
                profile = SessionProfileDto(
                    uid = SUPERVISOR_UID,
                    role = "supervisor",
                    status = "active",
                    authorized = true,
                    verified = true,
                ),
            ),
        )
    }

    private fun emptyRequests(): Response<ResourceListResponse<SupervisionRequestDto>> {
        return Response.success(ResourceListResponse(data = emptyList()))
    }

    private fun forbiddenNotes(): Response<PatientNotesResponse> {
        return Response.error(
            403,
            """{"message":"El consentimiento no permite consultar notas."}"""
                .toResponseBody("application/json".toMediaType()),
        )
    }

    private fun consent(
        patientUid: String,
        status: String,
        revocationReason: String? = null,
        scope: List<String>? = null,
    ): ConsentDto {
        return ConsentDto(
            patientUid = patientUid,
            supervisorUid = SUPERVISOR_UID,
            status = status,
            explicitConsent = status == "active",
            scope = scope,
            revocationReason = revocationReason,
            updatedAt = "2026-07-25T20:00:00-06:00",
        )
    }

    private class SupervisorSessionManager : SessionManager {
        override fun saveSession(session: UserSession) = Unit
        override fun getIdToken(): String = "header.payload.signature"
        override fun getRefreshToken(): String = "refresh-token"
        override fun getUid(): String = SUPERVISOR_UID
        override fun getEmail(): String = "supervisor@example.com"
        override fun getRole(): String = "supervisor"
        override fun getDisplayName(): String = "Supervisor"
        override fun clear() = Unit
    }

    private companion object {
        const val SUPERVISOR_UID = "supervisor-1"
        const val PATIENT_ACTIVE = "patient-active"
        const val PATIENT_PAUSED = "patient-paused"
        const val PATIENT_NO_ACCESS = "patient-no-access"
        const val PATIENT_UNLINKED = "patient-unlinked"
        val ACTIVE_PATIENT = SupervisorPatientDto(
            uid = PATIENT_ACTIVE,
            supervisorUid = SUPERVISOR_UID,
            supervisionStatus = "accepted",
            consentActive = true,
            explicitConsent = true,
            consentStatus = "active",
        )
    }
}
