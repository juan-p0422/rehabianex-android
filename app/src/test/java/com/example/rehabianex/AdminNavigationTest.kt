package com.example.rehabianex

import com.example.rehabianex.navigation.Routes
import com.example.rehabianex.navigation.homeRouteForRole
import com.example.rehabianex.navigation.activeSessionHomeRoute
import com.example.rehabianex.navigation.postRegistrationRoute
import com.example.rehabianex.navigation.shouldRedirectToPrivacyGate
import com.example.rehabianex.navigation.patientSupervisionSetupRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminNavigationTest {
    @Test
    fun protectedRoutesRedirectToPrivacyGateUntilServerGrantsAccess() {
        assertTrue(shouldRedirectToPrivacyGate(true, false, Routes.PatientHome))
        assertTrue(shouldRedirectToPrivacyGate(true, false, Routes.AdminHome))
        assertFalse(shouldRedirectToPrivacyGate(true, false, Routes.PrivacyGate))
        assertFalse(shouldRedirectToPrivacyGate(true, false, Routes.PrivacyNotice))
        assertFalse(shouldRedirectToPrivacyGate(true, true, Routes.PatientHome))
        assertFalse(shouldRedirectToPrivacyGate(false, false, Routes.Register))
    }

    @Test
    fun adminRole_hasDedicatedHomeRoute() {
        assertEquals(Routes.AdminHome, homeRouteForRole("admin"))
    }

    @Test
    fun existingRoles_keepTheirHomeRoutes() {
        assertEquals(Routes.PatientHome, homeRouteForRole("patient"))
        assertEquals(Routes.SupervisorHome, homeRouteForRole("supervisor"))
    }

    @Test
    fun unknownRole_hasNoHomeRoute() {
        assertNull(homeRouteForRole("unknown"))
    }

    @Test
    fun activeSession_skipsRoleSelectorAndUsesConfirmedRole() {
        assertEquals(Routes.PatientHome, activeSessionHomeRoute(true, "patient"))
        assertEquals(Routes.SupervisorHome, activeSessionHomeRoute(true, "supervisor"))
        assertEquals(Routes.AdminHome, activeSessionHomeRoute(true, "admin"))
        assertNull(activeSessionHomeRoute(false, "patient"))
    }

    @Test
    fun registration_usesConfirmedBackendRole() {
        assertEquals(Routes.Consent, postRegistrationRoute("patient"))
        assertEquals(Routes.SupervisorHome, postRegistrationRoute("supervisor"))
        assertEquals(Routes.AdminHome, postRegistrationRoute("admin"))
        assertNull(postRegistrationRoute("unknown"))
    }

    @Test
    fun patientSupervisionChoice_hasDifferentDestinations() {
        assertEquals(Routes.SupervisorLink, patientSupervisionSetupRoute(configureNow = true))
        assertEquals(Routes.PatientHome, patientSupervisionSetupRoute(configureNow = false))
    }
}
