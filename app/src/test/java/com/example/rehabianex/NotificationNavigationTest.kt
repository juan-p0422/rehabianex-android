package com.example.rehabianex

import com.example.rehabianex.navigation.Routes
import com.example.rehabianex.navigation.notificationRouteAllowed
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationNavigationTest {
    @Test
    fun patientNotification_onlyOpensPatientRoutes() {
        assertTrue(notificationRouteAllowed("patient", Routes.Agenda))
        assertTrue(notificationRouteAllowed("patient", Routes.EmotionalCheckin))
        assertFalse(notificationRouteAllowed("patient", Routes.PatientsList))
    }

    @Test
    fun supervisorNotification_onlyOpensSupervisorRoutes() {
        assertTrue(notificationRouteAllowed("supervisor", Routes.SupervisorHome))
        assertTrue(notificationRouteAllowed("supervisor", Routes.PatientsList))
        assertTrue(notificationRouteAllowed("supervisor", "${Routes.PatientDetail}/opaque-1"))
        assertFalse(notificationRouteAllowed("supervisor", "${Routes.PatientDetail}/../../login"))
        assertFalse(notificationRouteAllowed("supervisor", Routes.Agenda))
    }

    @Test
    fun notificationRoute_requiresSupportedAuthenticatedRole() {
        assertFalse(notificationRouteAllowed(null, Routes.PatientHome))
        assertFalse(notificationRouteAllowed("admin", Routes.PatientsList))
        assertTrue(notificationRouteAllowed("admin", Routes.AdminHome))
        assertFalse(notificationRouteAllowed("patient", Routes.AdminHome))
    }
}
