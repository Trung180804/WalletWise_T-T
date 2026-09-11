package com.example.walletwise.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.walletwise.data.repository.ReminderRepositoryImpl
import com.example.walletwise.data.repository.RecurringTransactionRepositoryImpl
import com.example.walletwise.data.time.AndroidReminderDateTimeProvider
import com.example.walletwise.data.time.AndroidRecurringDateTimeProvider
import com.example.walletwise.domain.result.RepositoryResult
import com.example.walletwise.domain.service.ReconcileReminderSchedulingUseCase
import com.example.walletwise.domain.service.RecurringAutomationCoordinator
import com.example.walletwise.domain.usecase.ExecuteRecurringIfDueUseCase
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Restores reminders and recurring transactions after time/device changes. */
class RecurringBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in SUPPORTED_ACTIONS) return
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                restoreRecurringTransactions(context.applicationContext, userId)
                restoreReminders(context.applicationContext, userId)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun restoreRecurringTransactions(context: Context, userId: String) {
        val repository = RecurringTransactionRepositoryImpl()
        val dateTimeProvider = AndroidRecurringDateTimeProvider()
        val platform = AndroidRecurringPlatform(dateTimeProvider, context)
        val coordinator = RecurringAutomationCoordinator(
            executeIfDue = ExecuteRecurringIfDueUseCase(repository, dateTimeProvider),
            scheduler = platform,
            notifier = platform,
            dateTimeProvider = dateTimeProvider
        )
        when (val result = repository.getRecurringTransactions(userId)) {
            is RepositoryResult.Success -> {
                val report = coordinator.reconcile(userId, result.value, forceSchedule = true)
                report.errors.forEach { android.util.Log.e("RECURRING_BOOT", it) }
            }
            is RepositoryResult.Failure -> android.util.Log.e("RECURRING_BOOT", result.error.message)
        }
    }

    private suspend fun restoreReminders(context: Context, userId: String) {
        val platform = AndroidReminderPlatform(context)
        val reconcile = ReconcileReminderSchedulingUseCase(
            platform,
            platform,
            AndroidReminderDateTimeProvider()
        )
        when (val result = ReminderRepositoryImpl().getReminders(userId)) {
            is RepositoryResult.Success -> reconcile.reconcile(userId, result.value, force = true)
            is RepositoryResult.Failure -> android.util.Log.e(
                "REMINDER_BOOT",
                "Unable to restore reminders: ${result.error.message}"
            )
        }
    }

    private companion object {
        const val ACTION_EXACT_ALARM_PERMISSION_CHANGED =
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"

        val SUPPORTED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            ACTION_EXACT_ALARM_PERMISSION_CHANGED
        )
    }
}
