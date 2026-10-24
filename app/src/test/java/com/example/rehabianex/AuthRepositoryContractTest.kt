package com.example.rehabianex

import com.example.rehabianex.core.session.SessionManager
import com.example.rehabianex.core.session.UserSession
import com.example.rehabianex.data.model.AuthData
import com.example.rehabianex.data.model.AuthResponse
import com.example.rehabianex.data.model.LoginRequest
import com.example.rehabianex.data.model.PrivacyNoticeReadDto
import com.example.rehabianex.data.model.RegisterRequest
import com.example.rehabianex.data.model.SessionProfileDto
import com.example.rehabianex.data.model.TokenData
import com.example.rehabianex.data.remote.ApiService
import com.example.rehabianex.data.repository.AuthRepository
import com.example.rehabianex.data.repository.AuthResult
import com.example.rehabianex.data.repository.LogoutResult
import java.io.IOException
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
import retrofit2.http.POST

class AuthRepositoryContractTest {
    @Test
    fun loginCarriesAuthenticatedPrivacyStatusIntoTheGateDecision() = runTest {
        val repository = AuthRepository(
            apiService = apiServiceReturning(
                AuthResponse(
                    auth = AuthData(uid = "confirmed-user", role = "patient"),
                    profile = SessionProfileDto(
                        uid = "confirmed-user",
                        role = "patient",
                        privacyNoticeRead = PrivacyNoticeReadDto(
                            privacyNoticeVersion = "2026-10-18",
                            status = "confirmed",
                            isCurrent = true,
                        ),
                    ),
                    tokens = validTokens(),
                ),
            ),
            sessionManager = RecordingSessionManager(),
        )

        val result = repository.login(LoginRequest("synthetic@example.com", "synthetic-password"))

        assertTrue(result is AuthResult.Success)
        val session = (result as AuthResult.Success).session
        assertTrue(session.privacyNoticeStatusChecked)
        assertEquals("2026-10-18", session.privacyNoticeConfirmedVersion)
    }

    @Test
    fun logoutApiContract_usesPostAuthLogout() {
        val method = ApiService::class.java.methods.single { it.name == "logout" }
        val post = method.getAnnotation(POST::class.java)

        assertNotNull(post)
        assertEquals("auth/logout", post?.value)
    }

    @Test
    fun loginNormalPatient_prefersOfficialSafeDisplayNameAndPersistsSession() = runTest {
        val sessionManager = RecordingSessionManager()
        val repository = AuthRepository(
            apiService = apiServiceReturning(
                AuthResponse(
                    message = "Login correcto.",
                    auth = AuthData(
                        uid = "patient-001",
                        role = "patient",
                        email = "auth-patient@example.com",
                        displayName = "Nombre desde auth.display_name",
                        fullName = "Nombre legado desde auth.full_name",
                    ),
                    profile = SessionProfileDto(
                        uid = "patient-001",
                        email = "profile-patient@example.com",
                        fullName = "Nombre preferente del perfil",
                        displayName = "Nombre visible del perfil",
                        safeDisplayName = "Nombre seguro oficial",
                        role = "patient",
                        isAnonymous = false,
                        privacyMode = false,
                    ),
                    tokens = validTokens(),
                ),
            ),
            sessionManager = sessionManager,
        )

        val result = repository.login(
            LoginRequest(
                email = "patient@example.com",
                password = "UnaClaveSegura123!",
            ),
        )

        val success = result as AuthResult.Success
        assertEquals("patient", success.session.role)
        assertEquals("Nombre seguro oficial", success.session.displayName)
        assertEquals("auth-patient@example.com", success.session.email)
        assertEquals(success.session.displayName, sessionManager.savedSession?.displayName)
        assertEquals(success.session.idToken, sessionManager.savedSession?.token)
        assertEquals(success.session.refreshToken, sessionManager.savedSession?.refreshToken)
    }

