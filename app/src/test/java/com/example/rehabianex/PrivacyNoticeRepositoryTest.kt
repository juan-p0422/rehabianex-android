package com.example.rehabianex

import com.example.rehabianex.core.session.SessionManager
import com.example.rehabianex.core.session.UserSession
import com.example.rehabianex.data.local.PrivacyNoticeReadStore
import com.example.rehabianex.data.model.MeResponse
import com.example.rehabianex.data.model.PrivacyNoticeDto
import com.example.rehabianex.data.model.PrivacyNoticeReadDto
import com.example.rehabianex.data.model.PrivacyNoticeReadResponse
import com.example.rehabianex.data.model.PrivacyNoticeResponse
import com.example.rehabianex.data.model.SessionProfileDto
import com.example.rehabianex.data.remote.ApiService
import com.example.rehabianex.data.repository.PrivacyNoticeConfirmResult
import com.example.rehabianex.data.repository.PrivacyNoticeRepository
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class PrivacyNoticeRepositoryTest {
    @Test
    fun authenticatedCurrentConfirmationSkipsRepeatedGateRequests() = runTest {
        val calls = mutableListOf<String>()
        val store = FakePrivacyNoticeReadStore()
        val result = repository(
            apiService = apiService { method ->
                calls += method
                error("Unexpected call: $method")
            },
            store = store,
        ).load(
            requireServerStatus = true,
            authenticatedStatusChecked = true,
            authenticatedConfirmedVersion = CURRENT_VERSION,
        )

        assertTrue(result.accessGranted)
        assertTrue(calls.isEmpty())
        assertEquals(CURRENT_VERSION, store.confirmedVersion(OWNER_A))
    }

    @Test
    fun authenticatedPendingStatusLoadsOnlyNoticeContent() = runTest {
        val calls = mutableListOf<String>()
        val result = repository(
            apiService = apiService { method ->
                calls += method
                when (method) {
                    "privacyNotice" -> Response.success(noticeResponse())
                    else -> error("Unexpected call: $method")
                }
            },
            store = FakePrivacyNoticeReadStore(),
        ).load(
            requireServerStatus = true,
            authenticatedStatusChecked = true,
        )

        assertFalse(result.accessGranted)
        assertEquals(listOf("privacyNotice"), calls)
    }

    @Test
    fun authenticatedConfirmationWithoutCurrentOwnerDoesNotGrantAccess() = runTest {
        val calls = mutableListOf<String>()
        val result = PrivacyNoticeRepository(
            apiService = apiService { method ->
                calls += method
                when (method) {
                    "privacyNotice" -> Response.success(noticeResponse())
                    else -> error("Unexpected call: $method")
                }
            },
            sessionManager = FakeSessionManager(null),
            readStore = FakePrivacyNoticeReadStore(),
            fallbackContent = { "Aviso local completo" },
        ).load(
            requireServerStatus = true,
            authenticatedStatusChecked = true,
            authenticatedConfirmedVersion = CURRENT_VERSION,
        )

        assertFalse(result.accessGranted)
        assertEquals(listOf("privacyNotice"), calls)
    }

    @Test
    fun reopenedConfirmedAccountChecksProfileWithoutDownloadingNoticeAgain() = runTest {
        val calls = mutableListOf<String>()
        val store = FakePrivacyNoticeReadStore().apply {
            saveConfirmedVersion(OWNER_A, CURRENT_VERSION)
        }
        val result = repository(
            apiService = apiService { method ->
                calls += method
                when (method) {
                    "me" -> Response.success(confirmedMeResponse())
                    else -> error("Unexpected call: $method")
                }
            },
            store = store,
        ).load(requireServerStatus = true)

        assertTrue(result.accessGranted)
        assertEquals(listOf("me"), calls)
    }

    @Test
    fun gateLoadsNoticeAndProfileConcurrentlyWithOneRequestEach() = runTest {
        val privacyStarted = CompletableDeferred<Unit>()
        val profileStarted = CompletableDeferred<Unit>()
        var privacyCalls = 0
        var profileCalls = 0
        val apiService = suspendingApiService { method, continuation ->
            when (method) {
                "privacyNotice" -> {
                    privacyCalls += 1
                    privacyStarted.complete(Unit)
                    backgroundScope.launch {
                        profileStarted.await()
                        continuation.resume(Response.success(noticeResponse()))
                    }
                    COROUTINE_SUSPENDED
                }
                "me" -> {
                    profileCalls += 1
                    profileStarted.complete(Unit)
                    backgroundScope.launch {
                        privacyStarted.await()
                        continuation.resume(Response.success(confirmedMeResponse()))
                    }
                    COROUTINE_SUSPENDED
                }
                else -> error("Unexpected call: $method")
            }
        }

        val result = withTimeout(1_000) {
            repository(apiService, FakePrivacyNoticeReadStore()).load(requireServerStatus = true)
        }

        assertTrue(result.accessGranted)
        assertEquals(1, privacyCalls)
        assertEquals(1, profileCalls)
    }

    @Test
    fun legacyProfileWithoutConfirmationRequiresTheNoticeAndClearsStaleEvidence() = runTest {
        val store = FakePrivacyNoticeReadStore().apply {
            saveConfirmedVersion(OWNER_A, CURRENT_VERSION)
        }
        val repository = repository(
            apiService = apiService { method ->
                when (method) {
                    "privacyNotice" -> Response.success(noticeResponse())
                    "me" -> Response.success(MeResponse(profile = SessionProfileDto()))
                    else -> error("Unexpected call: $method")
                }
            },
            store = store,
        )

        val result = repository.load(requireServerStatus = true)

        assertTrue(result.serverStatusChecked)
        assertFalse(result.accessGranted)
        assertNull(store.confirmedVersion(OWNER_A))
    }

    @Test
    fun currentServerConfirmationSavesEvidenceAndGrantsAccess() = runTest {
        val store = FakePrivacyNoticeReadStore()
        val repository = repository(
            apiService = apiService { method ->
                when (method) {
                    "privacyNotice" -> Response.success(noticeResponse())
                    "me" -> Response.success(confirmedMeResponse())
                    else -> error("Unexpected call: $method")
                }
            },
            store = store,
        )

        val result = repository.load(requireServerStatus = true)

        assertTrue(result.serverStatusChecked)
        assertTrue(result.currentReadConfirmedByServer)
        assertTrue(result.accessGranted)
        assertEquals(CURRENT_VERSION, store.confirmedVersion(OWNER_A))
    }

    @Test
    fun currentCachedConfirmationAllowsOfflineAccessForSameOwner() = runTest {
        val store = FakePrivacyNoticeReadStore().apply {
            saveConfirmedVersion(OWNER_A, CURRENT_VERSION)
        }
        val repository = repository(
            apiService = apiService { throw IllegalStateException("offline") },
            store = store,
        )

        val result = repository.load(requireServerStatus = true)

        assertFalse(result.serverStatusChecked)
        assertFalse(result.currentReadConfirmedByServer)
        assertTrue(result.currentReadConfirmedByLocalEvidence)
        assertTrue(result.accessGranted)
        assertTrue(result.notice.content!!.contains("Aviso local"))
    }

    @Test
    fun missingOldOrOtherOwnerEvidenceDoesNotAllowOfflineAccess() = runTest {
        val store = FakePrivacyNoticeReadStore().apply {
            saveConfirmedVersion(OWNER_A, "2026-08-01")
            saveConfirmedVersion(OWNER_B, CURRENT_VERSION)
        }
        val repository = repository(
            apiService = apiService { throw IllegalStateException("offline") },
            store = store,
        )

        val result = repository.load(requireServerStatus = true)

        assertFalse(result.accessGranted)
        assertFalse(result.currentReadConfirmedByLocalEvidence)
        assertEquals(CURRENT_VERSION, store.confirmedVersion(OWNER_B))
    }

    @Test
    fun transientServerFailureKeepsValidEvidenceAndAllowsOfflineAccess() = runTest {
        val store = FakePrivacyNoticeReadStore().apply {
            saveConfirmedVersion(OWNER_A, CURRENT_VERSION)
        }
        val repository = repository(
            apiService = apiService { method ->
                when (method) {
                    "privacyNotice" -> Response.success(noticeResponse())
                    "me" -> Response.error<MeResponse>(
                        503,
                        "{}".toResponseBody("application/json".toMediaType()),
                    )
                    else -> error("Unexpected call: $method")
                }
            },
            store = store,
        )

        val result = repository.load(requireServerStatus = true)

        assertTrue(result.accessGranted)
        assertEquals(CURRENT_VERSION, store.confirmedVersion(OWNER_A))
    }

    @Test
    fun rateLimitKeepsValidEvidenceAndAllowsOfflineAccess() = runTest {
        val store = FakePrivacyNoticeReadStore().apply {
            saveConfirmedVersion(OWNER_A, CURRENT_VERSION)
        }
        val repository = repository(
            apiService = apiService { method ->
                when (method) {
                    "privacyNotice" -> Response.success(noticeResponse())
                    "me" -> Response.error<MeResponse>(
                        429,
                        "{}".toResponseBody("application/json".toMediaType()),
                    )
                    else -> error("Unexpected call: $method")
                }
            },
            store = store,
        )

        val result = repository.load(requireServerStatus = true)

        assertTrue(result.accessGranted)
        assertEquals(CURRENT_VERSION, store.confirmedVersion(OWNER_A))
    }

    @Test
    fun authoritativeUnauthorizedResponseClearsEvidenceAndDeniesAccess() = runTest {
        val store = FakePrivacyNoticeReadStore().apply {
            saveConfirmedVersion(OWNER_A, CURRENT_VERSION)
        }
        val repository = repository(
            apiService = apiService { method ->
                when (method) {
                    "privacyNotice" -> Response.success(noticeResponse())
                    "me" -> Response.error<MeResponse>(
                        401,
                        "{}".toResponseBody("application/json".toMediaType()),
                    )
                    else -> error("Unexpected call: $method")
                }
            },
            store = store,
        )

        val result = repository.load(requireServerStatus = true)

        assertFalse(result.accessGranted)
        assertTrue(result.serverStatusChecked)
        assertNull(store.confirmedVersion(OWNER_A))
    }

    @Test
    fun newerRemoteNoticeInvalidatesOldEvidenceBeforeTransientProfileFailure() = runTest {
        val store = FakePrivacyNoticeReadStore().apply {
            saveConfirmedVersion(OWNER_A, CURRENT_VERSION)
        }
        val newVersion = "2026-11-01"
        val repository = repository(
            apiService = apiService { method ->
                when (method) {
                    "privacyNotice" -> Response.success(noticeResponse(newVersion))
                    "me" -> Response.error<MeResponse>(
                        503,
                        "{}".toResponseBody("application/json".toMediaType()),
                    )
                    else -> error("Unexpected call: $method")
                }
            },
            store = store,
        )

        val result = repository.load(requireServerStatus = true)

        assertFalse(result.accessGranted)
        assertNull(store.confirmedVersion(OWNER_A))
    }

    @Test
    fun accountChangeDuringRefreshDoesNotAttributeConfirmationToEitherOwner() = runTest {
        val store = FakePrivacyNoticeReadStore()
        val sessionManager = FakeSessionManager(OWNER_A)
        val repository = PrivacyNoticeRepository(
            apiService = apiService { method ->
                when (method) {
                    "privacyNotice" -> Response.success(noticeResponse())
                    "me" -> {
                        sessionManager.currentUid = OWNER_B
                        Response.success(confirmedMeResponse())
                    }
                    else -> error("Unexpected call: $method")
                }
            },
            sessionManager = sessionManager,
            readStore = store,
            fallbackContent = { "Aviso local completo" },
        )

        val result = repository.load(requireServerStatus = true)

        assertFalse(result.accessGranted)
        assertNull(store.confirmedVersion(OWNER_A))
        assertNull(store.confirmedVersion(OWNER_B))
    }

    @Test
    fun failedTemporaryConfirmationDoesNotEraseExistingEvidence() = runTest {
        val store = FakePrivacyNoticeReadStore().apply {
            saveConfirmedVersion(OWNER_A, CURRENT_VERSION)
        }
        val repository = repository(
            apiService = apiService { method ->
                if (method == "confirmPrivacyNoticeRead") {
                    Response.error<PrivacyNoticeReadResponse>(
                        503,
                        "{\"message\":\"Fallo sintético\"}"
                            .toResponseBody("application/json".toMediaType()),
                    )
                } else {
                    error("Unexpected call: $method")
                }
            },
            store = store,
        )

        val result = repository.confirmRead(CURRENT_VERSION)

        assertTrue(result is PrivacyNoticeConfirmResult.Failure)
        assertEquals(CURRENT_VERSION, store.confirmedVersion(OWNER_A))
    }

    @Test
    fun rateLimitedConfirmationDoesNotEraseExistingEvidence() = runTest {
        val store = FakePrivacyNoticeReadStore().apply {
            saveConfirmedVersion(OWNER_A, CURRENT_VERSION)
        }
        val repository = repository(
            apiService = apiService { method ->
                if (method == "confirmPrivacyNoticeRead") {
                    Response.error<PrivacyNoticeReadResponse>(
                        429,
                        "{\"message\":\"Espera antes de reintentar\"}"
                            .toResponseBody("application/json".toMediaType()),
                    )
                } else {
                    error("Unexpected call: $method")
                }
            },
            store = store,
        )

        val result = repository.confirmRead(CURRENT_VERSION)

        assertTrue(result is PrivacyNoticeConfirmResult.Failure)
        assertEquals(CURRENT_VERSION, store.confirmedVersion(OWNER_A))
    }

    @Test
    fun successfulConfirmationRequiresConfirmedBodyAndPersistsEvidence() = runTest {
        val store = FakePrivacyNoticeReadStore()
        val repository = repository(
            apiService = apiService { method ->
                if (method == "confirmPrivacyNoticeRead") {
                    Response.success(
                        PrivacyNoticeReadResponse(
                            ok = true,
                            confirmation = PrivacyNoticeReadDto(
                                privacyNoticeVersion = CURRENT_VERSION,
                                status = "confirmed",
                            ),
                        ),
                    )
                } else {
                    error("Unexpected call: $method")
                }
            },
            store = store,
        )

        assertTrue(repository.confirmRead(CURRENT_VERSION) is PrivacyNoticeConfirmResult.Success)
        assertEquals(CURRENT_VERSION, store.confirmedVersion(OWNER_A))
    }

    @Test
    fun successfulHttpResponseWithDifferentVersionDoesNotGrantAccess() = runTest {
        val store = FakePrivacyNoticeReadStore().apply {
            saveConfirmedVersion(OWNER_A, CURRENT_VERSION)
        }
        val repository = repository(
            apiService = apiService { method ->
                if (method == "confirmPrivacyNoticeRead") {
                    Response.success(
                        PrivacyNoticeReadResponse(
                            ok = true,
                            confirmation = PrivacyNoticeReadDto(
                                privacyNoticeVersion = "2026-08-01",
                                status = "confirmed",
                            ),
                        ),
                    )
                } else {
                    error("Unexpected call: $method")
                }
            },
            store = store,
        )

        assertTrue(repository.confirmRead(CURRENT_VERSION) is PrivacyNoticeConfirmResult.Failure)
        assertNull(store.confirmedVersion(OWNER_A))
    }

    private fun repository(
        apiService: ApiService,
        store: PrivacyNoticeReadStore,
        uid: String = OWNER_A,
    ) = PrivacyNoticeRepository(
        apiService = apiService,
        sessionManager = FakeSessionManager(uid),
        readStore = store,
        fallbackContent = { "Aviso local completo" },
    )

    private fun confirmedMeResponse() = MeResponse(
        profile = SessionProfileDto(
            privacyNoticeRead = PrivacyNoticeReadDto(
                privacyNoticeVersion = CURRENT_VERSION,
                confirmedAt = "2026-10-30T12:00:00-06:00",
                status = "confirmed",
                isCurrent = true,
            ),
        ),
    )

    private fun noticeResponse(version: String = CURRENT_VERSION) = PrivacyNoticeResponse(
        ok = true,
        notice = PrivacyNoticeDto(
            title = "Aviso",
            version = version,
            publishedOn = version,
            content = "Contenido",
        ),
    )

    private fun apiService(handler: (String) -> Any): ApiService = Proxy.newProxyInstance(
        ApiService::class.java.classLoader,
        arrayOf(ApiService::class.java),
    ) { _, method, _ -> handler(method.name) } as ApiService

    private fun suspendingApiService(
        handler: (String, Continuation<Any?>) -> Any?,
    ): ApiService = Proxy.newProxyInstance(
        ApiService::class.java.classLoader,
        arrayOf(ApiService::class.java),
    ) { _, method, arguments ->
        @Suppress("UNCHECKED_CAST")
        val continuation = arguments?.lastOrNull() as? Continuation<Any?>
            ?: error("Missing continuation for ${method.name}")
        handler(method.name, continuation)
    } as ApiService

    private class FakePrivacyNoticeReadStore : PrivacyNoticeReadStore {
        private val versions = mutableMapOf<String, String>()

        override fun confirmedVersion(uid: String): String? = versions[uid]

        override fun saveConfirmedVersion(uid: String, version: String) {
            versions[uid] = version
        }

        override fun clearOwner(uid: String) {
            versions.remove(uid)
        }
    }

    private class FakeSessionManager(var currentUid: String?) : SessionManager {
        override fun saveSession(session: UserSession) = Unit
        override fun getIdToken(): String? = "synthetic-token"
        override fun getRefreshToken(): String? = null
        override fun getUid(): String? = currentUid
        override fun getEmail(): String? = null
        override fun getRole(): String? = "patient"
        override fun getDisplayName(): String? = null
        override fun clear() = Unit
    }

    private companion object {
        const val CURRENT_VERSION = "2026-10-30"
        const val OWNER_A = "synthetic-owner-a"
        const val OWNER_B = "synthetic-owner-b"
    }
}
