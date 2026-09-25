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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Adapts Swift callbacks to the existing shared repository and validated write use cases. */
class CallbackTransactionRepository(
    private val service: CallbackTransactionService?,
    private val writer: CallbackTransactionWriteService? = null
) : TransactionRepository {
    private var observationVersion = 0L
    private var owner: String? = null
    private var observed: List<Transaction> = emptyList()
    override fun observeTransactions(userId: String): Flow<RepositoryResult<List<Transaction>>> = callbackFlow {
        val version = ++observationVersion
        owner = userId
        observed = emptyList()
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
        fun map(documents: List<TransactionDocument>, legacy: Boolean = false): List<Transaction> = documents
            .mapNotNull { document ->
                if (document.documentId.isBlank()) null
                else runCatching { document.toTransaction(userId).copy(isLegacy = legacy) }.getOrNull()
            }.distinctBy(Transaction::id).sortedTransactionsNewestFirst()
        fun publish(result: RepositoryResult<List<Transaction>>) {
            if (!active || observationVersion != version) return
            if (result is RepositoryResult.Success) observed = result.value
            trySend(result)
        }
        val listener = service.observePrimary(userId, object : TransactionSnapshotObserver {
            override fun changed(documents: List<TransactionDocument>?, fromCache: Boolean, failure: TransactionReadFailure?) {
                if (!active) return
                if (failure != null || documents == null) {
                    revision++
                    publish((failure ?: TransactionReadFailure.UNKNOWN).result())
                    return
                }
                if (documents.isNotEmpty()) {
                    revision++
                    publish(RepositoryResult.Success(map(documents)))
                    return
                }
                // Cache emptiness is not proof that the authoritative primary collection is empty.
                if (fromCache) return
                legacyResult?.let { publish(it); return }
                if (legacyStarted) return
                legacyStarted = true
                val requestedRevision = ++revision
                legacyCancellation = service.readLegacy(userId, object : TransactionReadCompletion {
                    override fun complete(documents: List<TransactionDocument>?, failure: TransactionReadFailure?) {
                        if (!active) return
                        val result = if (failure != null || documents == null) {
                            (failure ?: TransactionReadFailure.UNKNOWN).result()
                        } else RepositoryResult.Success(map(documents, legacy = true))
                        legacyResult = result
                        if (revision == requestedRevision) publish(result)
                    }
                })
            }
        })
        awaitClose {
            active = false
            revision++
            listener.cancel()
            legacyCancellation?.cancel()
            if (observationVersion == version) { owner = null; observed = emptyList() }
        }
    }.buffer(Channel.CONFLATED)

    override suspend fun addTransaction(transaction: Transaction) = mutate(TransactionMutationKind.ADD, transaction)
    override suspend fun updateTransaction(transaction: Transaction): Result<Boolean> {
        if (observed.none { it.id == transaction.id && it.userId == transaction.userId && !it.isLegacy }) return rejected()
        return mutate(TransactionMutationKind.UPDATE, transaction)
    }
    override suspend fun deleteTransaction(transactionId: String): Result<Boolean> {
        val transaction = observed.firstOrNull { it.id == transactionId && !it.isLegacy } ?: return rejected()
        return mutate(TransactionMutationKind.REMOVE, transaction)
    }
    override suspend fun getTransactions(): Result<List<Transaction>> = readOnly()
    private fun <T> readOnly(): Result<T> = Result.failure(IllegalStateException("Chức năng chưa được hỗ trợ trên iOS"))
    private fun rejected(): Result<Boolean> = Result.failure(TransactionWriteException(TransactionReadFailure.PERMISSION_DENIED))
    private suspend fun mutate(kind: TransactionMutationKind, transaction: Transaction): Result<Boolean> {
        val writer = writer ?: return rejected()
        if (owner.isNullOrBlank() || owner != transaction.userId || transaction.id.isBlank() || '/' in transaction.id || transaction.isLegacy) return rejected()
        return suspendCancellableCoroutine { continuation ->
            val token = writer.mutate(kind, transaction, object : TransactionWriteCompletion {
                override fun complete(failure: TransactionReadFailure?) {
                    if (continuation.isActive) continuation.resume(if (failure == null) Result.success(true) else Result.failure(TransactionWriteException(failure)))
                }
            })
            continuation.invokeOnCancellation { token.cancel() }
        }
    }
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
