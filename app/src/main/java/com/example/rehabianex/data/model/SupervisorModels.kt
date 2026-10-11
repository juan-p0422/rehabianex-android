package com.example.rehabianex.data.model

import com.google.gson.annotations.SerializedName

data class SupervisedPatientsResponse(
    val ok: Boolean? = null,
    @SerializedName("supervisor_uid")
    val supervisorUid: String? = null,
    val count: Int? = null,
    val patients: List<SupervisorPatientDto>? = null,
)

data class SupervisorPatientDto(
    val uid: String? = null,
    val email: String? = null,
    @SerializedName("full_name")
    val fullName: String? = null,
    @SerializedName("display_name")
    val displayName: String? = null,
    @SerializedName("patient_ref")
    val patientRef: String? = null,
    val nickname: String? = null,
    val gender: String? = null,
    val age: Int? = null,
    @SerializedName("is_anonymous")
    val isAnonymous: Boolean? = null,
    @SerializedName("privacy_mode")
    val privacyMode: Boolean? = null,
    @SerializedName("sobriety_start_date")
    val sobrietyStartDate: String? = null,
    @SerializedName("supervision_status")
    val supervisionStatus: String? = null,
    @SerializedName("consent_active")
    val consentActive: Boolean? = null,
    @SerializedName("explicit_consent")
    val explicitConsent: Boolean? = null,
    @SerializedName("consent_status")
    val consentStatus: String? = null,
    @SerializedName("supervisor_uid")
    val supervisorUid: String? = null,
    val status: String? = null,
    val permissions: SupervisorPatientPermissionsDto? = null,
)

data class SupervisorPatientPermissionsDto(
    @SerializedName("ai_chat_summary")
    val aiChatSummary: Boolean? = null,
)

data class PatientNotesResponse(
    val ok: Boolean? = null,
    @SerializedName("patient_uid")
    val patientUid: String? = null,
    val count: Int? = null,
    val notes: List<PatientNoteDto>? = null,
    val data: List<PatientNoteDto>? = null,
) {
    fun resolvedNotes(): List<PatientNoteDto> = notes ?: data.orEmpty()
}

data class SupervisionRequestDto(
    @SerializedName("document_id")
    val documentId: String? = null,
    @SerializedName("request_id")
    val requestId: String? = null,
    @SerializedName("patient_uid")
    val patientUid: String? = null,
    @SerializedName("patient_name")
    val patientName: String? = null,
    @SerializedName("patient_email")
    val patientEmail: String? = null,
    @SerializedName("supervisor_uid")
    val supervisorUid: String? = null,
    val status: String? = null,
    val type: String? = null,
    @SerializedName("request_type")
    val requestType: String? = null,
    val message: String? = null,
    @SerializedName("requested_at")
    val requestedAt: String? = null,
    @SerializedName("responded_at")
    val respondedAt: String? = null,
)

data class RespondSupervisionRequest(
    val status: String,
)

data class UpdateSupervisionRequest(
    val status: String,
)

data class SupervisorChatRequest(
    @SerializedName("supervisor_uid")
    val supervisorUid: String,
    val question: String,
    val mode: String = "ai",
)

data class SupervisorChatResponse(
    val ok: Boolean? = null,
    val mode: String? = null,
    val message: String? = null,
    @SerializedName("supervisor_uid")
    val supervisorUid: String? = null,
    val question: String? = null,
    val answer: String? = null,
    val model: String? = null,
    val stored: Boolean? = null,
    @SerializedName("session_id")
    val sessionId: String? = null,
    val context: SupervisorChatContext? = null,
)

data class SupervisorChatEligibilityResponse(
    val ok: Boolean? = null,
    val eligible: Boolean? = null,
    @SerializedName("eligible_patients_count")
    val eligiblePatientsCount: Int? = null,
    @SerializedName("required_scope")
    val requiredScope: String? = null,
)

data class SupervisorChatContext(
    @SerializedName("authorized_patients_count")
    val authorizedPatientsCount: Int? = null,
    @SerializedName("notes_count")
    val notesCount: Int? = null,
)

data class SupervisorPatientSummary(
    val uid: String,
    val name: String,
    val soberDays: String,
    val latestStatus: String,
    val latestDateText: String,
    val anxietyText: String,
    val cravingText: String,
    val anxietyLevel: Int,
    val cravingLevel: Int,
    val riskLevel: String,
    val hasRecentActivity: Boolean,
    val notesCount: Int,
    val accessPaused: Boolean = false,
    val canOpenDetails: Boolean = true,
    val accessState: SupervisorPatientAccessState = SupervisorPatientAccessState.Active,
)

enum class SupervisorPatientAccessState {
    Active,
    Paused,
    NoAccess,
    Unlinked,
}

data class SupervisorHomeData(
    val fullName: String,
    val supervisorType: String,
    val supervisorUid: String,
    val supervisorCode: String,
    val authorized: Boolean,
    val patients: List<SupervisorPatientSummary>,
    val pendingRequests: List<SupervisorPendingRequest>,
    val patientsCount: Int = patients.size,
    val pendingRequestsCount: Int = pendingRequests.size,
    val openInterventionsCount: Int = 0,
    val isFromCache: Boolean = false,
    val isRefreshing: Boolean = false,
    val lastSyncedAt: Long? = null,
    val refreshMessage: String? = null,
) {
    val highAnxietyCount: Int = patients.count { it.canOpenDetails && it.anxietyLevel >= 7 }
}

data class SupervisorPatientsData(
    val patients: List<SupervisorPatientSummary>,
    val pendingRequests: List<SupervisorPendingRequest>,
    val supervisorUid: String,
    val supervisorCode: String,
    val authorized: Boolean,
)

data class SupervisorPatientDetailData(
    val patient: SupervisorPatientSummary,
    val latestNotes: List<SupervisorNoteItem>,
    val relapseCount: Int,
)

data class SupervisorNoteItem(
    val title: String,
    val detail: String,
    val dateText: String,
)

data class SupervisorPendingRequest(
    val id: String,
    val patientUid: String,
    val patientName: String,
    val message: String,
    val requestedAt: String,
    val kind: SupervisorRequestKind = SupervisorRequestKind.Link,
)

enum class SupervisorRequestKind {
    Link,
    Unlink,
}

data class SupervisorProfileData(
    val initials: String,
    val fullName: String,
    val email: String,
    val phone: String,
    val supervisorType: String,
    val uid: String,
    val supervisorCode: String,
    val authorized: Boolean,
    val status: String,
    val linkedPatientsCount: Int,
    val pendingRequestsCount: Int,
    val isFromCache: Boolean = false,
    val isRefreshing: Boolean = false,
    val lastSyncedAt: Long? = null,
    val refreshMessage: String? = null,
    val supervisorTypeCode: String? = null,
    val supervisorTypeOther: String? = null,
)

data class SupervisorChatData(
    val supervisorUid: String,
    val patientsCount: Int,
    val authorized: Boolean,
    val messages: List<SupervisorChatMessage> = emptyList(),
)

data class SupervisorChatMessage(
    val text: String,
    val fromUser: Boolean,
    val responseMode: String? = null,
)
