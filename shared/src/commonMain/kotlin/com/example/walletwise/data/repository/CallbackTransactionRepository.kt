package com.example.walletwise.data.repository

import com.example.walletwise.domain.model.Transaction
import com.example.walletwise.domain.repository.*
import com.example.walletwise.domain.result.*
import com.example.walletwise.domain.service.sortedTransactionsNewestFirst
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow

/** Adapts Swift observation into the existing shared repository/session contract. No write API on iOS. */
class CallbackTransactionRepository(private val service: CallbackTransactionService?) : TransactionRepository {
    override fun observeTransactions(userId: String): Flow<RepositoryResult<List<Transaction>>> = callbackFlow {
        if (service == null || userId.isBlank()) {
            trySend(TransactionReadFailure.NOT_AUTHENTICATED.result())
            awaitClose { }
            return@callbackFlow
        }
        var active = true
        var revision = 0L
        var legacyStarted = false
        var legacyResult: RepositoryResult<List<Transaction>>? = null
        var legacyCancellation: TransactionCancellation? = null
        fun map(documents: List<TransactionDocument>): List<Transaction> = documents
            .mapNotNull { document ->
                if (document.documentId.isBlank()) null
                else runCatching { document.toTransaction(userId) }.getOrNull()
            }.distinctBy(Transaction::id).sortedTransactionsNewestFirst()
        val listener = service.observePrimary(userId, object : TransactionSnapshotObserver {
            override fun changed(documents: List<TransactionDocument>?, fromCache: Boolean, failure: TransactionReadFailure?) {
                if (!active) return
                if (failure != null || documents == null) {
                    revision++
                    trySend((failure ?: TransactionReadFailure.UNKNOWN).result())
                    return
                }
                if (documents.isNotEmpty()) {
                    revision++
                    trySend(RepositoryResult.Success(map(documents)))
                    return
                }
                // Cache emptiness is not proof that the authoritative primary collection is empty.
                if (fromCache) return
                legacyResult?.let { trySend(it); return }
                if (legacyStarted) return
                legacyStarted = true
                val requestedRevision = ++revision
                legacyCancellation = service.readLegacy(userId, object : TransactionReadCompletion {
                    override fun complete(documents: List<TransactionDocument>?, failure: TransactionReadFailure?) {
                        if (!active) return
                        val result = if (failure != null || documents == null) {
                            (failure ?: TransactionReadFailure.UNKNOWN).result()
                        } else RepositoryResult.Success(map(documents))
                        legacyResult = result
                        if (revision == requestedRevision) trySend(result)
                    }
                })
            }
        })
        awaitClose {
            active = false
            revision++
            listener.cancel()
            legacyCancellation?.cancel()
        }
    }.buffer(Channel.CONFLATED)

    override suspend fun addTransaction(transaction: Transaction): Result<Boolean> = readOnly()
    override suspend fun updateTransaction(transaction: Transaction): Result<Boolean> = readOnly()
    override suspend fun deleteTransaction(transactionId: String): Result<Boolean> = readOnly()
    override suspend fun getTransactions(): Result<List<Transaction>> = readOnly()
    private fun <T> readOnly(): Result<T> = Result.failure(IllegalStateException("Chức năng chưa được hỗ trợ trên iOS"))
}

private fun TransactionReadFailure.result(): RepositoryResult.Failure = RepositoryResult.Failure(
    RepositoryError(
        when (this) {
            TransactionReadFailure.NOT_AUTHENTICATED -> RepositoryErrorCode.NOT_AUTHENTICATED
            TransactionReadFailure.NETWORK -> RepositoryErrorCode.NETWORK
            TransactionReadFailure.PERMISSION_DENIED -> RepositoryErrorCode.PERMISSION_DENIED
            TransactionReadFailure.UNKNOWN -> RepositoryErrorCode.UNKNOWN
        },
        "Không thể tải giao dịch. Vui lòng thử lại."
    )
)
