package com.example.rehabianex

import com.example.rehabianex.core.session.SessionManager
import com.example.rehabianex.core.session.UserSession
import com.example.rehabianex.data.model.ConsentDto
import com.example.rehabianex.data.model.CreateConsentRequest
import com.example.rehabianex.data.model.MeResponse
import com.example.rehabianex.data.model.MutationResponse
import com.example.rehabianex.data.model.ResourceListResponse
import com.example.rehabianex.data.model.SessionProfileDto
import com.example.rehabianex.data.model.SupervisionPolicy
import com.example.rehabianex.data.model.SupervisionRequestDto
import com.example.rehabianex.data.model.SupervisorPublicDto
import com.example.rehabianex.data.model.UpdateConsentRequest
import com.example.rehabianex.data.model.UpdateSupervisionRequest
import com.example.rehabianex.data.remote.ApiService
import com.example.rehabianex.data.repository.PatientDataResult
import com.example.rehabianex.data.repository.PatientRepository
import java.lang.reflect.Proxy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class SupervisionGranularConsentTest {
    @Test
    fun scopeCatalog_hasCanonicalOrderAndDropsUnknownValues() {
        assertEquals(
            listOf(
                "patient_notes",
                "agenda_events",
                "support_contacts",
                "ai_chat_summary",
                "patient_achievements",
                "patient_phone",
            ),
            SupervisionPolicy.consentScopes.map { it.code },
        )
        assertEquals(
            listOf("patient_notes", "patient_phone"),
            SupervisionPolicy.normalizeScopes(
                listOf("patient_phone", "unknown", "patient_notes", "patient_phone"),
            ),
        )
    }

    @Test
    fun activation_requiresExplicitScopeSelectionAndSendsOnlySelectedScopes() = runTest {
        val captured = mutableListOf<CreateConsentRequest>()
        val api = apiService { method, arguments ->
            when (method) {
                "me" -> activePatientProfile()
                "consents" -> Response.success(ResourceListResponse<ConsentDto>(data = emptyList()))
                "supervisionRequests" ->
                    Response.success(ResourceListResponse<SupervisionRequestDto>(data = emptyList()))
                "createConsent" -> {
                    captured += arguments.filterIsInstance<CreateConsentRequest>().single()
                    Response.success(MutationResponse<Unit>(message = "Consentimiento activado."))
                }
                else -> error("Unexpected ApiService call: $method")
            }
        }
        val repository = PatientRepository(api, PatientSessionManager())

        val emptyResult = repository.setSupervisorConsent(true, emptySet())
        val selectedResult = repository.setSupervisorConsent(
            enabled = true,
            selectedScopes = setOf("patient_phone", "patient_notes", "unknown"),
        )

        assertTrue(emptyResult is PatientDataResult.Failure)
        assertEquals(422, (emptyResult as PatientDataResult.Failure).statusCode)
        assertTrue(selectedResult is PatientDataResult.Success)
        assertEquals(listOf("patient_notes", "patient_phone"), captured.single().scope)
        assertFalse(captured.single().scope.contains("unknown"))
    }

    @Test
    fun existingConsent_updatesScopesWithoutAddingDefaults() = runTest {
        val captured = mutableListOf<UpdateConsentRequest>()
        val current = ConsentDto(
            documentId = "consent-1",
            patientUid = PATIENT_UID,
            supervisorUid = SUPERVISOR_UID,
            type = "supervision_and_alerts",
            status = "active",
            explicitConsent = true,
            scope = listOf("patient_notes"),
        )
        val api = apiService { method, arguments ->
            when (method) {
                "me" -> activePatientProfile()
                "consents" -> Response.success(ResourceListResponse(data = listOf(current)))
                "supervisionRequests" ->
                    Response.success(ResourceListResponse<SupervisionRequestDto>(data = emptyList()))
                "updateConsent" -> {
                    captured += arguments.filterIsInstance<UpdateConsentRequest>().single()
                    Response.success(MutationResponse<ConsentDto>())
                }
                else -> error("Unexpected ApiService call: $method")
            }
        }

        val result = PatientRepository(api, PatientSessionManager()).setSupervisorConsent(
            enabled = true,
            selectedScopes = setOf("agenda_events"),
        )

        assertTrue(result is PatientDataResult.Success)
        assertEquals("active", captured.single().status)
        assertEquals(listOf("agenda_events"), captured.single().scope)
    }

    @Test
    fun profileUsesLinkedSupervisorSummary() = runTest {
        val api = apiService { method, _ ->
            when (method) {
                "me" -> Response.success(
                    MeResponse(
                        profile = SessionProfileDto(
                            uid = PATIENT_UID,
                            role = "patient",
                            supervisorUid = SUPERVISOR_UID,
                            supervisionStatus = "accepted",
                            supervisor = SupervisorPublicDto(
                                uid = SUPERVISOR_UID,
                                fullName = "Supervisora Sintética",
                                supervisorType = "clinical_psychologist",
                            ),
                        ),
                    ),
                )
                "consents" -> Response.success(ResourceListResponse<ConsentDto>(data = emptyList()))
                "supervisionRequests" ->
                    Response.success(ResourceListResponse<SupervisionRequestDto>(data = emptyList()))
                else -> error("Unexpected ApiService call: $method")
            }
        }

        val result = PatientRepository(api, PatientSessionManager()).loadProfileData()
        val profile = (result as PatientDataResult.Success).data

        assertTrue(profile.supervisorText.contains("Supervisora Sintética"))
        assertEquals("Psicología clínica", profile.supervisorType)
    }

    @Test
    fun profileExposesOwnedPendingLinkRequestId() = runTest {
        val pending = SupervisionRequestDto(
            documentId = "request-1",
            patientUid = PATIENT_UID,
            supervisorUid = SUPERVISOR_UID,
            type = "link",
            status = "pending",
        )
        val api = apiService { method, _ ->
            when (method) {
                "me" -> Response.success(
                    MeResponse(profile = SessionProfileDto(uid = PATIENT_UID, role = "patient")),
                )
                "supervisionRequests" -> Response.success(ResourceListResponse(data = listOf(pending)))
                else -> error("Unexpected ApiService call: $method")
            }
        }

        val result = PatientRepository(api, PatientSessionManager()).loadProfileData()
        val profile = (result as PatientDataResult.Success).data

        assertEquals("request-1", profile.pendingLinkRequestId)
    }

    @Test
    fun patientCanCancelOwnedPendingLinkWithLogicalPatch() = runTest {
        val pending = SupervisionRequestDto(
            documentId = "request-1",
            patientUid = PATIENT_UID,
            supervisorUid = SUPERVISOR_UID,
            type = "link",
            status = "pending",
        )
        var pendingReads = 0
        val captured = mutableListOf<UpdateSupervisionRequest>()
        val api = apiService { method, arguments ->
            when (method) {
                "me" -> activePatientProfile()
                "consents" -> Response.success(ResourceListResponse<ConsentDto>(data = emptyList()))
                "supervisionRequests" -> {
                    pendingReads++
                    Response.success(
                        ResourceListResponse(
                            data = if (pendingReads == 1) listOf(pending) else emptyList(),
                        ),
                    )
                }
                "updateSupervisionRequest" -> {
                    captured += arguments.filterIsInstance<UpdateSupervisionRequest>().single()
                    Response.success(MutationResponse<SupervisionRequestDto>())
                }
                else -> error("Unexpected ApiService call: $method")
            }
        }

        val result = PatientRepository(api, PatientSessionManager())
            .cancelPendingSupervisorRequest("request-1")

        assertTrue(result is PatientDataResult.Success)
        assertEquals("cancelled", captured.single().status)
    }

    private fun activePatientProfile(): Response<MeResponse> = Response.success(
        MeResponse(
            profile = SessionProfileDto(
                uid = PATIENT_UID,
                role = "patient",
                supervisorUid = SUPERVISOR_UID,
                supervisionStatus = "accepted",
                supervisor = SupervisorPublicDto(
                    uid = SUPERVISOR_UID,
                    fullName = "Supervisora Sintética",
                    supervisorType = "clinical_psychologist",
                ),
            ),
        ),
    )

    private fun apiService(
        handler: (String, List<Any?>) -> Any,
    ): ApiService = Proxy.newProxyInstance(
        ApiService::class.java.classLoader,
        arrayOf(ApiService::class.java),
    ) { _, method, arguments ->
        handler(method.name, arguments?.toList().orEmpty())
    } as ApiService

    private class PatientSessionManager : SessionManager {
        override fun saveSession(session: UserSession) = Unit
        override fun getIdToken(): String = "header.payload.signature"
        override fun getRefreshToken(): String = "refresh-token"
        override fun getUid(): String = PATIENT_UID
        override fun getEmail(): String = "patient@example.test"
        override fun getRole(): String = "patient"
        override fun getDisplayName(): String = "Paciente Sintético"
        override fun clear() = Unit
    }

    private companion object {
        const val PATIENT_UID = "patient-1"
        const val SUPERVISOR_UID = "supervisor-1"
    }
}
