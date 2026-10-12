package com.example.rehabianex

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.rehabianex.data.local.db.AgendaEventEntity
import com.example.rehabianex.data.local.db.PatientNoteEntity
import com.example.rehabianex.data.local.db.PatientNoteSyncState
import com.example.rehabianex.data.local.db.PatientProfileEntity
import com.example.rehabianex.data.local.db.RehabiAnexDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineDaosInstrumentedTest {
    private lateinit var database: RehabiAnexDatabase

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, RehabiAnexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun notesAreIsolatedByOwnerAndTombstonesAreHidden() = runBlocking {
        database.patientNoteDao().upsert(note("patient-a", "note-a"))
        database.patientNoteDao().upsert(note("patient-b", "note-b"))
        database.patientNoteDao().upsert(
            note("patient-a", "deleted-note").copy(deletedAt = "2026-08-01T12:00:00-06:00"),
        )

        val patientA = database.patientNoteDao().activeForOwner("patient-a")
        val patientB = database.patientNoteDao().activeForOwner("patient-b")

        assertEquals(listOf("note-a"), patientA.mapNotNull { it.serverId })
        assertEquals(listOf("note-b"), patientB.mapNotNull { it.serverId })
    }

    @Test
    fun pendingNotesAndScheduledAgendaRemainAvailableOffline() = runBlocking {
        database.patientNoteDao().upsert(
            note("patient-a", "local-1").copy(
                serverId = null,
                clientMutationId = "496e3b8b-d115-4567-82ee-85fd9c2f57bb",
                syncState = PatientNoteSyncState.Pending,
                pendingSync = true,
            ),
        )
        database.agendaEventDao().upsert(
            listOf(
                AgendaEventEntity(
                    cacheKey = "patient-a:event-1",
                    serverId = "event-1",
                    ownerUid = "patient-a",
                    type = "support_meeting",
                    title = "Reunión",
                    startsAt = "2026-08-02T19:00:00-06:00",
                    endsAt = null,
                    location = null,
                    status = "scheduled",
                    notes = null,
                    createdAt = null,
                    updatedAt = null,
                    deletedAt = null,
                    lastSyncedAt = 1L,
                ),
                AgendaEventEntity(
                    cacheKey = "patient-a:event-2",
                    serverId = "event-2",
                    ownerUid = "patient-a",
                    type = null,
                    title = "Completado",
                    startsAt = "2026-08-01T19:00:00-06:00",
                    endsAt = null,
                    location = null,
                    status = "completed",
                    attendanceReported = true,
                    attendanceReportedAt = "2026-08-01T20:00:00-06:00",
                    notes = null,
                    createdAt = null,
                    updatedAt = null,
                    deletedAt = null,
                    lastSyncedAt = 1L,
                ),
            ),
        )

        assertEquals(1, database.patientNoteDao().pendingCount("patient-a"))
        val agenda = database.agendaEventDao().activeForOwner("patient-a")
        assertEquals(listOf("event-2", "event-1"), agenda.map { it.serverId })
        assertTrue(agenda.first { it.serverId == "event-2" }.attendanceReported == true)
        assertTrue(database.patientNoteDao().readyForSync("patient-b").isEmpty())
    }

    @Test
    fun patientProfilesAreOwnerScopedAndCanBeMarkedStale() = runBlocking {
        database.patientProfileDao().upsert(profile("patient-a", "Paciente A"))
        database.patientProfileDao().upsert(profile("patient-b", "Paciente B"))

        database.patientProfileDao().markStale("patient-a")

        assertEquals("Paciente A", database.patientProfileDao().get("patient-a")?.displayName)
        assertTrue(database.patientProfileDao().get("patient-a")?.isStale == true)
        assertTrue(database.patientProfileDao().get("patient-b")?.isStale == false)

        database.patientProfileDao().clearOwner("patient-a")
        assertEquals(null, database.patientProfileDao().get("patient-a"))
        assertEquals("Paciente B", database.patientProfileDao().get("patient-b")?.displayName)
    }

    private fun note(ownerUid: String, id: String) = PatientNoteEntity(
        cacheKey = "$ownerUid:$id",
        serverId = id,
        ownerUid = ownerUid,
        mood = "neutral",
        moodScore = 5,
        anxietyLevel = 2,
        cravingLevel = 1,
        energyLevel = 6,
        sleepQuality = 7,
        hadRelapse = false,
        triggers = emptyList(),
        copingActions = emptyList(),
        noteText = null,
        aiRiskLevel = null,
        aiRiskScore = null,
        createdAt = "2026-08-01T10:00:00-06:00",
        updatedAt = null,
        deletedAt = null,
        clientMutationId = null,
        syncState = PatientNoteSyncState.Synced,
        pendingSync = false,
        syncAttempts = 0,
        lastSyncError = null,
        lastSyncedAt = 1L,
    )

    private fun profile(ownerUid: String, displayName: String) = PatientProfileEntity(
        ownerUid = ownerUid,
        displayName = displayName,
        nickname = null,
        fullName = displayName,
        privacyMode = false,
        isAnonymous = false,
        sobrietyStartDate = null,
        photoUrl = null,
        lastSyncedAt = 1L,
        isStale = false,
    )
}
