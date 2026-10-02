package com.example.walletwise.data.mapper

import com.google.firebase.Timestamp
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AndroidFirestoreTransactionMapperTest {
    @Test
    fun firebaseTimestampIsNormalizedWithoutChangingOtherFields() {
        val timestamp = Timestamp(Date(1_725_840_000_456L))
        val source = mapOf<String, Any?>(
            "timestamp" to timestamp,
            "amount" to 125_000L,
            "note" to "legacy"
        )

        val normalized = AndroidFirestoreTransactionMapper.normalizeTimestamp(source)
        val transaction = AndroidFirestoreTransactionMapper.fromMap("tx-1", "user-1", source)

        assertEquals(
            FirestoreTimestampValue(timestamp.seconds, timestamp.nanoseconds),
            normalized["timestamp"]
        )
        assertEquals(1_725_840_000_456L, transaction.timestamp)
        assertEquals(125_000.0, transaction.amount, 0.0)
        assertEquals("legacy", transaction.note)
    }

    @Test
    fun numericTimestampMapIsReusedAndStillSupported() {
        val source = mapOf<String, Any?>("timestamp" to 123L)

        assertSame(source, AndroidFirestoreTransactionMapper.normalizeTimestamp(source))
        assertEquals(
            123L,
            AndroidFirestoreTransactionMapper.fromMap("tx-1", "user-1", source).timestamp
        )
    }
}
