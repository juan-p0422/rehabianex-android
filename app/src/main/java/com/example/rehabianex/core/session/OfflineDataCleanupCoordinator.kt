package com.example.rehabianex.core.session

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.example.rehabianex.data.local.PatientIdentityStore
import com.example.rehabianex.data.local.SupervisorNotificationSettingsStore
import com.example.rehabianex.data.local.PrivacyNoticeReadStore
import com.example.rehabianex.data.local.PendingDeactivationCleanupStore
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
    private val pendingDeactivationCleanupStore: PendingDeactivationCleanupStore? = null,
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

    suspend fun clearAfterAccountDeactivation(uid: String, role: String) {
        pendingDeactivationCleanupStore?.save(uid, role)
        clearOwner(uid, role, deleteUnsyncedNotes = true)
        pendingDeactivationCleanupStore?.clear()
    }

    suspend fun retryPendingAccountDeactivationCleanup() {
        val pending = pendingDeactivationCleanupStore?.load() ?: return
        clearOwner(pending.uid, pending.role, deleteUnsyncedNotes = true)
        pendingDeactivationCleanupStore.clear()
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
            val eventIds = database.agendaEventDao().serverIdsForOwner(uid)
            eventIds.filter(String::isNotBlank).forEach { eventId ->
                AgendaNotificationHelper.cancelEventReminder(applicationContext, eventId)
            }

            clearSynchronizedRoomData(uid)
            if (deleteUnsyncedNotes) {
                database.patientNoteDao().clearUnsyncedForOwner(uid)
            }
            patientIdentityStore.clearOwner(uid)
            supervisorNotificationSettingsStore.clearOwner(uid)
            privacyNoticeReadStore.clearOwner(uid)
        }

        NotificationManagerCompat.from(applicationContext).cancelAll()
        FcmNotificationDeduplicator.clear(applicationContext)
    }

    private suspend fun clearSynchronizedRoomData(uid: String) {
        database.patientNoteDao().clearSyncedForOwner(uid)
        database.agendaEventDao().clearOwner(uid)
        database.supportContactDao().clearOwner(uid)
        database.notificationSettingsDao().clearOwner(uid)
        database.patientAchievementDao().clearOwner(uid)
        database.cacheSyncStateDao().clearOwner(uid)
        database.patientSummaryDao().clearOwner(uid)
        database.patientProfileDao().clearOwner(uid)
        database.supervisorProfileSnapshotDao().clearOwner(uid)
        database.supervisorDashboardSummaryDao().clearOwner(uid)
        database.adminProfileSnapshotDao().clearOwner(uid)
        database.adminDashboardSummaryDao().clearOwner(uid)
    }
}
