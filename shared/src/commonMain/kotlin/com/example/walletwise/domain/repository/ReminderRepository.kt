package com.example.walletwise.domain.repository

import com.example.walletwise.domain.model.Reminder

interface ReminderRepository {
    suspend fun getReminders(userId: String): Result<List<Reminder>>

    suspend fun saveReminder(reminder: Reminder): Result<Boolean>

    suspend fun deleteReminder(userId: String, reminderId: String): Result<Boolean>

    suspend fun setReminderEnabled(
        userId: String,
        reminderId: String,
        isEnabled: Boolean
    ): Result<Boolean>
}
