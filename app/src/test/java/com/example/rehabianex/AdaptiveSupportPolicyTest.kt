package com.example.rehabianex

import com.example.rehabianex.adaptive.AdaptiveSupportPolicy
import com.example.rehabianex.data.model.CreatePatientNoteRequest
import com.example.rehabianex.data.model.NotificationSettingsData
import com.example.rehabianex.ui.screens.patient.InterventionCatalog
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveSupportPolicyTest {
    @Test
    fun immediateSuggestion_requiresAvailabilityAndExplicitOptIn() {
        val note = note(anxiety = 10, craving = 10)

        assertNull(AdaptiveSupportPolicy.immediateSuggestion(note, settings(available = false, enabled = true)))
        assertNull(AdaptiveSupportPolicy.immediateSuggestion(note, settings(available = true, enabled = false)))
    }

    @Test
    fun cravingHasDocumentedPrecedenceAndExplainsExperimentalReason() {
        val suggestion = AdaptiveSupportPolicy.immediateSuggestion(
            note(anxiety = 10, craving = 8),
            settings(available = true, enabled = true),
        )

        assertEquals(AdaptiveSupportPolicy.CRAVING_RESOURCE_ID, suggestion?.resourceId)
        assertTrue(suggestion?.reason.orEmpty().contains("reportaste craving 8/10"))
        assertTrue(suggestion?.reason.orEmpty().contains("No es un diagnóstico"))
        assertTrue(suggestion?.reason.orEmpty().contains("ni una predicción de recaída"))
    }

    @Test
    fun anxietyUsesBriefBreathingResourceWhenCravingIsBelowThreshold() {
        val suggestion = AdaptiveSupportPolicy.immediateSuggestion(
            note(anxiety = 8, craving = 6),
            settings(available = true, enabled = true),
        )

        assertEquals(AdaptiveSupportPolicy.ANXIETY_RESOURCE_ID, suggestion?.resourceId)
    }

    @Test
    fun missingCatalogResourceSuppressesSuggestion() {
        val suggestion = AdaptiveSupportPolicy.immediateSuggestion(
            note(anxiety = 10, craving = 10),
            settings(available = true, enabled = true),
            availableResourceIds = emptySet(),
        )

        assertNull(suggestion)
    }

    @Test
    fun inactivityUsesExactElapsedDaysThenConfiguredLocalTime() {
        val lastRecord = Instant.parse("2026-10-01T23:00:00Z")
        val beforeThreshold = Instant.parse("2026-10-04T22:59:59Z")
        val atThreshold = Instant.parse("2026-10-04T23:00:00Z")

        assertFalse(AdaptiveSupportPolicy.inactivityDue(lastRecord, beforeThreshold, 3))
        assertTrue(AdaptiveSupportPolicy.inactivityDue(lastRecord, atThreshold, 3))
        assertEquals(
            Instant.parse("2026-10-05T00:00:00Z"),
            AdaptiveSupportPolicy.nextInactivityReminder(
                lastRecordAt = lastRecord,
                now = Instant.parse("2026-10-02T00:00:00Z"),
                inactivityDays = 3,
                reminderTime = LocalTime.of(18, 0),
                zoneId = ZoneId.of("America/Mexico_City"),
            ),
        )
    }

    @Test
    fun inactivityReminderIsDeliveredOnlyOnceForTheSameEpisode() {
        assertTrue(AdaptiveSupportPolicy.shouldDeliverInactivityEpisode("note-2", "note-1"))
        assertFalse(AdaptiveSupportPolicy.shouldDeliverInactivityEpisode("note-2", "note-2"))
        assertFalse(AdaptiveSupportPolicy.shouldDeliverInactivityEpisode("", null))
    }

    @Test
    fun invalidLegacyTimezoneFallsBackWithoutBlockingScheduling() {
        val fallback = ZoneId.of("UTC")
        assertEquals(fallback, AdaptiveSupportPolicy.resolveZoneId("not/a-zone", fallback))
    }

    @Test
    fun configuredResourcesExistAndInactivityCopyIsNeutral() {
        assertEquals(
            "Grounding 5-4-3-2-1",
            InterventionCatalog.findById(AdaptiveSupportPolicy.CRAVING_RESOURCE_ID)?.title,
        )
        assertEquals(
            "Respiración 4-4-6",
            InterventionCatalog.findById(AdaptiveSupportPolicy.ANXIETY_RESOURCE_ID)?.title,
        )
        val normalized = AdaptiveSupportPolicy.INACTIVITY_REMINDER_MESSAGE.lowercase()
        listOf("recaída", "consumo", "riesgo", "diagnóstico").forEach {
            assertFalse(normalized.contains(it))
        }
    }

    private fun note(anxiety: Int, craving: Int) = CreatePatientNoteRequest(
        mood = "neutral",
        moodScore = 5,
        anxietyLevel = anxiety,
        cravingLevel = craving,
        hadRelapse = false,
    )

    private fun settings(available: Boolean, enabled: Boolean) = NotificationSettingsData.defaults().copy(
        adaptiveRulesAvailable = available,
        adaptiveSuggestionsEnabled = enabled,
        adaptiveCravingThreshold = 8,
        adaptiveAnxietyThreshold = 8,
    )
}
