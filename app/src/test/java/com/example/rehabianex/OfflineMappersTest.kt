package com.example.rehabianex

import com.example.rehabianex.data.local.db.toDto
import com.example.rehabianex.data.local.db.toEntity
import com.example.rehabianex.data.local.db.toPendingEntity
import com.example.rehabianex.data.local.db.toCreateRequest
import com.example.rehabianex.data.local.db.PatientNoteSyncState
import com.example.rehabianex.data.local.db.toSafeProfileEntity
import com.example.rehabianex.data.local.db.toUi
import com.example.rehabianex.data.model.CreatePatientNoteRequest
import com.example.rehabianex.data.model.NotificationSettingsDto
import com.example.rehabianex.data.model.PatientNoteDto
import com.example.rehabianex.data.model.SessionProfileDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class OfflineMappersTest {
    @Test
    fun patientNoteRoundTripKeepsOwnerContentAndSyncMetadata() {
        val dto = PatientNoteDto(
            noteId = "note-1",
            patientUid = "patient-1",
            mood = "calmado",
            anxietyLevel = 2,
            cravingLevel = 3,
            triggers = listOf("estrés"),
            copingActions = listOf("respiración"),
            noteText = "Registro propio",
            createdAt = "2026-08-01T10:00:00-06:00",
        )

        val entity = requireNotNull(dto.toEntity("patient-1", syncedAt = 1234L))
        val restored = entity.toDto()

        assertEquals("patient-1:note-1", entity.cacheKey)
        assertFalse(entity.pendingSync)
        assertEquals(1234L, entity.lastSyncedAt)
        assertEquals(dto.noteId, restored.noteId)
        assertEquals(dto.noteText, restored.noteText)
        assertEquals(dto.triggers, restored.triggers)
    }

    @Test
    fun pendingNoteUsesLocalIdAndCannotCollideAcrossPatients() {
        val request = CreatePatientNoteRequest(
            mood = "neutral",
            moodScore = 5,
            anxietyLevel = 4,
            cravingLevel = 2,
            hadRelapse = false,
        )

        val patientA = request.toPendingEntity("patient-a")
        val patientB = request.toPendingEntity("patient-b")

        assertTrue(patientA.pendingSync)
        assertEquals(PatientNoteSyncState.Pending, patientA.syncState)
        assertEquals(0, patientA.syncAttempts)
        assertNull(patientA.serverId)
        assertTrue(patientA.cacheKey.startsWith("patient-a:local-"))
        assertTrue(patientB.cacheKey.startsWith("patient-b:local-"))
        assertNotEquals(patientA.cacheKey, patientB.cacheKey)
        UUID.fromString(requireNotNull(patientA.clientMutationId))
        assertEquals(patientA.clientMutationId, patientA.toCreateRequest().clientMutationId)
    }

    @Test
    fun notificationAliasesMapToSingleRoomAndUiModel() {
        val dto = NotificationSettingsDto(
            notificationSettingId = "patient-1",
            patientUid = "patient-1",
            dailyCheckInEnabled = false,
            dailyCheckInTime = "21:15",
            soberDayEnabled = true,
            soberDayTime = "08:00",
            timezone = "America/Mexico_City",
        )

        val entity = dto.toEntity("patient-1", syncedAt = 99L)
        val ui = entity.toUi()

        assertEquals("patient-1", entity.serverId)
        assertFalse(ui.dailyNoteEnabled)
        assertEquals("21:15", ui.dailyNoteTime)
        assertTrue(ui.soberDayEnabled)
        assertEquals(99L, entity.lastSyncedAt)
    }

    @Test
    fun tombstoneRemainsMarkedAndDoesNotBecomePendingMutation() {
        val dto = PatientNoteDto(
            noteId = "deleted-note",
            patientUid = "patient-1",
            updatedAt = "2026-08-01T11:00:00-06:00",
            deletedAt = "2026-08-01T11:00:00-06:00",
        )

        val entity = requireNotNull(dto.toEntity("patient-1", syncedAt = 55L))

        assertEquals(dto.deletedAt, entity.deletedAt)
        assertFalse(entity.pendingSync)
        assertEquals(PatientNoteSyncState.Synced, entity.syncState)
    }

    @Test
    fun privateProfileCachesNicknameButNeverRealName() {
        val entity = SessionProfileDto(
            uid = "patient-1",
            fullName = "Nombre Real Sensible",
            nickname = "Luz",
            privacyMode = true,
            isAnonymous = false,
            sobrietyStartDate = "2026-08-01",
        ).toSafeProfileEntity(
            ownerUid = "patient-1",
            sessionDisplayName = "Nombre de sesión",
            syncedAt = 100L,
        )

        assertEquals("Luz", entity.displayName)
        assertEquals("Luz", entity.nickname)
        assertNull(entity.fullName)
        assertTrue(entity.privacyMode)
        assertFalse(entity.isAnonymous)
    }

    @Test
    fun anonymousProfileDropsRealNameAndUsesNeutralDisplayName() {
        val entity = SessionProfileDto(
            uid = "patient-1",
            fullName = "Nombre Real Sensible",
            nickname = null,
            privacyMode = true,
            isAnonymous = true,
        ).toSafeProfileEntity(
            ownerUid = "patient-1",
            sessionDisplayName = "Otro nombre real",
            syncedAt = 101L,
        )

        assertEquals("Paciente anónimo", entity.displayName)
        assertNull(entity.fullName)
        assertTrue(entity.isAnonymous)
    }

    @Test
    fun visibleProfileMayCacheRealNameWhenPrivacyAllowsIt() {
        val entity = SessionProfileDto(
            uid = "patient-1",
            fullName = "Nombre Visible",
            privacyMode = false,
            isAnonymous = false,
        ).toSafeProfileEntity(
            ownerUid = "patient-1",
            sessionDisplayName = "Nombre de sesión",
            syncedAt = 102L,
        )

        assertEquals("Nombre Visible", entity.displayName)
        assertEquals("Nombre Visible", entity.fullName)
        assertFalse(entity.privacyMode)
    }
}
