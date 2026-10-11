package com.example.rehabianex.notifications

import com.example.rehabianex.navigation.Routes
import java.time.OffsetDateTime

internal object FcmContract {
    const val TYPE_APPOINTMENT_REMINDER = "appointment_reminder"
    const val TYPE_PROGRESS_CHECKIN = "progress_checkin"
    const val TYPE_SOBER_DAY_UPDATE = "sober_day_update"
    const val TYPE_ACHIEVEMENT_UNLOCKED = "achievement_unlocked"
    const val TYPE_SUPERVISION_REQUEST = "supervision_request"
    const val TYPE_SUPERVISION_RESPONSE = "supervision_response"
    const val TYPE_UNLINK_REQUEST = "unlink_request"
    const val TYPE_CONSENT_SUSPENDED = "consent_suspended"
    const val TYPE_INTERVENTION_UPDATE = "intervention_update"
    const val TYPE_RELAPSE_ALERT = "relapse_alert"
    const val TYPE_RISK_ALERT = "risk_alert"
    const val TYPE_VULNERABLE_USER_PRIORITY = "vulnerable_user_priority"
    const val TYPE_USER_VALIDATION_REQUIRED = "user_validation_required"
    const val TYPE_SUPERVISOR_VALIDATION_REQUIRED = "supervisor_validation_required"
    const val TYPE_SUPERVISOR_VALIDATION_APPROVED = "supervisor_validation_approved"
    const val TYPE_SUPERVISION_REQUEST_CONFLICT = "supervision_request_conflict"
    const val TYPE_SYSTEM_NOTICE = "system_notice"
    const val TYPE_TEST_NOTIFICATION = "test_notification"

    const val DATA_TYPE = "type"
    const val DATA_TITLE = "title"
    const val DATA_BODY = "body"
    const val DATA_ROUTE = "route"
    const val DATA_ENTITY_ID = "entity_id"
    const val DATA_NOTIFICATION_ID = "notification_id"
    const val DATA_CREATED_AT = "created_at"
    const val DATA_PRIORITY = "priority"

    val allowedTypes = setOf(
        TYPE_APPOINTMENT_REMINDER,
        TYPE_PROGRESS_CHECKIN,
        TYPE_SOBER_DAY_UPDATE,
        TYPE_ACHIEVEMENT_UNLOCKED,
        TYPE_SUPERVISION_REQUEST,
        TYPE_SUPERVISION_RESPONSE,
        TYPE_UNLINK_REQUEST,
        TYPE_CONSENT_SUSPENDED,
        TYPE_INTERVENTION_UPDATE,
        TYPE_RELAPSE_ALERT,
        TYPE_RISK_ALERT,
        TYPE_VULNERABLE_USER_PRIORITY,
        TYPE_USER_VALIDATION_REQUIRED,
        TYPE_SUPERVISOR_VALIDATION_REQUIRED,
        TYPE_SUPERVISOR_VALIDATION_APPROVED,
        TYPE_SUPERVISION_REQUEST_CONFLICT,
        TYPE_SYSTEM_NOTICE,
        TYPE_TEST_NOTIFICATION,
    )

    // These keys are never consumed or rendered, even if a malformed backend payload includes them.
    val prohibitedDataKeys = setOf(
        "patient_name",
        "patient_alias",
        "display_name",
        "email",
        "phone",
        "clinical_note",
        "relapse_detail",
        "craving_level",
        "anxiety_level",
        "diagnosis",
        "free_text_message",
        "location",
        "uid",
    )
}

internal data class SafeFcmNotificationContent(
    val title: String,
    val body: String,
)

internal fun validatedFcmType(type: String?): String? = type
    ?.trim()
    ?.takeIf { it in FcmContract.allowedTypes }

internal fun validatedFcmPriority(priority: String?): String = priority
    ?.trim()
    ?.lowercase()
    ?.takeIf { it in ALLOWED_PRIORITIES }
    ?: PRIORITY_NORMAL

internal fun validatedFcmCreatedAt(createdAt: String?): String? {
    val candidate = createdAt?.trim()?.takeIf { it.length in 1..MAX_CREATED_AT_LENGTH } ?: return null
    return runCatching { OffsetDateTime.parse(candidate).toString() }.getOrNull()
}

