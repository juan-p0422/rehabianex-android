package com.example.rehabianex.data.local

import android.content.Context
import androidx.core.content.edit
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

interface PrivacyNoticeReadStore {
    fun confirmedVersion(uid: String): String?

    fun saveConfirmedVersion(uid: String, version: String)

    fun clearOwner(uid: String)
}

class SharedPreferencesPrivacyNoticeReadStore(context: Context) : PrivacyNoticeReadStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override fun confirmedVersion(uid: String): String? {
        val key = ownerKey(uid) ?: return null
        return preferences.getString(key, null)
            ?.takeIf { it.isNotBlank() && it.length <= MAX_VERSION_LENGTH }
    }

    override fun saveConfirmedVersion(uid: String, version: String) {
        val key = ownerKey(uid) ?: return
        val safeVersion = version.takeIf {
            it.isNotBlank() && it.length <= MAX_VERSION_LENGTH
        } ?: return
        preferences.edit { putString(key, safeVersion) }
    }

    override fun clearOwner(uid: String) {
        val key = ownerKey(uid) ?: return
        preferences.edit { remove(key) }
    }

    private fun ownerKey(uid: String): String? {
        val safeUid = uid.takeIf { it.isNotBlank() } ?: return null
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(safeUid.toByteArray(StandardCharsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
        return "$OWNER_PREFIX$digest"
    }

    private companion object {
        const val PREFERENCES_NAME = "privacy_notice_read_evidence"
        const val OWNER_PREFIX = "owner_"
        const val MAX_VERSION_LENGTH = 128
    }
}
