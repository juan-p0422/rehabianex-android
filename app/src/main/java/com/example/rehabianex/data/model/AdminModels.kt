package com.example.rehabianex.data.model

import com.google.gson.annotations.SerializedName

data class AdminSupervisorDto(
    val uid: String? = null,
    @SerializedName("full_name")
    val fullName: String? = null,
    val email: String? = null,
    val phone: String? = null,
    @SerializedName("supervisor_type")
    val supervisorType: String? = null,
    @SerializedName("supervisor_code")
    val supervisorCode: String? = null,
    val authorized: Boolean? = null,
    val verified: Boolean? = null,
    val status: String? = null,
    @SerializedName("created_at")
    val createdAt: String? = null,
)

data class AdminSupervisorsResponse(
    val ok: Boolean? = null,
    val count: Int? = null,
    val page: Int? = null,
    val limit: Int? = null,
    val total: Int? = null,
    @SerializedName("has_more")
    val hasMore: Boolean? = null,
    val data: List<AdminSupervisorDto>? = null,
)

data class AdminSupervisorResponse(
    val ok: Boolean? = null,
    val message: String? = null,
    val data: AdminSupervisorDto? = null,
)

data class AdminAuthorizeSupervisorRequest(
    val notes: String? = null,
)

data class AdminSupervisorReasonRequest(
    val reason: String,
)