internal fun safeFcmRoute(
    role: String?,
    type: String?,
    requestedRoute: String?,
    entityId: String? = null,
): String? {
    val safeType = validatedFcmType(type) ?: return null
    if (role !in setOf(
            NotificationScheduler.ROLE_PATIENT,
            NotificationScheduler.ROLE_SUPERVISOR,
            ROLE_ADMIN,
        )
    ) {
        return null
    }
    val home = notificationHomeRouteForRole(role) ?: return null
    val route = requestedRoute?.trim()?.take(MAX_ROUTE_LENGTH).orEmpty()
    val safeEntityId = entityId.safeEntityIdOrNull()
    if (route == ROUTE_HOME) return home

    return when (safeType) {
        FcmContract.TYPE_APPOINTMENT_REMINDER -> if (
            role == NotificationScheduler.ROLE_PATIENT &&
            routeMatchesEntity(AGENDA_EVENT_ROUTE, route, safeEntityId)
        ) Routes.Agenda else home

        FcmContract.TYPE_PROGRESS_CHECKIN -> if (
            role == NotificationScheduler.ROLE_PATIENT &&
            route in setOf(ROUTE_CHECKIN_NEW, ROUTE_EMOTIONAL_CHECKIN, Routes.EmotionalCheckin)
        ) Routes.EmotionalCheckin else home

        FcmContract.TYPE_SOBER_DAY_UPDATE -> home

        FcmContract.TYPE_ACHIEVEMENT_UNLOCKED -> if (
            role == NotificationScheduler.ROLE_PATIENT &&
            routeMatchesEntity(ACHIEVEMENT_ROUTE, route, safeEntityId)
        ) Routes.Achievements else home

        FcmContract.TYPE_SUPERVISION_REQUEST,
        FcmContract.TYPE_SUPERVISION_RESPONSE,
        FcmContract.TYPE_UNLINK_REQUEST,
        -> safeSupervisionRoute(role, safeType, route, safeEntityId, home)

        FcmContract.TYPE_CONSENT_SUSPENDED -> if (
            role == NotificationScheduler.ROLE_SUPERVISOR &&
            routeMatchesEntity(CONSENT_ROUTE, route, safeEntityId)
        ) Routes.PatientsList else home

        FcmContract.TYPE_INTERVENTION_UPDATE -> if (
            role == NotificationScheduler.ROLE_PATIENT &&
            routeMatchesEntity(INTERVENTION_ROUTE, route, safeEntityId)
        ) Routes.Interventions else home

        FcmContract.TYPE_RELAPSE_ALERT,
        FcmContract.TYPE_RISK_ALERT,
        FcmContract.TYPE_VULNERABLE_USER_PRIORITY,
        -> if (
            role == NotificationScheduler.ROLE_SUPERVISOR && route == ROUTE_PATIENTS_PRIORITY
        ) Routes.PatientsList else home

        FcmContract.TYPE_USER_VALIDATION_REQUIRED -> if (
            role == ROLE_ADMIN && routeMatchesEntity(ADMIN_USER_PENDING_ROUTE, route, safeEntityId)
        ) Routes.AdminHome else home

        FcmContract.TYPE_SUPERVISOR_VALIDATION_REQUIRED -> if (
            role == ROLE_ADMIN && routeMatchesEntity(ADMIN_SUPERVISOR_PENDING_ROUTE, route, safeEntityId)
        ) Routes.AdminHome else home

        FcmContract.TYPE_SUPERVISOR_VALIDATION_APPROVED -> if (
            role == NotificationScheduler.ROLE_SUPERVISOR && route == ROUTE_HOME
        ) Routes.SupervisorHome else home

        FcmContract.TYPE_SUPERVISION_REQUEST_CONFLICT -> if (
            role == ROLE_ADMIN && routeMatchesEntity(ADMIN_CONFLICT_ROUTE, route, safeEntityId)
        ) Routes.AdminHome else home

        FcmContract.TYPE_SYSTEM_NOTICE -> when {
            role == NotificationScheduler.ROLE_PATIENT &&
                routeMatchesEntity(INTERVENTION_ROUTE, route, safeEntityId) -> Routes.Interventions
            role == NotificationScheduler.ROLE_PATIENT &&
                routeMatchesEntity(SUPERVISION_REQUEST_ROUTE, route, safeEntityId) -> Routes.SupervisorLink
            role == NotificationScheduler.ROLE_SUPERVISOR &&
                routeMatchesEntity(SUPERVISION_REQUEST_ROUTE, route, safeEntityId) -> Routes.PatientsList
            else -> home
        }

        FcmContract.TYPE_TEST_NOTIFICATION -> home
        else -> home
    }
}

