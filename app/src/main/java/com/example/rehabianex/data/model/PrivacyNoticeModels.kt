package com.example.rehabianex.data.model

import com.google.gson.annotations.SerializedName

data class PrivacyNoticeResponse(
    val ok: Boolean? = null,
    val notice: PrivacyNoticeDto? = null,
    val message: String? = null,
)

data class PrivacyNoticeDto(
    val title: String? = null,
    val version: String? = null,
    @SerializedName("published_on")
    val publishedOn: String? = null,
    val content: String? = null,
    @SerializedName("responsible_status")
    val responsibleStatus: String? = null,
    @SerializedName("contact_status")
    val contactStatus: String? = null,
    @SerializedName("sensitive_data_consent_separate")
    val sensitiveDataConsentSeparate: Boolean? = null,
)

data class PrivacyNoticeReadRequest(
    val version: String,
    @SerializedName("confirmed_read")
    val confirmedRead: Boolean,
)

data class PrivacyNoticeReadResponse(
    val ok: Boolean? = null,
    val message: String? = null,
    val confirmation: PrivacyNoticeReadDto? = null,
)
