package com.example.rehabianex.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.rehabianex.MainActivity
import com.example.rehabianex.R
import com.example.rehabianex.navigation.Routes
import kotlin.math.absoluteValue

object AgendaNotificationHelper {
    const val EXTRA_OPEN_ROUTE = "com.example.rehabianex.extra.OPEN_ROUTE"
    const val EXTRA_ACTION_TYPE = "extra_action_type"
    const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    const val EXTRA_EVENT_ID = "extra_event_id"
    const val EXTRA_TIMEOUT_MILLIS = "extra_timeout_millis"
    const val EXTRA_PERIOD_LABEL = "extra_period_label"
    const val EXTRA_EVENT_COUNT = "extra_event_count"
    const val EXTRA_OPEN_NOTIFICATION_ROUTE = "extra_open_notification_route"
    const val EXTRA_REPEAT_DAILY = "extra_repeat_daily"
    const val EXTRA_ACTION_KEY = "extra_action_key"
    const val EXTRA_HOUR = "extra_hour"
    const val EXTRA_MINUTE = "extra_minute"
    const val EXTRA_EXPECTED_ROLE = "extra_expected_role"

    const val ACTION_EVENT_REMINDER = "event_reminder"
    const val ACTION_EVENT_CLEANUP = "event_cleanup"
    const val ACTION_SUMMARY = "summary"
    const val ACTION_GENERIC_REMINDER = "generic_reminder"

    const val REMINDER_DAILY_NOTE = "daily_note"
    const val REMINDER_SOBER_DAY = "sober_day"
    const val REMINDER_ACHIEVEMENT = "achievement"
    const val REMINDER_MOTIVATIONAL = "motivational"
    const val REMINDER_SUPERVISOR_SUMMARY = "supervisor_summary"
    const val REMINDER_SUPERVISOR_RELAPSE = "supervisor_relapse"
    const val REMINDER_SUPERVISOR_REQUESTS = "supervisor_requests"
    const val REMINDER_SUPERVISOR_FOLLOW_UP = "supervisor_follow_up"

    private const val CHANNEL_ID = "agenda_events"
    private const val CHANNEL_NAME = "Agenda y eventos"
    private const val SUMMARY_NOTIFICATION_ID = 3001
    private const val MINUTE_MILLIS = 60_000L

    fun canPostNotifications(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
    }

    fun showSummaryNotification(
        context: Context,
        periodLabel: String,
        eventCount: Int,
    ): Boolean {
        if (eventCount <= 0 || !canPostNotifications(context)) return false

        return showNotification(
            context = context,
            notificationId = SUMMARY_NOTIFICATION_ID,
            title = "Recordatorio RehabiAnex",
            message = "Tienes novedades en tu agenda. Abre la app para consultarlas.",
            timeoutMillis = 24 * 60 * MINUTE_MILLIS,
        )
    }

    fun scheduleSummaryNotification(
        context: Context,
        periodLabel: String,
        eventCount: Int,
        triggerAtMillis: Long,
    ): Boolean {
        if (eventCount <= 0) return false

        val requestCode = "summary-$periodLabel-$triggerAtMillis".stableNotificationId()
        val intent = Intent(context, AgendaNotificationReceiver::class.java).apply {
            putExtra(EXTRA_ACTION_TYPE, ACTION_SUMMARY)
            putExtra(EXTRA_PERIOD_LABEL, periodLabel)
            putExtra(EXTRA_EVENT_COUNT, eventCount)
        }
        scheduleAlarm(
            context = context,
            requestCode = requestCode,
            intent = intent,
            triggerAtMillis = triggerAtMillis,
        )
        return true
    }

    fun scheduleDailyReminder(
        context: Context,
        actionKey: String,
        time: String,
        openRoute: String,
        expectedRole: String,
    ): Boolean {
        val (hour, minute) = time.toHourMinuteOrNull() ?: return false
        val notificationId = appReminderNotificationId(actionKey)
        val intent = Intent(context, AgendaNotificationReceiver::class.java).apply {
            putExtra(EXTRA_ACTION_TYPE, ACTION_GENERIC_REMINDER)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(EXTRA_OPEN_NOTIFICATION_ROUTE, openRoute)
            putExtra(EXTRA_REPEAT_DAILY, true)
            putExtra(EXTRA_ACTION_KEY, actionKey)
            putExtra(EXTRA_HOUR, hour)
            putExtra(EXTRA_MINUTE, minute)
            putExtra(EXTRA_EXPECTED_ROLE, expectedRole)
        }
        scheduleAlarm(
            context = context,
            requestCode = appReminderRequestCode(actionKey),
            intent = intent,
            triggerAtMillis = nextDailyTriggerMillis(hour, minute),
        )
        return true
    }

    fun cancelDailyReminder(context: Context, actionKey: String) {
        cancelBroadcast(context, appReminderRequestCode(actionKey))
        NotificationManagerCompat.from(context).cancel(appReminderNotificationId(actionKey))
    }

    fun scheduleEventReminder(
        context: Context,
        eventId: String,
        startsAtMillis: Long,
        durationMinutes: Int,
    ): Boolean {
        if (eventId.isBlank() || startsAtMillis <= System.currentTimeMillis()) return false

        val timing = agendaReminderTiming(
            startsAtMillis = startsAtMillis,
            durationMinutes = durationMinutes,
        )
        val notificationId = eventNotificationId(eventId)
        val intent = Intent(context, AgendaNotificationReceiver::class.java).apply {
            putExtra(EXTRA_ACTION_TYPE, ACTION_EVENT_REMINDER)
            putExtra(EXTRA_EVENT_ID, eventId)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(EXTRA_TIMEOUT_MILLIS, timing.timeoutMillis)
            putExtra(EXTRA_EXPECTED_ROLE, NotificationScheduler.ROLE_PATIENT)
        }

        scheduleAlarm(
            context = context,
            requestCode = eventReminderRequestCode(eventId),
            intent = intent,
            triggerAtMillis = timing.triggerAtMillis,
        )
        scheduleEventCleanupAt(
            context = context,
            eventId = eventId,
            cleanupAtMillis = timing.cleanupAtMillis,
            notificationId = notificationId,
        )
        return true
    }

