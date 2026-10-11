package com.example.rehabianex.navigation

import android.net.Uri

object Routes {
    const val Splash = "splash"
    const val Login = "login"
    const val Register = "register"
    const val Consent = "consent"
    const val RoleSelection = "role_selection"

    const val PatientHome = "patient_home"
    const val EmotionalCheckin = "emotional_checkin"
    const val Metrics = "metrics"
    const val Achievements = "achievements"
    const val Interventions = "interventions"
    const val ActionPlan = "action_plan"
    const val SupportNetwork = "support_network"
    const val Agenda = "agenda"
    const val NewEvent = "new_event"
    const val LocalDirectory = "local_directory"
    const val SupervisorLink = "supervisor_link"
    const val Notifications = "notifications"
    const val PatientProfile = "patient_profile"

    const val SupervisorHome = "supervisor_home"
    const val PatientsList = "patients_list"
    const val PatientDetail = "patient_detail"
    const val PatientDetailArg = "$PatientDetail/{patientUid}"
    const val AiChat = "ai_chat"
    const val SupervisorProfile = "supervisor_profile"
    const val SupervisorNotifications = "supervisor_notifications"

    const val AdminHome = "admin_home"
    const val FcmDemo = "fcm_demo"

    fun patientDetail(patientUid: String): String {
        return "$PatientDetail/${Uri.encode(patientUid)}"
    }
}
