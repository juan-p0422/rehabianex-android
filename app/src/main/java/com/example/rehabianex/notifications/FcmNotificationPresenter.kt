package com.example.rehabianex.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.rehabianex.MainActivity
import com.example.rehabianex.R

object FcmNotificationPresenter {
    const val CHANNEL_ID = "rehabianex_reminders"
    private const val CHANNEL_NAME = "Recordatorios de Rehabianex"

    fun show(
        context: Context,
        role: String?,
        type: String?,
        title: String?,
        body: String?,
        requestedRoute: String?,
        entityId: String?,
        priority: String?,
        remoteNotificationId: String?,
        messageId: String?,
    ): Boolean {
        val safeType = validatedFcmType(type) ?: return false
        val route = safeFcmRoute(role, safeType, requestedRoute, entityId) ?: return false
        if (!canPostNotifications(context)) return false

        ensureChannel(context)
        val content = safeFcmNotificationContent(safeType, title, body)
        val notificationPriority = when (validatedFcmPriority(priority)) {
            "high", "critical" -> NotificationCompat.PRIORITY_HIGH
            else -> NotificationCompat.PRIORITY_DEFAULT
        }
        val notificationId = stableNotificationId(
            remoteNotificationId,
            entityId,
            type,
            messageId,
        )
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AgendaNotificationHelper.EXTRA_OPEN_ROUTE, route)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(content.title)
            .setContentText(content.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(notificationPriority)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
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

    private fun canPostNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Avisos remotos discretos de RehabiAnex."
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun stableNotificationId(vararg candidates: String?): Int {
        val seed = candidates.firstNotNullOfOrNull { value ->
            value?.trim()?.take(MAX_IDENTIFIER_LENGTH)?.takeIf(String::isNotEmpty)
        } ?: DEFAULT_NOTIFICATION_KEY
        return seed.hashCode() and Int.MAX_VALUE
    }

    private const val MAX_IDENTIFIER_LENGTH = 256
    private const val DEFAULT_NOTIFICATION_KEY = "rehabianex-fcm-notification"
}
