package com.example.rehabianex

import com.example.rehabianex.ui.components.NOTIFICATION_PERMISSION_DISABLED_MESSAGE
import com.example.rehabianex.ui.components.shouldShowNotificationPermissionBanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPermissionBannerPolicyTest {
    @Test
    fun android13Denied_showsPersistentBanner() {
        assertTrue(shouldShowNotificationPermissionBanner(sdkInt = 33, permissionGranted = false))
    }

    @Test
    fun android13Granted_hidesBanner() {
        assertFalse(shouldShowNotificationPermissionBanner(sdkInt = 33, permissionGranted = true))
    }

    @Test
    fun preAndroid13_doesNotRequireRuntimePermissionBanner() {
        assertFalse(shouldShowNotificationPermissionBanner(sdkInt = 32, permissionGranted = false))
    }

    @Test
    fun bannerUsesApprovedPersistentExplanation() {
        assertEquals(
            "Las notificaciones están desactivadas. " +
                "Puedes habilitarlas desde Ajustes para recibir recordatorios.",
            NOTIFICATION_PERMISSION_DISABLED_MESSAGE,
        )
    }
}
