package com.example.rehabianex.data.model

import com.google.gson.annotations.SerializedName

data class MeResponse(
    val ok: Boolean? = null,
    val auth: AuthData? = null,
    val profile: SessionProfileDto? = null,
)

data class SessionProfileDto(
    val uid: String? = null,
    val email: String? = null,
    @SerializedName("full_name")
    val fullName: String? = null,
    @SerializedName("display_name")
    val displayName: String? = null,
    @SerializedName("safe_display_name")
    val safeDisplayName: String? = null,
    val nickname: String? = null,
    val role: String? = null,
    val status: String? = null,
    val phone: String? = null,
    val gender: String? = null,
    val age: Int? = null,
    val authorized: Boolean? = null,
    val verified: Boolean? = null,
    @SerializedName("supervisor_type")
    val supervisorType: String? = null,
    @SerializedName("supervisor_type_other")
    val supervisorTypeOther: String? = null,
    @SerializedName("supervisor_code")
    val supervisorCode: String? = null,
    @SerializedName("primary_risks")
    val primaryRisks: List<String>? = null,
    @SerializedName("is_anonymous")
    val isAnonymous: Boolean? = null,
    @SerializedName("privacy_mode")
    val privacyMode: Boolean? = null,
    @SerializedName("photo_url")
    val photoUrl: String? = null,
    @SerializedName("supervisor_uid")
    val supervisorUid: String? = null,
    @SerializedName("supervision_status")
    val supervisionStatus: String? = null,
    @SerializedName("wants_supervision")
    val wantsSupervision: Boolean? = null,
    @SerializedName("supervision_ended_at")
    val supervisionEndedAt: String? = null,
    @SerializedName("sobriety_start_date")
    val sobrietyStartDate: String? = null,
    val supervisor: SupervisorPublicDto? = null,
)

data class ResourceListResponse<T>(
    val ok: Boolean? = null,
    val count: Int? = null,
    val data: List<T>? = null,
    val sync: SyncMetadataDto? = null,
)

data class SyncMetadataDto(
    val mode: String? = null,
    @SerializedName("cache_action")
    val cacheAction: String? = null,
    @SerializedName("include_deleted")
    val includeDeleted: Boolean? = null,
    @SerializedName("incremental_supported")
    val incrementalSupported: Boolean? = null,
    @SerializedName("server_time")
    val serverTime: String? = null,
)

data class PatientNoteDto(
    @SerializedName("document_id")
    val documentId: String? = null,
    @SerializedName("note_id")
    val noteId: String? = null,
    @SerializedName("patient_uid")
    val patientUid: String? = null,
    val mood: String? = null,
    @SerializedName("mood_score")
    val moodScore: Int? = null,
    @SerializedName("anxiety_level")
    val anxietyLevel: Int? = null,
    @SerializedName("craving_level")
    val cravingLevel: Int? = null,
    @SerializedName("energy_level")
    val energyLevel: Int? = null,
    @SerializedName("sleep_quality")
    val sleepQuality: Int? = null,
    @SerializedName("had_relapse")
    val hadRelapse: Boolean? = null,
    val triggers: List<String>? = null,
    @SerializedName("coping_actions")
    val copingActions: List<String>? = null,
    @SerializedName("note_text")
    val noteText: String? = null,
    @SerializedName("ai_risk_level")
    val aiRiskLevel: String? = null,
    @SerializedName("ai_risk_score")
    val aiRiskScore: Double? = null,
    @SerializedName("created_at")
    val createdAt: String? = null,
    @SerializedName("updated_at")
    val updatedAt: String? = null,
    @SerializedName("deleted_at")
    val deletedAt: String? = null,
    @SerializedName("client_mutation_id")
    val clientMutationId: String? = null,
)

data class CreatePatientNoteRequest(
    val mood: String,
    @SerializedName("mood_score")
    val moodScore: Int,
    @SerializedName("anxiety_level")
    val anxietyLevel: Int,
    @SerializedName("craving_level")
    val cravingLevel: Int,
    @SerializedName("energy_level")
    val energyLevel: Int = 5,
    @SerializedName("sleep_quality")
    val sleepQuality: Int = 5,
    @SerializedName("had_relapse")
    val hadRelapse: Boolean,
    val triggers: List<String> = emptyList(),
    @SerializedName("coping_actions")
    val copingActions: List<String> = emptyList(),
    @SerializedName("note_text")
    val noteText: String = "",
    @SerializedName("client_mutation_id")
    val clientMutationId: String? = null,
)

