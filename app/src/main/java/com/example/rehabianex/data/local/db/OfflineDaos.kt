package com.example.rehabianex.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface PatientNoteDao {
    @Query("SELECT * FROM patient_notes WHERE ownerUid = :ownerUid AND deletedAt IS NULL ORDER BY createdAt DESC")
    suspend fun activeForOwner(ownerUid: String): List<PatientNoteEntity>

    @Query("SELECT * FROM patient_notes WHERE ownerUid = :ownerUid AND deletedAt IS NULL ORDER BY createdAt DESC LIMIT 1")
    suspend fun latestActiveForOwner(ownerUid: String): PatientNoteEntity?

    @Query("""
        SELECT * FROM patient_notes
        WHERE ownerUid = :ownerUid
          AND (
            syncState IN ('pending', 'retryable_error')
            OR (syncState = 'ambiguous' AND clientMutationId IS NOT NULL)
          )
        ORDER BY createdAt
    """)
    suspend fun readyForSync(ownerUid: String): List<PatientNoteEntity>

    @Query("SELECT COUNT(*) FROM patient_notes WHERE ownerUid = :ownerUid AND syncState != 'synced'")
    suspend fun pendingCount(ownerUid: String): Int

    @Query("""
        SELECT * FROM patient_notes
        WHERE ownerUid = :ownerUid AND syncState IN ('permanent_error', 'ambiguous')
        ORDER BY updatedAt DESC, createdAt DESC
        LIMIT 1
    """)
    suspend fun latestActionRequired(ownerUid: String): PatientNoteEntity?

    @Query("""
        UPDATE patient_notes
        SET syncState = 'retryable_error', pendingSync = 1,
            lastSyncError = :message
        WHERE ownerUid = :ownerUid AND syncState = 'syncing'
    """)
    suspend fun recoverInterruptedSync(ownerUid: String, message: String)

    @Upsert
    suspend fun upsert(items: List<PatientNoteEntity>)

    @Upsert
    suspend fun upsert(item: PatientNoteEntity)

    @Query("DELETE FROM patient_notes WHERE cacheKey = :cacheKey")
    suspend fun deleteByCacheKey(cacheKey: String)

    @Query("DELETE FROM patient_notes WHERE ownerUid = :ownerUid AND syncState = 'synced'")
    suspend fun clearSyncedForOwner(ownerUid: String)

    @Query("DELETE FROM patient_notes WHERE ownerUid = :ownerUid AND syncState != 'synced'")
    suspend fun clearUnsyncedForOwner(ownerUid: String)

    @Query("DELETE FROM patient_notes WHERE ownerUid = :ownerUid")
    suspend fun clearOwner(ownerUid: String)
}

@Dao
interface AgendaEventDao {
    @Query("SELECT * FROM agenda_events WHERE ownerUid = :ownerUid AND deletedAt IS NULL ORDER BY startsAt")
    suspend fun activeForOwner(ownerUid: String): List<AgendaEventEntity>

    @Query("SELECT serverId FROM agenda_events WHERE ownerUid = :ownerUid")
    suspend fun serverIdsForOwner(ownerUid: String): List<String>

    @Upsert
    suspend fun upsert(items: List<AgendaEventEntity>)

    @Query("DELETE FROM agenda_events WHERE ownerUid = :ownerUid")
    suspend fun clearOwner(ownerUid: String)
}

@Dao
interface SupportContactDao {
    @Query("SELECT * FROM support_contacts WHERE ownerUid = :ownerUid AND deletedAt IS NULL ORDER BY priority, name")
    suspend fun activeForOwner(ownerUid: String): List<SupportContactEntity>

    @Upsert
    suspend fun upsert(items: List<SupportContactEntity>)

    @Query("DELETE FROM support_contacts WHERE ownerUid = :ownerUid")
    suspend fun clearOwner(ownerUid: String)
}

@Dao
interface NotificationSettingsDao {
    @Query("SELECT * FROM notification_settings WHERE ownerUid = :ownerUid LIMIT 1")
    suspend fun forOwner(ownerUid: String): NotificationSettingsEntity?

    @Upsert
    suspend fun upsert(item: NotificationSettingsEntity)

    @Query("DELETE FROM notification_settings WHERE ownerUid = :ownerUid")
    suspend fun clearOwner(ownerUid: String)
}

@Dao
interface LocalResourceDao {
    @Query("SELECT * FROM local_resources WHERE deletedAt IS NULL ORDER BY type, name")
    suspend fun active(): List<LocalResourceEntity>

    @Upsert
    suspend fun upsert(items: List<LocalResourceEntity>)
}

