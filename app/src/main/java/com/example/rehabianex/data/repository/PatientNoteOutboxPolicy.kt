package com.example.rehabianex.data.repository

import com.example.rehabianex.data.local.db.PatientNoteSyncState
import java.io.IOException
import java.net.SocketTimeoutException

enum class NoteSyncDisposition {
    Retryable,
    Ambiguous,
    Permanent,
}

fun classifyNoteSyncHttpStatus(statusCode: Int): NoteSyncDisposition = when (statusCode) {
    408, 425, 429, 500, 502, 503, 504 -> NoteSyncDisposition.Retryable
    else -> NoteSyncDisposition.Permanent
}

fun classifyNoteSyncFailure(
    error: Throwable,
    hasIdempotencyKey: Boolean,
): NoteSyncDisposition = when {
    error is SocketTimeoutException || error is IOException -> {
        if (hasIdempotencyKey) NoteSyncDisposition.Retryable else NoteSyncDisposition.Ambiguous
    }
    else -> NoteSyncDisposition.Ambiguous
}

fun canAutomaticallyRetry(
    state: PatientNoteSyncState,
    clientMutationId: String?,
): Boolean = when (state) {
    PatientNoteSyncState.Pending,
    PatientNoteSyncState.RetryableError,
    -> true
    PatientNoteSyncState.Ambiguous -> !clientMutationId.isNullOrBlank()
    PatientNoteSyncState.Syncing,
    PatientNoteSyncState.PermanentError,
    PatientNoteSyncState.Synced,
    -> false
}

data class PatientNoteSyncBatchResult(
    val syncedCount: Int = 0,
    val retryableCount: Int = 0,
    val ambiguousCount: Int = 0,
    val idempotentAmbiguousCount: Int = 0,
    val permanentErrorCount: Int = 0,
    val ownerMismatch: Boolean = false,
) {
    val shouldRetry: Boolean
        get() = !ownerMismatch && (retryableCount > 0 || idempotentAmbiguousCount > 0)
}