    @Test
    fun loginAnonymousPatientNeverPersistsRealFullNameAndRequestsLocalPurge() = runTest {
        val sessionManager = RecordingSessionManager()
        var purgedUid: String? = null
        var purgedDisplayName: String? = null
        var purgedAsAnonymous = false
        val repository = AuthRepository(
            apiService = apiServiceReturning(
                AuthResponse(
                    auth = AuthData(
                        uid = "patient-private",
                        role = "patient",
                        fullName = "Nombre Real Registrado",
                        displayName = "Paciente anónimo",
                        safeDisplayName = "Paciente anónimo",
                    ),
                    profile = SessionProfileDto(
                        uid = "patient-private",
                        role = "patient",
                        fullName = "Nombre Real Registrado",
                        displayName = "Paciente anónimo",
                        safeDisplayName = "Paciente anónimo",
                        isAnonymous = true,
                        privacyMode = true,
                    ),
                    tokens = validTokens(),
                ),
            ),
            sessionManager = sessionManager,
            restrictPrivatePatientIdentity = { uid, _, safeName, anonymous ->
                purgedUid = uid
                purgedDisplayName = safeName
                purgedAsAnonymous = anonymous
            },
        )

        val success = repository.login(LoginRequest("private@example.com", "password"))
            as AuthResult.Success

        assertEquals("Paciente anónimo", success.session.displayName)
        assertFalse(success.session.displayName.orEmpty().contains("Nombre Real"))
        assertEquals("Paciente anónimo", sessionManager.savedSession?.displayName)
        assertEquals("patient-private", purgedUid)
        assertEquals("Paciente anónimo", purgedDisplayName)
        assertTrue(purgedAsAnonymous)
        assertEquals(validTokens().idToken, sessionManager.savedSession?.token)
        assertEquals(validTokens().refreshToken, sessionManager.savedSession?.refreshToken)
    }

    @Test
    fun logoutThenLoginAgainKeepsAnonymousDisplayProjection() = runTest {
        val sessionManager = RecordingSessionManager()
        val response = AuthResponse(
            auth = AuthData(
                uid = "patient-private",
                role = "patient",
                fullName = "Nombre Real Registrado",
                safeDisplayName = "Paciente anónimo",
            ),
            profile = SessionProfileDto(
                uid = "patient-private",
                role = "patient",
                fullName = "Nombre Real Registrado",
                safeDisplayName = "Paciente anónimo",
                isAnonymous = true,
                privacyMode = true,
            ),
            tokens = validTokens(),
        )
        val repository = AuthRepository(
            apiService = apiServiceReturning(response),
            sessionManager = sessionManager,
        )

        val first = repository.login(LoginRequest("private@example.com", "password"))
            as AuthResult.Success
        sessionManager.clear()
        val second = repository.login(LoginRequest("private@example.com", "password"))
            as AuthResult.Success

        assertEquals("Paciente anónimo", first.session.displayName)
        assertEquals("Paciente anónimo", second.session.displayName)
        assertEquals("Paciente anónimo", sessionManager.savedSession?.displayName)
        assertFalse(second.session.displayName.orEmpty().contains("Nombre Real"))
    }

    @Test
    fun register422_mapsPrivacyAcceptanceToCheckboxError() = runTest {
        val repository = AuthRepository(
            apiService = apiService { method ->
                when (method) {
                    "register" -> Response.error<AuthResponse>(
                        422,
                        """
                        {
                          "ok": false,
                          "message": "Debes aceptar el aviso de privacidad vigente.",
                          "errors": {
                            "privacy_notice_accepted": ["La aceptación es obligatoria."]
                          }
                        }
                        """.trimIndent().toResponseBody(JSON_MEDIA_TYPE),
                    )
                    else -> error("Unexpected ApiService call: $method")
                }
            },
            sessionManager = RecordingSessionManager(),
        )

        val result = repository.register(
            RegisterRequest(
                email = "paciente@example.com",
                password = "UnaClaveSegura123!",
                fullName = "Paciente Prueba",
                role = "patient",
                phone = "+524497654321",
                gender = "female",
                age = 29,
                privacyNoticeAccepted = true,
                privacyNoticeVersion = "2026-10-18",
            ),
        ) as AuthResult.Failure

        assertEquals(422, result.statusCode)
        assertEquals("Debes aceptar el aviso de privacidad vigente.", result.message)
        assertEquals("La aceptación es obligatoria.", result.fieldErrors["privacy"])
    }

