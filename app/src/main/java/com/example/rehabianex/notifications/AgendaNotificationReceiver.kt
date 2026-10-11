package com.example.rehabianex.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.rehabianex.AppContainer
import com.example.rehabianex.data.repository.PatientDataResult
import com.example.rehabianex.data.repository.SupervisorDataResult
import com.example.rehabianex.navigation.Routes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AgendaNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getStringExtra(AgendaNotificationHelper.EXTRA_ACTION_TYPE)) {
            AgendaNotificationHelper.ACTION_EVENT_CLEANUP -> cleanupEvent(context, intent)
            AgendaNotificationHelper.ACTION_SUMMARY -> {
                if (hasExpectedSession(context, NotificationScheduler.ROLE_PATIENT)) {
                    showSummary(context, intent)
                }
            }
            AgendaNotificationHelper.ACTION_GENERIC_REMINDER -> {
                val actionKey = intent.getStringExtra(AgendaNotificationHelper.EXTRA_ACTION_KEY)
                    .orEmpty()
                if (!NotificationScheduler.isLocalFallbackAllowed(actionKey)) {
                    AgendaNotificationHelper.cancelDailyReminder(context, actionKey)
                    return
                }
                val expectedRole = intent.getStringExtra(
                    AgendaNotificationHelper.EXTRA_EXPECTED_ROLE,
                ) ?: expectedRoleFor(actionKey)
                if (!hasExpectedSession(context, expectedRole)) return

                when (actionKey) {
                    AgendaNotificationHelper.REMINDER_ACHIEVEMENT -> checkAchievements(context, intent)
                    AgendaNotificationHelper.REMINDER_SUPERVISOR_SUMMARY ->
                        checkSupervisorReminder(context, intent)
                    else -> showGenericReminder(context, intent)
                }
            }
            else -> {
                if (hasExpectedSession(context, NotificationScheduler.ROLE_PATIENT)) {
                    showEventReminder(context, intent)
                }
            }
        }
    }

    private fun showSummary(context: Context, intent: Intent) {
        val periodLabel = intent.getStringExtra(AgendaNotificationHelper.EXTRA_PERIOD_LABEL)
            ?: "hoy"
        val eventCount = intent.getIntExtra(
            AgendaNotificationHelper.EXTRA_EVENT_COUNT,
            0,
        )
        AgendaNotificationHelper.showSummaryNotification(context, periodLabel, eventCount)
    }

    private fun showEventReminder(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(
            AgendaNotificationHelper.EXTRA_NOTIFICATION_ID,
            1,
        )
        val timeoutMillis = intent.getLongExtra(
            AgendaNotificationHelper.EXTRA_TIMEOUT_MILLIS,
            60 * 60_000L,
        )
        val shown = AgendaNotificationHelper.showEventNotification(
            context = context,
            notificationId = notificationId,
            title = NEUTRAL_TITLE,
            message = "Tienes un recordatorio de agenda. Abre la app para consultar los detalles.",
            timeoutMillis = timeoutMillis,
        )
        if (shown) {
            intent.getStringExtra(AgendaNotificationHelper.EXTRA_EVENT_ID)
                .safeEntityIdOrNull()
                ?.let { FcmNotificationDeduplicator.recordLocalAppointment(context, it) }
        }
    }

    private fun showGenericReminder(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(
            AgendaNotificationHelper.EXTRA_NOTIFICATION_ID,
            1,
        )
        val actionKey = intent.getStringExtra(AgendaNotificationHelper.EXTRA_ACTION_KEY).orEmpty()
        val route = intent.getStringExtra(AgendaNotificationHelper.EXTRA_OPEN_NOTIFICATION_ROUTE)
            ?: Routes.PatientHome
        val content = neutralContent(actionKey)

        AgendaNotificationHelper.showGenericNotification(
            context = context,
            notificationId = notificationId,
            title = content.first,
            message = content.second,
            route = route,
        )
        rescheduleDaily(context, intent, actionKey, route)
    }

    private fun checkAchievements(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val notificationId = intent.getIntExtra(
            AgendaNotificationHelper.EXTRA_NOTIFICATION_ID,
            1,
        )

        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val result = AppContainer(appContext).patientRepository.loadAchievementsData()
                if (result is PatientDataResult.Success) {
                    val unlockedCount = result.data.achievements.count { it.isUnlocked }
                    val preferences = appContext.getSharedPreferences(
                        NOTIFICATION_STATE_PREFERENCES,
                        Context.MODE_PRIVATE,
                    )
                    val previousCount = preferences.getInt(LAST_ACHIEVEMENT_COUNT, -1)

                    if (previousCount >= 0 && unlockedCount > previousCount) {
                        AgendaNotificationHelper.showGenericNotification(
                            context = appContext,
                            notificationId = notificationId,
                            title = NEUTRAL_TITLE,
                            message = "Tienes una novedad disponible. Abre la app para consultarla.",
                            route = Routes.Achievements,
                        )
                    }
                    preferences.edit()
                        .putInt(LAST_ACHIEVEMENT_COUNT, unlockedCount)
                        .apply()
                }
            }
            rescheduleDaily(appContext, intent, AgendaNotificationHelper.REMINDER_ACHIEVEMENT, Routes.Achievements)
            pendingResult.finish()
        }
    }

    private fun checkSupervisorReminder(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val notificationId = intent.getIntExtra(
            AgendaNotificationHelper.EXTRA_NOTIFICATION_ID,
            1,
        )
        val actionKey = intent.getStringExtra(AgendaNotificationHelper.EXTRA_ACTION_KEY).orEmpty()
        val route = intent.getStringExtra(AgendaNotificationHelper.EXTRA_OPEN_NOTIFICATION_ROUTE)
            ?: Routes.SupervisorHome

        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val result = AppContainer(appContext).supervisorRepository.loadHomeData()
                if (result is SupervisorDataResult.Success && shouldNotifySupervisor(actionKey, result.data)) {
                    val content = neutralContent(actionKey)
                    AgendaNotificationHelper.showGenericNotification(
                        context = appContext,
                        notificationId = notificationId,
                        title = content.first,
                        message = content.second,
                        route = route,
                    )
                }
            }
            rescheduleDaily(appContext, intent, actionKey, route)
            pendingResult.finish()
        }
    }

    private fun shouldNotifySupervisor(
        actionKey: String,
        home: com.example.rehabianex.data.model.SupervisorHomeData,
    ): Boolean {
        return when (actionKey) {
            AgendaNotificationHelper.REMINDER_SUPERVISOR_SUMMARY -> home.patients.isNotEmpty()
            else -> false
        }
    }

    private fun rescheduleDaily(
        context: Context,
        intent: Intent,
        actionKey: String,
        route: String,
    ) {
        if (!intent.getBooleanExtra(AgendaNotificationHelper.EXTRA_REPEAT_DAILY, false)) return
        val hour = intent.getIntExtra(AgendaNotificationHelper.EXTRA_HOUR, -1)
        val minute = intent.getIntExtra(AgendaNotificationHelper.EXTRA_MINUTE, -1)
        val expectedRole = intent.getStringExtra(AgendaNotificationHelper.EXTRA_EXPECTED_ROLE)
            ?: expectedRoleFor(actionKey)
        if (actionKey.isBlank() || hour !in 0..23 || minute !in 0..59) return

        AgendaNotificationHelper.scheduleDailyReminder(
            context = context,
            actionKey = actionKey,
            time = "%02d:%02d".format(hour, minute),
            openRoute = route,
            expectedRole = expectedRole,
        )
    }

    private fun cleanupEvent(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(
            AgendaNotificationHelper.EXTRA_NOTIFICATION_ID,
            1,
        )
        // El vencimiento de una notificación nunca debe modificar la agenda ni llamar al backend.
        AgendaNotificationHelper.cancelEventNotification(context, notificationId)
    }

    private fun hasExpectedSession(context: Context, expectedRole: String): Boolean {
        val sessionManager = AppContainer(context.applicationContext).sessionManager
        return sessionManager.hasActiveSession() && sessionManager.getRole() == expectedRole
    }

    private fun expectedRoleFor(actionKey: String): String {
        return if (actionKey.startsWith("supervisor_")) {
            NotificationScheduler.ROLE_SUPERVISOR
        } else {
            NotificationScheduler.ROLE_PATIENT
        }
    }

    private fun neutralContent(actionKey: String): Pair<String, String> {
        val message = if (actionKey.startsWith("supervisor_")) {
            "Hay novedades de seguimiento disponibles. Abre la app para revisarlas."
        } else {
            "Tienes un recordatorio configurado. Abre la app para continuar."
        }
        return NEUTRAL_TITLE to message
    }

    private companion object {
        const val NEUTRAL_TITLE = "Recordatorio RehabiAnex"
        const val NOTIFICATION_STATE_PREFERENCES = "rehabianex_notifications"
        const val LAST_ACHIEVEMENT_COUNT = "last_unlocked_achievement_count"
    }
}