private fun safeSupervisionRoute(
    role: String?,
    type: String,
    route: String,
    entityId: String?,
    home: String,
): String {
    val routeIsAllowed = if (type == FcmContract.TYPE_UNLINK_REQUEST) {
        routeMatchesEntity(UNLINK_REQUEST_ROUTE, route, entityId)
    } else {
        routeMatchesEntity(SUPERVISION_REQUEST_ROUTE, route, entityId)
    }
    if (!routeIsAllowed) return home
    return when (role) {
        NotificationScheduler.ROLE_PATIENT -> Routes.SupervisorLink
        NotificationScheduler.ROLE_SUPERVISOR -> Routes.PatientsList
        else -> home
    }
}

internal fun safeFcmNotificationContent(
    type: String?,
    @Suppress("UNUSED_PARAMETER") title: String?,
    @Suppress("UNUSED_PARAMETER") body: String?,
): SafeFcmNotificationContent {
    val safeBody = when (validatedFcmType(type)) {
        FcmContract.TYPE_APPOINTMENT_REMINDER -> APPOINTMENT_REMINDER_BODY
        FcmContract.TYPE_PROGRESS_CHECKIN -> PROGRESS_CHECKIN_BODY
        FcmContract.TYPE_SOBER_DAY_UPDATE -> SOBER_DAY_UPDATE_BODY
        FcmContract.TYPE_ACHIEVEMENT_UNLOCKED -> ACHIEVEMENT_UNLOCKED_BODY
        FcmContract.TYPE_SUPERVISION_REQUEST -> SUPERVISION_REQUEST_BODY
        FcmContract.TYPE_SUPERVISION_RESPONSE -> SUPERVISION_RESPONSE_BODY
        FcmContract.TYPE_UNLINK_REQUEST -> UNLINK_REQUEST_BODY
        FcmContract.TYPE_CONSENT_SUSPENDED -> CONSENT_SUSPENDED_BODY
        FcmContract.TYPE_INTERVENTION_UPDATE -> INTERVENTION_UPDATE_BODY
        FcmContract.TYPE_RELAPSE_ALERT -> RELAPSE_ALERT_BODY
        FcmContract.TYPE_RISK_ALERT,
        FcmContract.TYPE_VULNERABLE_USER_PRIORITY,
        -> IMPORTANT_FOLLOW_UP_BODY
        FcmContract.TYPE_USER_VALIDATION_REQUIRED -> USER_VALIDATION_REQUIRED_BODY
        FcmContract.TYPE_SUPERVISOR_VALIDATION_REQUIRED -> SUPERVISOR_VALIDATION_REQUIRED_BODY
        FcmContract.TYPE_SUPERVISOR_VALIDATION_APPROVED -> SUPERVISOR_VALIDATION_APPROVED_BODY
        FcmContract.TYPE_SUPERVISION_REQUEST_CONFLICT -> SUPERVISION_REQUEST_CONFLICT_BODY
        FcmContract.TYPE_SYSTEM_NOTICE,
        FcmContract.TYPE_TEST_NOTIFICATION,
        -> SAFE_SYSTEM_BODY
        else -> SAFE_SYSTEM_BODY
    }
    return SafeFcmNotificationContent(title = SAFE_TITLE, body = safeBody)
}

internal fun String?.safeEntityIdOrNull(): String? = this
    ?.trim()
    ?.takeIf {
        it.isNotEmpty() && it.length <= MAX_ENTITY_ID_LENGTH &&
            it.all { character -> character.isLetterOrDigit() || character in "_-" }
    }

