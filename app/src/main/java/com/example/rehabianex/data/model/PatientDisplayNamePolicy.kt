package com.example.rehabianex.data.model

const val ANONYMOUS_PATIENT_DISPLAY_NAME = "Paciente anónimo"

fun SessionProfileDto.isPatientPrivacyRestricted(): Boolean {
    return isAnonymous == true || privacyMode == true
}

fun AuthData.isPatientPrivacyRestricted(): Boolean {
    return isAnonymous == true || privacyMode == true
}

/**
 * Nombre que se puede persistir en sesión después de login/registro.
 * `full_name` queda fuera de los candidatos cuando el paciente está restringido.
 */
fun resolveAuthSessionDisplayName(
    role: String?,
    auth: AuthData?,
    profile: SessionProfileDto?,
): String? {
    val privacyRestricted = role == "patient" && (
        profile?.isPatientPrivacyRestricted() == true ||
            auth?.isPatientPrivacyRestricted() == true
        )
    val candidates = if (privacyRestricted) {
        listOf(
            profile?.safeDisplayName,
            auth?.safeDisplayName,
            profile?.displayName,
            auth?.displayName,
            profile?.nickname,
            ANONYMOUS_PATIENT_DISPLAY_NAME,
        )
    } else {
        listOf(
            profile?.safeDisplayName,
            auth?.safeDisplayName,
            profile?.displayName,
            profile?.nickname,
            profile?.fullName,
            auth?.displayName,
            auth?.fullName,
        )
    }
    return candidates.firstNonBlank()
}

/** Fuente única para Perfil, Home y snapshots Room del paciente propietario. */
fun SessionProfileDto.resolvedPatientDisplayName(
    sessionDisplayName: String? = null,
): String {
    val candidates = if (isPatientPrivacyRestricted()) {
        listOf(
            safeDisplayName,
            displayName,
            nickname,
            ANONYMOUS_PATIENT_DISPLAY_NAME,
        )
    } else {
        listOf(
            safeDisplayName,
            displayName,
            nickname,
            fullName,
            sessionDisplayName,
            "Usuario RehabiAnex",
        )
    }
    return candidates.firstNonBlank() ?: "Usuario RehabiAnex"
}

private fun List<String?>.firstNonBlank(): String? {
    return firstNotNullOfOrNull { value -> value?.trim()?.takeIf(String::isNotBlank) }
}
