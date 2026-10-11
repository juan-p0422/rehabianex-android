package com.example.rehabianex

import com.example.rehabianex.data.model.FcmTokenRegistrationRequest
import com.example.rehabianex.data.remote.ApiService
import com.example.rehabianex.navigation.Routes
import com.example.rehabianex.notifications.AgendaNotificationHelper
import com.example.rehabianex.notifications.FcmContract
import com.example.rehabianex.notifications.NotificationScheduler
import com.example.rehabianex.notifications.fcmDeliveryKeys
import com.example.rehabianex.notifications.localAppointmentDeliveryKey
import com.example.rehabianex.notifications.safeFcmNotificationContent
import com.example.rehabianex.notifications.safeFcmRoute
import com.example.rehabianex.notifications.validatedFcmCreatedAt
import com.example.rehabianex.notifications.validatedFcmPriority
import com.example.rehabianex.notifications.validatedFcmType
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.http.HTTP
import retrofit2.http.POST

class FcmNotificationPolicyTest {
    @Test
    fun contract_acceptsExactlyTheFinalNotificationTypes() {
        assertEquals(18, FcmContract.allowedTypes.size)
        assertEquals(
            setOf(
                "appointment_reminder",
                "progress_checkin",
                "sober_day_update",
                "achievement_unlocked",
                "supervision_request",
                "supervision_response",
                "unlink_request",
                "consent_suspended",
                "intervention_update",
                "relapse_alert",
                "risk_alert",
                "vulnerable_user_priority",
                "user_validation_required",
                "supervisor_validation_required",
                "supervisor_validation_approved",
                "supervision_request_conflict",
                "system_notice",
                "test_notification",
            ),
            FcmContract.allowedTypes,
        )
        assertNull(validatedFcmType("exercise_reminder"))
        assertNull(validatedFcmType("unknown_notification"))
    }

    @Test
    fun patientTypes_mapOnlyToExistingSafeDestinations() {
        assertEquals(
            Routes.Agenda,
            safeFcmRoute("patient", "appointment_reminder", "/agenda-events/event-1", "event-1"),
        )
        assertEquals(
            Routes.EmotionalCheckin,
            safeFcmRoute("patient", "progress_checkin", "/checkins/new"),
        )
        assertEquals(
            Routes.PatientHome,
            safeFcmRoute("patient", "sober_day_update", "/home"),
        )
        assertEquals(
            Routes.Achievements,
            safeFcmRoute("patient", "achievement_unlocked", "/achievements/a-1", "a-1"),
        )
        assertEquals(
            Routes.Interventions,
            safeFcmRoute("patient", "intervention_update", "/interventions/i-1", "i-1"),
        )
    }

    @Test
    fun supervisionTypes_mapByRole() {
        listOf("supervision_request", "supervision_response").forEach { type ->
            assertEquals(
                Routes.SupervisorLink,
                safeFcmRoute("patient", type, "/supervision-requests/r-1", "r-1"),
            )
            assertEquals(
                Routes.PatientsList,
                safeFcmRoute("supervisor", type, "/supervision-requests/r-1", "r-1"),
            )
        }
        assertEquals(
            Routes.SupervisorLink,
            safeFcmRoute("patient", "unlink_request", "/unlink-requests/u-1", "u-1"),
        )
        assertEquals(
            Routes.PatientsList,
            safeFcmRoute("supervisor", "unlink_request", "/unlink-requests/u-1", "u-1"),
        )
        assertEquals(
            Routes.PatientHome,
            safeFcmRoute("patient", "unlink_request", "/supervision-requests/u-1", "u-1"),
        )
        assertEquals(
            Routes.SupervisorHome,
            safeFcmRoute("supervisor", "unlink_request", "/supervision-requests/u-1", "u-1"),
        )
        assertEquals(
            Routes.PatientsList,
            safeFcmRoute("supervisor", "consent_suspended", "/consents/c-1", "c-1"),
        )
        assertEquals(
            Routes.SupervisorHome,
            safeFcmRoute("supervisor", "consent_suspended", "/consents/c-1", null),
        )
        assertEquals(
            Routes.SupervisorHome,
            safeFcmRoute("supervisor", "supervisor_validation_approved", "/home", "s-1"),
        )
        assertEquals(
            Routes.PatientHome,
            safeFcmRoute("patient", "supervisor_validation_approved", "/home", "s-1"),
        )
    }

    @Test
    fun sensitiveSupervisorTypes_requireSupervisorAndSafeRoutes() {
        listOf("relapse_alert", "risk_alert", "vulnerable_user_priority").forEach { type ->
            assertEquals(
                Routes.PatientsList,
                safeFcmRoute("supervisor", type, "/patients/priority", "opaque-1"),
            )
            assertEquals(
                Routes.PatientHome,
                safeFcmRoute("patient", type, "/patients/priority", "opaque-1"),
            )
            assertEquals(
                Routes.AdminHome,
                safeFcmRoute("admin", type, "/patients/priority", "opaque-1"),
            )
        }
        assertEquals(
            Routes.SupervisorHome,
            safeFcmRoute("supervisor", "risk_alert", "/patients/opaque-1/risk", "opaque-1"),
        )
    }

