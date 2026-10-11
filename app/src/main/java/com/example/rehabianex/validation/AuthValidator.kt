package com.example.rehabianex.validation

import com.example.rehabianex.data.model.LoginRequest
import com.example.rehabianex.data.model.PRIVACY_NOTICE_VERSION
import com.example.rehabianex.data.model.RegisterRequest
import java.util.Locale

data class LoginFormInput(
    val email: String,
    val password: String,
)

data class RegisterFormInput(
    val fullName: String,
    val email: String,
    val password: String,
    val passwordConfirmation: String,
    val role: String,
    val phone: String,
    val gender: String,
    val age: String,
    val supervisorType: String,
    val acceptedPrivacy: Boolean,
)

data class ValidationResult<T>(
    val value: T? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
) {
    val isValid: Boolean
        get() = value != null && fieldErrors.isEmpty()
}

object AuthValidator {
    fun validateLogin(input: LoginFormInput): ValidationResult<LoginRequest> {
        val errors = linkedMapOf<String, String>()
        val email = input.email.trim().lowercase(Locale.ROOT)

        validateEmail(email)?.let { errors["email"] = it }

        if (input.password.isEmpty()) {
            errors["password"] = "Ingresa tu contraseña."
        } else if (input.password.length > MAX_PASSWORD_LENGTH) {
            errors["password"] = "La contraseña es demasiado larga."
        } else if (input.password.any { it.isISOControl() }) {
            errors["password"] = "La contraseña contiene caracteres no válidos."
        }

        return if (errors.isEmpty()) {
            ValidationResult(
                value = LoginRequest(
                    email = email,
                    password = input.password,
                ),
            )
        } else {
            ValidationResult(fieldErrors = errors)
        }
    }

    fun validateRegister(
        input: RegisterFormInput,
    ): ValidationResult<RegisterRequest> {
        val errors = linkedMapOf<String, String>()
        val fullName = input.fullName.trim().replace(MULTIPLE_SPACES_REGEX, " ")
        val email = input.email.trim().lowercase(Locale.ROOT)
        val phone = input.phone.filterNot(Char::isWhitespace)

        validateFullName(fullName)?.let { errors["fullName"] = it }
        validateEmail(email)?.let { errors["email"] = it }
        validateStrongPassword(input.password)?.let { errors["password"] = it }

        if (input.password != input.passwordConfirmation) {
            errors["passwordConfirmation"] = "Las contraseñas no coinciden."
        }

        if (!PHONE_REGEX.matches(phone)) {
            errors["phone"] = "Usa formato internacional, por ejemplo +524491234567."
        }

        if (input.role !in VALID_ROLES) {
            errors["role"] = "Selecciona un tipo de cuenta válido."
        }

        var gender: String? = null
        var age: Int? = null
        var supervisorType: String? = null

        if (input.role == ROLE_PATIENT) {
            gender = input.gender.takeIf { it in VALID_GENDERS }
            if (gender == null) {
                errors["gender"] = "Selecciona una opción válida."
            }

            age = input.age.toIntOrNull()
            if (age == null || age !in MIN_AGE..MAX_AGE) {
                errors["age"] = "Ingresa una edad entre $MIN_AGE y $MAX_AGE años."
            }
        }

        if (input.role == ROLE_SUPERVISOR) {
            supervisorType = input.supervisorType.trim().lowercase(Locale.ROOT)
            if (!SUPERVISOR_TYPE_REGEX.matches(supervisorType)) {
                errors["supervisorType"] =
                    "Usa un código válido, por ejemplo clinical_psychologist."
            }
        }

        if (!input.acceptedPrivacy) {
            errors["privacy"] = "Debes aceptar el aviso de privacidad."
        }

        return if (errors.isEmpty()) {
            ValidationResult(
                value = RegisterRequest(
                    email = email,
                    password = input.password,
                    fullName = fullName,
                    role = input.role,
                    phone = phone,
                    gender = gender,
                    age = age,
                    supervisorType = supervisorType,
                    privacyNoticeAccepted = true,
                    privacyNoticeVersion = PRIVACY_NOTICE_VERSION,
                ),
            )
        } else {
            ValidationResult(fieldErrors = errors)
        }
    }

    private fun validateEmail(email: String): String? = when {
        email.isBlank() -> "Ingresa tu correo electrónico."
        email.length > MAX_EMAIL_LENGTH -> "El correo electrónico es demasiado largo."
        !EMAIL_REGEX.matches(email) -> "Ingresa un correo electrónico válido."
        else -> null
    }

    private fun validateFullName(fullName: String): String? = when {
        fullName.isBlank() -> "Ingresa tu nombre completo."
        fullName.length !in MIN_NAME_LENGTH..MAX_NAME_LENGTH ->
            "El nombre debe tener entre $MIN_NAME_LENGTH y $MAX_NAME_LENGTH caracteres."
        !FULL_NAME_REGEX.matches(fullName) ->
            "El nombre contiene caracteres no válidos."
        else -> null
    }

    private fun validateStrongPassword(password: String): String? = when {
        password.isBlank() -> "Ingresa una contraseña."
        password.length < MIN_PASSWORD_LENGTH ->
            "Usa al menos $MIN_PASSWORD_LENGTH caracteres."
        password.length > MAX_PASSWORD_LENGTH ->
            "Usa máximo $MAX_PASSWORD_LENGTH caracteres."
        password.any { it.isWhitespace() || it.isISOControl() } ->
            "La contraseña no debe contener espacios."
        password.none(Char::isUpperCase) ->
            "Agrega al menos una letra mayúscula."
        password.none(Char::isLowerCase) ->
            "Agrega al menos una letra minúscula."
        password.none(Char::isDigit) ->
            "Agrega al menos un número."
        password.none { !it.isLetterOrDigit() } ->
            "Agrega al menos un símbolo."
        else -> null
    }

    private val EMAIL_REGEX = Regex(
        "^[A-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?(?:\\.[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?)+$",
        RegexOption.IGNORE_CASE,
    )
    private val FULL_NAME_REGEX = Regex("^[\\p{L}\\p{M}][\\p{L}\\p{M} .'-]*$")
    private val PHONE_REGEX = Regex("^\\+[1-9]\\d{7,14}$")
    private val SUPERVISOR_TYPE_REGEX = Regex("^[a-z][a-z0-9_]{2,49}$")
    private val MULTIPLE_SPACES_REGEX = Regex("\\s+")

    private const val ROLE_PATIENT = "patient"
    private const val ROLE_SUPERVISOR = "supervisor"
    private val VALID_ROLES = setOf(ROLE_PATIENT, ROLE_SUPERVISOR)
    private val VALID_GENDERS = setOf("female", "male", "other")
    private const val MIN_NAME_LENGTH = 2
    private const val MAX_NAME_LENGTH = 100
    private const val MAX_EMAIL_LENGTH = 254
    private const val MIN_PASSWORD_LENGTH = 8
    private const val MAX_PASSWORD_LENGTH = 128
    private const val MIN_AGE = 13
    private const val MAX_AGE = 120
}
