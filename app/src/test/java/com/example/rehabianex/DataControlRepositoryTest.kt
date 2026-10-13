package com.example.rehabianex

import com.example.rehabianex.core.session.SessionManager
import com.example.rehabianex.core.session.UserSession
import com.example.rehabianex.data.model.AccountDeletionResponse
import com.example.rehabianex.data.model.AccountDeletionStatusDto
import com.example.rehabianex.data.model.AuthData
import com.example.rehabianex.data.model.AuthResponse
import com.example.rehabianex.data.model.DataControlAccountDeletionDto
import com.example.rehabianex.data.model.DataControlCategoryDto
import com.example.rehabianex.data.model.DataControlProfileRectificationDto
import com.example.rehabianex.data.model.DataControlRectificationDto
import com.example.rehabianex.data.model.DataControlSummaryResponse
import com.example.rehabianex.data.model.TokenData
import com.example.rehabianex.data.remote.ApiService
import com.example.rehabianex.data.repository.DataControlRepository
import com.example.rehabianex.data.repository.DataControlResult
import java.lang.reflect.Proxy
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class DataControlRepositoryTest {
    @Test
    fun summaryPreservesServerInventoryAndRequiresConnectionWhenProfileQueueIsDisabled() = runTest {
        val repository = repository { method, _ ->
            require(method == "dataControlSummary")
            Response.success(
                DataControlSummaryResponse(
                    role = "patient",
                    categories = listOf(
                        DataControlCategoryDto(
                            code = "patient_notes",
                            label = "Registros autorreportados",
                            location = "firestore_and_encrypted_room",
                            exported = true,
                            deletionMode = "record_or_account_request",
                        ),
                    ),
                    rectification = DataControlRectificationDto(
                        profile = DataControlProfileRectificationDto(
                            editableFields = listOf("full_name", "phone"),
                            offlineQueueAllowed = false,
                        ),
                    ),
                    accountDeletion = DataControlAccountDeletionDto(
                        automated = true,
                        confirmationPhrase = "ELIMINAR MI CUENTA",
                    ),
                ),
            )
        }

        val result = repository.loadSummary() as DataControlResult.Success

        assertEquals("patient_notes", result.data.categories.single().code)
        assertTrue(result.data.rectificationRequiresConnection)
        assertTrue(result.data.automatedAccountDeletion)
    }

    @Test
    fun exportAcceptsOnlyStructuredHolderPayload() = runTest {
        val validJson = """{
            "scope":"authenticated_account_holder",
            "sensitivity_notice":"Contiene datos sensibles",
            "identity":{"uid":"synthetic-patient"},
            "profile":{},
            "records":{}
        }""".trimIndent()
        val repository = repository { method, _ ->
            require(method == "exportOwnData")
            Response.success(validJson.toResponseBody(JSON_MEDIA_TYPE))
        }

        val rawResult = repository.exportOwnData()
        assertTrue(
            (rawResult as? DataControlResult.Failure)?.message ?: "resultado esperado",
            rawResult is DataControlResult.Success,
        )
        val result = rawResult as DataControlResult.Success

        assertEquals(validJson, result.data)
    }

    @Test
    fun reauthenticationRejectsAnotherFirebaseIdentityWithoutReplacingSession() = runTest {
        val session = RecordingSessionManager()
        val repository = repository(session) { method, _ ->
            require(method == "login")
            Response.success(
                AuthResponse(
                    auth = AuthData(uid = "different-user", role = "patient"),
                    tokens = TokenData(idToken = "new-token", refreshToken = "new-refresh"),
                ),
            )
        }

        val result = repository.reauthenticate("Synthetic-password-1")

        assertTrue(result is DataControlResult.Failure)
        assertEquals("old-token", session.getIdToken())
        assertEquals(0, session.savedSessions)
    }

    @Test
    fun confirmedDeletionClearsLocalOwnerDataAndSessionOnlyAfterServerBlock() = runTest {
        val session = RecordingSessionManager()
        var clearedOwner: Pair<String?, String?>? = null
        val repository = repository(
            session = session,
            clearLocal = { uid, role -> clearedOwner = uid to role },
        ) { method, _ ->
            require(method == "requestAccountDeletion")
            Response.success(
                202,
                AccountDeletionResponse(
                    ok = true,
                    request = AccountDeletionStatusDto(
                        requestId = "synthetic-request-1",
                        status = "pending_manual_review",
                        requestedAt = "2026-10-12T12:00:00Z",
                    ),
                    syncBlocked = true,
                    localCleanupRequired = true,
                    manualAttentionRequired = true,
                    retentionNotice = "Revisión operativa pendiente.",
                ),
            )
        }

        val result = repository.requestAccountDeletion("ELIMINAR MI CUENTA", true)

        assertTrue(result is DataControlResult.Success)
        assertEquals("synthetic-patient" to "patient", clearedOwner)
        assertTrue(session.cleared)
    }

    @Test
    fun incompleteDeletionResponsePreservesLocalSession() = runTest {
        val session = RecordingSessionManager()
        var localCleanupCalled = false
        val repository = repository(
            session = session,
            clearLocal = { _, _ -> localCleanupCalled = true },
        ) { method, _ ->
            require(method == "requestAccountDeletion")
            Response.success(
                202,
                AccountDeletionResponse(
                    request = AccountDeletionStatusDto(requestId = "synthetic-request-2"),
                    syncBlocked = false,
                    localCleanupRequired = true,
                ),
            )
        }

        val result = repository.requestAccountDeletion("ELIMINAR MI CUENTA", true)

        assertTrue(result is DataControlResult.Failure)
        assertFalse(localCleanupCalled)
        assertFalse(session.cleared)
    }

    private fun repository(
        session: RecordingSessionManager = RecordingSessionManager(),
        clearLocal: suspend (String?, String?) -> Unit = { _, _ -> },
        response: (String, List<Any?>) -> Any,
    ) = DataControlRepository(
        apiService = Proxy.newProxyInstance(
            ApiService::class.java.classLoader,
            arrayOf(ApiService::class.java),
        ) { _, method, arguments -> response(method.name, arguments?.toList().orEmpty()) } as ApiService,
        sessionManager = session,
        clearLocalUserData = clearLocal,
    )

    private class RecordingSessionManager : SessionManager {
        private var session = UserSession(
            uid = "synthetic-patient",
            role = "patient",
            token = "old-token",
            refreshToken = "old-refresh",
            email = "synthetic@example.invalid",
            displayName = "Cuenta sintética",
        )
        var savedSessions = 0
        var cleared = false

        override fun saveSession(session: UserSession) {
            this.session = session
            savedSessions++
        }
        override fun getIdToken(): String? = session.token
        override fun getRefreshToken(): String? = session.refreshToken
        override fun getUid(): String? = session.uid
        override fun getEmail(): String? = session.email
        override fun getRole(): String? = session.role
        override fun getDisplayName(): String? = session.displayName
        override fun clear() {
            cleared = true
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
