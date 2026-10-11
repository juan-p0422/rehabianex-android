package com.example.rehabianex.data.local

import android.content.Context
import androidx.core.content.edit

data class PatientIdentity(
    val originalName: String?,
    val nickname: String?,
)

interface PatientIdentityStorage {
    fun load(patientUid: String): PatientIdentity
    fun ensureOriginalName(patientUid: String, originalName: String): String
    fun saveNickname(patientUid: String, nickname: String)
    fun clearNickname(patientUid: String)
    fun clearOriginalName(patientUid: String)
    fun clearOwner(patientUid: String)
}

class PatientIdentityStore(context: Context) : PatientIdentityStorage {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override fun load(patientUid: String): PatientIdentity {
        val prefix = patientUid.preferencePrefix()
        return PatientIdentity(
            originalName = preferences.getString("$prefix$KEY_ORIGINAL_NAME", null),
            nickname = preferences.getString("$prefix$KEY_NICKNAME", null),
        )
    }

    override fun ensureOriginalName(
        patientUid: String,
        originalName: String,
    ): String {
        val prefix = patientUid.preferencePrefix()
        val current = preferences.getString("$prefix$KEY_ORIGINAL_NAME", null)
            ?.takeIf { it.isNotBlank() }
        if (current != null) return current

        preferences.edit {
            putString("$prefix$KEY_ORIGINAL_NAME", originalName)
        }
        return originalName
    }

    override fun saveNickname(
        patientUid: String,
        nickname: String,
    ) {
        preferences.edit {
            putString("${patientUid.preferencePrefix()}$KEY_NICKNAME", nickname)
        }
    }

    override fun clearNickname(patientUid: String) {
        preferences.edit {
            remove("${patientUid.preferencePrefix()}$KEY_NICKNAME")
        }
    }

    override fun clearOriginalName(patientUid: String) {
        preferences.edit {
            remove("${patientUid.preferencePrefix()}$KEY_ORIGINAL_NAME")
        }
    }

    override fun clearOwner(patientUid: String) {
        val prefix = patientUid.preferencePrefix()
        preferences.edit {
            remove("$prefix$KEY_ORIGINAL_NAME")
            remove("$prefix$KEY_NICKNAME")
        }
    }

    private fun String.preferencePrefix(): String {
        return filter { it.isLetterOrDigit() || it == '_' || it == '-' }
            .ifBlank { "patient" }
            .let { "patient_${it}_" }
    }

    private companion object {
        const val PREFERENCES_NAME = "patient_identity"
        const val KEY_ORIGINAL_NAME = "original_name"
        const val KEY_NICKNAME = "nickname"
    }
}
