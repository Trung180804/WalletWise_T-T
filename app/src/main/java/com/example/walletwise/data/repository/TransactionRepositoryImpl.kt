package com.example.walletwise.data.repository

import com.example.walletwise.BuildConfig
import com.example.walletwise.data.image.AndroidTransactionWriter
import com.example.walletwise.data.image.ContentResolverImageReader
import com.example.walletwise.data.image.ImgBbImageUploader
import com.example.walletwise.data.mapper.AndroidFirestoreTransactionMapper
import com.example.walletwise.data.mapper.FirestoreSchema
import com.example.walletwise.data.mapper.FirestoreWireMapper
import com.example.walletwise.domain.model.Transaction
import com.example.walletwise.domain.repository.ImageUploader
import com.example.walletwise.domain.repository.TransactionRepository
import com.example.walletwise.domain.result.RepositoryError
import com.example.walletwise.domain.result.RepositoryErrorCode
import com.example.walletwise.domain.result.RepositoryResult
import com.example.walletwise.domain.service.TransactionFallbackPolicy
import com.example.walletwise.domain.service.sortedTransactionsNewestFirst
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class TransactionRepositoryImpl(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val legacyLoader: (suspend (String) -> List<Transaction>)? = null
) : TransactionRepository {

    private val imageUploader: ImageUploader by lazy {
        ImgBbImageUploader(BuildConfig.IMGBB_API_KEY)
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

    override fun observeTransactions(
        userId: String
    ): Flow<RepositoryResult<List<Transaction>>> = callbackFlow {
        if (!hasSession(userId)) {
            trySend(notAuthenticated())
            awaitClose {}
            return@callbackFlow
        }

        var legacyReadJob: Job? = null
        val snapshotGeneration = java.util.concurrent.atomic.AtomicLong()
        val registration = transactionsCollection(userId).addSnapshotListener { snapshot, error ->
            val generation = snapshotGeneration.incrementAndGet()
            legacyReadJob?.cancel()
            legacyReadJob = null
            if (!hasSession(userId)) {
                trySend(notAuthenticated())
                return@addSnapshotListener
            }
            if (error != null) {
                trySend(
                    RepositoryResult.Failure(
                        error.toRepositoryError("Không thể tải giao dịch")
                    )
                )
                return@addSnapshotListener
            }

            val primary = mapDocuments(snapshot?.documents.orEmpty(), userId)
            if (!TransactionFallbackPolicy.shouldLoadLegacy(primary.size)) {
                trySend(RepositoryResult.Success(primary.sortedTransactionsNewestFirst()))
                return@addSnapshotListener
            }

            // Legacy is migration-only: keep one live Firebase listener and use
            // a one-shot fallback read only while the primary collection is empty.
            legacyReadJob = launch {
                val legacy = try { legacyLoader?.invoke(userId) ?: loadLegacyTransactions(userId) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { emptyList() }
                coroutineContext.ensureActive()
                if (hasSession(userId) && snapshotGeneration.get() == generation) {
                    trySend(
                        RepositoryResult.Success(
                            TransactionFallbackPolicy.select(primary, legacy)
                                .sortedTransactionsNewestFirst()
                        )
                    )
                }
            }
        }
        awaitClose {
            snapshotGeneration.incrementAndGet()
            legacyReadJob?.cancel()
            registration.remove()
        }
    }

    override suspend fun addTransaction(transaction: Transaction): Result<Boolean> {
        return try {
            val userId = auth.currentUser?.uid ?: throw Exception("Chưa đăng nhập")
            require(transaction.userId.isBlank() || transaction.userId == userId) { "Phiên giao dịch đã thay đổi" }
            val docRef = db.collection(FirestoreSchema.USERS)
                .document(userId)
                .collection(FirestoreSchema.TRANSACTIONS)
                .document(transaction.id.ifBlank { java.util.UUID.randomUUID().toString() })

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
            val snapshot = transactionsCollection(userId).get().await()
            var transactions = mapDocuments(snapshot.documents, userId)

            if (TransactionFallbackPolicy.shouldLoadLegacy(transactions.size)) {
                try {
                    val rootTransactions = loadLegacyTransactions(userId)
                    if (rootTransactions.isNotEmpty()) {
                        transactions = TransactionFallbackPolicy.select(transactions, rootTransactions)
                    }
                } catch (_: Exception) {}
            }

            Result.success(transactions.sortedTransactionsNewestFirst())
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
            require(transaction.userId.isBlank() || transaction.userId == userId) { "Phiên giao dịch đã thay đổi" }

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

    private fun transactionsCollection(userId: String) = db
        .collection(FirestoreSchema.USERS)
        .document(userId)
        .collection(FirestoreSchema.TRANSACTIONS)

    private suspend fun loadLegacyTransactions(userId: String): List<Transaction> =
        db.collection(FirestoreSchema.LEGACY_ROOT_TRANSACTIONS)
            .whereEqualTo("userId", userId)
            .get()
            .await()
            .documents
            .let { mapDocuments(it, userId) }

    private fun mapDocuments(
        documents: List<DocumentSnapshot>,
        userId: String
    ): List<Transaction> = documents.map { document ->
        AndroidFirestoreTransactionMapper.fromMap(
            documentId = document.id,
            ownerUserId = userId,
            data = document.data.orEmpty()
        )
    }

    private fun hasSession(userId: String): Boolean =
        userId.isNotBlank() && auth.currentUser?.uid == userId

    private fun notAuthenticated(): RepositoryResult.Failure = RepositoryResult.Failure(
        RepositoryError(RepositoryErrorCode.NOT_AUTHENTICATED, "Phiên đăng nhập đã thay đổi")
    )

}
