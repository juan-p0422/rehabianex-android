package com.example.rehabianex.core.session

data class UserSession(
    val uid: String,
    val email: String? = null,
    val role: String,
    val token: String? = null,
    val refreshToken: String? = null,
    val displayName: String? = null,
)
