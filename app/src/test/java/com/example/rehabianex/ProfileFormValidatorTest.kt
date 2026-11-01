package com.example.rehabianex

import com.example.rehabianex.validation.EditableProfileFormInput
import com.example.rehabianex.validation.PHONE_REGION_MEXICO
import com.example.rehabianex.validation.ProfileFormValidator
import com.example.rehabianex.validation.PhoneNumberValidator
import com.example.rehabianex.validation.SUPERVISOR_TYPE_OTHER
import com.example.rehabianex.validation.SUPERVISOR_TYPE_OPTIONS
import com.example.rehabianex.validation.CONTACT_RELATIONSHIP_OPTIONS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProfileFormValidatorTest {
    @Test
    fun patientProfile_rejectsFutureSobrietyDate() {
        val result = ProfileFormValidator.validate(
            EditableProfileFormInput(
                fullName = "Paciente Prueba",
                phone = "5512345678",
                phoneRegion = PHONE_REGION_MEXICO,
                sobrietyStartDate = LocalDate.now().plusDays(1).toString(),
            ),
            includeSupervisorType = false,
        )

        assertEquals(
            "La fecha de sobriedad no puede estar en el futuro.",
            result.fieldErrors["sobrietyStartDate"],
        )
    }

    @Test
    fun patientProfile_rejectsClearingSobrietyDate() {
        val result = ProfileFormValidator.validate(
            EditableProfileFormInput(
                fullName = "Paciente Prueba",
                phone = "5512345678",
                phoneRegion = PHONE_REGION_MEXICO,
                sobrietyStartDate = "   ",
            ),
            includeSupervisorType = false,
        )

        assertEquals("Ingresa la fecha de sobriedad.", result.fieldErrors["sobrietyStartDate"])
    }
    @Test
    fun `form option codes match the backend contract`() {
        assertTrue(SUPERVISOR_TYPE_OPTIONS.keys.containsAll(
            listOf(
                "doctor",
                "psychiatrist",
                "psychologist",
                "clinical_psychologist",
                "social_worker",
                "support_sponsor",
                "other",
            ),
        ))
        assertFalse("therapist" in SUPERVISOR_TYPE_OPTIONS)
        assertEquals(
            setOf(
                "family",
                "partner",
                "friend",
                "health_professional",
                "support_peer",
                "work_or_school",
                "other",
            ),
            CONTACT_RELATIONSHIP_OPTIONS.keys,
        )
    }

    @Test
    fun `patient profile keeps accents and normalizes mexico phone`() {
        val result = ProfileFormValidator.validate(
            EditableProfileFormInput(
                fullName = "  María José de la O  ",
                phone = "449 123 4567",
                phoneRegion = PHONE_REGION_MEXICO,
            ),
            includeSupervisorType = false,
        )

        assertTrue(result.isValid)
        assertEquals("María José de la O", result.value?.fullName)
        assertEquals("+524491234567", result.value?.phone)
        assertTrue(result.value?.phoneChanged == true)
    }

    @Test
    fun `unchanged historical phone is preserved without applying current mexico rule`() {
        val historicalPhone = "+1 (555) 010-0200"
        val displayed = PhoneNumberValidator.parseStored(historicalPhone)

        val result = ProfileFormValidator.validate(
            EditableProfileFormInput(
                fullName = "Paciente Histórico",
                phone = displayed.number,
                phoneRegion = displayed.region,
                originalPhone = historicalPhone,
            ),
            includeSupervisorType = false,
        )

        assertTrue(result.isValid)
        assertEquals(historicalPhone, result.value?.phone)
        assertFalse(result.value?.phoneChanged ?: true)
    }

    @Test
    fun `editing a historical phone applies the current mexico rule`() {
        val historicalPhone = "+1 (555) 010-0200"

        val result = ProfileFormValidator.validate(
            EditableProfileFormInput(
                fullName = "Paciente Histórico",
                phone = "123",
                phoneRegion = PHONE_REGION_MEXICO,
                originalPhone = historicalPhone,
            ),
            includeSupervisorType = false,
        )

        assertFalse(result.isValid)
        assertTrue(result.fieldErrors.containsKey("phone"))
    }

    @Test
    fun `other supervisor type uses separate bounded description`() {
        val result = ProfileFormValidator.validate(
            EditableProfileFormInput(
                fullName = "Ana Gómez",
                phone = "4491234567",
                phoneRegion = PHONE_REGION_MEXICO,
                supervisorType = SUPERVISOR_TYPE_OTHER,
                supervisorTypeOther = "Terapeuta familiar",
            ),
            includeSupervisorType = true,
        )

        assertTrue(result.isValid)
        assertEquals(SUPERVISOR_TYPE_OTHER, result.value?.supervisorType)
        assertEquals("Terapeuta familiar", result.value?.supervisorTypeOther)
    }

    @Test
    fun `invalid fields return specific messages`() {
        val result = ProfileFormValidator.validate(
            EditableProfileFormInput(
                fullName = "<>",
                phone = "123",
                phoneRegion = PHONE_REGION_MEXICO,
                supervisorType = SUPERVISOR_TYPE_OTHER,
                supervisorTypeOther = " ",
            ),
            includeSupervisorType = true,
        )

        assertFalse(result.isValid)
        assertTrue(result.fieldErrors.keys.containsAll(
            listOf("fullName", "phone", "supervisorTypeOther"),
        ))
    }
}
