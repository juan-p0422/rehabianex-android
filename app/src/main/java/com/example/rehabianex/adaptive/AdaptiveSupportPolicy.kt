package com.example.rehabianex.adaptive

import com.example.rehabianex.data.model.CreatePatientNoteRequest
import com.example.rehabianex.data.model.NotificationSettingsData
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

data class AdaptiveSuggestion(
    val resourceId: String,
    val title: String,
    val reason: String,
)

object AdaptiveSupportPolicy {
    const val CRAVING_RESOURCE_ID = "grounding_54321"
    const val ANXIETY_RESOURCE_ID = "breathing_446"
    const val INACTIVITY_REMINDER_MESSAGE =
        "Hace un tiempo que no registras cómo te sientes. Si quieres, abre la app para hacer un registro."

    private val supportedResources = mapOf(
        CRAVING_RESOURCE_ID to "Grounding 5-4-3-2-1",
        ANXIETY_RESOURCE_ID to "Respiración 4-4-6",
    )

    fun immediateSuggestion(
        note: CreatePatientNoteRequest,
        settings: NotificationSettingsData,
        availableResourceIds: Set<String> = supportedResources.keys,
    ): AdaptiveSuggestion? {
        if (!settings.adaptiveRulesAvailable || !settings.adaptiveSuggestionsEnabled) return null

        return when {
            note.cravingLevel >= settings.adaptiveCravingThreshold -> suggestion(
                resourceId = CRAVING_RESOURCE_ID,
                value = note.cravingLevel,
                scale = "craving",
                threshold = settings.adaptiveCravingThreshold,
                availableResourceIds = availableResourceIds,
            )
            note.anxietyLevel >= settings.adaptiveAnxietyThreshold -> suggestion(
                resourceId = ANXIETY_RESOURCE_ID,
                value = note.anxietyLevel,
                scale = "ansiedad",
                threshold = settings.adaptiveAnxietyThreshold,
                availableResourceIds = availableResourceIds,
            )
            else -> null
        }
    }

    fun inactivityDue(
        lastRecordAt: Instant,
        now: Instant,
        inactivityDays: Int,
    ): Boolean = !now.isBefore(lastRecordAt.plus(Duration.ofDays(inactivityDays.toLong())))

    fun shouldDeliverInactivityEpisode(
        currentMarker: String,
        deliveredMarker: String?,
    ): Boolean = currentMarker.isNotBlank() && currentMarker != deliveredMarker

    fun resolveZoneId(timezone: String, fallback: ZoneId = ZoneId.systemDefault()): ZoneId =
        runCatching { ZoneId.of(timezone) }.getOrDefault(fallback)

    fun nextInactivityReminder(
        lastRecordAt: Instant,
        now: Instant,
        inactivityDays: Int,
        reminderTime: LocalTime,
        zoneId: ZoneId,
    ): Instant {
        val threshold = lastRecordAt.plus(Duration.ofDays(inactivityDays.toLong()))
        var candidate = threshold.atZone(zoneId).toLocalDate().atTime(reminderTime).atZone(zoneId)
        if (candidate.toInstant().isBefore(threshold)) candidate = candidate.plusDays(1)
        if (!candidate.toInstant().isAfter(now)) {
            val today = ZonedDateTime.of(now.atZone(zoneId).toLocalDate(), reminderTime, zoneId)
            candidate = if (today.toInstant().isAfter(now)) today else today.plusDays(1)
        }
        return candidate.toInstant()
    }

    private fun suggestion(
        resourceId: String,
        value: Int,
        scale: String,
        threshold: Int,
        availableResourceIds: Set<String>,
    ): AdaptiveSuggestion? {
        if (resourceId !in availableResourceIds) return null
        val title = supportedResources[resourceId] ?: return null
        return AdaptiveSuggestion(
            resourceId = resourceId,
            title = title,
            reason = "Se sugiere porque reportaste $scale $value/10, igual o mayor al valor experimental $threshold/10. No es un diagnóstico ni una predicción de recaída.",
        )
    }
}
