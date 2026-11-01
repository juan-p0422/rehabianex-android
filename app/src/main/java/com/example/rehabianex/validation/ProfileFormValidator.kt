package com.example.rehabianex.validation

import java.util.Locale
import java.time.LocalDate

data class EditableProfileFormInput(
    val fullName: String,
    val phone: String,
    val phoneRegion: String,
    val originalPhone: String? = null,
    val supervisorType: String? = null,
    val supervisorTypeOther: String = "",
    val sobrietyStartDate: String? = null,
)

data class EditableProfileValue(
    val fullName: String,
    val phone: String,
    val phoneChanged: Boolean,
    val supervisorType: String? = null,
    val supervisorTypeOther: String? = null,
    val sobrietyStartDate: String? = null,
)

object ProfileFormValidator {
    fun validate(
        input: EditableProfileFormInput,
        includeSupervisorType: Boolean,
    ): ValidationResult<EditableProfileValue> {
        val errors = linkedMapOf<String, String>()
        val fullName = normalizeHumanName(input.fullName)
        validateHumanName(fullName)?.let { errors["fullName"] = it }

        val phoneChanged = !PhoneNumberValidator.matchesStoredInput(
            stored = input.originalPhone,
            region = input.phoneRegion,
            input = input.phone,
        )
        val phone = if (phoneChanged) {
            PhoneNumberValidator.validate(input.phoneRegion, input.phone)
        } else {
            PhoneValidationResult(value = input.originalPhone)
        }
        phone.error?.let { errors["phone"] = it }
        val sobrietyStartDate = input.sobrietyStartDate?.trim()?.takeIf { it.isNotEmpty() }
        if (input.sobrietyStartDate != null && sobrietyStartDate == null) {
            errors["sobrietyStartDate"] = "Ingresa la fecha de sobriedad."
        } else if (sobrietyStartDate != null) {
            val parsed = runCatching { LocalDate.parse(sobrietyStartDate) }.getOrNull()
            when {
                parsed == null -> errors["sobrietyStartDate"] = "Usa una fecha válida con formato AAAA-MM-DD."
                parsed.isAfter(LocalDate.now()) -> errors["sobrietyStartDate"] = "La fecha de sobriedad no puede estar en el futuro."
            }
        }

        val supervisorType = if (includeSupervisorType) {
            normalizeSupervisorType(input, errors)
        } else {
            null to null
        }

        return if (errors.isEmpty()) {
            ValidationResult(
                EditableProfileValue(
                    fullName = fullName,
                    phone = checkNotNull(phone.value),
                    phoneChanged = phoneChanged,
                    supervisorType = supervisorType.first,
                    supervisorTypeOther = supervisorType.second,
                    sobrietyStartDate = sobrietyStartDate,
                ),
            )
        } else {
            ValidationResult(fieldErrors = errors)
        }
    }

    private fun normalizeSupervisorType(
        input: EditableProfileFormInput,
        errors: MutableMap<String, String>,
    ): Pair<String?, String?> {
        val selected = input.supervisorType?.trim()?.lowercase(Locale.ROOT)
        if (selected == SUPERVISOR_TYPE_OTHER) {
            val custom = input.supervisorTypeOther.trim().replace(MULTIPLE_SPACES_REGEX, " ")
            if (
                custom.length !in MIN_CUSTOM_TYPE_LENGTH..MAX_CUSTOM_TYPE_LENGTH ||
                custom.any(Char::isISOControl)
            ) {
                errors["supervisorTypeOther"] =
                    "Describe el tipo de supervisor con 1 a 80 caracteres."
                return selected to null
            }
            return selected to custom
        }
        if (selected !in SUPERVISOR_TYPE_CODES) {
            errors["supervisorType"] = "Selecciona un tipo de supervisor."
            return null to null
        }
        return selected to null
    }
}

fun normalizeHumanName(value: String): String = value
    .trim()
    .replace(MULTIPLE_SPACES_REGEX, " ")

fun validateHumanName(value: String): String? = when {
    value.isBlank() -> "Ingresa tu nombre completo."
    value.length !in MIN_NAME_LENGTH..MAX_NAME_LENGTH ->
        "El nombre debe tener entre $MIN_NAME_LENGTH y $MAX_NAME_LENGTH caracteres."
    !FULL_NAME_REGEX.matches(value) -> "El nombre contiene caracteres no válidos."
    else -> null
}

const val SUPERVISOR_TYPE_OTHER = "other"
val SUPERVISOR_TYPE_OPTIONS = linkedMapOf(
    "doctor" to "Doctor",
    "psychiatrist" to "Psiquiatra",
    "psychologist" to "Psicólogo",
    "clinical_psychologist" to "Psicólogo clínico",
    "social_worker" to "Trabajador social",
    "support_sponsor" to "Padrino o madrina",
    SUPERVISOR_TYPE_OTHER to "Otro",
)
val SUPERVISOR_TYPE_CODES = SUPERVISOR_TYPE_OPTIONS.keys - SUPERVISOR_TYPE_OTHER

const val CONTACT_RELATIONSHIP_OTHER = "other"
val CONTACT_RELATIONSHIP_OPTIONS = linkedMapOf(
    "family" to "Familia",
    "partner" to "Pareja",
    "friend" to "Amistad",
    "health_professional" to "Profesional de salud",
    "support_peer" to "Grupo de apoyo",
    "work_or_school" to "Trabajo o escuela",
    CONTACT_RELATIONSHIP_OTHER to "Otro",
)

private val FULL_NAME_REGEX = Regex("^[\\p{L}\\p{M}][\\p{L}\\p{M} .'-]*$")
private val MULTIPLE_SPACES_REGEX = Regex("\\s+")
private const val MIN_NAME_LENGTH = 2
private const val MAX_NAME_LENGTH = 120
private const val MIN_CUSTOM_TYPE_LENGTH = 1
private const val MAX_CUSTOM_TYPE_LENGTH = 80
