package com.example.walletwise.utils

import android.content.Context
import com.example.walletwise.domain.model.RecurringTransaction
import com.example.walletwise.domain.model.Transaction
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.time.LocalDateTime
import java.time.ZoneId

data class RecurringExecutionResult(
    val transactionWasCreated: Boolean,
    val recurring: RecurringTransaction?
)

/**
 * Creates a due transaction and marks its recurring rule in one Firestore
 * transaction. This is what prevents a foreground check and an AlarmManager
 * callback from creating the same transaction twice.
 */
object RecurringTransactionExecutor {
    private val db: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    suspend fun executeIfDue(
        context: Context,
        userId: String,
        recurringId: String,
        now: LocalDateTime? = null
    ): RecurringExecutionResult {
        val recurringRef = db.collection("users").document(userId)
            .collection("recurring_transactions").document(recurringId)
        val transactionsRef = db.collection("users").document(userId).collection("transactions")

        return db.runTransaction { firestoreTransaction ->
            val snapshot = firestoreTransaction.get(recurringRef)
            if (!snapshot.exists()) {
                return@runTransaction RecurringExecutionResult(false, null)
            }
            val recurring = SettingsFirestoreMapper.recurringFromMap(
                documentId = recurringId,
                ownerUserId = userId,
                data = snapshot.data.orEmpty()
            )

            if (!recurring.isEnabled) {
                return@runTransaction RecurringExecutionResult(false, recurring)
            }

            // Resolve current time after the Firestore read. Capturing it before
            // network I/O could cross the configured minute and then schedule
            // the following period without ever creating this one.
            val executionNow = now ?: LocalDateTime.now()
            val occurrence = RecurringSchedule.latestDueOccurrence(recurring, executionNow)
                ?: return@runTransaction RecurringExecutionResult(false, recurring)
            val occurrenceKey = RecurringSchedule.occurrenceKey(occurrence)
            if (recurring.lastExecutedDate == occurrenceKey) {
                if (RecurringSchedule.hasReachedExecutionLimit(recurring, occurrence.index)) {
                    val completedRecurring = recurring.copy(isEnabled = false)
                    firestoreTransaction.set(
                        recurringRef,
                        SettingsFirestoreMapper.recurringToMap(completedRecurring)
                    )
                    return@runTransaction RecurringExecutionResult(false, completedRecurring)
                }
                return@runTransaction RecurringExecutionResult(false, recurring)
            }

            // A stable document id is an additional idempotency guard if an old
            // client writes the recurring rule outside this transaction.
            val transactionId = "${recurring.id}_${occurrenceKey}"
            val transactionRef = transactionsRef.document(transactionId)
            val alreadyCreated = firestoreTransaction.get(transactionRef).exists()
            val completedAllRuns = RecurringSchedule.hasReachedExecutionLimit(recurring, occurrence.index)
            val updatedRecurring = recurring.copy(
                lastExecutedDate = occurrenceKey,
                isEnabled = !completedAllRuns
            )

            if (!alreadyCreated) {
                val generatedTransaction = Transaction(
                    id = transactionId,
                    userId = userId,
                    amount = recurring.amount,
                    type = recurring.type,
                    category = recurring.category,
                    paymentMethod = recurring.paymentMethod,
                    note = "[Định kỳ] ${recurring.title}" +
                        if (recurring.note.isBlank()) "" else " - ${recurring.note}",
                    // Use the actual execution time so a recovered scheduled
                    // transaction is immediately visible in Home.
                    timestamp = executionNow.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                )
                firestoreTransaction.set(transactionRef, generatedTransaction)
            }
            firestoreTransaction.set(
                recurringRef,
                SettingsFirestoreMapper.recurringToMap(updatedRecurring)
            )
            RecurringExecutionResult(!alreadyCreated, updatedRecurring)
        }.await()
    }

    suspend fun executeIfDue(
        context: Context,
        userId: String,
        recurring: RecurringTransaction,
        now: LocalDateTime? = null
    ): RecurringExecutionResult = executeIfDue(context, userId, recurring.id, now)
}