@Dao
interface AchievementDao {
    @Query("SELECT * FROM achievements WHERE deletedAt IS NULL ORDER BY title")
    suspend fun active(): List<AchievementEntity>

    @Upsert
    suspend fun upsert(items: List<AchievementEntity>)
}

@Dao
interface PatientAchievementDao {
    @Query("SELECT * FROM patient_achievements WHERE ownerUid = :ownerUid AND deletedAt IS NULL ORDER BY awardedAt DESC")
    suspend fun activeForOwner(ownerUid: String): List<PatientAchievementEntity>

    @Upsert
    suspend fun upsert(items: List<PatientAchievementEntity>)

    @Query("DELETE FROM patient_achievements WHERE ownerUid = :ownerUid")
    suspend fun clearOwner(ownerUid: String)
}

@Dao
interface CacheSyncStateDao {
    @Query("SELECT * FROM cache_sync_state WHERE cacheKey = :cacheKey LIMIT 1")
    suspend fun get(cacheKey: String): CacheSyncStateEntity?

    @Upsert
    suspend fun upsert(item: CacheSyncStateEntity)

    @Query("DELETE FROM cache_sync_state WHERE ownerUid = :ownerUid")
    suspend fun clearOwner(ownerUid: String)
}

@Dao
interface PatientSummaryDao {
    @Query("SELECT * FROM patient_summary WHERE ownerUid = :ownerUid LIMIT 1")
    suspend fun get(ownerUid: String): PatientSummaryEntity?

    @Upsert
    suspend fun upsert(item: PatientSummaryEntity)

    @Query("DELETE FROM patient_summary WHERE ownerUid = :ownerUid")
    suspend fun clearOwner(ownerUid: String)
}

@Dao
interface PatientProfileDao {
    @Query("SELECT * FROM patient_profile WHERE ownerUid = :ownerUid LIMIT 1")
    suspend fun get(ownerUid: String): PatientProfileEntity?

    @Upsert
    suspend fun upsert(item: PatientProfileEntity)

    @Query("UPDATE patient_profile SET isStale = 1 WHERE ownerUid = :ownerUid")
    suspend fun markStale(ownerUid: String)

    @Query("DELETE FROM patient_profile WHERE ownerUid = :ownerUid")
    suspend fun clearOwner(ownerUid: String)
}

@Dao
interface SupervisorProfileSnapshotDao {
    @Query("SELECT * FROM supervisor_profile_snapshot WHERE ownerUid = :ownerUid LIMIT 1")
    suspend fun get(ownerUid: String): SupervisorProfileSnapshotEntity?

    @Upsert
    suspend fun upsert(item: SupervisorProfileSnapshotEntity)

    @Query("UPDATE supervisor_profile_snapshot SET isStale = 1 WHERE ownerUid = :ownerUid")
    suspend fun markStale(ownerUid: String)

    @Query("DELETE FROM supervisor_profile_snapshot WHERE ownerUid = :ownerUid")
    suspend fun clearOwner(ownerUid: String)
}

@Dao
interface SupervisorDashboardSummaryDao {
    @Query("SELECT * FROM supervisor_dashboard_summary WHERE ownerUid = :ownerUid LIMIT 1")
    suspend fun get(ownerUid: String): SupervisorDashboardSummaryEntity?

    @Upsert
    suspend fun upsert(item: SupervisorDashboardSummaryEntity)

    @Query("DELETE FROM supervisor_dashboard_summary WHERE ownerUid = :ownerUid")
    suspend fun clearOwner(ownerUid: String)
}

@Dao
interface AdminProfileSnapshotDao {
    @Query("SELECT * FROM admin_profile_snapshot WHERE ownerUid = :ownerUid LIMIT 1")
    suspend fun get(ownerUid: String): AdminProfileSnapshotEntity?

    @Upsert
    suspend fun upsert(item: AdminProfileSnapshotEntity)

    @Query("DELETE FROM admin_profile_snapshot WHERE ownerUid = :ownerUid")
    suspend fun clearOwner(ownerUid: String)
}

@Dao
interface AdminDashboardSummaryDao {
    @Query("SELECT * FROM admin_dashboard_summary WHERE ownerUid = :ownerUid LIMIT 1")
    suspend fun get(ownerUid: String): AdminDashboardSummaryEntity?

    @Upsert
    suspend fun upsert(item: AdminDashboardSummaryEntity)

    @Query("DELETE FROM admin_dashboard_summary WHERE ownerUid = :ownerUid")
    suspend fun clearOwner(ownerUid: String)
}