data class UpdatePatientRequest(
    @SerializedName("full_name")
    val fullName: String? = null,
    val nickname: String? = null,
    val phone: String? = null,
    val gender: String? = null,
    val age: Int? = null,
    @SerializedName("photo_url")
    val photoUrl: String? = null,
    @SerializedName("sobriety_start_date")
    val sobrietyStartDate: String? = null,
    @SerializedName("is_anonymous")
    val isAnonymous: Boolean? = null,
    @SerializedName("privacy_mode")
    val privacyMode: Boolean? = null,
    @SerializedName("primary_risks")
    val primaryRisks: List<String>? = null,
)

data class UpdateSupervisorRequest(
    @SerializedName("full_name")
    val fullName: String,
    val phone: String,
    @SerializedName("supervisor_type")
    val supervisorType: String,
    @SerializedName("supervisor_type_other")
    val supervisorTypeOther: String? = null,
)

data class MutationResponse<T>(
    val ok: Boolean? = null,
    val message: String? = null,
    val id: String? = null,
    val data: T? = null,
)

data class CreateSupervisionRequest(
    @SerializedName("supervisor_uid")
    val supervisorUid: String?,
    val message: String,
    val type: String,
)

data class SupervisorResolveResponse(
    val ok: Boolean? = null,
    val uid: String? = null,
    @SerializedName("full_name")
    val fullName: String? = null,
    @SerializedName("supervisor_type")
    val supervisorType: String? = null,
    val available: Boolean? = null,
    val supervisor: SupervisorPublicDto? = null,
) {
    fun resolvedSupervisor(): SupervisorPublicDto? {
        return supervisor ?: if (
            uid != null || fullName != null || supervisorType != null || available != null
        ) {
            SupervisorPublicDto(
                uid = uid,
                fullName = fullName,
                supervisorType = supervisorType,
                available = available,
            )
        } else {
            null
        }
    }
}

data class SupervisorPublicDto(
    val uid: String? = null,
    @SerializedName("full_name")
    val fullName: String? = null,
    @SerializedName("supervisor_type")
    val supervisorType: String? = null,
    val available: Boolean? = null,
)

data class SupervisorCodePreview(
    val code: String,
    val supervisorUid: String,
    val fullName: String,
    val supervisorType: String,
    val available: Boolean,
)

data class CreateConsentRequest(
    @SerializedName("supervisor_uid")
    val supervisorUid: String,
    val type: String,
    @SerializedName("explicit_consent")
    val explicitConsent: Boolean,
    @SerializedName("consent_text")
    val consentText: String,
    val scope: List<String>,
)

data class ConsentDto(
    @SerializedName("document_id")
    val documentId: String? = null,
    @SerializedName("consent_id")
    val consentId: String? = null,
    @SerializedName("patient_uid")
    val patientUid: String? = null,
    @SerializedName("supervisor_uid")
    val supervisorUid: String? = null,
    val type: String? = null,
    val status: String? = null,
    @SerializedName("explicit_consent")
    val explicitConsent: Boolean? = null,
    @SerializedName("consent_text")
    val consentText: String? = null,
    val scope: List<String>? = null,
    @SerializedName("paused_at")
    val pausedAt: String? = null,
    @SerializedName("revoked_at")
    val revokedAt: String? = null,
    @SerializedName("revocation_reason")
    val revocationReason: String? = null,
    @SerializedName("updated_at")
    val updatedAt: String? = null,
)

data class UpdateConsentRequest(
    @SerializedName("explicit_consent")
    val explicitConsent: Boolean? = null,
    val status: String? = null,
    val scope: List<String>? = null,
)

data class UnlinkSupervisionResponse(
    val ok: Boolean? = null,
    val message: String? = null,
    val relationship: SupervisionRelationshipDto? = null,
    val patient: UnlinkedPatientDto? = null,
)

data class SupervisionRelationshipDto(
    @SerializedName("supervisor_uid")
    val supervisorUid: String? = null,
    @SerializedName("patient_uid")
    val patientUid: String? = null,
    val status: String? = null,
    @SerializedName("ended_at")
    val endedAt: String? = null,
    @SerializedName("revoked_consents")
    val revokedConsents: Int? = null,
)

