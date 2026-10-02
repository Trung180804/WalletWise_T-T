package com.example.walletwise.data.repository

import com.example.walletwise.data.mapper.FirestoreSchema
import com.example.walletwise.data.mapper.FirestoreWireMapper
import com.example.walletwise.domain.model.Reminder
import com.example.walletwise.domain.repository.ReminderRepository
import com.example.walletwise.domain.result.RepositoryResult
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class ReminderRepositoryImpl(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) : ReminderRepository {
    override fun observeReminders(userId: String): Flow<RepositoryResult<List<Reminder>>> = callbackFlow {
        val registration = remindersCollection(userId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(RepositoryResult.Failure(error.toRepositoryError("Không thể tải lời nhắc")))
                return@addSnapshotListener
            }
            val reminders = snapshot?.documents.orEmpty()
                .map { document ->
                    FirestoreWireMapper.reminderFromMap(
                        documentId = document.id,
                        ownerUserId = userId,
                        data = document.data.orEmpty()
                    )
                }
                .sortedBy(Reminder::id)
            trySend(RepositoryResult.Success(reminders))
        }
        awaitClose { registration.remove() }
    }

    override suspend fun getReminders(userId: String): RepositoryResult<List<Reminder>> = try {
        val reminders = remindersCollection(userId).get().await().documents
            .map { document ->
                FirestoreWireMapper.reminderFromMap(
                    documentId = document.id,
                    ownerUserId = userId,
                    data = document.data.orEmpty()
                )
            }
            .sortedBy(Reminder::id)
        RepositoryResult.Success(reminders)
    } catch (error: Throwable) {
        RepositoryResult.Failure(error.toRepositoryError("Không thể tải lời nhắc"))
    }

    override suspend fun addReminder(
        userId: String,
        reminder: Reminder
    ): RepositoryResult<Unit> = write(userId, reminder, "Không thể lưu lời nhắc")

    override suspend fun updateReminder(
        userId: String,
        reminder: Reminder
    ): RepositoryResult<Unit> = write(userId, reminder, "Không thể cập nhật lời nhắc")

    override suspend fun deleteReminder(
        userId: String,
        reminderId: String
    ): RepositoryResult<Unit> = try {
        reminderDocument(userId, reminderId).delete().await()
        RepositoryResult.Success(Unit)
    } catch (error: Throwable) {
        RepositoryResult.Failure(error.toRepositoryError("Không thể xóa lời nhắc"))
    }

    override suspend fun setReminderEnabled(
        userId: String,
        reminderId: String,
        isEnabled: Boolean
    ): RepositoryResult<Unit> = try {
        reminderDocument(userId, reminderId)
            .update(FirestoreWireMapper.enabledFields(isEnabled))
            .await()
        RepositoryResult.Success(Unit)
    } catch (error: Throwable) {
        RepositoryResult.Failure(error.toRepositoryError("Không thể đổi trạng thái lời nhắc"))
    }

    private suspend fun write(
        userId: String,
        reminder: Reminder,
        fallbackMessage: String
    ): RepositoryResult<Unit> = try {
        reminderDocument(userId, reminder.id)
            .set(FirestoreWireMapper.reminderToMap(reminder.copy(userId = userId)))
            .await()
        RepositoryResult.Success(Unit)
    } catch (error: Throwable) {
        RepositoryResult.Failure(error.toRepositoryError(fallbackMessage))
    }

    private fun remindersCollection(userId: String) = db
        .collection(FirestoreSchema.USERS)
        .document(userId)
        .collection(FirestoreSchema.REMINDERS)

    private fun reminderDocument(userId: String, reminderId: String) =
        remindersCollection(userId).document(reminderId)
}
