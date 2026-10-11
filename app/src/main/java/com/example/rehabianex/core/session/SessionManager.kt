package com.example.rehabianex.core.session

interface SessionManager {
    fun saveSession(session: UserSession)

    fun getIdToken(): String?

    fun getRefreshToken(): String?

    fun getUid(): String?

    fun getEmail(): String?

    fun getRole(): String?

    fun getDisplayName(): String?

    fun saveFcmToken(token: String) = Unit

    fun getFcmToken(): String? = null

    fun clearFcmToken() = Unit

    fun clear()

    fun getCurrentSession(): UserSession? {
        val uid = getUid()?.takeIf { it.isNotBlank() } ?: return null
        val role = getRole()?.takeIf { it.isNotBlank() } ?: return null

        return UserSession(
            uid = uid,
            email = getEmail()?.takeIf { it.isNotBlank() },
            role = role,
            token = getIdToken()?.takeIf { it.isNotBlank() },
            refreshToken = getRefreshToken()?.takeIf { it.isNotBlank() },
            displayName = getDisplayName()?.takeIf { it.isNotBlank() },
        )
    }

    fun hasActiveSession(): Boolean {
        val session = getCurrentSession() ?: return false
        return !session.token.isNullOrBlank()
    }
}