data class UnlinkedPatientDto(
    val uid: String? = null,
    @SerializedName("supervisor_uid")
    val supervisorUid: String? = null,
    @SerializedName("wants_supervision")
    val wantsSupervision: Boolean? = null,
    @SerializedName("supervision_status")
    val supervisionStatus: String? = null,
    @SerializedName("supervision_ended_at")
    val supervisionEndedAt: String? = null,
)

data class NotificationSettingsDto(
    val id: String? = null,
    @SerializedName("settings_id")
    val settingsId: String? = null,
    @SerializedName("document_id")
    val documentId: String? = null,
    @SerializedName("notification_setting_id")
    val notificationSettingId: String? = null,
    @SerializedName("patient_uid")
    val patientUid: String? = null,
    @SerializedName("user_uid")
    val userUid: String? = null,
    @SerializedName("supervisor_uid")
    val supervisorUid: String? = null,
    @SerializedName("daily_check_in_enabled")
    val dailyCheckInEnabled: Boolean? = null,
    @SerializedName("daily_check_in_time")
    val dailyCheckInTime: String? = null,
    @SerializedName("daily_note_enabled")
    val dailyNoteEnabled: Boolean? = null,
    @SerializedName("daily_note_time")
    val dailyNoteTime: String? = null,
    @SerializedName("sober_day_enabled")
    val soberDayEnabled: Boolean? = null,
    @SerializedName("sober_day_time")
    val soberDayTime: String? = null,
    @SerializedName("achievement_enabled")
    val achievementEnabled: Boolean? = null,
    @SerializedName("achievement_time")
    val achievementTime: String? = null,
    @SerializedName("event_reminders_enabled")
    val eventRemindersEnabled: Boolean? = null,
    @SerializedName("motivational_enabled")
    val motivationalEnabled: Boolean? = null,
    @SerializedName("motivational_time")
    val motivationalTime: String? = null,
    @SerializedName("craving_alerts_enabled")
    val cravingAlertsEnabled: Boolean? = null,
    @SerializedName("supervisor_alerts_enabled")
    val supervisorAlertsEnabled: Boolean? = null,
    @SerializedName("support_contact_alerts_enabled")
    val supportContactAlertsEnabled: Boolean? = null,
    val timezone: String? = null,
    @SerializedName("created_at")
    val createdAt: String? = null,
    @SerializedName("updated_at")
    val updatedAt: String? = null,
)

data class SaveNotificationSettingsRequest(
    @SerializedName("daily_check_in_enabled")
    val dailyCheckInEnabled: Boolean,
    @SerializedName("daily_check_in_time")
    val dailyCheckInTime: String,
    @SerializedName("daily_note_enabled")
    val dailyNoteEnabled: Boolean,
    @SerializedName("daily_note_time")
    val dailyNoteTime: String,
    @SerializedName("sober_day_enabled")
    val soberDayEnabled: Boolean,
    @SerializedName("sober_day_time")
    val soberDayTime: String,
    @SerializedName("achievement_enabled")
    val achievementEnabled: Boolean,
    @SerializedName("achievement_time")
    val achievementTime: String,
    @SerializedName("event_reminders_enabled")
    val eventRemindersEnabled: Boolean,
    @SerializedName("motivational_enabled")
    val motivationalEnabled: Boolean,
    @SerializedName("motivational_time")
    val motivationalTime: String,
    @SerializedName("craving_alerts_enabled")
    val cravingAlertsEnabled: Boolean,
    @SerializedName("supervisor_alerts_enabled")
    val supervisorAlertsEnabled: Boolean,
    @SerializedName("support_contact_alerts_enabled")
    val supportContactAlertsEnabled: Boolean,
    val timezone: String = "America/Mexico_City",
)

data class StorePatientNoteResponse(
    val ok: Boolean? = null,
    val message: String? = null,
    val note: PatientNoteDto? = null,
    val data: PatientNoteDto? = null,
    @SerializedName("idempotent_replay")
    val idempotentReplay: Boolean? = null,
) {
    fun resolvedNote(): PatientNoteDto? = note ?: data
}

