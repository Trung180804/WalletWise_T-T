package com.example.walletwise.domain.repository

import com.example.walletwise.domain.model.Reminder
import com.example.walletwise.domain.result.RepositoryResult
import kotlinx.coroutines.flow.Flow

interface ReminderRepository {
    fun observeReminders(userId: String): Flow<RepositoryResult<List<Reminder>>>

    suspend fun getReminders(userId: String): RepositoryResult<List<Reminder>>

    suspend fun addReminder(userId: String, reminder: Reminder): RepositoryResult<Unit>

    suspend fun updateReminder(userId: String, reminder: Reminder): RepositoryResult<Unit>

    suspend fun deleteReminder(userId: String, reminderId: String): RepositoryResult<Unit>

    suspend fun setReminderEnabled(
        userId: String,
        reminderId: String,
        isEnabled: Boolean
    ): RepositoryResult<Unit>
}
