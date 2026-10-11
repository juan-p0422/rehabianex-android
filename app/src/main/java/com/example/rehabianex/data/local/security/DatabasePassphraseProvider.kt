package com.example.rehabianex.data.local.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class DatabaseKeyUnavailableException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

/**
 * Genera una passphrase aleatoria para SQLCipher y guarda únicamente su sobre
 * AES-GCM dentro de noBackupFilesDir. La clave AES permanece no exportable en
 * Android Keystore; ninguna clave o passphrase se escribe en logs.
 */
class DatabasePassphraseProvider(context: Context) {
    private val keyEnvelopeFile = File(
        context.applicationContext.noBackupFilesDir,
        KEY_ENVELOPE_FILE,
    )

    @Synchronized
    fun getOrCreate(databaseAlreadyExists: Boolean): ByteArray {
        if (keyEnvelopeFile.exists()) {
            return decryptEnvelope(keyEnvelopeFile.readBytes())
        }
        if (databaseAlreadyExists) {
            throw DatabaseKeyUnavailableException(
                "La clave protegida de la base local no está disponible.",
            )
        }

        val passphrase = ByteArray(PASSPHRASE_SIZE_BYTES).also(SecureRandom()::nextBytes)
        try {
            persistEnvelope(encryptPassphrase(passphrase))
        } catch (error: Exception) {
            passphrase.fill(0)
            throw DatabaseKeyUnavailableException(
                "No fue posible proteger la clave de la base local.",
                error,
            )
        }
        return passphrase
    }

    private fun encryptPassphrase(passphrase: ByteArray): DatabaseKeyEnvelope {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateWrappingKey())
        cipher.updateAAD(ENVELOPE_AAD)
        return DatabaseKeyEnvelope(
            initializationVector = cipher.iv,
            cipherText = cipher.doFinal(passphrase),
        )
    }

    private fun decryptEnvelope(encoded: ByteArray): ByteArray {
        try {
            val envelope = DatabaseKeyEnvelopeCodec.decode(encoded)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateWrappingKey(),
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, envelope.initializationVector),
            )
            cipher.updateAAD(ENVELOPE_AAD)
            return cipher.doFinal(envelope.cipherText).also {
                require(it.size == PASSPHRASE_SIZE_BYTES)
            }
        } catch (error: AEADBadTagException) {
            throw DatabaseKeyUnavailableException(
                "La clave protegida de la base local no pudo autenticarse.",
                error,
            )
        } catch (error: Exception) {
            throw DatabaseKeyUnavailableException(
                "La clave protegida de la base local está dañada o no está disponible.",
                error,
            )
        }
    }

    private fun persistEnvelope(envelope: DatabaseKeyEnvelope) {
        val encoded = DatabaseKeyEnvelopeCodec.encode(envelope)
        val temporaryFile = File(keyEnvelopeFile.parentFile, "$KEY_ENVELOPE_FILE.tmp")
        temporaryFile.outputStream().use { output ->
            output.write(encoded)
            output.flush()
            output.fd.sync()
        }
        if (!temporaryFile.renameTo(keyEnvelopeFile)) {
            temporaryFile.delete()
            throw DatabaseKeyUnavailableException(
                "No fue posible guardar la clave protegida de la base local.",
            )
        }
    }

    private fun getOrCreateWrappingKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        val existing = (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)
            ?.secretKey
        if (existing != null) return existing

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
        const val KEY_ENVELOPE_FILE = "rehabianex_room_key_v1.enc"
        const val KEY_ALIAS = "rehabianex_room_wrap_key_v1"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
        const val PASSPHRASE_SIZE_BYTES = 32
        val ENVELOPE_AAD = "rehabianex-room-key-v1".toByteArray(Charsets.UTF_8)
    }
}