data class SupportContactDto(
    val id: String? = null,
    @SerializedName("document_id")
    val documentId: String? = null,
    @SerializedName("support_contact_id")
    val supportContactId: String? = null,
    @SerializedName("patient_uid")
    val patientUid: String? = null,
    val name: String? = null,
    val relationship: String? = null,
    @SerializedName("relationship_category")
    val relationshipCategory: String? = null,
    @SerializedName("relationship_other")
    val relationshipOther: String? = null,
    val phone: String? = null,
    val priority: Int? = null,
    @SerializedName("can_receive_alerts")
    val canReceiveAlerts: Boolean? = null,
    val notes: String? = null,
    @SerializedName("created_at")
    val createdAt: String? = null,
    @SerializedName("updated_at")
    val updatedAt: String? = null,
    @SerializedName("deleted_at")
    val deletedAt: String? = null,
)

data class CreateSupportContactRequest(
    val name: String,
    val relationship: String,
    val phone: String,
    val priority: Int,
    @SerializedName("can_receive_alerts")
    val canReceiveAlerts: Boolean,
    val notes: String? = null,
    @SerializedName("relationship_category")
    val relationshipCategory: String = "other",
    @SerializedName("relationship_other")
    val relationshipOther: String? = null,
)

data class UpdateSupportContactRequest(
    val name: String,
    val relationship: String,
    val phone: String,
    val priority: Int? = null,
    @SerializedName("can_receive_alerts")
    val canReceiveAlerts: Boolean,
    val notes: String? = null,
    @SerializedName("relationship_category")
    val relationshipCategory: String = "other",
    @SerializedName("relationship_other")
    val relationshipOther: String? = null,
)

data class AgendaEventDto(
    @SerializedName("document_id")
    val documentId: String? = null,
    @SerializedName("event_id")
    val eventId: String? = null,
    @SerializedName("patient_uid")
    val patientUid: String? = null,
    val type: String? = null,
    val title: String? = null,
    @SerializedName("starts_at")
    val startsAt: String? = null,
    @SerializedName("ends_at")
    val endsAt: String? = null,
    val location: String? = null,
    val status: String? = null,
    @SerializedName("attendance_reported")
    val attendanceReported: Boolean? = null,
    @SerializedName("attendance_reported_at")
    val attendanceReportedAt: String? = null,
    val notes: String? = null,
    @SerializedName("created_at")
    val createdAt: String? = null,
    @SerializedName("updated_at")
    val updatedAt: String? = null,
    @SerializedName("deleted_at")
    val deletedAt: String? = null,
)

data class CreateAgendaEventRequest(
    val type: String,
    val title: String,
    @SerializedName("starts_at")
    val startsAt: String,
    @SerializedName("ends_at")
    val endsAt: String,
    val location: String,
    val status: String = "scheduled",
    val notes: String? = null,
)

data class UpdateAgendaEventRequest(
    val status: String? = null,
    @SerializedName("attendance_reported")
    val attendanceReported: Boolean? = null,
)

data class PatientHomeData(
    val firstName: String,
    val soberDays: String,
    val dailyStatus: String,
    val recentState: String,
)

data class SupportContact(
    val id: String,
    val name: String,
    val relationship: String,
    val phone: String,
    val priority: Int,
    val canReceiveAlerts: Boolean,
    val notes: String = "",
)

data class LocalResourceDto(
    @SerializedName("document_id")
    val documentId: String? = null,
    @SerializedName("resource_id")
    val resourceId: String? = null,
    val name: String? = null,
    val type: String? = null,
    val city: String? = null,
    val address: String? = null,
    val phone: String? = null,
    @SerializedName("meeting_schedule")
    val meetingSchedule: List<String>? = null,
    val tags: List<String>? = null,
    @SerializedName("source_name")
    val sourceName: String? = null,
    @SerializedName("source_url")
    val sourceUrl: String? = null,
    @SerializedName("reviewed_at")
    val reviewedAt: String? = null,
    @SerializedName("created_at")
    val createdAt: String? = null,
    @SerializedName("updated_at")
    val updatedAt: String? = null,
    @SerializedName("deleted_at")
    val deletedAt: String? = null,
)

data class LocalResource(
    val id: String,
    val name: String,
    val type: String,
    val typeLabel: String,
    val city: String,
    val detail: String,
    val phone: String,
    val sourceName: String,
    val sourceUrl: String,
    val reviewedAt: String,
    val updatedAt: String,
)

