package com.example.rehabianex.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.rehabianex.AppContainer
import com.example.rehabianex.data.repository.PatientDataResult
import com.example.rehabianex.data.repository.SupervisorDataResult

/** Recupera preferencias persistidas y vuelve a crear alarmas locales. */
class NotificationRescheduleWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val container = AppContainer(applicationContext)
        if (!container.sessionManager.hasActiveSession()) return Result.success()

        return when (container.sessionManager.getRole()) {
            NotificationScheduler.ROLE_PATIENT -> reschedulePatient(container)
            NotificationScheduler.ROLE_SUPERVISOR -> rescheduleSupervisor(container)
            else -> Result.success()
        }
    }

    private suspend fun reschedulePatient(container: AppContainer): Result {
        val settings = container.patientRepository.loadNotificationSettings()
        if (settings !is PatientDataResult.Success) return retryOrFinish()
        val events = when (val result = container.patientRepository.loadAgendaEvents()) {
            is PatientDataResult.Success -> result.data
            is PatientDataResult.Failure -> emptyList()
        }
        NotificationScheduler.schedulePatient(applicationContext, settings.data, events)
        container.adaptiveSupportManager.scheduleInactivity(settings.data)
        return Result.success()
    }

    private suspend fun rescheduleSupervisor(container: AppContainer): Result {
        val settings = container.supervisorRepository.loadNotificationSettings()
        if (settings !is SupervisorDataResult.Success) return retryOrFinish()
        NotificationScheduler.scheduleSupervisor(applicationContext, settings.data)
        return Result.success()
    }

    private fun retryOrFinish(): Result {
        return if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()
    }

    companion object {
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<NotificationRescheduleWorker>()
                .addTag(WORK_TAG)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context.applicationContext)
                .cancelUniqueWork(UNIQUE_WORK_NAME)
        }

        private const val UNIQUE_WORK_NAME = "rehabianex-notification-reschedule"
        private const val WORK_TAG = "rehabianex-local-notifications"
        private const val MAX_RETRIES = 3
    }
}