    fun scheduleManualEventCleanup(
        context: Context,
        eventId: String,
        delayMinutes: Int = 30,
    ): Boolean {
        if (eventId.isBlank()) return false

        scheduleEventCleanupAt(
            context = context,
            eventId = eventId,
            cleanupAtMillis = System.currentTimeMillis() + delayMinutes.coerceAtLeast(1) * MINUTE_MILLIS,
            notificationId = eventNotificationId(eventId),
        )
        return true
    }

    fun cancelEventReminder(context: Context, eventId: String) {
        cancelBroadcast(context, eventReminderRequestCode(eventId))
        cancelBroadcast(context, eventCleanupRequestCode(eventId))
        NotificationManagerCompat.from(context).cancel(eventNotificationId(eventId))
    }

    internal fun showEventNotification(
        context: Context,
        notificationId: Int,
        title: String,
        message: String,
        timeoutMillis: Long,
    ): Boolean {
        return showNotification(
            context = context,
            notificationId = notificationId,
            title = title,
            message = message,
            timeoutMillis = timeoutMillis,
        )
    }

    internal fun cancelEventNotification(
        context: Context,
        notificationId: Int,
    ) {
        NotificationManagerCompat.from(context).cancel(notificationId)
    }

    internal fun showGenericNotification(
        context: Context,
        notificationId: Int,
        title: String,
        message: String,
        route: String,
    ): Boolean {
        return showNotification(
            context = context,
            notificationId = notificationId,
            title = title,
            message = message,
            timeoutMillis = 24 * 60 * MINUTE_MILLIS,
            openRoute = route,
        )
    }

    private fun scheduleEventCleanupAt(
        context: Context,
        eventId: String,
        cleanupAtMillis: Long,
        notificationId: Int,
    ) {
        val intent = Intent(context, AgendaNotificationReceiver::class.java).apply {
            putExtra(EXTRA_ACTION_TYPE, ACTION_EVENT_CLEANUP)
            putExtra(EXTRA_EVENT_ID, eventId)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
        scheduleAlarm(
            context = context,
            requestCode = eventCleanupRequestCode(eventId),
            intent = intent,
            triggerAtMillis = cleanupAtMillis,
        )
    }

    private fun scheduleAlarm(
        context: Context,
        requestCode: Int,
        intent: Intent,
        triggerAtMillis: Long,
    ) {
        val safeTriggerAt = triggerAtMillis.coerceAtLeast(System.currentTimeMillis() + 1_000L)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, safeTriggerAt, pendingIntent)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, safeTriggerAt, pendingIntent)
        }
    }

    private fun cancelBroadcast(
        context: Context,
        requestCode: Int,
    ) {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, AgendaNotificationReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        pendingIntent?.cancel()
    }

    private fun showNotification(
        context: Context,
        notificationId: Int,
        title: String,
        message: String,
        timeoutMillis: Long?,
        openRoute: String = Routes.Agenda,
    ): Boolean {
        if (!canPostNotifications(context)) return false

        ensureChannel(context)
        val logoRes = notificationLogoRes(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(logoRes)
            .setLargeIcon(BitmapFactory.decodeResource(context.resources, logoRes))
            .setContentTitle(title)
            .setContentText(message.lineSequence().firstOrNull().orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(openRoutePendingIntent(context, notificationId, openRoute))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .apply {
                timeoutMillis?.let { setTimeoutAfter(it) }
            }
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        return try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Recordatorios configurados en RehabiAnex."
        }

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun notificationLogoRes(context: Context): Int {
        val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return if (nightMode == Configuration.UI_MODE_NIGHT_YES) {
            R.drawable.logo_sf
        } else {
            R.drawable.logo_inv_sf
        }
    }

    private fun openRoutePendingIntent(
        context: Context,
        requestCode: Int,
        route: String,
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_ROUTE, route)
        }

        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun eventNotificationId(eventId: String): Int {
        return "event-notification-$eventId".stableNotificationId()
    }

    private fun eventReminderRequestCode(eventId: String): Int {
        return "event-reminder-$eventId".stableNotificationId()
    }

    private fun eventCleanupRequestCode(eventId: String): Int {
        return "event-cleanup-$eventId".stableNotificationId()
    }

    private fun appReminderRequestCode(actionKey: String): Int {
        return "patient-reminder-$actionKey".stableNotificationId()
    }

    private fun appReminderNotificationId(actionKey: String): Int {
        return "patient-reminder-notification-$actionKey".stableNotificationId()
    }

    private fun nextDailyTriggerMillis(hour: Int, minute: Int): Long {
        val now = java.time.ZonedDateTime.now()
        var next = now
            .withHour(hour)
            .withMinute(minute)
            .withSecond(0)
            .withNano(0)
        if (!next.isAfter(now)) {
            next = next.plusDays(1)
        }
        return next.toInstant().toEpochMilli()
    }

    private fun String.toHourMinuteOrNull(): Pair<Int, Int>? {
        val parts = split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull()?.takeIf { it in 0..23 } ?: return null
        val minute = parts[1].toIntOrNull()?.takeIf { it in 0..59 } ?: return null
        return hour to minute
    }

    private fun String.stableNotificationId(): Int {
        return hashCode().absoluteValue.takeIf { it != 0 } ?: 1
    }
}
