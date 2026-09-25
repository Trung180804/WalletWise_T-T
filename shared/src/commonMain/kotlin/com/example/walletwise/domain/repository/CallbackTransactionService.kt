package com.example.walletwise.domain.repository

import com.example.walletwise.data.mapper.FirestoreTimestampValue
import com.example.walletwise.data.mapper.FirestoreWireMapper
import com.example.walletwise.domain.model.Transaction

/** Primitive SDK boundary. Document identity and owner come from the query, never payload fields. */
data class TransactionDocument(
    val documentId: String,
    val type: String,
    val amount: Double,
    val category: String,
    val paymentMethod: String,
    val note: String,
    val timestampMilliseconds: Long,
    val legacyTimestamp: FirestoreTimestampValue?,
    val imageUrl: String,
    val categoryId: String = ""
) {
    fun toTransaction(ownerUserId: String): Transaction = FirestoreWireMapper.transactionFromMap(
        documentId, ownerUserId, mapOf(
            "type" to type, "amount" to amount, "category" to category,
            "paymentMethod" to paymentMethod, "note" to note,
            "timestamp" to (legacyTimestamp ?: timestampMilliseconds), "imageUrl" to imageUrl,
            "categoryId" to categoryId
        )
    )
}

enum class TransactionReadFailure { NOT_AUTHENTICATED, NETWORK, PERMISSION_DENIED, UNKNOWN }

interface TransactionCancellation { fun cancel() }

interface TransactionSnapshotObserver {
    /** Null documents represent an error, not an empty collection. */
    fun changed(documents: List<TransactionDocument>?, fromCache: Boolean, failure: TransactionReadFailure?)
}

interface TransactionReadCompletion {
    fun complete(documents: List<TransactionDocument>?, failure: TransactionReadFailure?)
}

/** UI-thread callbacks; one primary listener per subscription, server-only legacy one-shot reads. */
interface CallbackTransactionService {
    fun observePrimary(userId: String, observer: TransactionSnapshotObserver): TransactionCancellation
    fun readLegacy(userId: String, completion: TransactionReadCompletion): TransactionCancellation
}
