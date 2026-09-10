package com.example.walletwise.data.repository

import com.example.walletwise.data.image.AndroidTransactionWriter
import com.example.walletwise.data.image.ContentResolverImageReader
import com.example.walletwise.data.image.ImgBbImageUploader
import com.example.walletwise.data.mapper.FirestoreSchema
import com.example.walletwise.data.mapper.FirestoreWireMapper
import com.example.walletwise.domain.model.Transaction
import com.example.walletwise.domain.repository.ImageUploader
import com.example.walletwise.domain.repository.TransactionRepository
import com.example.walletwise.domain.service.TransactionFallbackPolicy
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class TransactionRepositoryImpl : TransactionRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val IMGBB_API_KEY = "eba44471019a00974a5ce9624bb366cc"

    private val imageUploader: ImageUploader by lazy {
        ImgBbImageUploader(IMGBB_API_KEY)
    }

    private val androidWriter by lazy {
        AndroidTransactionWriter(
            repository = this,
            imageReader = ContentResolverImageReader(),
            imageUploader = imageUploader
        )
    }

    internal fun androidWriter(): AndroidTransactionWriter = androidWriter

    internal fun imageUploader(): ImageUploader = imageUploader

    override suspend fun addTransaction(transaction: Transaction): Result<Boolean> {
        return try {
            val userId = auth.currentUser?.uid ?: throw Exception("Chưa đăng nhập")
            val docRef = db.collection(FirestoreSchema.USERS)
                .document(userId)
                .collection(FirestoreSchema.TRANSACTIONS)
                .document()

            val finalTransaction = transaction.copy(
                id = docRef.id,
                userId = userId
            )

            docRef.set(FirestoreWireMapper.transactionToMap(finalTransaction)).await()
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getTransactions(): Result<List<Transaction>> {
        return try {
            val userId = auth.currentUser?.uid ?: throw Exception("Chưa đăng nhập")
            val userTxCollection = db.collection(FirestoreSchema.USERS)
                .document(userId)
                .collection(FirestoreSchema.TRANSACTIONS)
            val snapshot = userTxCollection.get().await()
            var transactions = snapshot.documents.map { document ->
                FirestoreWireMapper.transactionFromMap(
                    documentId = document.id,
                    ownerUserId = userId,
                    data = document.data.orEmpty()
                )
            }

            if (TransactionFallbackPolicy.shouldLoadLegacy(transactions.size)) {
                try {
                    val rootSnapshot = db.collection(FirestoreSchema.LEGACY_ROOT_TRANSACTIONS)
                        .whereEqualTo("userId", userId)
                        .get()
                        .await()
                    val rootTransactions = rootSnapshot.documents.map { document ->
                        FirestoreWireMapper.transactionFromMap(
                            documentId = document.id,
                            ownerUserId = userId,
                            data = document.data.orEmpty()
                        )
                    }
                    if (rootTransactions.isNotEmpty()) {
                        transactions = TransactionFallbackPolicy.select(transactions, rootTransactions)
                    }
                } catch (_: Exception) {}
            }

            Result.success(transactions)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteTransaction(transactionId: String): Result<Boolean> {
        return try {
            val userId = auth.currentUser?.uid ?: throw Exception("Chưa đăng nhập")
            db.collection(FirestoreSchema.USERS)
                .document(userId)
                .collection(FirestoreSchema.TRANSACTIONS)
                .document(transactionId)
                .delete()
                .await()
            try {
                db.collection(FirestoreSchema.LEGACY_ROOT_TRANSACTIONS)
                    .document(transactionId)
                    .delete()
                    .await()
            } catch (_: Exception) {}
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateTransaction(transaction: Transaction): Result<Boolean> {
        return try {
            val userId = auth.currentUser?.uid ?: throw Exception("Chưa đăng nhập")

            db.collection(FirestoreSchema.USERS)
                .document(userId)
                .collection(FirestoreSchema.TRANSACTIONS)
                .document(transaction.id)
                .set(FirestoreWireMapper.transactionToMap(transaction))
                .await()

            try {
                db.collection(FirestoreSchema.LEGACY_ROOT_TRANSACTIONS)
                    .document(transaction.id)
                    .set(FirestoreWireMapper.transactionToMap(transaction))
                    .await()
            } catch (_: Exception) {}

            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

}
