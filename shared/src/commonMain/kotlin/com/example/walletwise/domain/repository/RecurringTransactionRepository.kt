package com.example.walletwise.domain.repository

import com.example.walletwise.domain.model.RecurringTransaction

interface RecurringTransactionRepository {
    suspend fun getRecurringTransactions(userId: String): Result<List<RecurringTransaction>>

    suspend fun saveRecurringTransaction(recurring: RecurringTransaction): Result<Boolean>

    suspend fun deleteRecurringTransaction(
        userId: String,
        recurringId: String
    ): Result<Boolean>

    suspend fun setRecurringTransactionEnabled(
        userId: String,
        recurringId: String,
        isEnabled: Boolean
    ): Result<Boolean>
}
