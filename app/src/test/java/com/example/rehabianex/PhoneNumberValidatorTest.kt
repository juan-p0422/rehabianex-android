package com.example.rehabianex

import com.example.rehabianex.validation.PHONE_REGION_INTERNATIONAL
import com.example.rehabianex.validation.PHONE_REGION_MEXICO
import com.example.rehabianex.validation.PhoneNumberValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneNumberValidatorTest {
    @Test
    fun `mexico phone accepts formatting and stores e164`() {
        val result = PhoneNumberValidator.validate(
            PHONE_REGION_MEXICO,
            "(449) 123-4567",
        )

        assertEquals("+524491234567", result.value)
        assertNull(result.error)
    }

    @Test
    fun `mexico phone rejects wrong length and foreign country`() {
        assertNotNull(
            PhoneNumberValidator.validate(PHONE_REGION_MEXICO, "449123456").error,
        )
        assertNotNull(
            PhoneNumberValidator.validate(PHONE_REGION_INTERNATIONAL, "+14155552671").error,
        )
    }

    @Test
    fun `stored legacy mexico phone remains editable`() {
        val parsed = PhoneNumberValidator.parseStored("4491234567")

        assertEquals(PHONE_REGION_MEXICO, parsed.region)
        assertEquals("4491234567", parsed.number)
    }

    @Test
    fun `stored foreign phone remains visible as legacy read only value`() {
        val parsed = PhoneNumberValidator.parseStored("+14155552671")

        assertEquals(PHONE_REGION_INTERNATIONAL, parsed.region)
        assertEquals("+14155552671", parsed.number)
    }
}