    @Test
    fun adminTypes_requireAdminAndUseExistingAdminHomeFallback() {
        val routesByType = mapOf(
            "user_validation_required" to "/admin/users/pending/user-1",
            "supervisor_validation_required" to "/admin/supervisors/pending/supervisor-1",
            "supervision_request_conflict" to "/admin/supervision-conflicts/conflict-1",
        )
        routesByType.forEach { (type, route) ->
            val id = route.substringAfterLast('/')
            assertEquals(Routes.AdminHome, safeFcmRoute("admin", type, route, id))
            assertEquals(Routes.PatientHome, safeFcmRoute("patient", type, route, id))
            assertEquals(Routes.SupervisorHome, safeFcmRoute("supervisor", type, route, id))
        }
    }

    @Test
    fun systemAndTestTypes_useSafeExistingDestinations() {
        assertEquals(
            Routes.Interventions,
            safeFcmRoute("patient", "system_notice", "/interventions/i-1", "i-1"),
        )
        assertEquals(
            Routes.SupervisorLink,
            safeFcmRoute("patient", "system_notice", "/supervision-requests/r-1", "r-1"),
        )
        assertEquals(
            Routes.PatientsList,
            safeFcmRoute("supervisor", "system_notice", "/supervision-requests/r-1", "r-1"),
        )
        assertEquals(
            Routes.PatientHome,
            safeFcmRoute("patient", "test_notification", "/not-trusted"),
        )
    }

    @Test
    fun invalidRouteOrEntityMismatch_fallsBackToRoleHome() {
        assertEquals(
            Routes.PatientHome,
            safeFcmRoute("patient", "appointment_reminder", "/agenda-events/other", "event-1"),
        )
        assertEquals(
            Routes.SupervisorHome,
            safeFcmRoute("supervisor", "risk_alert", "/patients/not-priority", "patient-1"),
        )
        assertEquals(
            Routes.AdminHome,
            safeFcmRoute("admin", "user_validation_required", "/admin/not-allowed", "id-1"),
        )
    }

    @Test
    fun unauthenticatedOrUnsupportedRole_hasNoDestination() {
        assertNull(safeFcmRoute(null, "appointment_reminder", "/agenda-events/1", "1"))
        assertNull(safeFcmRoute("guest", "test_notification", "/home"))
    }

    @Test
    fun remoteTitleBodyAndProhibitedIdentityFields_neverReachVisibleContent() {
        val prohibitedValues = listOf(
            "Paciente Juan",
            "alias-secreto",
            "juan@example.com",
            "5551234567",
            "uid-visible",
            "Recaída, craving, ansiedad y riesgo alto",
            "Diagnóstico, nota clínica, dolor, lesión, adicción y ubicación",
        )
        FcmContract.allowedTypes.forEach { type ->
            prohibitedValues.forEach { untrusted ->
                val content = safeFcmNotificationContent(type, untrusted, untrusted)
                val visible = "${content.title} ${content.body}".lowercase()
                assertEquals("Rehabianex", content.title)
                assertFalse(visible.contains("juan"))
                assertFalse(visible.contains("alias"))
                assertFalse(visible.contains("@"))
                assertFalse(visible.contains("5551234567"))
                assertFalse(visible.contains("uid"))
                assertFalse(visible.contains("reca"))
                assertFalse(visible.contains("craving"))
                assertFalse(visible.contains("ansiedad"))
                assertFalse(visible.contains("riesgo alto"))
                assertFalse(visible.contains("diagnóstico"))
                assertFalse(visible.contains("nota"))
                assertFalse(visible.contains("dolor"))
                assertFalse(visible.contains("lesión"))
                assertFalse(visible.contains("adicción"))
                assertFalse(visible.contains("ubicación"))
            }
        }
        listOf(
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
        ).forEach { prohibitedKey ->
            assertTrue(prohibitedKey in FcmContract.prohibitedDataKeys)
        }
    }

