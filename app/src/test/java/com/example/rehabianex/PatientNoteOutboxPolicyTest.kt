package com.example.rehabianex

import com.example.rehabianex.data.local.db.PatientNoteSyncState
import com.example.rehabianex.data.model.CreatePatientNoteRequest
import com.example.rehabianex.data.repository.NoteSyncDisposition
import com.example.rehabianex.data.repository.canAutomaticallyRetry
import com.example.rehabianex.data.repository.classifyNoteSyncFailure
import com.example.rehabianex.data.repository.classifyNoteSyncHttpStatus
import com.example.rehabianex.data.repository.PatientNoteSyncBatchResult
import com.google.gson.Gson
import java.net.SocketTimeoutException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PatientNoteOutboxPolicyTest {
    @Test
    fun requestSerializesStableClientMutationId() {
        val json = Gson().toJson(
            CreatePatientNoteRequest(
                mood = "neutral",
                moodScore = 5,
                anxietyLevel = 2,
                cravingLevel = 1,
                hadRelapse = false,
                clientMutationId = "496e3b8b-d115-4567-82ee-85fd9c2f57bb",
            ),
        )

        assertTrue(json.contains("\"client_mutation_id\":\"496e3b8b-d115-4567-82ee-85fd9c2f57bb\""))
    }

    @Test
    fun temporaryHttpErrorsAreRetryableAnd422IsPermanent() {
        assertEquals(NoteSyncDisposition.Retryable, classifyNoteSyncHttpStatus(500))
        assertEquals(NoteSyncDisposition.Retryable, classifyNoteSyncHttpStatus(502))
        assertEquals(NoteSyncDisposition.Retryable, classifyNoteSyncHttpStatus(429))
        assertEquals(NoteSyncDisposition.Permanent, classifyNoteSyncHttpStatus(422))
    }

    @Test
    fun timeoutNeedsIdempotencyToRetryAutomatically() {
        val timeout = SocketTimeoutException("timeout")

        assertEquals(NoteSyncDisposition.Retryable, classifyNoteSyncFailure(timeout, true))
        assertEquals(NoteSyncDisposition.Ambiguous, classifyNoteSyncFailure(timeout, false))
        assertTrue(canAutomaticallyRetry(PatientNoteSyncState.Ambiguous, "stable-id"))
        assertFalse(canAutomaticallyRetry(PatientNoteSyncState.Ambiguous, null))
        assertFalse(canAutomaticallyRetry(PatientNoteSyncState.PermanentError, "stable-id"))
        assertFalse(PatientNoteSyncBatchResult(ambiguousCount = 1).shouldRetry)
        assertTrue(
            PatientNoteSyncBatchResult(
                ambiguousCount = 1,
                idempotentAmbiguousCount = 1,
            ).shouldRetry,
        )
    }
}