private fun routeMatchesEntity(pattern: Regex, route: String, entityId: String?): Boolean {
    if (entityId == null) return false
    return pattern.matchEntire(route)?.groupValues?.getOrNull(1) == entityId
}

private fun notificationHomeRouteForRole(role: String?): String? = when (role) {
    NotificationScheduler.ROLE_PATIENT -> Routes.PatientHome
    NotificationScheduler.ROLE_SUPERVISOR -> Routes.SupervisorHome
    ROLE_ADMIN -> Routes.AdminHome
    else -> null
}

private val AGENDA_EVENT_ROUTE = Regex("^/agenda-events/([A-Za-z0-9_-]{1,128})$")
private val ACHIEVEMENT_ROUTE = Regex("^/achievements/([A-Za-z0-9_-]{1,128})$")
private val SUPERVISION_REQUEST_ROUTE = Regex("^/supervision-requests/([A-Za-z0-9_-]{1,128})$")
private val UNLINK_REQUEST_ROUTE = Regex("^/unlink-requests/([A-Za-z0-9_-]{1,128})$")
private val CONSENT_ROUTE = Regex("^/consents/([A-Za-z0-9_-]{1,128})$")
private val INTERVENTION_ROUTE = Regex("^/interventions/([A-Za-z0-9_-]{1,128})$")
private val ADMIN_USER_PENDING_ROUTE = Regex("^/admin/users/pending/([A-Za-z0-9_-]{1,128})$")
private val ADMIN_SUPERVISOR_PENDING_ROUTE = Regex("^/admin/supervisors/pending/([A-Za-z0-9_-]{1,128})$")
private val ADMIN_CONFLICT_ROUTE = Regex("^/admin/supervision-conflicts/([A-Za-z0-9_-]{1,128})$")
private val ALLOWED_PRIORITIES = setOf("normal", "high", "critical")
private const val PRIORITY_NORMAL = "normal"
private const val ROLE_ADMIN = "admin"
private const val ROUTE_HOME = "/home"
private const val ROUTE_CHECKIN_NEW = "/checkins/new"
private const val ROUTE_EMOTIONAL_CHECKIN = "/emotional-checkin"
private const val ROUTE_PATIENTS_PRIORITY = "/patients/priority"
private const val SAFE_TITLE = "Rehabianex"
private const val SAFE_SYSTEM_BODY = "Tienes una actualización en Rehabianex."
private const val APPOINTMENT_REMINDER_BODY = "Tienes un evento de agenda próximo."
private const val PROGRESS_CHECKIN_BODY = "Es momento de registrar tu seguimiento."
private const val ACHIEVEMENT_UNLOCKED_BODY = "Has alcanzado un nuevo logro."
private const val SOBER_DAY_UPDATE_BODY = "Has alcanzado un nuevo avance en tu proceso."
private const val INTERVENTION_UPDATE_BODY = "Tu plan de apoyo tiene una actualización."
private const val SUPERVISION_RESPONSE_BODY = "Tu solicitud de acompañamiento fue actualizada."
private const val SUPERVISION_REQUEST_BODY = "Tienes una nueva solicitud de acompañamiento."
private const val UNLINK_REQUEST_BODY = "Tienes una solicitud de cambio de consentimiento."
private const val CONSENT_SUSPENDED_BODY = "Se actualizó el estado de un consentimiento."
private const val IMPORTANT_FOLLOW_UP_BODY = "Hay una actualización importante de seguimiento."
private const val RELAPSE_ALERT_BODY = "Hay una actualización prioritaria de seguimiento."
private const val SUPERVISOR_VALIDATION_APPROVED_BODY = "Tu cuenta de supervisor fue aprobada."
private const val SUPERVISOR_VALIDATION_REQUIRED_BODY = "Hay una cuenta de supervisor pendiente de validación."
private const val USER_VALIDATION_REQUIRED_BODY = "Hay una cuenta pendiente de validación."
private const val SUPERVISION_REQUEST_CONFLICT_BODY = "Hay una solicitud de supervisión que requiere revisión."
private const val MAX_ROUTE_LENGTH = 256
private const val MAX_ENTITY_ID_LENGTH = 128
private const val MAX_CREATED_AT_LENGTH = 64
