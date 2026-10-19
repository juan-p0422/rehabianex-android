package com.example.rehabianex.data.model

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    val email: String,
    val password: String,
)

data class RegisterRequest(
    val email: String,
    val password: String,
    @SerializedName("full_name")
    val fullName: String,
    val role: String,
    val phone: String,
    val gender: String? = null,
    val age: Int? = null,
    @SerializedName("supervisor_type")
    val supervisorType: String? = null,
    @SerializedName("supervisor_type_other")
    val supervisorTypeOther: String? = null,
    @SerializedName("privacy_notice_accepted")
    val privacyNoticeAccepted: Boolean,
    @SerializedName("privacy_notice_version")
    val privacyNoticeVersion: String,
)

const val PRIVACY_NOTICE_VERSION = "2026-10-18"

data class RefreshRequest(
    @SerializedName("refresh_token")
    val refreshToken: String,
)

data class TokenData(
    @SerializedName("id_token")
    val idToken: String? = null,
    @SerializedName("refresh_token")
    val refreshToken: String? = null,
)

data class AuthData(
    val uid: String? = null,
    val role: String? = null,
    val email: String? = null,
    @SerializedName("display_name")
    val displayName: String? = null,
    @SerializedName("safe_display_name")
    val safeDisplayName: String? = null,
    @SerializedName("full_name")
    val fullName: String? = null,
    @SerializedName("is_anonymous")
    val isAnonymous: Boolean? = null,
    @SerializedName("privacy_mode")
    val privacyMode: Boolean? = null,
    @SerializedName("id_token")
    val idToken: String? = null,
    @SerializedName("refresh_token")
    val refreshToken: String? = null,
)

data class AuthPayload(
    val tokens: TokenData? = null,
    val auth: AuthData? = null,
    val user: AuthData? = null,
    val profile: SessionProfileDto? = null,
)

data class AuthResponse(
    val message: String? = null,
    val tokens: TokenData? = null,
    val auth: AuthData? = null,
    val user: AuthData? = null,
    val profile: SessionProfileDto? = null,
    val data: AuthPayload? = null,
) {
    fun resolvedTokens(): TokenData? {
        val authData = auth ?: user ?: data?.auth ?: data?.user
        val legacyTokens = tokens ?: data?.tokens
        val idToken = authData?.idToken ?: legacyTokens?.idToken
        val refreshToken = authData?.refreshToken ?: legacyTokens?.refreshToken
        return if (idToken != null || refreshToken != null) {
            TokenData(idToken = idToken, refreshToken = refreshToken)
        } else {
            null
        }
    }

    fun resolvedAuth(): AuthData? = auth ?: user ?: data?.auth ?: data?.user

    fun resolvedProfile(): SessionProfileDto? = profile ?: data?.profile
}

data class AuthSession(
    val idToken: String,
    val refreshToken: String?,
    val uid: String,
    val role: String,
    val email: String? = null,
    val displayName: String? = null,
)
