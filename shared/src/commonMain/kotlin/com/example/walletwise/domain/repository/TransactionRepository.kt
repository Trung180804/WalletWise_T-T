package com.example.walletwise.domain.repository

import com.example.walletwise.domain.model.Transaction

/** Persists transactions whose platform-specific image processing is complete. */
interface TransactionRepository {
    suspend fun addTransaction(transaction: Transaction): Result<Boolean>

    suspend fun getTransactions(): Result<List<Transaction>>

    suspend fun deleteTransaction(transactionId: String): Result<Boolean>

    suspend fun updateTransaction(transaction: Transaction): Result<Boolean>
}
