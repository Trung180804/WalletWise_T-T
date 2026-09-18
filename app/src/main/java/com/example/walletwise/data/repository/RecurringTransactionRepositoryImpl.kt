package com.example.walletwise.data.repository

import com.example.walletwise.data.mapper.FirestoreSchema
import com.example.walletwise.data.mapper.FirestoreWireMapper
import com.example.walletwise.domain.model.RecurringTransaction
import com.example.walletwise.domain.repository.RecurringExecutionResult
import com.example.walletwise.domain.repository.RecurringTransactionRepository
import com.example.walletwise.domain.result.RepositoryError
import com.example.walletwise.domain.result.RepositoryErrorCode
import com.example.walletwise.domain.result.RepositoryResult
import com.example.walletwise.domain.service.RecurringScheduleCalculator
import com.example.walletwise.domain.service.ReminderLocalDateTime
import com.example.walletwise.domain.service.RecurringDateTimeProvider
import com.example.walletwise.data.time.AndroidRecurringDateTimeProvider
import com.example.walletwise.domain.usecase.CreateRecurringTransactionWriteUseCase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

class RecurringTransactionRepositoryImpl(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val createTransactionWrite: CreateRecurringTransactionWriteUseCase =
        CreateRecurringTransactionWriteUseCase(),
    private val dateTimeProvider: RecurringDateTimeProvider = AndroidRecurringDateTimeProvider()
) : RecurringTransactionRepository {

    override fun observeRecurringTransactions(
        userId: String
    ): Flow<RepositoryResult<List<RecurringTransaction>>> = callbackFlow {
        if (!hasSession(userId)) {
            trySend(notAuthenticated())
            awaitClose {}
            return@callbackFlow
        }
        val registration = recurringCollection(userId).addSnapshotListener { snapshot, error ->
            if (!hasSession(userId)) {
                trySend(notAuthenticated())
                return@addSnapshotListener
            }
            if (error != null) {
                trySend(
                    RepositoryResult.Failure(
                        error.toRepositoryError("Không thể tải giao dịch định kỳ")
                    )
                )
                return@addSnapshotListener
            }
            val mapped = runCatching {
                snapshot?.documents.orEmpty().map { document ->
                    FirestoreWireMapper.recurringFromMap(
                        documentId = document.id,
                        ownerUserId = userId,
                        data = document.data.orEmpty()
                    )
                }.sortedBy(RecurringTransaction::id)
            }
            mapped.fold(
                onSuccess = { trySend(RepositoryResult.Success(it)) },
                onFailure = {
                    trySend(
                        RepositoryResult.Failure(
                            it.toRepositoryError("Dữ liệu giao dịch định kỳ không hợp lệ")
                        )
                    )
                }
            )
        }
        awaitClose { registration.remove() }
    }

    override suspend fun getRecurringTransactions(
        userId: String
    ): RepositoryResult<List<RecurringTransaction>> = read("Không thể tải giao dịch định kỳ") {
        requireSession(userId)
        recurringCollection(userId).get().await().documents.map { document ->
            FirestoreWireMapper.recurringFromMap(document.id, userId, document.data.orEmpty())
        }.sortedBy(RecurringTransaction::id)
    }

    override suspend fun addRecurringTransaction(
        userId: String,
        recurring: RecurringTransaction
    ): RepositoryResult<Unit> = write("Không thể lưu giao dịch định kỳ") {
        requireSession(userId)
        recurringDocument(userId, recurring.id)
            .set(FirestoreWireMapper.recurringToMap(recurring.copy(userId = userId)))
            .await()
    }

    override suspend fun updateRecurringTransaction(
        userId: String,
        recurring: RecurringTransaction
    ): RepositoryResult<Unit> = write("Không thể cập nhật giao dịch định kỳ") {
        requireSession(userId)
        recurringDocument(userId, recurring.id)
            .set(FirestoreWireMapper.recurringToMap(recurring.copy(userId = userId)))
            .await()
    }

    override suspend fun deleteRecurringTransaction(
        userId: String,
        recurringId: String
    ): RepositoryResult<Unit> = write("Không thể xóa giao dịch định kỳ") {
        requireSession(userId)
        recurringDocument(userId, recurringId).delete().await()
    }

    override suspend fun setRecurringTransactionEnabled(
        userId: String,
        recurringId: String,
        isEnabled: Boolean
    ): RepositoryResult<Unit> = write("Không thể đổi trạng thái giao dịch định kỳ") {
        requireSession(userId)
        val ref = recurringDocument(userId, recurringId)
        db.runTransaction { transaction ->
            requireSession(userId)
            val stored = transaction.get(ref)
            check(stored.exists())
            val fields = FirestoreWireMapper.enabledFields(isEnabled).toMutableMap()
            val rule = FirestoreWireMapper.recurringFromMap(stored.id, userId, stored.data.orEmpty())
            if (isEnabled && !rule.isEnabled) {
                // Explicit re-enabling skips missed periods, then resumes the original anchor.
                RecurringScheduleCalculator.latestDueOccurrence(rule, dateTimeProvider.currentLocalDateTime())?.let { due ->
                    val key = RecurringScheduleCalculator.occurrenceKey(due)
                    if (!RecurringScheduleCalculator.hasProcessed(rule.lastExecutedDate, key)) fields["lastExecutedDate"] = key
                }
            }
            transaction.update(ref, fields)
        }.await()
    }

    override suspend fun updateLastExecutedDate(
        userId: String,
        recurringId: String,
        lastExecutedDate: String
    ): RepositoryResult<Unit> = write("Không thể cập nhật lần thực thi giao dịch định kỳ") {
        requireSession(userId)
        recurringDocument(userId, recurringId)
            .update(mapOf("lastExecutedDate" to lastExecutedDate))
            .await()
    }

    override suspend fun executeIfDue(
        userId: String,
        recurringId: String,
        now: ReminderLocalDateTime,
        executedAtEpochMilliseconds: Long
    ): RepositoryResult<RecurringExecutionResult> = try {
        requireSession(userId)
        val recurringRef = recurringDocument(userId, recurringId)
        val transactionsRef = db.collection(FirestoreSchema.USERS)
            .document(userId)
            .collection(FirestoreSchema.TRANSACTIONS)
        val result = withTimeout(EXECUTION_TIMEOUT_MILLIS) { db.runTransaction { transaction ->
            requireSession(userId)
            val snapshot = transaction.get(recurringRef)
            if (!snapshot.exists()) return@runTransaction RecurringExecutionResult(false, null)
            val recurring = FirestoreWireMapper.recurringFromMap(
                documentId = snapshot.id,
                ownerUserId = userId,
                data = snapshot.data.orEmpty()
            )
            if (!recurring.isEnabled) return@runTransaction RecurringExecutionResult(false, recurring)

            val occurrence = RecurringScheduleCalculator.latestDueOccurrence(recurring, now)
                ?: return@runTransaction RecurringExecutionResult(false, recurring)
            val occurrenceKey = RecurringScheduleCalculator.occurrenceKey(occurrence)
            if (RecurringScheduleCalculator.hasProcessed(recurring.lastExecutedDate, occurrenceKey)) {
                return@runTransaction RecurringExecutionResult(false, recurring, occurrenceKey)
            }

            val transactionId = "${recurring.id}_$occurrenceKey"
            val transactionRef = transactionsRef.document(transactionId)
            val alreadyCreated = transaction.get(transactionRef).exists()
            val updatedRecurring = recurring.copy(
                lastExecutedDate = occurrenceKey
            )
            if (!alreadyCreated) {
                val generated = createTransactionWrite(
                    recurring = recurring,
                    userId = userId,
                    transactionId = transactionId,
                    executedAtEpochMilliseconds = executedAtEpochMilliseconds
                ).getOrThrow()
                transaction.set(transactionRef, FirestoreWireMapper.transactionToMap(generated))
            }
            // Execution owns only this marker. A concurrent user toggle remains authoritative.
            transaction.update(recurringRef, mapOf("lastExecutedDate" to occurrenceKey))
            RecurringExecutionResult(!alreadyCreated, updatedRecurring, occurrenceKey)
        }.await() }
        RepositoryResult.Success(result)
    } catch (error: Throwable) {
        error.failure("Không thể thực thi giao dịch định kỳ")
    }

    private fun recurringCollection(userId: String) = db
        .collection(FirestoreSchema.USERS)
        .document(userId)
        .collection(FirestoreSchema.RECURRING_TRANSACTIONS)

    private fun recurringDocument(userId: String, recurringId: String) =
        recurringCollection(userId).document(recurringId)

    private fun hasSession(userId: String): Boolean =
        userId.isNotBlank() && auth.currentUser?.uid == userId

    private fun requireSession(userId: String) {
        if (!hasSession(userId)) throw RecurringSessionChangedException()
    }

    private suspend fun write(
        fallbackMessage: String,
        block: suspend () -> Unit
    ): RepositoryResult<Unit> = try {
        block()
        RepositoryResult.Success(Unit)
    } catch (error: Throwable) {
        error.failure(fallbackMessage)
    }

    private suspend fun <T> read(
        fallbackMessage: String,
        block: suspend () -> T
    ): RepositoryResult<T> = try {
        RepositoryResult.Success(block())
    } catch (error: Throwable) {
        error.failure(fallbackMessage)
    }

    private fun notAuthenticated(): RepositoryResult.Failure = RepositoryResult.Failure(
        RepositoryError(RepositoryErrorCode.NOT_AUTHENTICATED, "Phiên đăng nhập đã thay đổi")
    )

    private fun Throwable.failure(fallbackMessage: String): RepositoryResult.Failure =
        if (this is RecurringSessionChangedException) notAuthenticated()
        else RepositoryResult.Failure(toRepositoryError(fallbackMessage))

    private companion object {
        const val EXECUTION_TIMEOUT_MILLIS = 8_000L
    }
}

private class RecurringSessionChangedException : IllegalStateException("Recurring session changed")
