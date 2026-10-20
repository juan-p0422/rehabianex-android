package com.example.rehabianex

import com.google.gson.Gson
import com.example.rehabianex.core.session.SessionManager
import com.example.rehabianex.core.session.UserSession
import com.example.rehabianex.core.validation.MAX_AI_CHAT_QUESTION_LENGTH
import com.example.rehabianex.core.validation.preserveAiChatDraft
import com.example.rehabianex.core.validation.sanitizeAiChatQuestion
import com.example.rehabianex.core.network.ApiClient
import com.example.rehabianex.data.model.AuthResponse
import com.example.rehabianex.data.model.MeResponse
import com.example.rehabianex.data.model.SessionProfileDto
import com.example.rehabianex.data.model.SupervisedPatientsResponse
import com.example.rehabianex.data.model.SupervisorChatEligibilityResponse
import com.example.rehabianex.data.model.SupervisorChatRequest
import com.example.rehabianex.data.model.SupervisorChatResponse
import com.example.rehabianex.data.model.SupervisorChatContext
import com.example.rehabianex.data.model.SupervisorChatPeriod
import com.example.rehabianex.data.model.SupervisorChatQuota
import com.example.rehabianex.data.model.SupervisorPatientDto
import com.example.rehabianex.data.model.SupervisorPatientPermissionsDto
import com.example.rehabianex.data.model.TokenData
import com.example.rehabianex.data.remote.ApiService
import com.example.rehabianex.data.repository.SupervisorDataResult
import com.example.rehabianex.data.repository.SupervisorRepository
import java.lang.reflect.Proxy
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class SupervisorAiChatContractTest {
    @Test
    fun draftPolicyPreservesSpacesAccentsPunctuationAndMiddleEdits() {
        val phrase = "Resume mis registros de esta semana"
        val draftWithComposingSpace = preserveAiChatDraft("Resume ")
        val editedInTheMiddle = preserveAiChatDraft("Resume mis registros, ¿sí? de esta semana")
        val pasted = preserveAiChatDraft("Ánimo, sueño y craving: mañana; niño/niña.")

        assertEquals("Resume ", draftWithComposingSpace)
        assertEquals(phrase, preserveAiChatDraft(phrase))
        assertEquals("Resume mis registros, ¿sí? de esta semana", editedInTheMiddle)
        assertEquals("Ánimo, sueño y craving: mañana; niño/niña.", pasted)
    }

    @Test
    fun draftPolicyKeepsExistingLengthLimitWithoutNormalizingDuringEntry() {
        val overLimit = "x".repeat(MAX_AI_CHAT_QUESTION_LENGTH + 2)

        assertEquals(MAX_AI_CHAT_QUESTION_LENGTH + 1, preserveAiChatDraft(overLimit).length)
        assertEquals("   ", preserveAiChatDraft("   "))
        assertEquals("", "   ".sanitizeAiChatQuestion())
    }

    @Test
    fun questionPolicySanitizesAndPreservesOverLimitDetection() {
        val sanitized = "  <b>Pregunta\u0000   sensible</b>  ".sanitizeAiChatQuestion()

        assertEquals("Pregunta sensible", sanitized)
        assertTrue("x".repeat(MAX_AI_CHAT_QUESTION_LENGTH + 1).sanitizeAiChatQuestion().length > MAX_AI_CHAT_QUESTION_LENGTH)
    }

    @Test
    fun questionPolicyPreservesNormalSpacesAndAccents() {
        val sanitized = "¿Qué cambió en la última sesión de apoyo?".sanitizeAiChatQuestion()

        assertEquals("¿Qué cambió en la última sesión de apoyo?", sanitized)
    }

    @Test
    fun questionPolicyRedactsCommonSensitiveDataBeforeNetwork() {
        val sanitized = (
            "Contacta a persona@example.com, teléfono +52 449 123 4567, " +
                "UID abcdefghijklmnop12345678 en avenida Reforma 123"
            ).sanitizeAiChatQuestion()

        assertFalse(sanitized.contains("persona@example.com"))
        assertFalse(sanitized.contains("449 123 4567"))
        assertFalse(sanitized.contains("abcdefghijklmnop12345678"))
        assertFalse(sanitized.contains("Reforma 123"))
        assertTrue(sanitized.contains("[dato protegido]"))
    }

    @Test
    fun unauthorizedSupervisorCannotLoadPatientContext() = runTest {
        val calls = mutableListOf<String>()
        val repository = repository { method, _ ->
            calls += method
            when (method) {
                "me" -> me(authorized = false)
                else -> error("Unexpected call: $method")
            }
        }

        val result = repository.loadChatData() as SupervisorDataResult.Success

        assertFalse(result.data.authorized)
        assertEquals(0, result.data.patientsCount)
        assertEquals(listOf("me"), calls)
    }

    @Test
    fun chatEntryUsesDedicatedEligibilityEndpoint() = runTest {
        val calls = mutableListOf<String>()
        val handler = activeChatHandler()
        val repository = repository { method, args ->
            calls += method
            handler(method, args)
        }

        val result = repository.loadChatData() as SupervisorDataResult.Success

        assertTrue(result.data.authorized)
        assertEquals(1, result.data.patientsCount)
        assertEquals(listOf("me", "supervisorChatEligibility"), calls)
        assertFalse("patientNotesByPatient" in calls)
        assertFalse("supervisedPatients" in calls)
        assertFalse("supervisionRequests" in calls)
        assertFalse("consents" in calls)
    }

    @Test
    fun missingEligibilityRouteFallsBackToPatientPermissionProjection() = runTest {
        val calls = mutableListOf<String>()
        val repository = repository { method, _ ->
            calls += method
            when (method) {
                "me" -> me()
                "supervisorChatEligibility" -> Response.error<SupervisorChatEligibilityResponse>(
                    404,
                    "{}".toResponseBody("application/json".toMediaType()),
                )
                "supervisedPatients" -> Response.success(
                    SupervisedPatientsResponse(
                        patients = listOf(
                            patient(PATIENT_ALLOWED, aiChatSummary = true),
                            patient(PATIENT_DENIED, aiChatSummary = false),
                        ),
                    ),
                )
                else -> error("Unexpected call: $method")
            }
        }

        val result = repository.loadChatData() as SupervisorDataResult.Success

        assertEquals(1, result.data.patientsCount)
        assertEquals(
            listOf("me", "supervisorChatEligibility", "supervisedPatients"),
            calls,
        )
        assertFalse("consents" in calls)
    }

    @Test
    fun eachQuestionCallsOnlyChatEndpointWithoutEligibilityPreflight() = runTest {
        val calls = mutableListOf<String>()
        val handler = activeChatHandler()
        val repository = repository { method, args ->
            calls += method
            handler(method, args)
        }
        repository.loadChatData()
        calls.clear()

        val first = repository.askSupervisorChat("Primera pregunta de seguimiento")
        val second = repository.askSupervisorChat("Segunda pregunta de seguimiento")

        assertTrue(first is SupervisorDataResult.Success)
        assertTrue(second is SupervisorDataResult.Success)
        assertEquals(listOf("supervisorChat", "supervisorChat"), calls)
    }

    @Test
    fun emptyAndExcessiveQuestionsAreRejectedBeforeChatRequest() = runTest {
        var chatCalls = 0
        val repository = repository { method, _ ->
            if (method == "supervisorChat") chatCalls += 1
            when (method) {
                "me" -> me()
                else -> error("Unexpected call: $method")
            }
        }

        val empty = repository.askSupervisorChat(" \u0000 <> ") as SupervisorDataResult.Failure
        val long = repository.askSupervisorChat(
            "x".repeat(MAX_AI_CHAT_QUESTION_LENGTH + 1),
        ) as SupervisorDataResult.Failure

        assertEquals(422, empty.statusCode)
        assertEquals(422, long.statusCode)
        assertEquals(0, chatCalls)
    }

    @Test
    fun sendsSanitizedQuestionWithoutAppendingLocalPatientIdentifiers() = runTest {
        var sentRequest: SupervisorChatRequest? = null
        val repository = repository(
            activeChatHandler { request ->
                sentRequest = request
                Response.success(SupervisorChatResponse(answer = "Seguimiento sugerido."))
            },
        )

        val result = repository.askSupervisorChat("  <b>Revisar\u0000   ansiedad</b>  ")

        assertTrue(result is SupervisorDataResult.Success)
        assertEquals("Revisar ansiedad", sentRequest?.question)
        assertFalse(sentRequest?.question.orEmpty().contains(PATIENT_ALLOWED))
        assertFalse(sentRequest?.question.orEmpty().contains(PATIENT_DENIED))
    }

    @Test
    fun normalPhraseReachesNetworkDtoWithInternalSpacesUnchanged() = runTest {
        var sentRequest: SupervisorChatRequest? = null
        val repository = repository(
            activeChatHandler { request ->
                sentRequest = request
                Response.success(SupervisorChatResponse(answer = "Respuesta local sintética."))
            },
        )
        val phrase = "Resume mis registros de esta semana"

        val result = repository.askSupervisorChat(phrase, mode = "local")

        assertTrue(result is SupervisorDataResult.Success)
        assertEquals(phrase, sentRequest?.question)
    }

    @Test
    fun requestContainsOnlyGuaranteedFieldsAndSupportsAiAndLocalModes() = runTest {
        val requests = mutableListOf<SupervisorChatRequest>()
        val repository = repository(
            activeChatHandler { request ->
                requests += request
                Response.success(
                    SupervisorChatResponse(answer = "Respuesta segura.", mode = request.mode),
                )
            },
        )

        val ai = repository.askSupervisorChat("Pregunta para modo IA", mode = "ai")
        val local = repository.askSupervisorChat("Pregunta para modo local", mode = "local")

        assertTrue(ai is SupervisorDataResult.Success)
        assertTrue(local is SupervisorDataResult.Success)
        assertEquals(listOf("ai", "local"), requests.map { it.mode })
        assertEquals(
            setOf("supervisor_uid", "question", "mode"),
            Gson().toJsonTree(requests.first()).asJsonObject.keySet(),
        )
    }

    @Test
    fun fallbackLocalReturnsAnswerAndMarksItsOrigin() = runTest {
        val repository = repository(
            activeChatHandler {
                Response.success(
                    SupervisorChatResponse(
                        answer = "Respuesta local segura.",
                        mode = "fallback_local",
                    ),
                )
            },
        )

        val result = repository.askSupervisorChat("Pregunta de seguimiento", mode = "ai")
            as SupervisorDataResult.Success

        assertEquals("fallback_local", result.data.responseMode)
        assertTrue(result.data.text.contains("Respuesta local segura"))
    }

    @Test
    fun responseKeepsOptionalPeriodDataLimitsAndPerSupervisorQuota() = runTest {
        val repository = repository(
            activeChatHandler {
                Response.success(
                    SupervisorChatResponse(
                        answer = "Resumen seguro.",
                        mode = "ai",
                        context = SupervisorChatContext(
                            period = SupervisorChatPeriod(
                                startDate = "2026-10-06",
                                endDate = "2026-10-12",
                                timezone = "America/Mexico_City",
                                label = "Últimos 7 días naturales",
                            ),
                            dataLimits = listOf("Solo hay dos registros autorreportados."),
                        ),
                        quota = SupervisorChatQuota(limit = 50, remaining = 49),
                    ),
                )
            },
        )

        val result = repository.askSupervisorChat("Resume los registros de esta semana")
            as SupervisorDataResult.Success

        assertEquals("2026-10-06 a 2026-10-12", result.data.periodLabel)
        assertEquals(listOf("Solo hay dos registros autorreportados."), result.data.dataLimits)
        assertEquals(49, result.data.quotaRemaining)
        assertEquals(50, result.data.quotaLimit)
    }

    @Test
    fun mapsChatHttpErrorsAndEmptyResponse() = runTest {
        val expectedMessages = mapOf(
            403 to "permiso",
            409 to "pacientes",
            429 to "límite",
            500 to "temporalmente",
            502 to "temporalmente",
        )

        expectedMessages.forEach { (status, expectedText) ->
            val repository = repository(
                activeChatHandler {
                    Response.error<SupervisorChatResponse>(
                        status,
                        "{}".toResponseBody("application/json".toMediaType()),
                    )
                },
            )
            val failure = repository.askSupervisorChat("Pregunta de seguimiento") as SupervisorDataResult.Failure
            assertEquals(status, failure.statusCode)
            assertTrue(failure.message.contains(expectedText, ignoreCase = true))
        }

        val emptyRepository = repository(
            activeChatHandler {
                Response.success(SupervisorChatResponse(answer = "   "))
            },
        )
        val empty = emptyRepository.askSupervisorChat("Pregunta de seguimiento") as SupervisorDataResult.Failure
        assertTrue(empty.message.contains("no devolvió una respuesta"))
    }

    @Test
    fun final401AfterRefreshShowsExpiredSessionMessage() = runTest {
        var chatCalls = 0
        val baseHandler = activeChatHandler {
            chatCalls += 1
            Response.error<SupervisorChatResponse>(
                401,
                "{}".toResponseBody("application/json".toMediaType()),
            )
        }
        val repository = repository { method, args ->
            if (method == "refresh") {
                Response.success(
                    AuthResponse(
                        tokens = TokenData(
                            idToken = "renewed.header.signature",
                            refreshToken = "renewed-refresh-token",
                        ),
                    ),
                )
            } else {
                baseHandler(method, args)
            }
        }

        val failure = repository.askSupervisorChat("Pregunta de seguimiento") as SupervisorDataResult.Failure

        assertEquals(2, chatCalls)
        assertEquals(401, failure.statusCode)
        assertTrue(failure.message.contains("sesión expiró"))
    }

    @Test
    fun timeoutShowsFriendlyMessage() = runTest {
        val repository = repository(
            activeChatHandler {
                throw SocketTimeoutException("sensitive timeout detail")
            },
        )

        val failure = repository.askSupervisorChat("Pregunta de seguimiento") as SupervisorDataResult.Failure

        assertTrue(failure.message.contains("tardó demasiado"))
        assertFalse(failure.message.contains("sensitive"))
    }

    @Test
    fun offlineModeRequiresBackendAndNeverGeneratesLocalAnswer() = runTest {
        var chatCalls = 0
        val repository = repository(
            activeChatHandler {
                chatCalls += 1
                throw IOException("sensitive network detail")
            },
        )

        val failure = repository.askSupervisorChat("Pregunta de seguimiento", mode = "local")
            as SupervisorDataResult.Failure

        assertEquals(1, chatCalls)
        assertTrue(failure.message.contains("requiere conexión"))
        assertFalse(failure.message.contains("sensitive"))
    }

    @Test
    fun offlineEntryExplainsThatChatRequiresConnection() = runTest {
        val repository = repository { _, _ ->
            throw IOException("sensitive network detail")
        }

        val failure = repository.loadChatData() as SupervisorDataResult.Failure

        assertTrue(failure.message.contains("requiere conexión"))
        assertFalse(failure.message.contains("sensitive"))
    }

    @Test
    fun networkLoggingNeverIncludesChatBodies() {
        val debugClient = ApiClient.createOkHttpClient(TestSessionManager(), isDebug = true)
        val releaseClient = ApiClient.createOkHttpClient(TestSessionManager(), isDebug = false)
        val debugLogger = debugClient.interceptors
            .filterIsInstance<HttpLoggingInterceptor>()
            .single()

        assertEquals(HttpLoggingInterceptor.Level.BASIC, debugLogger.level)
        assertTrue(releaseClient.interceptors.none { it is HttpLoggingInterceptor })
    }

    private fun activeChatHandler(
        chatResponse: (SupervisorChatRequest) -> Any = {
            Response.success(SupervisorChatResponse(answer = "Respuesta segura."))
        },
    ): (String, Array<out Any?>?) -> Any {
        return { method, args ->
            when (method) {
                "me" -> me()
                "supervisorChatEligibility" -> Response.success(
                    SupervisorChatEligibilityResponse(
                        ok = true,
                        eligible = true,
                        eligiblePatientsCount = 1,
                        requiredScope = "ai_chat_summary",
                    ),
                )
                "supervisorChat" -> chatResponse(args?.first() as SupervisorChatRequest)
                else -> error("Unexpected call: $method")
            }
        }
    }

    private fun repository(
        handler: (String, Array<out Any?>?) -> Any,
    ): SupervisorRepository {
        val api = Proxy.newProxyInstance(
            ApiService::class.java.classLoader,
            arrayOf(ApiService::class.java),
        ) { _, method, args -> handler(method.name, args) } as ApiService
        return SupervisorRepository(api, TestSessionManager())
    }

    private fun me(authorized: Boolean = true): Response<MeResponse> {
        return Response.success(
            MeResponse(
                profile = SessionProfileDto(
                    uid = SUPERVISOR_UID,
                    role = "supervisor",
                    status = "active",
                    authorized = authorized,
                    verified = authorized,
                ),
            ),
        )
    }

    private fun patient(patientUid: String, aiChatSummary: Boolean): SupervisorPatientDto {
        return SupervisorPatientDto(
            uid = patientUid,
            permissions = SupervisorPatientPermissionsDto(
                aiChatSummary = aiChatSummary,
            ),
        )
    }

    private class TestSessionManager : SessionManager {
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
        const val PATIENT_ALLOWED = "patient-ai-allowed"
        const val PATIENT_DENIED = "patient-ai-denied"
    }
}
