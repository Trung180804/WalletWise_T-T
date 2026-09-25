package com.example.walletwise.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.walletwise.BuildConfig
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
        if (!AndroidSchedulingContract.isForceReconcileAction(intent.action)) return
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (BuildConfig.DEBUG) Log.i(LOG_TAG, "Recovery reconcile started action=${intent.action}")
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            runBroadcastWork(finish = pendingResult::finish) {
                restoreRecurringTransactions(context.applicationContext, userId)
                restoreReminders(context.applicationContext, userId)
                if (BuildConfig.DEBUG) Log.i(LOG_TAG, "Recovery reconcile completed action=${intent.action}")
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
                if (report.errors.isNotEmpty()) {
                    Log.e("RECURRING_BOOT", "Recurring recovery failures=${report.errors.size}")
                }
            }
            is RepositoryResult.Failure -> Log.e(
                "RECURRING_BOOT",
                "Recurring recovery repositoryCode=${result.error.code}"
            )
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
            is RepositoryResult.Failure -> Log.e(
                "REMINDER_BOOT",
                "Reminder recovery repositoryCode=${result.error.code}"
            )
        }
    }

    private companion object {
        const val LOG_TAG = "RUNTIME_RECOVERY"
    }
}
