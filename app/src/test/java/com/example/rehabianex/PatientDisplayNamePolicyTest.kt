package com.example.rehabianex

import com.example.rehabianex.data.model.AuthData
import com.example.rehabianex.data.model.SessionProfileDto
import com.example.rehabianex.data.model.resolveAuthSessionDisplayName
import com.example.rehabianex.data.model.resolvedPatientDisplayName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PatientDisplayNamePolicyTest {
    @Test
    fun anonymousHomeDisplayIgnoresRealFullName() {
        val profile = SessionProfileDto(
            fullName = "Nombre Real Sensible",
            displayName = "Paciente anónimo",
            safeDisplayName = "Paciente anónimo",
            isAnonymous = true,
            privacyMode = true,
        )

        val displayName = profile.resolvedPatientDisplayName("Nombre Real de sesión")

        assertEquals("Paciente anónimo", displayName)
        assertFalse(displayName.contains("Nombre Real"))
    }

    @Test
    fun privacyModeUsesAllowedNicknameAndNeverFullName() {
        val profile = SessionProfileDto(
            fullName = "Nombre Real Sensible",
            nickname = "Luz",
            privacyMode = true,
            isAnonymous = false,
        )

        assertEquals("Luz", profile.resolvedPatientDisplayName("Nombre Real de sesión"))
    }

    @Test
    fun missingSafeAliasesFallsBackToNeutralAnonymousName() {
        val displayName = resolveAuthSessionDisplayName(
            role = "patient",
            auth = AuthData(fullName = "Nombre Real Sensible", privacyMode = true),
            profile = SessionProfileDto(fullName = "Otro Nombre Real", privacyMode = true),
        )

        assertEquals("Paciente anónimo", displayName)
    }
}