    @Test
    fun visibleBody_isSelectedOnlyByValidatedType() {
        val expectedBodies = mapOf(
            "appointment_reminder" to "Tienes un evento de agenda próximo.",
            "progress_checkin" to "Es momento de registrar tu seguimiento.",
            "achievement_unlocked" to "Has alcanzado un nuevo logro.",
            "sober_day_update" to "Has alcanzado un nuevo avance en tu proceso.",
            "intervention_update" to "Tu plan de apoyo tiene una actualización.",
            "supervision_response" to "Tu solicitud de acompañamiento fue actualizada.",
            "supervision_request" to "Tienes una nueva solicitud de acompañamiento.",
            "unlink_request" to "Tienes una solicitud de cambio de consentimiento.",
            "consent_suspended" to "Se actualizó el estado de un consentimiento.",
            "risk_alert" to "Hay una actualización importante de seguimiento.",
            "relapse_alert" to "Hay una actualización prioritaria de seguimiento.",
            "supervisor_validation_approved" to "Tu cuenta de supervisor fue aprobada.",
            "supervisor_validation_required" to "Hay una cuenta de supervisor pendiente de validación.",
            "user_validation_required" to "Hay una cuenta pendiente de validación.",
            "supervision_request_conflict" to "Hay una solicitud de supervisión que requiere revisión.",
            "vulnerable_user_priority" to "Hay una actualización importante de seguimiento.",
            "system_notice" to "Tienes una actualización en Rehabianex.",
            "test_notification" to "Tienes una actualización en Rehabianex.",
        )

        assertEquals(FcmContract.allowedTypes, expectedBodies.keys)
        expectedBodies.forEach { (type, expectedBody) ->
            val content = safeFcmNotificationContent(type, "Título remoto", "Cuerpo remoto")
            assertEquals("Rehabianex", content.title)
            assertEquals(expectedBody, content.body)
        }
    }

    @Test
    fun priority_acceptsOnlyOfficialValues() {
        assertEquals("normal", validatedFcmPriority(null))
        assertEquals("normal", validatedFcmPriority("unexpected"))
        assertEquals("high", validatedFcmPriority(" HIGH "))
        assertEquals("critical", validatedFcmPriority("critical"))
    }

    @Test
    fun localFallback_allowsOnlyBasicNonSensitiveReminders() {
        assertTrue(
            NotificationScheduler.isLocalFallbackAllowed(
                AgendaNotificationHelper.REMINDER_DAILY_NOTE,
            ),
        )
        assertTrue(
            NotificationScheduler.isLocalFallbackAllowed(
                AgendaNotificationHelper.REMINDER_MOTIVATIONAL,
            ),
        )
        assertFalse(
            NotificationScheduler.isLocalFallbackAllowed(
                AgendaNotificationHelper.REMINDER_SUPERVISOR_SUMMARY,
            ),
        )
        assertFalse(
            NotificationScheduler.isLocalFallbackAllowed(
                AgendaNotificationHelper.REMINDER_SUPERVISOR_RELAPSE,
            ),
        )
        assertFalse(
            NotificationScheduler.isLocalFallbackAllowed(
                AgendaNotificationHelper.REMINDER_SUPERVISOR_REQUESTS,
            ),
        )
        assertFalse(
            NotificationScheduler.isLocalFallbackAllowed(
                AgendaNotificationHelper.REMINDER_SUPERVISOR_FOLLOW_UP,
            ),
        )
    }

    @Test
    fun appointmentPushSharesDeduplicationKeyWithLocalReminder() {
        val localKey = localAppointmentDeliveryKey("event-1")
        val remoteKeys = fcmDeliveryKeys("appointment_reminder", "rn_test", "event-1")
        val unrelatedKeys = fcmDeliveryKeys("system_notice", "rn_other", "event-1")

        assertTrue(localKey in remoteKeys)
        assertFalse(localKey in unrelatedKeys)
    }

    @Test
    fun createdAt_acceptsIso8601AndRejectsArbitraryText() {
        assertEquals(
            "2026-09-12T20:30-06:00",
            validatedFcmCreatedAt("2026-09-12T20:30:00-06:00"),
        )
        assertNull(validatedFcmCreatedAt("not-a-date"))
    }

    @Test
    fun registrationPayload_containsOnlyApprovedMetadata() {
        val json = Gson().toJson(
            FcmTokenRegistrationRequest(
                fcmToken = "synthetic-test-token",
                appVersion = "1.0",
                timezone = "America/Mexico_City",
                locale = "es-MX",
            ),
        )

        assertFalse(json.contains("user_id"))
        assertFalse(json.contains("role"))
        assertFalse(json.contains("is_admin"))
        assertFalse(json.contains("topics"))
        assertFalse(json.contains("device_id"))
    }

    @Test
    fun backendContract_usesAgreedFcmPaths() {
        val register = ApiService::class.java.methods.single { it.name == "registerFcmToken" }
            .getAnnotation(POST::class.java)
        val revoke = ApiService::class.java.methods.single { it.name == "revokeFcmToken" }
            .getAnnotation(HTTP::class.java)

        assertEquals("notifications/fcm-token", register.value)
        assertEquals("DELETE", revoke.method)
        assertEquals("notifications/fcm-token", revoke.path)
        assertTrue(revoke.hasBody)
    }
}
