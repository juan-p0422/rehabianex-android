package com.example.rehabianex.validation

const val PHONE_REGION_MEXICO = "mx"
const val PHONE_REGION_INTERNATIONAL = "international"

data class PhoneInputValue(
    val region: String,
    val number: String,
)

data class PhoneValidationResult(
    val value: String? = null,
    val error: String? = null,
)

object PhoneNumberValidator {
    fun validate(region: String, input: String): PhoneValidationResult {
        val trimmed = input.trim()
        if (trimmed.isBlank()) {
            return PhoneValidationResult(error = "Ingresa un número de teléfono.")
        }
        if (trimmed.any { it.isISOControl() } || trimmed.any { it !in ALLOWED_PHONE_CHARACTERS && !it.isDigit() }) {
            return PhoneValidationResult(error = "El teléfono contiene caracteres no válidos.")
        }

        if (trimmed.startsWith("+")) {
            val international = validateInternational(trimmed)
            val normalized = international.value
            return if (
                normalized != null &&
                normalized.startsWith(MEXICO_DIAL_CODE) &&
                normalized.removePrefix(MEXICO_DIAL_CODE).length == MEXICO_NATIONAL_DIGITS
            ) {
                international
            } else {
                PhoneValidationResult(
                    error = "El alcance actual admite México: usa +52 y 10 dígitos nacionales.",
                )
            }
        }

        return if (region == PHONE_REGION_MEXICO) {
            val digits = trimmed.filter(Char::isDigit)
            if (digits.length == MEXICO_NATIONAL_DIGITS) {
                PhoneValidationResult(value = "+52$digits")
            } else {
                PhoneValidationResult(
                    error = "Para México escribe exactamente 10 dígitos; se guardará con +52.",
                )
            }
        } else {
            PhoneValidationResult(
                error = "El alcance actual admite México: selecciona +52 e ingresa 10 dígitos.",
            )
        }
    }

    fun parseStored(value: String?): PhoneInputValue {
        val raw = value?.trim().orEmpty()
        if (LEGACY_MEXICO_REGEX.matches(raw)) {
            return PhoneInputValue(PHONE_REGION_MEXICO, raw)
        }

        val normalized = validateInternational(raw).value
        if (normalized != null && normalized.startsWith(MEXICO_DIAL_CODE)) {
            val national = normalized.removePrefix(MEXICO_DIAL_CODE)
            if (national.length == MEXICO_NATIONAL_DIGITS) {
                return PhoneInputValue(PHONE_REGION_MEXICO, national)
            }
        }

        return PhoneInputValue(
            region = PHONE_REGION_INTERNATIONAL,
            number = normalized ?: raw.take(MAX_PHONE_INPUT_LENGTH),
        )
    }

    private fun validateInternational(input: String): PhoneValidationResult {
        if (!input.startsWith("+") || input.drop(1).contains('+')) {
            return PhoneValidationResult(
                error = "Incluye un único código internacional al inicio, por ejemplo +52.",
            )
        }
        val digits = input.drop(1).filter(Char::isDigit)
        if (digits.length !in MIN_E164_DIGITS..MAX_E164_DIGITS || digits.startsWith("0")) {
            return PhoneValidationResult(
                error = "Usa formato internacional con 8 a 15 dígitos, incluido el código de país.",
            )
        }
        return PhoneValidationResult(value = "+$digits")
    }

    private const val MEXICO_DIAL_CODE = "+52"
    private const val MEXICO_NATIONAL_DIGITS = 10
    private const val MIN_E164_DIGITS = 8
    private const val MAX_E164_DIGITS = 15
    private const val MAX_PHONE_INPUT_LENGTH = 24
    private val LEGACY_MEXICO_REGEX = Regex("^\\d{10}$")
    private val ALLOWED_PHONE_CHARACTERS = setOf('+', ' ', '-', '(', ')', '.')
}