    @Test
    fun loginSupervisor_usesAuthDisplayNameWhenProfileNameIsMissing() = runTest {
        val sessionManager = RecordingSessionManager()
        val repository = AuthRepository(
            apiService = apiServiceReturning(
                AuthResponse(
                    message = "Login correcto.",
                    auth = AuthData(
                        uid = "supervisor-001",
                        role = "supervisor",
                        email = "supervisor@example.com",
                        displayName = "Dra. Laura desde display_name",
                        fullName = "Nombre legado",
                    ),
                    profile = SessionProfileDto(
                        uid = "supervisor-001",
                        role = "supervisor",
                        authorized = true,
                        verified = true,
                    ),
                    tokens = validTokens(),
                ),
            ),
            sessionManager = sessionManager,
        )

        val result = repository.login(
            LoginRequest(
                email = "supervisor@example.com",
                password = "UnaClaveSegura123!",
            ),
        )

        val success = result as AuthResult.Success
        assertEquals("supervisor", success.session.role)
        assertEquals("Dra. Laura desde display_name", success.session.displayName)
        assertEquals(success.session.displayName, sessionManager.savedSession?.displayName)
    }

    @Test
    fun loginAdmin_acceptsRoleAndPersistsAdminSession() = runTest {
        val sessionManager = RecordingSessionManager()
        val repository = AuthRepository(
            apiService = apiServiceReturning(
                AuthResponse(
                    message = "Login correcto.",
                    auth = AuthData(
                        uid = "admin-001",
                        email = "admin@example.com",
                        displayName = "Administración RehabiAnex",
                    ),
                    profile = SessionProfileDto(
                        uid = "admin-001",
                        role = "admin",
                        fullName = "Administrador principal",
                    ),
                    tokens = validTokens(),
                ),
            ),
            sessionManager = sessionManager,
        )

        val result = repository.login(
            LoginRequest(
                email = "admin@example.com",
                password = "UnaClaveSegura123!",
            ),
        )

        val success = result as AuthResult.Success
        assertEquals("admin", success.session.role)
        assertEquals("Administrador principal", success.session.displayName)
        assertEquals("admin", sessionManager.savedSession?.role)
        assertEquals(success.session.idToken, sessionManager.savedSession?.token)
    }

    @Test
    fun login_keepsLegacyAuthFullNameFallback() = runTest {
        val sessionManager = RecordingSessionManager()
        val repository = AuthRepository(
            apiService = apiServiceReturning(
                AuthResponse(
                    auth = AuthData(
                        uid = "patient-legacy",
                        role = "patient",
                        fullName = "Nombre legado",
                    ),
                    tokens = validTokens(),
                ),
            ),
            sessionManager = sessionManager,
        )

        val result = repository.login(LoginRequest("legacy@example.com", "password"))

        val success = result as AuthResult.Success
        assertEquals("Nombre legado", success.session.displayName)
        assertNotNull(sessionManager.savedSession)
    }

    @Test
    fun logout_callsBackendThenClearsUserDataAndSession() = runTest {
        val sessionManager = activeSessionManager()
        var cleanupUid: String? = null
        var cleanupRole: String? = null
        val repository = AuthRepository(
            apiService = apiService { method ->
                when (method) {
                    "logout" -> Response.success(
                        com.example.rehabianex.data.model.MutationResponse<Unit>(
                            ok = true,
                            message = "Sesión cerrada correctamente.",
                        ),
                    )
                    else -> error("Unexpected ApiService call: $method")
                }
            },
            sessionManager = sessionManager,
            clearLocalUserData = { uid, role ->
                cleanupUid = uid
                cleanupRole = role
            },
        )

        val result = repository.logout()

        assertTrue(result.remoteLogoutConfirmed)
        assertNull(result.message)
        assertEquals("patient-logout", cleanupUid)
        assertEquals("patient", cleanupRole)
        assertNull(sessionManager.savedSession)
    }

    @Test
    fun logout_networkFailureStillClearsLocalDataAndTokens() = runTest {
        val sessionManager = activeSessionManager()
        var cleanupCalled = false
        val repository = AuthRepository(
            apiService = apiService { method ->
                when (method) {
                    "logout" -> throw IOException("simulated offline")
                    else -> error("Unexpected ApiService call: $method")
                }
            },
            sessionManager = sessionManager,
            clearLocalUserData = { _, _ -> cleanupCalled = true },
        )

        val result: LogoutResult = repository.logout()

        assertFalse(result.remoteLogoutConfirmed)
        assertEquals(
            "La sesión se cerró en este dispositivo. No fue posible confirmar el cierre remoto.",
            result.message,
        )
        assertTrue(cleanupCalled)
        assertNull(sessionManager.savedSession)
    }

