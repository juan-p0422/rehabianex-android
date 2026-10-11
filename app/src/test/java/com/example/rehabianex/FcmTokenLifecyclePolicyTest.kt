package com.example.rehabianex

import com.example.rehabianex.notifications.FCM_REGISTRATION_RETRY_DELAYS_MILLIS
import com.example.rehabianex.notifications.FcmTokenLifecycleState
import com.example.rehabianex.notifications.fcmSessionOwnerOrNull
import com.example.rehabianex.notifications.shouldRegisterFcmToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FcmTokenLifecyclePolicyTest {
    @Test
    fun lateOnNewToken_isRejectedWhileLogoutIsRunningOrSuspended() {
        val owner = requireNotNull(fcmSessionOwnerOrNull("test-patient", "patient"))

        assertFalse(
            shouldRegisterFcmToken(
                state = state(
                    logoutInProgress = true,
                    registrationSuspended = true,
                    activeOwnerFingerprint = owner.fingerprint,
                ),
                hasActiveSession = true,
                currentOwner = owner,
            ),
        )
        assertFalse(
            shouldRegisterFcmToken(
                state = state(
                    logoutInProgress = false,
                    registrationSuspended = true,
                ),
                hasActiveSession = false,
                currentOwner = null,
            ),
        )
    }

    @Test
    fun registration_requiresActiveSessionAndMatchingOwner() {
        val patient = requireNotNull(fcmSessionOwnerOrNull("test-user", "patient"))
        val supervisor = requireNotNull(fcmSessionOwnerOrNull("test-user", "supervisor"))

        assertTrue(
            shouldRegisterFcmToken(
                state = state(activeOwnerFingerprint = patient.fingerprint),
                hasActiveSession = true,
                currentOwner = patient,
            ),
        )
        assertFalse(
            shouldRegisterFcmToken(
                state = state(activeOwnerFingerprint = patient.fingerprint),
                hasActiveSession = true,
                currentOwner = supervisor,
            ),
        )
        assertFalse(
            shouldRegisterFcmToken(
                state = state(activeOwnerFingerprint = patient.fingerprint),
                hasActiveSession = false,
                currentOwner = patient,
            ),
        )
    }

    @Test
    fun ownerFingerprintChangesWithRoleAndDoesNotExposeUid() {
        val uid = "synthetic-sensitive-uid"
        val patient = requireNotNull(fcmSessionOwnerOrNull(uid, "patient"))
        val supervisor = requireNotNull(fcmSessionOwnerOrNull(uid, "supervisor"))

        assertNotEquals(patient.fingerprint, supervisor.fingerprint)
        assertFalse(patient.fingerprint.contains(uid))
        assertEquals(64, patient.fingerprint.length)
        assertNull(fcmSessionOwnerOrNull(uid, "guest"))
        assertNull(fcmSessionOwnerOrNull(null, "patient"))
        assertNotNull(fcmSessionOwnerOrNull("synthetic-admin", "admin"))
    }

    @Test
    fun registrationBackoff_isLimitedToOfficialDelays() {
        assertEquals(
            listOf(1_000L, 5_000L, 15_000L, 60_000L),
            FCM_REGISTRATION_RETRY_DELAYS_MILLIS,
        )
    }

    private fun state(
        logoutInProgress: Boolean = false,
        registrationSuspended: Boolean = false,
        activeOwnerFingerprint: String? = null,
    ) = FcmTokenLifecycleState(
        logoutInProgress = logoutInProgress,
        registrationSuspended = registrationSuspended,
        activeOwnerFingerprint = activeOwnerFingerprint,
        lastRegisteredOwnerFingerprint = null,
    )
}
