package com.example.rehabianex

import com.example.rehabianex.navigation.Routes
import com.example.rehabianex.ui.components.supervisorNavigationRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SupervisorChatNavigationTest {
    @Test
    fun fullSupervisorNavigationIncludesHomePatientsAndChat() {
        assertEquals(
            listOf(Routes.SupervisorHome, Routes.PatientsList, Routes.AiChat),
            supervisorNavigationRoutes(),
        )
    }

    @Test
    fun limitedSupervisorNavigationDoesNotExposeSensitiveDestinations() {
        val routes = supervisorNavigationRoutes(limitedAccess = true)

        assertEquals(listOf(Routes.SupervisorHome), routes)
        assertTrue(Routes.PatientsList !in routes)
        assertTrue(Routes.AiChat !in routes)
    }
}
