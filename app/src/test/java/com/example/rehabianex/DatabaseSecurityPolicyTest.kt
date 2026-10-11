package com.example.rehabianex

import com.example.rehabianex.data.local.db.LegacyDatabaseAction
import com.example.rehabianex.data.local.db.legacyDatabaseAction
import com.example.rehabianex.data.local.security.DatabaseKeyEnvelope
import com.example.rehabianex.data.local.security.DatabaseKeyEnvelopeCodec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DatabaseSecurityPolicyTest {
    @Test
    fun keyEnvelopeRoundTripPreservesIvAndCipherText() {
        val envelope = DatabaseKeyEnvelope(
            initializationVector = ByteArray(12) { it.toByte() },
            cipherText = ByteArray(48) { (it + 20).toByte() },
        )

        val restored = DatabaseKeyEnvelopeCodec.decode(
            DatabaseKeyEnvelopeCodec.encode(envelope),
        )

        assertArrayEquals(envelope.initializationVector, restored.initializationVector)
        assertArrayEquals(envelope.cipherText, restored.cipherText)
    }

    @Test
    fun malformedKeyEnvelopeIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            DatabaseKeyEnvelopeCodec.decode(byteArrayOf(1, 12, 1, 2, 3))
        }
    }

    @Test
    fun plaintextDatabaseIsDeletedOnlyInDebugAndRejectedInRelease() {
        assertEquals(
            LegacyDatabaseAction.DeletePlaintextDebugDatabase,
            legacyDatabaseAction(true, true, true),
        )
        assertEquals(
            LegacyDatabaseAction.RejectPlaintextReleaseDatabase,
            legacyDatabaseAction(true, true, false),
        )
        assertEquals(
            LegacyDatabaseAction.Continue,
            legacyDatabaseAction(true, false, false),
        )
    }
}
