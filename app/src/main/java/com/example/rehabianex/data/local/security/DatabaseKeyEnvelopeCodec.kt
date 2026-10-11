package com.example.rehabianex.data.local.security

import java.nio.ByteBuffer

data class DatabaseKeyEnvelope(
    val initializationVector: ByteArray,
    val cipherText: ByteArray,
)

object DatabaseKeyEnvelopeCodec {
    private const val VERSION: Byte = 1
    private const val HEADER_SIZE = 2
    private const val MIN_IV_SIZE = 12
    private const val MAX_IV_SIZE = 32
    private const val MIN_CIPHER_TEXT_SIZE = 17

    fun encode(envelope: DatabaseKeyEnvelope): ByteArray {
        require(envelope.initializationVector.size in MIN_IV_SIZE..MAX_IV_SIZE)
        require(envelope.cipherText.size >= MIN_CIPHER_TEXT_SIZE)
        return ByteBuffer.allocate(
            HEADER_SIZE + envelope.initializationVector.size + envelope.cipherText.size,
        )
            .put(VERSION)
            .put(envelope.initializationVector.size.toByte())
            .put(envelope.initializationVector)
            .put(envelope.cipherText)
            .array()
    }

    fun decode(encoded: ByteArray): DatabaseKeyEnvelope {
        require(encoded.size >= HEADER_SIZE + MIN_IV_SIZE + MIN_CIPHER_TEXT_SIZE)
        val buffer = ByteBuffer.wrap(encoded)
        require(buffer.get() == VERSION)
        val ivSize = buffer.get().toInt() and 0xff
        require(ivSize in MIN_IV_SIZE..MAX_IV_SIZE)
        require(buffer.remaining() >= ivSize + MIN_CIPHER_TEXT_SIZE)
        val iv = ByteArray(ivSize).also(buffer::get)
        val cipherText = ByteArray(buffer.remaining()).also(buffer::get)
        return DatabaseKeyEnvelope(iv, cipherText)
    }
}
