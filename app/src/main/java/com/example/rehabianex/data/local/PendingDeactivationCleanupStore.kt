package com.example.rehabianex.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class PendingDeactivationCleanup(val uid: String, val role: String)

class PendingDeactivationCleanupStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    @Synchronized
    fun save(uid: String, role: String) {
        require(uid.isNotBlank() && uid.length <= 128 && uid.none(Char::isISOControl))
        require(role in VALID_ROLES)
        preferences.edit(commit = true) {
            putString(KEY_PENDING, encrypt("$uid$SEPARATOR$role"))
        }
    }

    @Synchronized
    fun load(): PendingDeactivationCleanup? {
        val stored = preferences.getString(KEY_PENDING, null) ?: return null
        val plain = decrypt(stored) ?: return null
        val separatorIndex = plain.lastIndexOf(SEPARATOR)
        if (separatorIndex <= 0) return null
        val uid = plain.substring(0, separatorIndex)
        val role = plain.substring(separatorIndex + 1)
        return PendingDeactivationCleanup(uid, role)
            .takeIf { uid.length <= 128 && uid.none(Char::isISOControl) && role in VALID_ROLES }
    }

    @Synchronized
    fun clear() {
        preferences.edit(commit = true) { remove(KEY_PENDING) }
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        cipher.updateAAD(AAD)
        val cipherText = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ENVELOPE_SEPARATOR +
            Base64.encodeToString(cipherText, Base64.NO_WRAP)
    }

    private fun decrypt(encrypted: String): String? = runCatching {
        val parts = encrypted.split(ENVELOPE_SEPARATOR, limit = 2)
        require(parts.size == 2)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateSecretKey(),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, Base64.decode(parts[0], Base64.NO_WRAP)),
        )
        cipher.updateAAD(AAD)
        String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), StandardCharsets.UTF_8)
    }.getOrNull()

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        val existing = (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
        if (existing != null) return existing
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE).apply {
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

    private companion object {
        const val PREFERENCES_NAME = "pending_deactivation_cleanup"
        const val KEY_PENDING = "pending"
        const val KEY_ALIAS = "rehabianex_deactivation_cleanup_key_v1"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
        const val SEPARATOR = "\u001f"
        const val ENVELOPE_SEPARATOR = ":"
        val AAD = "rehabianex-deactivation-cleanup-v1".toByteArray(StandardCharsets.UTF_8)
        val VALID_ROLES = setOf("patient", "supervisor", "admin")
    }
}
