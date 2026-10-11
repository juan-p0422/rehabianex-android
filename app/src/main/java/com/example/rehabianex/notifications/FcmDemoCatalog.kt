package com.example.rehabianex.notifications

data class FcmDemoCase(
    val type: String,
    val event: String,
    val body: String,
    val roles: Set<String>,
)

object FcmDemoCatalog {
    const val ROLE_PATIENT = "patient"
    const val ROLE_SUPERVISOR = "supervisor"
    const val ROLE_ADMIN = "admin"

    val cases: List<FcmDemoCase> = listOf(
        case(FcmContract.TYPE_APPOINTMENT_REMINDER, "Evento de agenda próximo", ROLE_PATIENT),
        case(FcmContract.TYPE_PROGRESS_CHECKIN, "Recordatorio de seguimiento", ROLE_PATIENT),
        case(FcmContract.TYPE_ACHIEVEMENT_UNLOCKED, "Logro alcanzado", ROLE_PATIENT),
        case(FcmContract.TYPE_SOBER_DAY_UPDATE, "Avance o racha", ROLE_PATIENT),
        case(FcmContract.TYPE_INTERVENTION_UPDATE, "Actualización de intervención", ROLE_PATIENT),
        case(FcmContract.TYPE_SUPERVISION_RESPONSE, "Respuesta de supervisión", ROLE_PATIENT),
        case(FcmContract.TYPE_SUPERVISION_REQUEST, "Solicitud de acompañamiento", ROLE_SUPERVISOR),
        case(FcmContract.TYPE_UNLINK_REQUEST, "Solicitud de desvinculación", ROLE_SUPERVISOR),
        case(FcmContract.TYPE_CONSENT_SUSPENDED, "Consentimiento suspendido", ROLE_SUPERVISOR),
        case(FcmContract.TYPE_RISK_ALERT, "Actualización de riesgo", ROLE_SUPERVISOR),
        case(FcmContract.TYPE_RELAPSE_ALERT, "Actualización prioritaria", ROLE_SUPERVISOR),
        case(FcmContract.TYPE_SUPERVISOR_VALIDATION_APPROVED, "Aprobación por administración", ROLE_SUPERVISOR),
        case(FcmContract.TYPE_VULNERABLE_USER_PRIORITY, "Seguimiento prioritario", ROLE_SUPERVISOR),
        case(FcmContract.TYPE_SUPERVISOR_VALIDATION_REQUIRED, "Supervisor pendiente", ROLE_ADMIN),
        case(FcmContract.TYPE_USER_VALIDATION_REQUIRED, "Usuario pendiente", ROLE_ADMIN),
        case(FcmContract.TYPE_SUPERVISION_REQUEST_CONFLICT, "Conflicto de supervisión", ROLE_ADMIN),
        case(
            FcmContract.TYPE_SYSTEM_NOTICE,
            "Aviso general del sistema",
            ROLE_PATIENT,
            ROLE_SUPERVISOR,
            ROLE_ADMIN,
        ),
        case(
            FcmContract.TYPE_TEST_NOTIFICATION,
            "Comprobación básica",
            ROLE_PATIENT,
            ROLE_SUPERVISOR,
            ROLE_ADMIN,
        ),
    )

    fun forRole(role: String?): List<FcmDemoCase> = cases.filter { role in it.roles }

    fun isAllowed(role: String?, type: String): Boolean = cases.any {
        it.type == type && role in it.roles
    }

    private fun case(type: String, event: String, vararg roles: String) = FcmDemoCase(
        type = type,
        event = event,
        body = safeFcmNotificationContent(type, null, null).body,
        roles = roles.toSet(),
    )
}
