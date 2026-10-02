package com.example.walletwise.domain.repository

import com.example.walletwise.domain.model.RecurringTransaction
import com.example.walletwise.domain.result.RepositoryResult
import com.example.walletwise.domain.service.ReminderLocalDateTime
import kotlinx.coroutines.flow.Flow

data class RecurringExecutionResult(
    val transactionWasCreated: Boolean,
    val recurring: RecurringTransaction?,
    val occurrenceKey: String? = null
)

interface RecurringTransactionRepository {
    fun observeRecurringTransactions(userId: String): Flow<RepositoryResult<List<RecurringTransaction>>>

    suspend fun getRecurringTransactions(userId: String): RepositoryResult<List<RecurringTransaction>>

    suspend fun addRecurringTransaction(
        userId: String,
        recurring: RecurringTransaction
    ): RepositoryResult<Unit>

    suspend fun updateRecurringTransaction(
        userId: String,
        recurring: RecurringTransaction
    ): RepositoryResult<Unit>

    suspend fun deleteRecurringTransaction(
        userId: String,
        recurringId: String
    ): RepositoryResult<Unit>

    suspend fun setRecurringTransactionEnabled(
        userId: String,
        recurringId: String,
        isEnabled: Boolean
    ): RepositoryResult<Unit>

    suspend fun updateLastExecutedDate(
        userId: String,
        recurringId: String,
        lastExecutedDate: String
    ): RepositoryResult<Unit>

    /** Atomically writes the generated transaction and the existing execution marker. */
    suspend fun executeIfDue(
        userId: String,
        recurringId: String,
        now: ReminderLocalDateTime,
        executedAtEpochMilliseconds: Long
    ): RepositoryResult<RecurringExecutionResult>
}