data class AgendaEvent(
    val id: String,
    val title: String,
    val timeText: String,
    val detail: String,
    val startsAt: String,
    val endsAt: String,
    val durationMinutes: Int,
    val dateLabel: String,
    val dayNumber: Int,
    val typeLabel: String,
    val status: String,
    val attendanceReported: Boolean? = null,
)

data class AchievementDto(
    @SerializedName("document_id")
    val documentId: String? = null,
    @SerializedName("achievement_id")
    val achievementId: String? = null,
    val title: String? = null,
    val description: String? = null,
    @SerializedName("created_at")
    val createdAt: String? = null,
    @SerializedName("updated_at")
    val updatedAt: String? = null,
    @SerializedName("deleted_at")
    val deletedAt: String? = null,
)

data class PatientAchievementDto(
    @SerializedName("document_id")
    val documentId: String? = null,
    @SerializedName("patient_achievement_id")
    val patientAchievementId: String? = null,
    @SerializedName("patient_uid")
    val patientUid: String? = null,
    @SerializedName("achievement_id")
    val achievementId: String? = null,
    @SerializedName("awarded_at")
    val awardedAt: String? = null,
    @SerializedName("created_at")
    val createdAt: String? = null,
    @SerializedName("updated_at")
    val updatedAt: String? = null,
    @SerializedName("deleted_at")
    val deletedAt: String? = null,
)

data class OfflineStatus(
    val isOffline: Boolean = false,
    val isRefreshing: Boolean = false,
    val pendingSyncCount: Int = 0,
    val lastSyncedAt: Long? = null,
    val warning: String? = null,
)

data class MetricsData(
    val soberDays: String,
    val weekly: MetricsPeriodData,
    val monthly: MetricsPeriodData,
) {
    val weeklyAnxiety: String get() = weekly.anxiety
    val weeklyCraving: String get() = weekly.craving
    val averageEnergy: String get() = weekly.averageEnergy
    val averageSleep: String get() = weekly.averageSleep
    val predominantMood: String get() = weekly.predominantMood
    val chartValues: List<Float> get() = weekly.chartValues
    val insight: String? get() = weekly.insight
    val recordsCount: Int get() = weekly.recordsCount
    val relapseCount: Int get() = weekly.relapseCount
    val riskSummary: String get() = weekly.riskSummary
}

data class MetricsPeriodData(
    val label: String,
    val dateRange: String,
    val anxiety: String,
    val craving: String,
    val averageEnergy: String,
    val averageSleep: String,
    val predominantMood: String,
    val chartValues: List<Float>,
    val insight: String?,
    val recordsCount: Int,
    val relapseCount: Int,
    val riskSummary: String,
)

data class AchievementItem(
    val id: String,
    val title: String,
    val description: String,
    val awardedAt: String,
    val isUnlocked: Boolean = true,
    val progressText: String = "",
)

data class AchievementsData(
    val soberDays: String,
    val achievements: List<AchievementItem>,
    val nextHint: String,
)

data class PatientProfileData(
    val initials: String,
    val fullName: String,
    val registeredName: String,
    val nickname: String?,
    val email: String,
    val phone: String,
    val supervisorText: String,
    val supervisorType: String? = null,
    val supervisorUid: String,
    val consentId: String?,
    val consentActive: Boolean,
    val consentScope: List<String> = emptyList(),
    val pendingLinkRequestId: String? = null,
    val privacyText: String,
    val isAnonymous: Boolean,
    val supervisionState: PatientSupervisionState = PatientSupervisionState.NoSupervisor,
    val privacyMode: Boolean = false,
    val sobrietyStartDate: String? = null,
    val photoUrl: String? = null,
    val isStale: Boolean = false,
)

enum class PatientSupervisionState {
    NoSupervisor,
    LinkPending,
    LinkRejected,
    Active,
    Paused,
    Revoked,
    UnlinkPending,
    Unlinked,
}

data class NotificationSettingsData(
    val id: String?,
    val dailyNoteEnabled: Boolean,
    val dailyNoteTime: String,
    val soberDayEnabled: Boolean,
    val soberDayTime: String,
    val achievementEnabled: Boolean,
    val achievementTime: String,
    val eventRemindersEnabled: Boolean,
    val motivationalEnabled: Boolean,
    val motivationalTime: String,
    val cravingAlertsEnabled: Boolean = true,
    val supervisorAlertsEnabled: Boolean = true,
    val supportContactAlertsEnabled: Boolean = true,
)
