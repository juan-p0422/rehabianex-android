package com.example.rehabianex

import com.example.rehabianex.data.model.SessionProfileDto
import com.example.rehabianex.data.repository.isAuthorizedSupervisor
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupervisorAuthorizationTest {
    @Test
    fun explicitAuthorizationAndVerification_areRequiredWhenBothArrive() {
        assertTrue(
            SessionProfileDto(
                status = "active",
                authorized = true,
                verified = true,
            ).isAuthorizedSupervisor(),
        )
        assertFalse(
            SessionProfileDto(
                status = "active",
                authorized = true,
                verified = false,
            ).isAuthorizedSupervisor(),
        )
        assertFalse(
            SessionProfileDto(
                status = "active",
                authorized = false,
                verified = true,
            ).isAuthorizedSupervisor(),
        )
    }

    @Test
    fun missingOrInactiveContractFields_neverAuthorizeSupervisor() {
        assertFalse(SessionProfileDto(status = "active").isAuthorizedSupervisor())
        assertFalse(SessionProfileDto(status = "disabled").isAuthorizedSupervisor())
        assertFalse(
            SessionProfileDto(
                status = "active",
                authorized = true,
            ).isAuthorizedSupervisor(),
        )
        assertFalse(
            SessionProfileDto(
                status = "active",
                verified = false,
            ).isAuthorizedSupervisor(),
        )
        assertFalse(
            SessionProfileDto(
                status = "pending",
                authorized = true,
                verified = true,
            ).isAuthorizedSupervisor(),
        )
    }
}
