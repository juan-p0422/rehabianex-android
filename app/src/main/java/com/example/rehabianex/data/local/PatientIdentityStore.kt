package com.example.rehabianex.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

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

class PatientIdentityProtectionException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

class PatientIdentityStore(context: Context) : PatientIdentityStorage {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override fun load(patientUid: String): PatientIdentity {
        val prefix = patientUid.protectedPreferencePrefix()
        val legacyPrefix = patientUid.legacyPreferencePrefix()
        return PatientIdentity(
            originalName = readProtected(
                protectedKey = "$prefix$KEY_ORIGINAL_NAME",
                legacyKey = "$legacyPrefix$KEY_ORIGINAL_NAME",
            ),
            nickname = readProtected(
                protectedKey = "$prefix$KEY_NICKNAME",
                legacyKey = "$legacyPrefix$KEY_NICKNAME",
            ),
        )
    }

    override fun ensureOriginalName(
        patientUid: String,
        originalName: String,
    ): String {
        val current = load(patientUid).originalName?.takeIf { it.isNotBlank() }
        if (current != null) return current

        putProtected(
            key = "${patientUid.protectedPreferencePrefix()}$KEY_ORIGINAL_NAME",
            value = originalName,
        )
        return originalName
    }

    override fun saveNickname(
        patientUid: String,
        nickname: String,
    ) {
        putProtected(
            key = "${patientUid.protectedPreferencePrefix()}$KEY_NICKNAME",
            value = nickname,
        )
    }

    override fun clearNickname(patientUid: String) {
        preferences.edit(commit = true) {
            remove("${patientUid.protectedPreferencePrefix()}$KEY_NICKNAME")
            remove("${patientUid.legacyPreferencePrefix()}$KEY_NICKNAME")
        }
    }

    override fun clearOriginalName(patientUid: String) {
        preferences.edit(commit = true) {
            remove("${patientUid.protectedPreferencePrefix()}$KEY_ORIGINAL_NAME")
            remove("${patientUid.legacyPreferencePrefix()}$KEY_ORIGINAL_NAME")
        }
    }

    override fun clearOwner(patientUid: String) {
        val prefix = patientUid.protectedPreferencePrefix()
        val legacyPrefix = patientUid.legacyPreferencePrefix()
        preferences.edit(commit = true) {
            remove("$prefix$KEY_ORIGINAL_NAME")
            remove("$prefix$KEY_NICKNAME")
            remove("$legacyPrefix$KEY_ORIGINAL_NAME")
            remove("$legacyPrefix$KEY_NICKNAME")
        }
    }

    private fun readProtected(protectedKey: String, legacyKey: String): String? {
        val protectedValue = preferences.getString(protectedKey, null)
        if (protectedValue != null) return decrypt(protectedKey, protectedValue)

        val legacyValue = preferences.getString(legacyKey, null) ?: return null
        val encryptedValue = encrypt(protectedKey, legacyValue)
        val migrated = preferences.edit()
            .putString(protectedKey, encryptedValue)
            .remove(legacyKey)
            .commit()
        if (!migrated) {
            throw PatientIdentityProtectionException(
                "No fue posible migrar la identidad local al almacenamiento protegido.",
            )
        }
        return legacyValue
    }

    private fun putProtected(key: String, value: String) {
        val saved = preferences.edit()
            .putString(key, encrypt(key, value))
            .commit()
        if (!saved) {
            throw PatientIdentityProtectionException(
                "No fue posible guardar la identidad local de forma protegida.",
            )
        }
    }

    private fun encrypt(preferenceKey: String, plainText: String): String {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            cipher.updateAAD(preferenceKey.toByteArray(StandardCharsets.UTF_8))
            val cipherText = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
            listOf(
                ENCRYPTED_VALUE_VERSION,
                Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
                Base64.encodeToString(cipherText, Base64.NO_WRAP),
            ).joinToString(SEPARATOR)
        } catch (error: Exception) {
            throw PatientIdentityProtectionException(
                "No fue posible cifrar la identidad local.",
                error,
            )
        }
    }

    private fun decrypt(preferenceKey: String, encoded: String): String {
        try {
            val parts = encoded.split(SEPARATOR, limit = 3)
            if (parts.size != 3 || parts[0] != ENCRYPTED_VALUE_VERSION) {
                throw PatientIdentityProtectionException(
                    "La identidad local protegida tiene un formato desconocido.",
                )
            }
            val secretKey = existingSecretKey() ?: throw PatientIdentityProtectionException(
                "La clave de la identidad local ya no está disponible.",
            )
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey,
                GCMParameterSpec(
                    GCM_TAG_LENGTH_BITS,
                    Base64.decode(parts[1], Base64.NO_WRAP),
                ),
            )
            cipher.updateAAD(preferenceKey.toByteArray(StandardCharsets.UTF_8))
            return String(
                cipher.doFinal(Base64.decode(parts[2], Base64.NO_WRAP)),
                StandardCharsets.UTF_8,
            )
        } catch (error: PatientIdentityProtectionException) {
            throw error
        } catch (error: AEADBadTagException) {
            throw PatientIdentityProtectionException(
                "La identidad local protegida no pudo autenticarse.",
                error,
            )
        } catch (error: Exception) {
            throw PatientIdentityProtectionException(
                "La identidad local protegida está dañada o no está disponible.",
                error,
            )
        }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        existingSecretKey()?.let { return it }
        return KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEY_STORE,
        ).apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }

    private fun existingSecretKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        return (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
    }

    private fun String.protectedPreferencePrefix(): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(toByteArray(StandardCharsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
        return "patient_${digest}_"
    }

    private fun String.legacyPreferencePrefix(): String {
        return filter { it.isLetterOrDigit() || it == '_' || it == '-' }
            .ifBlank { "patient" }
            .let { "patient_${it}_" }
    }

    private companion object {
        const val PREFERENCES_NAME = "patient_identity"
        const val KEY_ORIGINAL_NAME = "original_name"
        const val KEY_NICKNAME = "nickname"
        const val KEY_ALIAS = "rehabianex_patient_identity_key_v1"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
        const val ENCRYPTED_VALUE_VERSION = "v1"
        const val SEPARATOR = ":"
    }
}
