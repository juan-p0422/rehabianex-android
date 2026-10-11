package com.example.rehabianex.data.local

import android.content.Context
import androidx.core.content.edit
import com.example.rehabianex.data.model.NotificationSettingsData

class SupervisorNotificationSettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun load(supervisorUid: String): NotificationSettingsData {
        val prefix = supervisorUid.preferencePrefix()
        return NotificationSettingsData(
            id = preferences.getString("$prefix$id", null) ?: localId(supervisorUid),
            dailyNoteEnabled = preferences.getBoolean("$prefix$dailySummaryEnabled", true),
            dailyNoteTime = preferences.getString("$prefix$dailySummaryTime", "08:00") ?: "08:00",
            soberDayEnabled = preferences.getBoolean("$prefix$relapseAlertEnabled", true),
            soberDayTime = preferences.getString("$prefix$relapseAlertTime", "09:00") ?: "09:00",
            achievementEnabled = preferences.getBoolean("$prefix$pendingRequestsEnabled", true),
            achievementTime = preferences.getString("$prefix$pendingRequestsTime", "09:30") ?: "09:30",
            eventRemindersEnabled = false,
            motivationalEnabled = preferences.getBoolean("$prefix$followUpEnabled", false),
            motivationalTime = preferences.getString("$prefix$followUpTime", "18:00") ?: "18:00",
        )
    }

    fun save(
        supervisorUid: String,
        settings: NotificationSettingsData,
    ): NotificationSettingsData {
        val prefix = supervisorUid.preferencePrefix()
        val settingsId = settings.id?.takeIf { !it.startsWith(LOCAL_ID_PREFIX) }
            ?: localId(supervisorUid)

        preferences.edit {
            putString("$prefix$id", settingsId)
            putBoolean("$prefix$dailySummaryEnabled", settings.dailyNoteEnabled)
            putString("$prefix$dailySummaryTime", settings.dailyNoteTime)
            putBoolean("$prefix$relapseAlertEnabled", settings.soberDayEnabled)
            putString("$prefix$relapseAlertTime", settings.soberDayTime)
            putBoolean("$prefix$pendingRequestsEnabled", settings.achievementEnabled)
            putString("$prefix$pendingRequestsTime", settings.achievementTime)
            putBoolean("$prefix$followUpEnabled", settings.motivationalEnabled)
            putString("$prefix$followUpTime", settings.motivationalTime)
        }

        return settings.copy(
            id = settingsId,
            eventRemindersEnabled = false,
        )
    }

    fun clearOwner(supervisorUid: String) {
        val prefix = supervisorUid.preferencePrefix()
        preferences.edit {
            remove("$prefix$id")
            remove("$prefix$dailySummaryEnabled")
            remove("$prefix$dailySummaryTime")
            remove("$prefix$relapseAlertEnabled")
            remove("$prefix$relapseAlertTime")
            remove("$prefix$pendingRequestsEnabled")
            remove("$prefix$pendingRequestsTime")
            remove("$prefix$followUpEnabled")
            remove("$prefix$followUpTime")
        }
    }

    private fun String.preferencePrefix(): String {
        return filter { it.isLetterOrDigit() || it == '_' || it == '-' }
            .ifBlank { "supervisor" }
            .let { "supervisor_${it}_" }
    }

    private fun localId(supervisorUid: String): String {
        return "$LOCAL_ID_PREFIX${supervisorUid.preferencePrefix()}"
    }

    private companion object {
        const val PREFERENCES_NAME = "supervisor_notification_settings"
        const val LOCAL_ID_PREFIX = "local_"
        const val id = "id"
        const val dailySummaryEnabled = "daily_summary_enabled"
        const val dailySummaryTime = "daily_summary_time"
        const val relapseAlertEnabled = "relapse_alert_enabled"
        const val relapseAlertTime = "relapse_alert_time"
        const val pendingRequestsEnabled = "pending_requests_enabled"
        const val pendingRequestsTime = "pending_requests_time"
        const val followUpEnabled = "follow_up_enabled"
        const val followUpTime = "follow_up_time"
    }
}
