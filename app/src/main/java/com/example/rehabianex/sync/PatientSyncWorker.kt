package com.example.rehabianex.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.rehabianex.AppContainer
import java.util.concurrent.TimeUnit

class PatientSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val ownerUid = inputData.getString(INPUT_OWNER_UID)
            ?.takeIf { it.isNotBlank() }
            ?: return Result.failure()
        val repository = AppContainer(applicationContext).patientRepository
        val result = repository.syncPendingPatientNotes(ownerUid)
        return when {
            result.ownerMismatch -> Result.success()
            result.shouldRetry -> Result.retry()
            else -> Result.success()
        }
    }

    companion object {
        fun enqueue(context: Context, ownerUid: String) {
            val request = OneTimeWorkRequestBuilder<PatientSyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .setInputData(workDataOf(INPUT_OWNER_UID to ownerUid))
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    MIN_BACKOFF_SECONDS,
                    TimeUnit.SECONDS,
                )
                .addTag(WORK_TAG)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                "$UNIQUE_PREFIX-$ownerUid",
                ExistingWorkPolicy.KEEP,
                request,
            )
        }

        fun cancel(context: Context, ownerUid: String) {
            WorkManager.getInstance(context.applicationContext)
                .cancelUniqueWork("$UNIQUE_PREFIX-$ownerUid")
        }

        private const val UNIQUE_PREFIX = "rehabianex-patient-note-sync"
        private const val WORK_TAG = "rehabianex-offline-sync"
        const val INPUT_OWNER_UID = "ownerUid"
        private const val MIN_BACKOFF_SECONDS = 30L
    }
}
