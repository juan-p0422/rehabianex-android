package com.example.rehabianex

import com.example.rehabianex.core.session.SessionManager
import com.example.rehabianex.core.session.UserSession
import com.example.rehabianex.data.model.ConsentDto
import com.example.rehabianex.data.model.CreateSupervisionRequest
import com.example.rehabianex.data.model.MeResponse
import com.example.rehabianex.data.model.MutationResponse
import com.example.rehabianex.data.model.PatientNotesResponse
import com.example.rehabianex.data.model.ResourceListResponse
import com.example.rehabianex.data.model.SessionProfileDto
import com.example.rehabianex.data.model.SupervisedPatientsResponse
import com.example.rehabianex.data.model.SupervisionRequestDto
import com.example.rehabianex.data.model.SupervisorPatientDto
import com.example.rehabianex.data.model.UnlinkSupervisionResponse
import com.example.rehabianex.data.model.UpdateConsentRequest
import com.example.rehabianex.data.remote.ApiService
import com.example.rehabianex.data.repository.SupervisorDataResult
import com.example.rehabianex.data.repository.SupervisorRepository
import com.google.gson.Gson
import java.lang.reflect.Proxy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import retrofit2.http.POST

class SupervisionContractV1Test {
    private val gson = Gson()

    @Test
    fun linkAndUnlinkRequestsUseOfficialTypeField() {
        val linkJson = gson.toJson(
            CreateSupervisionRequest(
                supervisorUid = "supervisor-1",
                message = "Solicito acompañamiento.",
                type = "link",
            ),
        )
        val unlinkJson = gson.toJson(
            CreateSupervisionRequest(
                supervisorUid = null,
                message = "Solicito finalizar el seguimiento.",
                type = "unlink",
            ),
        )

        assertEquals("link", gson.fromJson(linkJson, Map::class.java)["type"])
        assertEquals("unlink", gson.fromJson(unlinkJson, Map::class.java)["type"])
        assertFalse(linkJson.contains("request_type"))
        assertFalse(unlinkJson.contains("request_type"))
        assertFalse(unlinkJson.contains("supervisor_uid"))
    }

    @Test
    fun consentPauseAndResumeUseBackendStatus() {
        val pauseJson = gson.toJson(UpdateConsentRequest(status = "paused"))
        val resumeJson = gson.toJson(UpdateConsentRequest(status = "active"))
        val revokeJson = gson.toJson(UpdateConsentRequest(explicitConsent = false))

        assertTrue(pauseJson.contains("\"status\":\"paused\""))
        assertTrue(resumeJson.contains("\"status\":\"active\""))
        assertFalse(pauseJson.contains("explicit_consent"))
        assertFalse(resumeJson.contains("explicit_consent"))
        assertTrue(revokeJson.contains("\"explicit_consent\":false"))
        assertFalse(revokeJson.contains("status"))
    }

    @Test
    fun finalResponseFieldsAndHistoricalAliasAreDeserialized() {
        val consent = gson.fromJson(
            """{"status":"revoked","explicit_consent":false,"revocation_reason":"supervision_unlinked"}""",
            ConsentDto::class.java,
        )
        val officialRequest = gson.fromJson(
            """{"request_id":"r1","type":"unlink","status":"pending"}""",
            SupervisionRequestDto::class.java,
        )
        val historicalRequest = gson.fromJson(
            """{"request_id":"r2","request_type":"unlink","status":"pending"}""",
            SupervisionRequestDto::class.java,
        )

        assertEquals("revoked", consent.status)
        assertEquals("supervision_unlinked", consent.revocationReason)
        assertEquals("unlink", officialRequest.type)
        assertNull(officialRequest.requestType)
        assertEquals("unlink", historicalRequest.requestType)
    }

    @Test
    fun directUnlinkUsesOfficialPostEndpoint() {
        val method = ApiService::class.java.methods.single { it.name == "unlinkSupervision" }
        val post = requireNotNull(method.getAnnotation(POST::class.java))

        assertEquals(
            "supervisors/{supervisor_uid}/patients/{patient_uid}/unlink",
            post.value,
        )
    }

