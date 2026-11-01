package com.example.rehabianex

import com.example.rehabianex.core.session.SessionManager
import com.example.rehabianex.core.session.UserSession
import com.example.rehabianex.data.local.NetworkStatusProvider
import com.example.rehabianex.data.local.PatientIdentity
import com.example.rehabianex.data.local.PatientIdentityStorage
import com.example.rehabianex.data.model.CreatePatientNoteRequest
import com.example.rehabianex.data.model.ConsentDto
import com.example.rehabianex.data.model.MeResponse
import com.example.rehabianex.data.model.MutationResponse
import com.example.rehabianex.data.model.ResourceListResponse
import com.example.rehabianex.data.model.SessionProfileDto
import com.example.rehabianex.data.model.StorePatientNoteResponse
import com.example.rehabianex.data.model.SupervisionRequestDto
import com.example.rehabianex.data.model.UpdatePatientRequest
import com.example.rehabianex.data.model.UpdateSupervisorRequest
import com.example.rehabianex.data.remote.ApiService
import com.example.rehabianex.data.repository.PatientDataResult
import com.example.rehabianex.data.repository.PatientRepository
import com.google.gson.Gson
import java.lang.reflect.Proxy
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class PatientUpdateContractTest {
    @Test
    fun profilePatch_omitsUnchangedPhoneAndIncludesEditedPhone() {
        val gson = Gson()
        val patientWithoutPhone = gson.toJsonTree(
            UpdatePatientRequest(fullName = "Paciente Histórico", phone = null),
        ).asJsonObject
        val supervisorWithoutPhone = gson.toJsonTree(
            UpdateSupervisorRequest(
                fullName = "Supervisor Histórico",
                phone = null,
                supervisorType = "psychologist",
            ),
        ).asJsonObject
        val patientWithPhone = gson.toJsonTree(
            UpdatePatientRequest(fullName = "Paciente Actualizado", phone = "+524491234567"),
        ).asJsonObject

        assertFalse(patientWithoutPhone.has("phone"))
        assertFalse(supervisorWithoutPhone.has("phone"))
        assertEquals("+524491234567", patientWithPhone.get("phone").asString)
    }

    @Test
    fun updatePatientRequest_serializesOnlyBackendAllowedFields() {
        val payload = Gson().toJsonTree(
            UpdatePatientRequest(
                fullName = "Nombre visible",
                nickname = "Apodo",
                phone = "+524491234567",
                gender = "female",
                age = 29,
                photoUrl = "https://example.com/photo.jpg",
                sobrietyStartDate = "2026-06-01T08:00:00-06:00",
                isAnonymous = false,
                privacyMode = true,
                primaryRisks = listOf("ansiedad nocturna"),
            ),
        ).asJsonObject

        assertEquals(
            setOf(
                "full_name",
                "nickname",
                "phone",
                "gender",
                "age",
                "photo_url",
                "sobriety_start_date",
                "is_anonymous",
                "privacy_mode",
                "primary_risks",
            ),
            payload.keySet(),
        )
        FORBIDDEN_FIELDS.forEach { field ->
            assertFalse(payload.has(field))
        }
    }

    @Test
    fun privacyUpdate_treats422AsFailure() = runTest {
        val capturedRequests = mutableListOf<UpdatePatientRequest>()
        val apiService = apiService { method, arguments ->
            when (method) {
                "updatePatient" -> {
                    arguments.orEmpty()
                        .filterIsInstance<UpdatePatientRequest>()
                        .single()
                        .also(capturedRequests::add)
                    Response.error<MutationResponse<SessionProfileDto>>(
                        422,
                        """
                        {
                          "ok": false,
                          "message": "Revisa los datos marcados.",
                          "errors": {
                            "nickname": ["El nickname no puede superar 80 caracteres."]
                          }
                        }
                        """.trimIndent()
                            .toResponseBody("application/json".toMediaType()),
                    )
                }
                else -> error("Unexpected ApiService call: $method")
            }
        }
        val repository = PatientRepository(apiService, PatientSessionManager())

        val result = repository.updatePrivacyMode(
            displayName = "Paciente",
            anonymous = false,
            useNickname = false,
        )

        val failure = result as PatientDataResult.Failure
        assertEquals(422, failure.statusCode)
        assertEquals("Revisa los datos marcados.", failure.message)
        assertEquals(
            "El nickname no puede superar 80 caracteres.",
            failure.fieldErrors["nickname"],
        )
        assertEquals("Paciente", capturedRequests.single().fullName)
        assertEquals(false, capturedRequests.single().privacyMode)
    }

    @Test
    fun privacyUpdate_isOnlineOnlyAndDoesNotCallApiWithoutConnection() = runTest {
        var apiCalls = 0
        val apiService = apiService { method, _ ->
            apiCalls += 1
            error("Unexpected ApiService call while offline: $method")
        }
        val repository = PatientRepository(
            apiService = apiService,
            sessionManager = PatientSessionManager(),
            networkStatusProvider = NetworkStatusProvider { false },
        )

        val result = repository.updatePrivacyMode(
            displayName = "Luz",
            anonymous = false,
            useNickname = true,
        )

        val failure = result as PatientDataResult.Failure
        assertEquals(0, apiCalls)
        assertTrue(failure.message.contains("conexión"))
    }

    @Test
    fun nicknamePrivacyUpdate_sendsPrivatePayloadWithoutRealName() = runTest {
        val capturedRequests = mutableListOf<UpdatePatientRequest>()
        val apiService = apiService { method, arguments ->
            when (method) {
                "updatePatient" -> {
                    arguments.orEmpty()
                        .filterIsInstance<UpdatePatientRequest>()
                        .single()
                        .also(capturedRequests::add)
                    Response.success(
                        MutationResponse(
                            data = SessionProfileDto(
                                uid = PATIENT_UID,
                                nickname = "Luz",
                                privacyMode = true,
                            ),
                        ),
                    )
                }
                else -> error("Unexpected ApiService call: $method")
            }
        }
        val repository = PatientRepository(apiService, PatientSessionManager())

        val result = repository.updatePrivacyMode(
            displayName = "Luz",
            anonymous = false,
            useNickname = true,
        )

        assertTrue(result is PatientDataResult.Success)
        val request = capturedRequests.single()
        assertNull(request.fullName)
        assertEquals("Luz", request.nickname)
        assertEquals(true, request.privacyMode)
        assertEquals(false, request.isAnonymous)
    }

    @Test
    fun activatingAnonymousModePurgesIdentityAndReplacesSessionDisplayWithoutChangingTokens() = runTest {
        val identityStore = RecordingPatientIdentityStorage(
            originalName = "Nombre Real Guardado",
            nickname = "Alias anterior",
        )
        val sessionManager = PatientSessionManager()
        val originalToken = sessionManager.getIdToken()
        val originalRefreshToken = sessionManager.getRefreshToken()
        val apiService = apiService { method, _ ->
            when (method) {
                "updatePatient" -> Response.success(
                    MutationResponse(
                        data = SessionProfileDto(
                            uid = PATIENT_UID,
                            role = "patient",
                            fullName = "Nombre Real Devuelto por Backend",
                            displayName = "Paciente anónimo",
                            safeDisplayName = "Paciente anónimo",
                            isAnonymous = true,
                            privacyMode = true,
                        ),
                    ),
                )
                else -> error("Unexpected ApiService call: $method")
            }
        }
        val repository = PatientRepository(
            apiService = apiService,
            sessionManager = sessionManager,
            identityStore = identityStore,
        )

        val result = repository.updatePrivacyMode(
            displayName = "Paciente anónimo",
            anonymous = true,
            useNickname = false,
        )

        assertTrue(result is PatientDataResult.Success)
        assertTrue(identityStore.ownerCleared)
        assertNull(identityStore.load(PATIENT_UID).originalName)
        assertNull(identityStore.load(PATIENT_UID).nickname)
        assertEquals("Paciente anónimo", sessionManager.getDisplayName())
        assertEquals(originalToken, sessionManager.getIdToken())
        assertEquals(originalRefreshToken, sessionManager.getRefreshToken())
    }

    @Test
    fun relapseUpdate_usesAllowedSobrietyStartDateField() = runTest {
        val capturedRequests = mutableListOf<UpdatePatientRequest>()
        val apiService = apiService { method, arguments ->
            when (method) {
                "me" -> Response.success(
                    MeResponse(
                        profile = SessionProfileDto(
                            uid = PATIENT_UID,
                            role = "patient",
                            sobrietyStartDate = "2026-01-01T08:00:00-06:00",
                            primaryRisks = emptyList(),
                        ),
                    ),
                )
                "createPatientNote" -> Response.success(
                    StorePatientNoteResponse(message = "Registro guardado correctamente."),
                )
                "updatePatient" -> {
                    arguments.orEmpty()
                        .filterIsInstance<UpdatePatientRequest>()
                        .single()
                        .also(capturedRequests::add)
                    Response.success(MutationResponse<SessionProfileDto>())
                }
                else -> error("Unexpected ApiService call: $method")
            }
        }
        val repository = PatientRepository(apiService, PatientSessionManager())

        val result = repository.saveDailyNote(
            CreatePatientNoteRequest(
                mood = "ansioso",
                moodScore = 3,
                anxietyLevel = 7,
                cravingLevel = 6,
                hadRelapse = true,
                triggers = listOf("estrés"),
            ),
        )

        assertTrue(result is PatientDataResult.Success)
        val update = capturedRequests.single()
        assertNotNull(update.sobrietyStartDate)
        assertEquals(listOf("estrés"), update.primaryRisks)
        val serialized = Gson().toJsonTree(update).asJsonObject
        assertTrue(serialized.has("sobriety_start_date"))
        FORBIDDEN_FIELDS.forEach { field ->
            assertFalse(serialized.has(field))
        }
    }

    @Test
    fun consentUpdate_doesNotHide422BackendError() = runTest {
        val apiService = apiService { method, _ ->
            when (method) {
                "me" -> profileWithSupervisor()
                "consents" -> Response.success(
                    ResourceListResponse(
                        data = listOf(
                            ConsentDto(
                                documentId = "consent-001",
                                patientUid = PATIENT_UID,
                                supervisorUid = SUPERVISOR_UID,
                                explicitConsent = true,
                            ),
                        ),
                    ),
                )
                "updateConsent" -> Response.error<MutationResponse<ConsentDto>>(
                    422,
                    """{"message":"El consentimiento ya tiene ese estado."}"""
                        .toResponseBody("application/json".toMediaType()),
                )
                else -> error("Unexpected ApiService call: $method")
            }
        }
        val repository = PatientRepository(apiService, PatientSessionManager())

        val result = repository.setSupervisorConsent(enabled = false)

        val failure = result as PatientDataResult.Failure
        assertEquals(422, failure.statusCode)
        assertEquals("El consentimiento ya tiene ese estado.", failure.message)
    }

    @Test
    fun unlinkRequest_exposes409BackendConflict() = runTest {
        val apiService = apiService { method, _ ->
            when (method) {
                "me" -> profileWithSupervisor()
                "supervisionRequests" -> Response.success(
                    ResourceListResponse<SupervisionRequestDto>(data = emptyList()),
                )
                "createSupervisionRequest" -> Response.error<MutationResponse<Unit>>(
                    409,
                    """{"message":"Ya existe una solicitud de desvinculación pendiente."}"""
                        .toResponseBody("application/json".toMediaType()),
                )
                else -> error("Unexpected ApiService call: $method")
            }
        }
        val repository = PatientRepository(apiService, PatientSessionManager())

        val result = repository.requestSupervisorUnlink()

        val failure = result as PatientDataResult.Failure
        assertEquals(409, failure.statusCode)
        assertEquals("Ya existe una solicitud de desvinculación pendiente.", failure.message)
    }

    private fun profileWithSupervisor(): Response<MeResponse> {
        return Response.success(
            MeResponse(
                profile = SessionProfileDto(
                    uid = PATIENT_UID,
                    role = "patient",
                    supervisorUid = SUPERVISOR_UID,
                ),
            ),
        )
    }

    private fun apiService(
        handler: (String, Array<out Any?>?) -> Any,
    ): ApiService {
        return Proxy.newProxyInstance(
            ApiService::class.java.classLoader,
            arrayOf(ApiService::class.java),
        ) { _, method, arguments ->
            handler(method.name, arguments)
        } as ApiService
    }

    private class PatientSessionManager : SessionManager {
        private var session = UserSession(
            uid = PATIENT_UID,
            email = "patient@example.com",
            role = "patient",
            token = "header.payload.signature",
            refreshToken = "refresh-token",
            displayName = "Nombre Real Guardado",
        )

        override fun saveSession(session: UserSession) {
            this.session = session
        }

        override fun getIdToken(): String? = session.token

        override fun getRefreshToken(): String? = session.refreshToken

        override fun getUid(): String = session.uid

        override fun getEmail(): String? = session.email

        override fun getRole(): String = session.role

        override fun getDisplayName(): String? = session.displayName

        override fun clear() {
            session = session.copy(token = null, refreshToken = null, displayName = null)
        }
    }

    private class RecordingPatientIdentityStorage(
        originalName: String?,
        nickname: String?,
    ) : PatientIdentityStorage {
        private var identity = PatientIdentity(originalName, nickname)
        var ownerCleared: Boolean = false
            private set

        override fun load(patientUid: String): PatientIdentity = identity

        override fun ensureOriginalName(patientUid: String, originalName: String): String {
            val value = identity.originalName ?: originalName
            identity = identity.copy(originalName = value)
            return value
        }

        override fun saveNickname(patientUid: String, nickname: String) {
            identity = identity.copy(nickname = nickname)
        }

        override fun clearNickname(patientUid: String) {
            identity = identity.copy(nickname = null)
        }

        override fun clearOriginalName(patientUid: String) {
            identity = identity.copy(originalName = null)
        }

        override fun clearOwner(patientUid: String) {
            identity = PatientIdentity(null, null)
            ownerCleared = true
        }
    }

    private companion object {
        const val PATIENT_UID = "patient-001"
        const val SUPERVISOR_UID = "supervisor-001"
        val FORBIDDEN_FIELDS = setOf(
            "risk_level",
            "supervisor_uid",
            "supervision_status",
            "wants_supervision",
            "role",
            "authorized",
            "verified",
        )
    }
}
