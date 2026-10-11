package com.example.rehabianex

import com.example.rehabianex.notifications.EVENT_REMINDER_ADVANCE_MINUTES
import com.example.rehabianex.notifications.agendaReminderTiming
import org.junit.Assert.assertEquals
import org.junit.Test

class AgendaReminderPolicyTest {
    @Test
    fun futureEvent_usesFixedAdvanceAndDurationOnlyForCleanup() {
        val now = 1_000_000L
        val startsAt = now + 2 * HOUR_MILLIS

        val timing = agendaReminderTiming(
            startsAtMillis = startsAt,
            durationMinutes = 60,
            nowMillis = now,
        )

        assertEquals(startsAt - EVENT_REMINDER_ADVANCE_MINUTES * MINUTE_MILLIS, timing.triggerAtMillis)
        assertEquals(startsAt + 60 * MINUTE_MILLIS, timing.cleanupAtMillis)
        assertEquals(90 * MINUTE_MILLIS, timing.timeoutMillis)
    }

    @Test
    fun nearEvent_usesFiveMinuteSafetyAdvanceInsteadOfEventDuration() {
        val now = 1_000_000L
        val startsAt = now + 20 * MINUTE_MILLIS

        val timing = agendaReminderTiming(
            startsAtMillis = startsAt,
            durationMinutes = 120,
            nowMillis = now,
        )

        assertEquals(startsAt - 5 * MINUTE_MILLIS, timing.triggerAtMillis)
        assertEquals(startsAt + 120 * MINUTE_MILLIS, timing.cleanupAtMillis)
    }

    @Test
    fun eventStartingSoon_schedulesShortSafeDelay() {
        val now = 1_000_000L
        val startsAt = now + 2 * MINUTE_MILLIS

        val timing = agendaReminderTiming(
            startsAtMillis = startsAt,
            durationMinutes = 30,
            nowMillis = now,
        )

        assertEquals(now + 5_000L, timing.triggerAtMillis)
    }

    private companion object {
        const val MINUTE_MILLIS = 60_000L
        const val HOUR_MILLIS = 60 * MINUTE_MILLIS
    }
}