    @Test
    fun logout_cleanupFailureCannotLeaveTokensAccessible() = runTest {
        val sessionManager = activeSessionManager()
        val repository = AuthRepository(
            apiService = apiService { method ->
                when (method) {
                    "logout" -> Response.success(
                        com.example.rehabianex.data.model.MutationResponse<Unit>(ok = true),
                    )
                    else -> error("Unexpected ApiService call: $method")
                }
            },
            sessionManager = sessionManager,
            clearLocalUserData = { _, _ -> error("simulated cleanup failure") },
        )

        val result = repository.logout()

        assertTrue(result.remoteLogoutConfirmed)
        assertNull(sessionManager.savedSession)
    }

    @Test
    fun logout_withPendingNotesRequiresConfirmationBeforeRemoteOrLocalCleanup() = runTest {
        val sessionManager = activeSessionManager()
        var backendCalls = 0
        var cleanupCalls = 0
        val repository = AuthRepository(
            apiService = apiService { method ->
                when (method) {
                    "logout" -> {
                        backendCalls += 1
                        Response.success(
                            com.example.rehabianex.data.model.MutationResponse<Unit>(ok = true),
                        )
                    }
                    else -> error("Unexpected ApiService call: $method")
                }
            },
            sessionManager = sessionManager,
            clearLocalUserData = { _, _ -> cleanupCalls += 1 },
            pendingLocalNotesCount = { 2 },
        )

        val blocked = repository.logout()

        assertTrue(blocked.requiresPendingNotesConfirmation)
        assertEquals(2, blocked.pendingNotesCount)
        assertEquals(0, backendCalls)
        assertEquals(0, cleanupCalls)
        assertNotNull(sessionManager.savedSession)

        val confirmed = repository.logout(confirmPendingNotesDeletion = true)

        assertTrue(confirmed.remoteLogoutConfirmed)
        assertEquals(1, backendCalls)
        assertEquals(1, cleanupCalls)
        assertNull(sessionManager.savedSession)
    }

    @Test
    fun logout_revokesFcmBeforeRemoteLogoutAndSessionCleanup() = runTest {
        val sessionManager = activeSessionManager()
        val events = mutableListOf<String>()
        val repository = AuthRepository(
            apiService = apiService { method ->
                when (method) {
                    "logout" -> {
                        events += "remote-logout"
                        Response.success(
                            com.example.rehabianex.data.model.MutationResponse<Unit>(ok = true),
                        )
                    }
                    else -> error("Unexpected ApiService call: $method")
                }
            },
            sessionManager = sessionManager,
            pendingLocalNotesCount = { 0 },
            revokeFcmToken = {
                assertNotNull(sessionManager.savedSession)
                events += "fcm-revoke"
            },
            clearLocalUserData = { _, _ -> events += "local-cleanup" },
        )

        repository.logout()

        assertEquals(listOf("fcm-revoke", "remote-logout", "local-cleanup"), events)
        assertNull(sessionManager.savedSession)
    }

    @Test
    fun logout_fcmRevocationFailureDoesNotBlockRemoteLogoutOrCleanup() = runTest {
        val sessionManager = activeSessionManager()
        val events = mutableListOf<String>()
        val repository = AuthRepository(
            apiService = apiService { method ->
                when (method) {
                    "logout" -> {
                        events += "remote-logout"
                        Response.success(
                            com.example.rehabianex.data.model.MutationResponse<Unit>(ok = true),
                        )
                    }
                    else -> error("Unexpected ApiService call: $method")
                }
            },
            sessionManager = sessionManager,
            pendingLocalNotesCount = { 0 },
            revokeFcmToken = {
                events += "fcm-revoke-attempt"
                error("simulated FCM failure")
            },
            clearLocalUserData = { _, _ -> events += "local-cleanup" },
        )

        val result = repository.logout()

        assertTrue(result.remoteLogoutConfirmed)
        assertEquals(
            listOf("fcm-revoke-attempt", "remote-logout", "local-cleanup"),
            events,
        )
        assertNull(sessionManager.savedSession)
    }

