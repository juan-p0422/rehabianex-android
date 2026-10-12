package com.example.rehabianex.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(
    tableName = "patient_notes",
    indices = [
        Index("ownerUid"),
        Index("serverId"),
        Index("pendingSync"),
        Index("clientMutationId"),
        Index("syncState"),
    ],
)
data class PatientNoteEntity(
    @PrimaryKey val cacheKey: String,
    val serverId: String?,
    val ownerUid: String,
    val mood: String?,
    val moodScore: Int?,
    val anxietyLevel: Int?,
    val cravingLevel: Int?,
    val energyLevel: Int?,
    val sleepQuality: Int?,
    val hadRelapse: Boolean?,
    val triggers: List<String>,
    val copingActions: List<String>,
    val noteText: String?,
    val aiRiskLevel: String?,
    val aiRiskScore: Double?,
    val createdAt: String?,
    val updatedAt: String?,
    val deletedAt: String?,
    val clientMutationId: String?,
    @ColumnInfo(defaultValue = "'synced'")
    val syncState: PatientNoteSyncState,
    val pendingSync: Boolean,
    val syncAttempts: Int,
    val lastSyncError: String?,
    val lastSyncedAt: Long?,
)

enum class PatientNoteSyncState(val persistedValue: String) {
    Pending("pending"),
    Syncing("syncing"),
    RetryableError("retryable_error"),
    Ambiguous("ambiguous"),
    PermanentError("permanent_error"),
    Synced("synced"),
}

@Entity(
    tableName = "agenda_events",
    indices = [Index("ownerUid"), Index("serverId")],
)
data class AgendaEventEntity(
    @PrimaryKey val cacheKey: String,
    val serverId: String,
    val ownerUid: String,
    val type: String?,
    val title: String?,
    val startsAt: String?,
    val endsAt: String?,
    val location: String?,
    val status: String?,
    val attendanceReported: Boolean? = null,
    val attendanceReportedAt: String? = null,
    val notes: String?,
    val createdAt: String?,
    val updatedAt: String?,
    val deletedAt: String?,
    val pendingSync: Boolean = false,
    val lastSyncedAt: Long,
)

@Entity(
    tableName = "support_contacts",
    indices = [Index("ownerUid"), Index("serverId")],
)
data class SupportContactEntity(
    @PrimaryKey val cacheKey: String,
    val serverId: String,
    val ownerUid: String,
    val name: String?,
    val relationship: String?,
    val phone: String?,
    val priority: Int?,
    val canReceiveAlerts: Boolean?,
    val notes: String?,
    val createdAt: String?,
    val updatedAt: String?,
    val deletedAt: String?,
    val pendingSync: Boolean = false,
    val lastSyncedAt: Long,
)

@Entity(tableName = "notification_settings", indices = [Index("ownerUid")])
data class NotificationSettingsEntity(
    @PrimaryKey val cacheKey: String,
    val serverId: String?,
    val ownerUid: String,
    val dailyNoteEnabled: Boolean,
    val dailyNoteTime: String,
    val soberDayEnabled: Boolean,
    val soberDayTime: String,
    val achievementEnabled: Boolean,
    val achievementTime: String,
    val eventRemindersEnabled: Boolean,
    val motivationalEnabled: Boolean,
    val motivationalTime: String,
    val cravingAlertsEnabled: Boolean,
    val supervisorAlertsEnabled: Boolean,
    val supportContactAlertsEnabled: Boolean,
    val timezone: String,
    val pendingSync: Boolean = false,
    val lastSyncedAt: Long,
)

@Entity(tableName = "local_resources")
data class LocalResourceEntity(
    @PrimaryKey val resourceId: String,
    val name: String?,
    val type: String?,
    val city: String?,
    val address: String?,
    val phone: String?,
    val meetingSchedule: List<String>,
    val tags: List<String>,
    val createdAt: String?,
    val updatedAt: String?,
    val deletedAt: String?,
    val pendingSync: Boolean = false,
    val lastSyncedAt: Long,
)

@Entity(tableName = "achievements")
data class AchievementEntity(
    @PrimaryKey val achievementId: String,
    val title: String?,
    val description: String?,
    val createdAt: String?,
    val updatedAt: String?,
    val deletedAt: String?,
    val pendingSync: Boolean = false,
    val lastSyncedAt: Long,
)

@Entity(
    tableName = "patient_achievements",
    indices = [Index("ownerUid"), Index("achievementId")],
)
data class PatientAchievementEntity(
    @PrimaryKey val cacheKey: String,
    val serverId: String,
    val ownerUid: String,
    val achievementId: String?,
    val awardedAt: String?,
    val createdAt: String?,
    val updatedAt: String?,
    val deletedAt: String?,
    val pendingSync: Boolean = false,
    val lastSyncedAt: Long,
)

@Entity(tableName = "cache_sync_state")
data class CacheSyncStateEntity(
    @PrimaryKey val cacheKey: String,
    val ownerUid: String,
    val resource: String,
    val lastSyncedAt: Long,
)

@Entity(tableName = "patient_summary")
data class PatientSummaryEntity(
    @PrimaryKey val ownerUid: String,
    /** Columna legada: contiene solo el nombre seguro proyectado, nunca el nombre real privado. */
    val fullName: String?,
    val sobrietyStartDate: String?,
    val lastSyncedAt: Long,
)

@Entity(tableName = "patient_profile")
data class PatientProfileEntity(
    @PrimaryKey val ownerUid: String,
    val displayName: String,
    val nickname: String?,
    val fullName: String?,
    val privacyMode: Boolean,
    val isAnonymous: Boolean,
    val sobrietyStartDate: String?,
    val photoUrl: String?,
    val lastSyncedAt: Long,
    val isStale: Boolean,
)

/** Snapshot del perfil propio del supervisor. No contiene datos de pacientes. */
@Entity(tableName = "supervisor_profile_snapshot")
data class SupervisorProfileSnapshotEntity(
    @PrimaryKey val ownerUid: String,
    val displayName: String,
    val supervisorType: String,
    val supervisorCode: String,
    val authorized: Boolean,
    val verified: Boolean,
    val status: String,
    val lastSyncedAt: Long,
    val isStale: Boolean,
)

/** Solo conserva contadores agregados; nunca identidades ni datos clínicos. */
@Entity(tableName = "supervisor_dashboard_summary")
data class SupervisorDashboardSummaryEntity(
    @PrimaryKey val ownerUid: String,
    val patientsCount: Int,
    val pendingRequestsCount: Int,
    val openInterventionsCount: Int,
    val lastSyncedAt: Long,
)

/** Snapshot de identidad propia de la cuenta administradora. */
@Entity(tableName = "admin_profile_snapshot")
data class AdminProfileSnapshotEntity(
    @PrimaryKey val ownerUid: String,
    val displayName: String,
    val email: String?,
    val status: String,
    val lastSyncedAt: Long,
)

/** Resumen agregado. La lista y los datos de supervisores no se guardan en Room. */
@Entity(tableName = "admin_dashboard_summary")
data class AdminDashboardSummaryEntity(
    @PrimaryKey val ownerUid: String,
    val pendingSupervisors: Int,
    val activeSupervisors: Int,
    val suspendedSupervisors: Int,
    val lastSyncedAt: Long,
)
