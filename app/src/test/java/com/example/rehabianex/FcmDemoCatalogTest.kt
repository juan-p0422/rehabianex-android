package com.example.rehabianex

import com.example.rehabianex.notifications.FcmDemoCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FcmDemoCatalogTest {
    @Test
    fun `catalog exposes the eighteen supported types exactly once`() {
        assertEquals(18, FcmDemoCatalog.cases.size)
        assertEquals(18, FcmDemoCatalog.cases.map { it.type }.toSet().size)
    }

    @Test
    fun `role filtering exposes only compatible demonstrations`() {
        assertEquals(8, FcmDemoCatalog.forRole(FcmDemoCatalog.ROLE_PATIENT).size)
        assertEquals(9, FcmDemoCatalog.forRole(FcmDemoCatalog.ROLE_SUPERVISOR).size)
        assertEquals(5, FcmDemoCatalog.forRole(FcmDemoCatalog.ROLE_ADMIN).size)
        assertTrue(FcmDemoCatalog.isAllowed(FcmDemoCatalog.ROLE_PATIENT, "appointment_reminder"))
        assertFalse(FcmDemoCatalog.isAllowed(FcmDemoCatalog.ROLE_PATIENT, "relapse_alert"))
        assertFalse(FcmDemoCatalog.isAllowed(null, "test_notification"))
    }

    @Test
    fun `all visible demo texts are canonical and generic`() {
        FcmDemoCatalog.cases.forEach { demoCase ->
            assertTrue(demoCase.body.isNotBlank())
            assertFalse(demoCase.body.contains('@'))
            assertFalse(demoCase.body.contains("paciente", ignoreCase = true))
        }
    }
}