    @Test
    fun loginWithDifferentUidCleansPreviousOwnerBeforeSavingNewSession() = runTest {
        val sessionManager = activeSessionManager()
        var previousUid: String? = null
        var previousRole: String? = null
        val repository = AuthRepository(
            apiService = apiServiceReturning(
                AuthResponse(
                    auth = AuthData(uid = "patient-new", role = "patient"),
                    tokens = validTokens(),
                ),
            ),
            sessionManager = sessionManager,
            clearPreviousUserData = { uid, role ->
                previousUid = uid
                previousRole = role
                assertEquals("patient-logout", sessionManager.getUid())
            },
        )

        val result = repository.login(LoginRequest("new@example.com", "password"))

        assertTrue(result is AuthResult.Success)
        assertEquals("patient-logout", previousUid)
        assertEquals("patient", previousRole)
        assertEquals("patient-new", sessionManager.getUid())
    }

    @Test
    fun successfulLogin_defersFcmRegistrationUntilPrivacyGateIsComplete() = runTest {
        val sessionManager = RecordingSessionManager()
        var registrationCalls = 0
        val repository = AuthRepository(
            apiService = apiServiceReturning(
                AuthResponse(
                    auth = AuthData(uid = "patient-fcm", role = "patient"),
                    tokens = validTokens(),
                ),
            ),
            sessionManager = sessionManager,
            registerFcmToken = {
                assertEquals("patient-fcm", sessionManager.getUid())
                assertEquals("patient", sessionManager.getRole())
                registrationCalls += 1
            },
        )

        val result = repository.login(LoginRequest("fcm@example.com", "password"))

        assertTrue(result is AuthResult.Success)
        assertEquals(0, registrationCalls)
        repository.registerNotificationsAfterPrivacyGate()
        assertEquals(1, registrationCalls)
    }

    @Test
    fun loginWithDifferentRole_revokesOldOwnerAndDefersNewRegistrationUntilPrivacyGate() = runTest {
        val sessionManager = activeSessionManager()
        val events = mutableListOf<String>()
        val repository = AuthRepository(
            apiService = apiServiceReturning(
                AuthResponse(
                    auth = AuthData(uid = "patient-logout", role = "supervisor"),
                    tokens = validTokens(),
                ),
            ),
            sessionManager = sessionManager,
            revokeFcmToken = {
                assertEquals("patient", sessionManager.getRole())
                events += "revoke-patient"
            },
            clearPreviousUserData = { uid, role ->
                assertEquals("patient-logout", uid)
                assertEquals("patient", role)
                events += "clear-patient"
            },
            registerFcmToken = {
                assertEquals("supervisor", sessionManager.getRole())
                events += "register-supervisor"
            },
        )

        val result = repository.login(LoginRequest("switch@example.com", "password"))

        assertTrue(result is AuthResult.Success)
        assertEquals(
            listOf("revoke-patient", "clear-patient"),
            events,
        )
        repository.registerNotificationsAfterPrivacyGate()
        assertEquals(
            listOf("revoke-patient", "clear-patient", "register-supervisor"),
            events,
        )
    }

    private fun validTokens() = TokenData(
        idToken = "header.payload.signature",
        refreshToken = "refresh-token",
    )

    private fun apiServiceReturning(responseBody: AuthResponse): ApiService {
        return apiService { method ->
            when (method) {
                "login" -> Response.success(responseBody)
                else -> error("Unexpected ApiService call: $method")
            }
        }
    }

    private fun apiService(handler: (String) -> Any): ApiService {
        return Proxy.newProxyInstance(
            ApiService::class.java.classLoader,
            arrayOf(ApiService::class.java),
        ) { _, method, _ ->
            handler(method.name)
        } as ApiService
    }

    private fun activeSessionManager() = RecordingSessionManager().apply {
        saveSession(
            UserSession(
                uid = "patient-logout",
                email = "patient@example.com",
                role = "patient",
                token = "id-token",
                refreshToken = "refresh-token",
                displayName = "Paciente",
            ),
        )
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }

    private class RecordingSessionManager : SessionManager {
        var savedSession: UserSession? = null

        override fun saveSession(session: UserSession) {
            savedSession = session
        }

        override fun getIdToken(): String? = savedSession?.token

        override fun getRefreshToken(): String? = savedSession?.refreshToken

        override fun getUid(): String? = savedSession?.uid

        override fun getEmail(): String? = savedSession?.email

        override fun getRole(): String? = savedSession?.role

        override fun getDisplayName(): String? = savedSession?.displayName

        override fun clear() {
            savedSession = null
        }
    }
}
