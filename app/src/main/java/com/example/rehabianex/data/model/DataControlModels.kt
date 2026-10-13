package com.example.rehabianex.data.model

import com.google.gson.annotations.SerializedName

data class DataControlSummaryResponse(
    val ok: Boolean? = null,
    val role: String? = null,
    @SerializedName("deletion_status")
    val deletionStatus: String? = null,
    val categories: List<DataControlCategoryDto>? = null,
    @SerializedName("editable_fields")
    val editableFields: List<String>? = null,
    val rectification: DataControlRectificationDto? = null,
    @SerializedName("account_deletion")
    val accountDeletion: DataControlAccountDeletionDto? = null,
    @SerializedName("retention_notice")
    val retentionNotice: String? = null,
    @SerializedName("manual_request_notice")
    val manualRequestNotice: String? = null,
)

data class DataControlCategoryDto(
    val code: String? = null,
    val label: String? = null,
    val location: String? = null,
    val exported: Boolean? = null,
    @SerializedName("deletion_mode")
    val deletionMode: String? = null,
)

data class DataControlRectificationDto(
    val profile: DataControlProfileRectificationDto? = null,
    val email: DataControlEmailRectificationDto? = null,
)

data class DataControlProfileRectificationDto(
    val method: String? = null,
    val route: String? = null,
    @SerializedName("editable_fields")
    val editableFields: List<String>? = null,
    @SerializedName("conflict_policy")
    val conflictPolicy: String? = null,
    @SerializedName("offline_queue_allowed")
    val offlineQueueAllowed: Boolean? = null,
)

data class DataControlEmailRectificationDto(
    @SerializedName("firebase_action")
    val firebaseAction: String? = null,
    @SerializedName("sync_route")
    val syncRoute: String? = null,
    @SerializedName("offline_queue_allowed")
    val offlineQueueAllowed: Boolean? = null,
)

data class DataControlAccountDeletionDto(
    val automated: Boolean? = null,
    @SerializedName("requires_recent_auth")
    val requiresRecentAuth: Boolean? = null,
    @SerializedName("recent_auth_max_age_seconds")
    val recentAuthMaxAgeSeconds: Int? = null,
    @SerializedName("confirmation_phrase")
    val confirmationPhrase: String? = null,
    @SerializedName("physical_purge_automatic")
    val physicalPurgeAutomatic: Boolean? = null,
)

data class AccountDeletionRequest(
    val scope: String = "account",
    val confirmation: String,
    @SerializedName("acknowledge_retention_limits")
    val acknowledgeRetentionLimits: Boolean,
)

data class AccountDeletionResponse(
    val ok: Boolean? = null,
    val request: AccountDeletionStatusDto? = null,
    @SerializedName("sync_blocked")
    val syncBlocked: Boolean? = null,
    @SerializedName("local_cleanup_required")
    val localCleanupRequired: Boolean? = null,
    @SerializedName("manual_attention_required")
    val manualAttentionRequired: Boolean? = null,
    @SerializedName("retention_notice")
    val retentionNotice: String? = null,
)

data class AccountDeletionStatusDto(
    @SerializedName("request_id")
    val requestId: String? = null,
    val status: String? = null,
    @SerializedName("requested_at")
    val requestedAt: String? = null,
)

data class DataControlSummary(
    val role: String,
    val deletionStatus: String,
    val categories: List<DataControlCategory>,
    val editableFields: List<String>,
    val rectificationRequiresConnection: Boolean,
    val automatedAccountDeletion: Boolean,
    val recentAuthMaxAgeSeconds: Int,
    val confirmationPhrase: String,
    val retentionNotice: String,
    val manualRequestNotice: String,
)

data class DataControlCategory(
    val code: String,
    val label: String,
    val location: String,
    val exported: Boolean,
    val deletionMode: String,
)

data class AccountDeletionReceipt(
    val requestId: String,
    val status: String,
    val requestedAt: String?,
    val manualAttentionRequired: Boolean,
    val retentionNotice: String,
)
