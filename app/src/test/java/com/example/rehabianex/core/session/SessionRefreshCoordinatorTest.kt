package com.example.rehabianex.core.session

import com.example.rehabianex.data.model.AuthResponse
import com.example.rehabianex.data.model.TokenData
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class SessionRefreshCoordinatorTest {
    @Test
    fun `concurrent unauthorized requests share one refresh`() = runTest {
        val session = FakeSessionManager()
        val refreshStarted = CompletableDeferred<Unit>()
        val releaseRefresh = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val coordinator = SessionRefreshCoordinator(
            sessionManager = session,
            refreshCall = {
                calls.incrementAndGet()
                refreshStarted.complete(Unit)
                releaseRefresh.await()
                Response.success(
                    AuthResponse(
                        tokens = TokenData(
                            idToken = "synthetic-id-token-2",
                            refreshToken = "synthetic-refresh-token-2",
                        ),
                    ),
                )
            },
            testOnly = Unit,
        )

        val first = async {
            coordinator.refreshAfterUnauthorized("synthetic-id-token-1")
        }
        refreshStarted.await()
        val second = async {
            coordinator.refreshAfterUnauthorized("synthetic-id-token-1")
        }
        runCurrent()
        releaseRefresh.complete(Unit)

        assertSame(SessionRefreshResult.Success, first.await())
        assertSame(SessionRefreshResult.Success, second.await())
        assertEquals(1, calls.get())
        assertEquals("synthetic-id-token-2", session.getIdToken())
        assertEquals("synthetic-refresh-token-2", session.getRefreshToken())
    }

    @Test
    fun `provider unauthorized marks session for invalidation`() = runTest {
        val session = FakeSessionManager()
        val coordinator = SessionRefreshCoordinator(
            sessionManager = session,
            refreshCall = { Response.error(401, "{}".toResponseBody()) },
            testOnly = Unit,
        )

        val result = coordinator.refreshAfterUnauthorized("synthetic-id-token-1")

        assertTrue(result is SessionRefreshResult.Failure)
        result as SessionRefreshResult.Failure
        assertEquals(401, result.statusCode)
        assertTrue(result.invalidatesSession)
    }

    @Test
    fun `temporary provider failure preserves local session`() = runTest {
        val session = FakeSessionManager()
        val coordinator = SessionRefreshCoordinator(
            sessionManager = session,
            refreshCall = { Response.error(502, "{}".toResponseBody()) },
            testOnly = Unit,
        )

        val result = coordinator.refreshAfterUnauthorized("synthetic-id-token-1")

        assertTrue(result is SessionRefreshResult.Failure)
        result as SessionRefreshResult.Failure
        assertEquals(502, result.statusCode)
        assertFalse(result.invalidatesSession)
        assertEquals("synthetic-id-token-1", session.getIdToken())
    }

    private class FakeSessionManager : SessionManager {
        @Volatile
        private var session = UserSession(
            uid = "synthetic-user",
            role = "patient",
            token = "synthetic-id-token-1",
            refreshToken = "synthetic-refresh-token-1",
            email = "synthetic@example.invalid",
            displayName = "Synthetic User",
        )

        override fun saveSession(session: UserSession) {
            this.session = session
        }

        override fun getIdToken(): String? = session.token
        override fun getRefreshToken(): String? = session.refreshToken
        override fun getUid(): String? = session.uid
        override fun getEmail(): String? = session.email
        override fun getRole(): String? = session.role
        override fun getDisplayName(): String? = session.displayName

        override fun clear() {
            session = UserSession(uid = "", role = "")
        }
    }
}
