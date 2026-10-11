package com.example.rehabianex

import com.example.rehabianex.core.performance.StaleRefreshController
import com.example.rehabianex.core.performance.RefreshInvalidationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StaleRefreshControllerTest {
    @Test
    fun initAndResumeWhileRequestIsRunning_executeOnlyOneRefresh() = runTest {
        var calls = 0
        val release = CompletableDeferred<Unit>()
        val controller = StaleRefreshController(ttlMillis = 20_000L) { 1_000L }

        assertTrue(controller.refreshIfStale(this) { calls += 1; release.await(); true })
        runCurrent()
        assertFalse(controller.refreshIfStale(this) { calls += 1; true })
        assertEquals(1, calls)

        release.complete(Unit)
        advanceUntilIdle()
        assertEquals(1_000L, controller.lastSyncedAt)
    }

    @Test
    fun successfulRefresh_isSkippedInsideTtlAndRunsAfterExpiry() = runTest {
        var now = 1_000L
        var calls = 0
        val controller = StaleRefreshController(ttlMillis = 20_000L) { now }

        controller.refreshIfStale(this) { calls += 1; true }
        advanceUntilIdle()
        assertFalse(controller.refreshIfStale(this) { calls += 1; true })
        now += 20_001L
        assertTrue(controller.refreshIfStale(this) { calls += 1; true })
        advanceUntilIdle()

        assertEquals(2, calls)
    }

    @Test
    fun forceRefresh_cancelsInFlightJobAndExecutesMutationRefresh() = runTest {
        var calls = 0
        val firstRelease = CompletableDeferred<Unit>()
        val controller = StaleRefreshController(ttlMillis = 20_000L) { 5_000L }

        controller.refreshIfStale(this) { calls += 1; firstRelease.await(); true }
        runCurrent()
        assertTrue(controller.refreshIfStale(this, force = true) { calls += 1; true })
        advanceUntilIdle()

        assertEquals(2, calls)
        assertEquals(5_000L, controller.lastSyncedAt)
    }

    @Test
    fun mutationInAnotherScreen_invalidatesTtlForSharedScope() = runTest {
        val scopeKey = "patient-test-${System.nanoTime()}"
        var calls = 0
        val controller = StaleRefreshController(
            ttlMillis = 20_000L,
            invalidationScope = scopeKey,
        ) { 1_000L }

        controller.refreshIfStale(this) { calls += 1; true }
        advanceUntilIdle()
        RefreshInvalidationRegistry.invalidate(scopeKey)

        assertTrue(controller.refreshIfStale(this) { calls += 1; true })
        advanceUntilIdle()
        assertEquals(2, calls)
    }

    @Test
    fun failedRefresh_doesNotStartTtlAndCanRetryImmediately() = runTest {
        var calls = 0
        val controller = StaleRefreshController(ttlMillis = 20_000L) { 1_000L }

        controller.refreshIfStale(this) { calls += 1; false }
        advanceUntilIdle()

        assertTrue(controller.refreshIfStale(this) { calls += 1; true })
        advanceUntilIdle()
        assertEquals(2, calls)
    }
}
