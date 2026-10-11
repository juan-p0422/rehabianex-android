package com.example.rehabianex.notifications

import android.content.Context
import com.example.rehabianex.data.model.AgendaEvent
import com.example.rehabianex.data.model.NotificationSettingsData
import com.example.rehabianex.navigation.Routes
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * Único punto de programación de notificaciones locales.
 *
 * Los textos son deliberadamente neutros: el detalle se consulta dentro de la app,
 * después de validar la sesión y los permisos correspondientes.
 */
object NotificationScheduler {
    fun schedulePatient(
        context: Context,
        settings: NotificationSettingsData,
        events: List<AgendaEvent>,
    ): Int {
        var scheduled = 0

        scheduled += scheduleOrCancel(
            context = context,
            enabled = settings.dailyNoteEnabled,
            actionKey = AgendaNotificationHelper.REMINDER_DAILY_NOTE,
            time = settings.dailyNoteTime,
            route = Routes.EmotionalCheckin,
        )
        scheduled += scheduleOrCancel(
            context = context,
            enabled = settings.soberDayEnabled,
            actionKey = AgendaNotificationHelper.REMINDER_SOBER_DAY,
            time = settings.soberDayTime,
            route = Routes.PatientHome,
        )
        scheduled += scheduleOrCancel(
            context = context,
            enabled = settings.achievementEnabled,
            actionKey = AgendaNotificationHelper.REMINDER_ACHIEVEMENT,
            time = settings.achievementTime,
            route = Routes.Achievements,
        )
        scheduled += scheduleOrCancel(
            context = context,
            enabled = settings.motivationalEnabled,
            actionKey = AgendaNotificationHelper.REMINDER_MOTIVATIONAL,
            time = settings.motivationalTime,
            route = Routes.PatientHome,
        )

        events.forEach { event ->
            if (settings.eventRemindersEnabled) {
                val startsAtMillis = event.startsAtMillis() ?: return@forEach
                if (AgendaNotificationHelper.scheduleEventReminder(
                        context = context,
                        eventId = event.id,
                        startsAtMillis = startsAtMillis,
                        durationMinutes = event.durationMinutes,
                    )
                ) {
                    scheduled++
                }
            } else {
                AgendaNotificationHelper.cancelEventReminder(context, event.id)
            }
        }

        return scheduled
    }

    fun scheduleSupervisor(
        context: Context,
        @Suppress("UNUSED_PARAMETER")
        settings: NotificationSettingsData,
    ): Int {
        // El fallback local del supervisor permanece deshabilitado: solicitudes,
        // alertas clínicas y resúmenes de seguimiento deben llegar solo por FCM.
        SUPERVISOR_REMINDERS.forEach {
            AgendaNotificationHelper.cancelDailyReminder(context, it)
        }
        return 0
    }

    internal fun isLocalFallbackAllowed(actionKey: String): Boolean =
        actionKey in ALLOWED_LOCAL_REMINDERS

    fun cancelRoleReminders(context: Context, role: String?) {
        val keys = when (role) {
            ROLE_PATIENT -> PATIENT_REMINDERS
            ROLE_SUPERVISOR -> SUPERVISOR_REMINDERS
            else -> PATIENT_REMINDERS + SUPERVISOR_REMINDERS
        }
        keys.forEach { AgendaNotificationHelper.cancelDailyReminder(context, it) }
    }

    private fun scheduleOrCancel(
        context: Context,
        enabled: Boolean,
        actionKey: String,
        time: String,
        route: String,
    ): Int {
        if (!enabled) {
            AgendaNotificationHelper.cancelDailyReminder(context, actionKey)
            return 0
        }

        return if (AgendaNotificationHelper.scheduleDailyReminder(
                context = context,
                actionKey = actionKey,
                time = time,
                openRoute = route,
                expectedRole = expectedRole(actionKey),
            )
        ) {
            1
        } else {
            0
        }
    }

    private fun expectedRole(actionKey: String): String {
        return if (actionKey in SUPERVISOR_REMINDERS) ROLE_SUPERVISOR else ROLE_PATIENT
    }

    private fun AgendaEvent.startsAtMillis(): Long? {
        return runCatching {
            OffsetDateTime.parse(startsAt).toInstant().toEpochMilli()
        }.getOrNull()
            ?: runCatching {
                LocalDateTime.parse(startsAt)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            }.getOrNull()
    }

    const val ROLE_PATIENT = "patient"
    const val ROLE_SUPERVISOR = "supervisor"

    private val PATIENT_REMINDERS = setOf(
        AgendaNotificationHelper.REMINDER_DAILY_NOTE,
        AgendaNotificationHelper.REMINDER_SOBER_DAY,
        AgendaNotificationHelper.REMINDER_ACHIEVEMENT,
        AgendaNotificationHelper.REMINDER_MOTIVATIONAL,
    )
    private val SUPERVISOR_REMINDERS = setOf(
        AgendaNotificationHelper.REMINDER_SUPERVISOR_SUMMARY,
        AgendaNotificationHelper.REMINDER_SUPERVISOR_RELAPSE,
        AgendaNotificationHelper.REMINDER_SUPERVISOR_REQUESTS,
        AgendaNotificationHelper.REMINDER_SUPERVISOR_FOLLOW_UP,
    )
    private val ALLOWED_LOCAL_REMINDERS = PATIENT_REMINDERS
}
