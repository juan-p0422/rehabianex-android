package com.example.rehabianex.notifications

import android.content.Context
import androidx.core.content.edit
import java.security.MessageDigest

object FcmNotificationDeduplicator {
    fun isDuplicate(
        context: Context,
        type: String,
        notificationId: String?,
        entityId: String?,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        val preferences = preferences(context)
        removeExpired(preferences, nowMillis)
        return fcmDeliveryKeys(type, notificationId, entityId).any { key ->
            val deliveredAt = preferences.getLong(key, 0L)
            deliveredAt > 0L && nowMillis - deliveredAt in 0..RETENTION_MILLIS
        }
    }

    fun recordRemote(
        context: Context,
        type: String,
        notificationId: String?,
        entityId: String?,
        deliveredAtMillis: Long = System.currentTimeMillis(),
    ) {
        val deliveryKeys = fcmDeliveryKeys(type, notificationId, entityId)
        if (deliveryKeys.isEmpty()) return
        preferences(context).edit {
            deliveryKeys.forEach { putLong(it, deliveredAtMillis) }
        }
    }

    fun recordLocalAppointment(
        context: Context,
        entityId: String,
        deliveredAtMillis: Long = System.currentTimeMillis(),
    ) {
        val safeEntityId = entityId.safeEntityIdOrNull() ?: return
        preferences(context).edit {
            putLong(localAppointmentDeliveryKey(safeEntityId), deliveredAtMillis)
        }
    }

    fun clear(context: Context) {
        preferences(context).edit { clear() }
    }

    private fun removeExpired(
        preferences: android.content.SharedPreferences,
        nowMillis: Long,
    ) {
        val expiredKeys = preferences.all.mapNotNull { (key, value) ->
            val deliveredAt = value as? Long ?: return@mapNotNull key
            key.takeIf { deliveredAt <= 0L || nowMillis - deliveredAt > RETENTION_MILLIS }
        }
        if (expiredKeys.isNotEmpty()) {
            preferences.edit { expiredKeys.forEach { remove(it) } }
        }
    }

    private fun preferences(context: Context) = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    private const val PREFERENCES_NAME = "rehabianex_fcm_delivery_state"
    private const val RETENTION_MILLIS = 48 * 60 * 60_000L
}

internal fun fcmDeliveryKeys(
    type: String,
    notificationId: String?,
    entityId: String?,
): Set<String> = buildSet {
    notificationId.safeNotificationIdentifierOrNull()?.let { add(hashedDeliveryKey("remote", it)) }
    if (type == FcmContract.TYPE_APPOINTMENT_REMINDER) {
        entityId.safeEntityIdOrNull()?.let { add(localAppointmentDeliveryKey(it)) }
    }
}

internal fun localAppointmentDeliveryKey(entityId: String): String =
    hashedDeliveryKey("appointment", entityId)

private fun hashedDeliveryKey(namespace: String, value: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest("$namespace|$value".toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    return "delivery_$digest"
}

private fun String?.safeNotificationIdentifierOrNull(): String? = this
    ?.trim()
    ?.takeIf {
        it.isNotEmpty() && it.length <= MAX_NOTIFICATION_ID_LENGTH &&
            it.none { char -> char.isISOControl() }
    }

private const val MAX_NOTIFICATION_ID_LENGTH = 256