    @Test
    fun directUnlinkCallsBackendAndRefreshesRemoteLists() = runTest {
        val calls = mutableListOf<String>()
        var patientsReadCount = 0
        val api = apiService { method ->
            calls += method
            when (method) {
                "me" -> activeSupervisorProfile()
                "supervisionRequests" ->
                    Response.success(ResourceListResponse<SupervisionRequestDto>(data = emptyList()))
                "supervisedPatients" -> {
                    patientsReadCount++
                    Response.success(
                        SupervisedPatientsResponse(
                            patients = if (patientsReadCount == 1) listOf(LINKED_PATIENT) else emptyList(),
                        ),
                    )
                }
                "patientNotesByPatient" -> Response.success(PatientNotesResponse(notes = emptyList()))
                "consents" -> Response.success(ResourceListResponse<ConsentDto>(data = emptyList()))
                "unlinkSupervision" -> Response.success(
                    UnlinkSupervisionResponse(
                        ok = true,
                        message = "Relación de supervisión finalizada correctamente.",
                    ),
                )
                else -> error("Unexpected ApiService call: $method")
            }
        }
        val repository = SupervisorRepository(api, SupervisorSessionManager())

        val result = repository.unlinkPatient(PATIENT_UID)

        assertTrue(result is SupervisorDataResult.Success)
        assertEquals(1, calls.count { it == "unlinkSupervision" })
        assertTrue(patientsReadCount >= 2)
    }

    @Test
    fun acceptingUnlinkRequestDoesNotExecuteSecondLocalUnlink() = runTest {
        val calls = mutableListOf<String>()
        var pendingReadCount = 0
        val api = apiService { method ->
            calls += method
            when (method) {
                "me" -> activeSupervisorProfile()
                "supervisionRequests" -> {
                    pendingReadCount++
                    Response.success(
                        ResourceListResponse(
                            data = if (pendingReadCount == 1) listOf(UNLINK_REQUEST) else emptyList(),
                        ),
                    )
                }
                "respondSupervisionRequest" -> Response.success(
                    MutationResponse(
                        message = "Solicitud aceptada y relación de supervisión finalizada.",
                        data = UNLINK_REQUEST.copy(status = "accepted"),
                    ),
                )
                "supervisedPatients" -> Response.success(
                    SupervisedPatientsResponse(patients = emptyList()),
                )
                "consents" -> Response.success(ResourceListResponse<ConsentDto>(data = emptyList()))
                "unlinkSupervision" -> error("No debe ejecutarse después de aceptar type=unlink")
                else -> error("Unexpected ApiService call: $method")
            }
        }
        val repository = SupervisorRepository(api, SupervisorSessionManager())

        val result = repository.respondRequest(REQUEST_ID, accepted = true)

        assertTrue(result is SupervisorDataResult.Success)
        assertEquals(1, calls.count { it == "respondSupervisionRequest" })
        assertFalse("unlinkSupervision" in calls)
        assertTrue(pendingReadCount >= 2)
    }

    private fun apiService(handler: (String) -> Any): ApiService {
        return Proxy.newProxyInstance(
            ApiService::class.java.classLoader,
            arrayOf(ApiService::class.java),
        ) { _, method, _ -> handler(method.name) } as ApiService
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
        const val PATIENT_UID = "patient-1"
        const val REQUEST_ID = "request-unlink-1"
        val LINKED_PATIENT = SupervisorPatientDto(
            uid = PATIENT_UID,
            supervisorUid = SUPERVISOR_UID,
            supervisionStatus = "accepted",
            consentActive = true,
            explicitConsent = true,
        )
        val UNLINK_REQUEST = SupervisionRequestDto(
            documentId = REQUEST_ID,
            patientUid = PATIENT_UID,
            patientName = "Paciente",
            supervisorUid = SUPERVISOR_UID,
            status = "pending",
            type = "unlink",
        )
    }
}
