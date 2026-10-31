package com.example.rehabianex

import com.example.rehabianex.validation.AuthValidator
import com.example.rehabianex.validation.LoginFormInput
import com.example.rehabianex.validation.RegisterFormInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthValidatorTest {
    @Test
    fun login_rejectsEmptyAndInvalidFields() {
        val empty = AuthValidator.validateLogin(LoginFormInput("", ""))
        val invalidEmail = AuthValidator.validateLogin(
            LoginFormInput("correo<script>", "UnaClaveSegura123!"),
        )

        assertFalse(empty.isValid)
        assertTrue(empty.fieldErrors.containsKey("email"))
        assertTrue(empty.fieldErrors.containsKey("password"))
        assertTrue(invalidEmail.fieldErrors.containsKey("email"))
    }

    @Test
    fun login_normalizesEmailAndPreservesPassword() {
        val password = "Segura'123!"
        val result = AuthValidator.validateLogin(
            LoginFormInput("  PERSONA@EXAMPLE.COM ", password),
        )

        assertTrue(result.isValid)
        assertEquals("persona@example.com", result.value?.email)
        assertEquals(password, result.value?.password)
    }

    @Test
    fun register_rejectsMarkupAndWeakPassword() {
        val result = AuthValidator.validateRegister(
            validPatient().copy(
                fullName = "<script>alert(1)</script>",
                password = "debil",
                passwordConfirmation = "debil",
            ),
        )

        assertFalse(result.isValid)
        assertTrue(result.fieldErrors.containsKey("fullName"))
        assertTrue(result.fieldErrors.containsKey("password"))
    }

    @Test
    fun register_acceptsValidPatient() {
        val result = AuthValidator.validateRegister(validPatient())

        assertTrue(result.isValid)
        assertEquals("patient", result.value?.role)
        assertEquals("female", result.value?.gender)
        assertEquals(29, result.value?.age)
        assertEquals(null, result.value?.supervisorType)
        assertTrue(result.value?.privacyNoticeAccepted == true)
        assertEquals("2026-10-30", result.value?.privacyNoticeVersion)
    }

    @Test
    fun register_acceptsValidSupervisor() {
        val result = AuthValidator.validateRegister(
            validPatient().copy(
                role = "supervisor",
                gender = "",
                age = "",
                supervisorType = "clinical_psychologist",
            ),
        )

        assertTrue(result.isValid)
        assertEquals("supervisor", result.value?.role)
        assertEquals("clinical_psychologist", result.value?.supervisorType)
        assertEquals(null, result.value?.gender)
        assertEquals(null, result.value?.age)
    }

    @Test
    fun register_rejectsMismatchedPasswordsAndInvalidPhone() {
        val result = AuthValidator.validateRegister(
            validPatient().copy(
                passwordConfirmation = "OtraClave123!",
                phone = "449-123-456",
            ),
        )

        assertFalse(result.isValid)
        assertTrue(result.fieldErrors.containsKey("passwordConfirmation"))
        assertTrue(result.fieldErrors.containsKey("phone"))
    }

    @Test
    fun register_rejectsMissingPrivacyAcceptanceBeforeCallingBackend() {
        val result = AuthValidator.validateRegister(
            validPatient().copy(acceptedPrivacy = false),
        )

        assertFalse(result.isValid)
        assertEquals("Debes aceptar el aviso de privacidad.", result.fieldErrors["privacy"])
    }

    private fun validPatient() = RegisterFormInput(
        fullName = "Paciente Prueba",
        email = "paciente@example.com",
        password = "UnaClaveSegura123!",
        passwordConfirmation = "UnaClaveSegura123!",
        role = "patient",
        phone = "+524497654321",
        gender = "female",
        age = "29",
        supervisorType = "",
        acceptedPrivacy = true,
    )
}
