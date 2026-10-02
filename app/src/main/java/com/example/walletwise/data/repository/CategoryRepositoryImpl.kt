package com.example.walletwise.data.repository

import com.example.walletwise.data.mapper.FirestoreSchema
import com.example.walletwise.data.mapper.FirestoreWireMapper
import com.example.walletwise.domain.model.Category
import com.example.walletwise.domain.repository.CategoryRepository
import com.example.walletwise.domain.result.RepositoryResult
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

class CategoryRepositoryImpl(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) : CategoryRepository {
    private val defaultsMutex = Mutex()

    override fun observeCategories(userId: String): Flow<RepositoryResult<List<Category>>> =
        callbackFlow {
            val registration = categoriesCollection(userId).addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(RepositoryResult.Failure(error.toRepositoryError("Không thể tải danh mục")))
                    return@addSnapshotListener
                }
                val categories = snapshot?.documents.orEmpty().map { document ->
                    FirestoreWireMapper.categoryFromMap(
                        documentId = document.id,
                        data = document.data.orEmpty()
                    )
                }
                trySend(RepositoryResult.Success(categories))
            }
            awaitClose { registration.remove() }
        }

    override suspend fun addCategory(
        userId: String,
        category: Category
    ): RepositoryResult<Unit> = write("Không thể thêm danh mục") {
        categoriesCollection(userId).document(category.id)
            .set(FirestoreWireMapper.categoryToMap(category)).await()
    }

    override suspend fun updateCategory(
        userId: String,
        category: Category
    ): RepositoryResult<Unit> = write("Không thể cập nhật danh mục") {
        categoriesCollection(userId).document(category.id)
            .set(FirestoreWireMapper.categoryToMap(category)).await()
    }

    override suspend fun deleteCategory(
        userId: String,
        categoryId: String
    ): RepositoryResult<Unit> = write("Không thể xóa danh mục") {
        categoriesCollection(userId).document(categoryId).delete().await()
    }

    override suspend fun swapCategories(
        userId: String,
        first: Category,
        second: Category
    ): RepositoryResult<Unit> = write("Không thể đổi vị trí danh mục") {
        val collection = categoriesCollection(userId)
        db.runBatch { batch ->
            batch.set(collection.document(first.id), FirestoreWireMapper.categoryToMap(first))
            batch.set(collection.document(second.id), FirestoreWireMapper.categoryToMap(second))
        }.await()
    }

    override suspend fun ensureDefaultCategories(
        userId: String,
        defaults: List<Category>
    ): RepositoryResult<Boolean> = defaultsMutex.withLock {
        try {
            val existing = categoriesCollection(userId).limit(1).get().await()
            if (!existing.isEmpty) {
                RepositoryResult.Success(false)
            } else {
                val collection = categoriesCollection(userId)
                db.runBatch { batch ->
                    defaults.forEach { category ->
                        batch.set(
                            collection.document(category.id),
                            FirestoreWireMapper.categoryToMap(category)
                        )
                    }
                }.await()
                RepositoryResult.Success(true)
            }
        } catch (error: Throwable) {
            RepositoryResult.Failure(error.toRepositoryError("Không thể tạo danh mục mặc định"))
        }
    }

    private fun categoriesCollection(userId: String) =
        db.collection(FirestoreSchema.USERS)
            .document(userId)
            .collection(FirestoreSchema.CATEGORIES)

    private suspend inline fun write(
        fallbackMessage: String,
        crossinline block: suspend () -> Unit
    ): RepositoryResult<Unit> = try {
        block()
        RepositoryResult.Success(Unit)
    } catch (error: Throwable) {
        RepositoryResult.Failure(error.toRepositoryError(fallbackMessage))
    }
}
