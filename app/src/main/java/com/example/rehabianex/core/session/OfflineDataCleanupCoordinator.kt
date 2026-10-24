package com.example.rehabianex.core.session

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.example.rehabianex.data.local.PatientIdentityStore
import com.example.rehabianex.data.local.SupervisorNotificationSettingsStore
import com.example.rehabianex.data.local.PrivacyNoticeReadStore
import com.example.rehabianex.data.local.db.RehabiAnexDatabase
import com.example.rehabianex.notifications.AgendaNotificationHelper
import com.example.rehabianex.notifications.NotificationRescheduleWorker
import com.example.rehabianex.notifications.NotificationScheduler
import com.example.rehabianex.notifications.FcmNotificationDeduplicator
import com.example.rehabianex.sync.PatientSyncWorker

/**
 * Centraliza la invalidación de datos identificables por propietario.
 *
 * Room está cifrado en reposo, pero el cifrado no reemplaza el aislamiento lógico.
 * Las notas no sincronizadas solo se eliminan tras confirmación explícita de logout;
 * en un cambio de usuario se eliminan para impedir que otra sesión las vea.
 */
class OfflineDataCleanupCoordinator(
    context: Context,
    private val database: RehabiAnexDatabase,
    private val patientIdentityStore: PatientIdentityStore,
    private val supervisorNotificationSettingsStore: SupervisorNotificationSettingsStore,
    private val privacyNoticeReadStore: PrivacyNoticeReadStore,
    private val clearInMemoryState: () -> Unit = {},
) {
    private val applicationContext = context.applicationContext

    suspend fun pendingPatientNotes(uid: String?): Int {
        if (uid.isNullOrBlank()) return 0
        return database.patientNoteDao().pendingCount(uid)
    }

    suspend fun clearAfterConfirmedLogout(uid: String?, role: String?) {
        clearOwner(uid, role, deleteUnsyncedNotes = true)
    }

    suspend fun clearForUserSwitch(uid: String?, role: String?) {
        clearOwner(uid, role, deleteUnsyncedNotes = true)
    }

    private suspend fun clearOwner(
        uid: String?,
        role: String?,
        deleteUnsyncedNotes: Boolean,
    ) {
        NotificationRescheduleWorker.cancel(applicationContext)
        NotificationScheduler.cancelRoleReminders(applicationContext, role)
        clearInMemoryState()

        if (!uid.isNullOrBlank()) {
            PatientSyncWorker.cancel(applicationContext, uid)
            val eventIds = runCatching {
                database.agendaEventDao().serverIdsForOwner(uid)
            }.getOrDefault(emptyList())
            eventIds.filter(String::isNotBlank).forEach { eventId ->
                AgendaNotificationHelper.cancelEventReminder(applicationContext, eventId)
            }

            clearSynchronizedRoomData(uid)
            if (deleteUnsyncedNotes) {
                runCatching { database.patientNoteDao().clearUnsyncedForOwner(uid) }
            }
            runCatching { patientIdentityStore.clearOwner(uid) }
            runCatching { supervisorNotificationSettingsStore.clearOwner(uid) }
            runCatching { privacyNoticeReadStore.clearOwner(uid) }
        }

        NotificationManagerCompat.from(applicationContext).cancelAll()
        FcmNotificationDeduplicator.clear(applicationContext)
    }

    private suspend fun clearSynchronizedRoomData(uid: String) {
        runCatching { database.patientNoteDao().clearSyncedForOwner(uid) }
        runCatching { database.agendaEventDao().clearOwner(uid) }
        runCatching { database.supportContactDao().clearOwner(uid) }
        runCatching { database.notificationSettingsDao().clearOwner(uid) }
        runCatching { database.patientAchievementDao().clearOwner(uid) }
        runCatching { database.cacheSyncStateDao().clearOwner(uid) }
        runCatching { database.patientSummaryDao().clearOwner(uid) }
        runCatching { database.patientProfileDao().clearOwner(uid) }
        runCatching { database.supervisorProfileSnapshotDao().clearOwner(uid) }
        runCatching { database.supervisorDashboardSummaryDao().clearOwner(uid) }
        runCatching { database.adminProfileSnapshotDao().clearOwner(uid) }
        runCatching { database.adminDashboardSummaryDao().clearOwner(uid) }
    }
}
