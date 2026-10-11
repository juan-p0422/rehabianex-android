package com.example.rehabianex.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import com.example.rehabianex.core.session.SessionManager
import com.example.rehabianex.core.session.UserSession
import com.example.rehabianex.data.model.AuthSession
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureSessionManager(context: Context) : SessionManager {
    private val preferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    @Synchronized
    fun saveSession(session: AuthSession) {
        saveSession(
            UserSession(
                uid = session.uid,
                email = session.email,
                role = session.role,
                token = session.idToken,
                refreshToken = session.refreshToken,
                displayName = session.displayName,
            ),
        )
    }

    @Synchronized
    override fun saveSession(session: UserSession) {
        val encryptedIdToken = session.token?.let { encrypt(KEY_ID_TOKEN, it) }
        val encryptedRefreshToken = session.refreshToken?.let {
            encrypt(KEY_REFRESH_TOKEN, it)
        }
        val encryptedUid = encrypt(KEY_UID, session.uid)
        val encryptedEmail = session.email?.let { encrypt(KEY_EMAIL, it) }
        val encryptedRole = encrypt(KEY_ROLE, session.role)
        val encryptedDisplayName = session.displayName?.let {
            encrypt(KEY_DISPLAY_NAME, it)
        }

        preferences.edit {
            putString(KEY_ID_TOKEN, encryptedIdToken)
            putString(KEY_REFRESH_TOKEN, encryptedRefreshToken)
            putString(KEY_UID, encryptedUid)
            putString(KEY_EMAIL, encryptedEmail)
            putString(KEY_ROLE, encryptedRole)
            putString(KEY_DISPLAY_NAME, encryptedDisplayName)
        }
    }

    @Synchronized
    override fun getIdToken(): String? = decryptStoredValue(KEY_ID_TOKEN)

    @Synchronized
    override fun getRefreshToken(): String? = decryptStoredValue(KEY_REFRESH_TOKEN)

    @Synchronized
    override fun getUid(): String? = decryptStoredValue(KEY_UID)

    @Synchronized
    override fun getEmail(): String? = decryptStoredValue(KEY_EMAIL)

    @Synchronized
    override fun getRole(): String? = decryptStoredValue(KEY_ROLE)

    @Synchronized
    override fun getDisplayName(): String? = decryptStoredValue(KEY_DISPLAY_NAME)

    @Synchronized
    override fun saveFcmToken(token: String) {
        preferences.edit { putString(KEY_FCM_TOKEN, encrypt(KEY_FCM_TOKEN, token)) }
    }

    @Synchronized
    override fun getFcmToken(): String? = decryptStoredValue(KEY_FCM_TOKEN)

    @Synchronized
    override fun clearFcmToken() {
        preferences.edit { remove(KEY_FCM_TOKEN) }
    }

    @Synchronized
    override fun clear() {
        preferences.edit { clear() }
    }

    private fun encrypt(preferenceKey: String, plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        cipher.updateAAD(preferenceKey.toByteArray(StandardCharsets.UTF_8))
        val cipherText = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))

        return listOf(
            Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
            Base64.encodeToString(cipherText, Base64.NO_WRAP),
        ).joinToString(SEPARATOR)
    }

    private fun decryptStoredValue(preferenceKey: String): String? {
        val encryptedValue = preferences.getString(preferenceKey, null) ?: return null

        return runCatching {
            val parts = encryptedValue.split(SEPARATOR, limit = 2)
            require(parts.size == 2)

            val initializationVector = Base64.decode(parts[0], Base64.NO_WRAP)
            val cipherText = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)

            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateSecretKey(),
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, initializationVector),
            )
            cipher.updateAAD(preferenceKey.toByteArray(StandardCharsets.UTF_8))
            String(cipher.doFinal(cipherText), StandardCharsets.UTF_8)
        }.getOrElse {
            preferences.edit { remove(preferenceKey) }
            null
        }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        val existingKey = (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)
            ?.secretKey

        if (existingKey != null) {
            return existingKey
        }

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

    private companion object {
        const val PREFERENCES_NAME = "secure_session"
        const val KEY_ALIAS = "rehabianex_session_key"
        const val KEY_ID_TOKEN = "id_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_UID = "uid"
        const val KEY_EMAIL = "email"
        const val KEY_ROLE = "role"
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_FCM_TOKEN = "fcm_token"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
        const val SEPARATOR = ":"
    }
}
