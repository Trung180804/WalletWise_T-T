package com.example.walletwise.domain.repository

import com.example.walletwise.domain.model.Transaction
import com.example.walletwise.domain.result.RepositoryResult
import kotlinx.coroutines.flow.Flow

/** Persists transactions whose platform-specific image processing is complete. */
interface TransactionRepository {
    /** Observes the established primary collection, using legacy data only while primary is empty. */
    fun observeTransactions(userId: String): Flow<RepositoryResult<List<Transaction>>>

    suspend fun addTransaction(transaction: Transaction): Result<Boolean>

    suspend fun getTransactions(): Result<List<Transaction>>

    suspend fun deleteTransaction(transactionId: String): Result<Boolean>

    suspend fun updateTransaction(transaction: Transaction): Result<Boolean>
}
