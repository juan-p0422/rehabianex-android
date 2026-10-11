package com.example.rehabianex.notifications

internal const val EVENT_REMINDER_ADVANCE_MINUTES = 30

internal data class AgendaReminderTiming(
    val triggerAtMillis: Long,
    val cleanupAtMillis: Long,
    val timeoutMillis: Long,
)

/**
 * Separa la duración real del evento de la anticipación del recordatorio.
 * La anticipación es una política fija; la duración solo determina cuándo
 * retirar el aviso.
 */
internal fun agendaReminderTiming(
    startsAtMillis: Long,
    durationMinutes: Int,
    nowMillis: Long = System.currentTimeMillis(),
): AgendaReminderTiming {
    val safeDurationMinutes = durationMinutes.coerceIn(1, MAX_EVENT_DURATION_MINUTES)
    val cleanupAtMillis = startsAtMillis + safeDurationMinutes * MINUTE_MILLIS
    val requestedTrigger = startsAtMillis - EVENT_REMINDER_ADVANCE_MINUTES * MINUTE_MILLIS
    val safetyTrigger = startsAtMillis - SAFETY_ADVANCE_MINUTES * MINUTE_MILLIS
    val triggerAtMillis = when {
        requestedTrigger > nowMillis -> requestedTrigger
        safetyTrigger > nowMillis -> safetyTrigger
        startsAtMillis > nowMillis -> nowMillis + MINIMUM_IMMEDIATE_DELAY_MILLIS
        else -> startsAtMillis
    }
    return AgendaReminderTiming(
        triggerAtMillis = triggerAtMillis,
        cleanupAtMillis = cleanupAtMillis,
        timeoutMillis = (cleanupAtMillis - triggerAtMillis).coerceAtLeast(MINUTE_MILLIS),
    )
}

private const val MINUTE_MILLIS = 60_000L
private const val SAFETY_ADVANCE_MINUTES = 5
private const val MAX_EVENT_DURATION_MINUTES = 24 * 60
private const val MINIMUM_IMMEDIATE_DELAY_MILLIS = 5_000L
