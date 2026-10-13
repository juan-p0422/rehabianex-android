package com.example.rehabianex.adaptive

import android.content.Context
import com.example.rehabianex.core.session.SessionManager
import com.example.rehabianex.data.local.db.PatientNoteEntity
import com.example.rehabianex.data.local.db.RehabiAnexDatabase
import com.example.rehabianex.data.local.db.toUi
import com.example.rehabianex.data.model.CreatePatientNoteRequest
import com.example.rehabianex.data.model.NotificationSettingsData
import com.example.rehabianex.notifications.AgendaNotificationHelper
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId

class AdaptiveSupportManager(
    context: Context,
    private val database: RehabiAnexDatabase,
    private val sessionManager: SessionManager,
) {
    private val applicationContext = context.applicationContext
    private val state = applicationContext.getSharedPreferences(
        STATE_PREFERENCES,
        Context.MODE_PRIVATE,
    )

    suspend fun onNoteSaved(note: CreatePatientNoteRequest): AdaptiveSuggestion? {
        val uid = sessionManager.getUid()?.takeIf(String::isNotBlank) ?: return null
        clearDeliveredMarker(uid)
        val settings = database.notificationSettingsDao().forOwner(uid)?.toUi()
            ?: NotificationSettingsData.defaults()
        scheduleInactivity(settings)
        return AdaptiveSupportPolicy.immediateSuggestion(note, settings)
    }

    suspend fun scheduleInactivity(settings: NotificationSettingsData) {
        val uid = sessionManager.getUid()?.takeIf(String::isNotBlank) ?: return
        if (!settings.adaptiveRulesAvailable || !settings.inactivityReminderEnabled) {
            AgendaNotificationHelper.cancelDailyReminder(
                applicationContext,
                AgendaNotificationHelper.REMINDER_ADAPTIVE_INACTIVITY,
            )
            return
        }
        val latest = database.patientNoteDao().latestActiveForOwner(uid) ?: return
        if (!shouldDeliver(uid, latest.episodeMarker())) return
        val lastRecordAt = latest.recordInstant() ?: return
        val reminderTime = settings.inactivityReminderTime.toLocalTimeOrNull() ?: return
        val triggerAt = AdaptiveSupportPolicy.nextInactivityReminder(
            lastRecordAt = lastRecordAt,
            now = Instant.now(),
            inactivityDays = settings.inactivityDays,
            reminderTime = reminderTime,
            zoneId = AdaptiveSupportPolicy.resolveZoneId(settings.timezone),
        )
        AgendaNotificationHelper.scheduleAdaptiveInactivityReminder(
            applicationContext,
            triggerAt.toEpochMilli(),
        )
    }

    suspend fun shouldShowInactivityReminder(now: Instant = Instant.now()): Boolean {
        val uid = sessionManager.getUid()?.takeIf(String::isNotBlank) ?: return false
        val settings = database.notificationSettingsDao().forOwner(uid)?.toUi() ?: return false
        if (!settings.adaptiveRulesAvailable || !settings.inactivityReminderEnabled) return false
        val latest = database.patientNoteDao().latestActiveForOwner(uid) ?: return false
        val marker = latest.episodeMarker()
        if (!shouldDeliver(uid, marker)) return false
        val lastRecordAt = latest.recordInstant() ?: return false
        return AdaptiveSupportPolicy.inactivityDue(lastRecordAt, now, settings.inactivityDays)
    }

    suspend fun markInactivityReminderDelivered() {
        val uid = sessionManager.getUid()?.takeIf(String::isNotBlank) ?: return
        val latest = database.patientNoteDao().latestActiveForOwner(uid) ?: return
        state.edit().putString(markerKey(uid), latest.episodeMarker()).apply()
    }

    fun clearOwnerState(uid: String?) {
        if (!uid.isNullOrBlank()) state.edit().remove(markerKey(uid)).apply()
        AgendaNotificationHelper.cancelDailyReminder(
            applicationContext,
            AgendaNotificationHelper.REMINDER_ADAPTIVE_INACTIVITY,
        )
    }

    private fun shouldDeliver(uid: String, marker: String): Boolean =
        AdaptiveSupportPolicy.shouldDeliverInactivityEpisode(
            currentMarker = marker,
            deliveredMarker = state.getString(markerKey(uid), null),
        )

    private fun clearDeliveredMarker(uid: String) {
        state.edit().remove(markerKey(uid)).apply()
    }

    private fun markerKey(uid: String) = "last_inactivity_episode:$uid"

    private fun PatientNoteEntity.episodeMarker(): String = cacheKey

    private fun PatientNoteEntity.recordInstant(): Instant? {
        val value = createdAt ?: updatedAt ?: return null
        return runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
            ?: runCatching { Instant.parse(value) }.getOrNull()
            ?: runCatching {
                LocalDateTime.parse(value).atZone(ZoneId.systemDefault()).toInstant()
            }.getOrNull()
    }

    private fun String.toLocalTimeOrNull(): LocalTime? =
        runCatching { LocalTime.parse(this) }.getOrNull()

    private companion object {
        const val STATE_PREFERENCES = "adaptive_support_state"
    }
}
