package com.example.walletwise.data.mapper

import com.example.walletwise.domain.model.Transaction
import com.google.firebase.Timestamp

internal object AndroidFirestoreTransactionMapper {
    fun fromMap(
        documentId: String,
        ownerUserId: String,
        data: Map<String, Any?>
    ): Transaction = FirestoreWireMapper.transactionFromMap(
        documentId = documentId,
        ownerUserId = ownerUserId,
        data = normalizeTimestamp(data)
    )

    internal fun normalizeTimestamp(data: Map<String, Any?>): Map<String, Any?> {
        val timestamp = data["timestamp"] as? Timestamp ?: return data
        return data + (
            "timestamp" to FirestoreTimestampValue(
                seconds = timestamp.seconds,
                nanoseconds = timestamp.nanoseconds
            )
        )
    }
}
