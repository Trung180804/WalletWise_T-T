package com.example.walletwise.data.repository

import com.example.walletwise.domain.model.Category
import com.example.walletwise.domain.model.DefaultCategories
import com.example.walletwise.domain.repository.*
import com.example.walletwise.domain.result.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

/** Reuses the existing category session, without its Android default-seeding side effect. */
class CallbackCategoryRepository(private val service: CallbackCategoryService?) : CategoryRepository {
    override fun observeCategories(userId: String) = callbackFlow<RepositoryResult<List<Category>>> {
        var active = true
        if (service == null) {
            trySend(RepositoryResult.Success(DefaultCategories))
            awaitClose { active = false }
        } else {
            val listener = service.observeCategories(userId, object : CategorySnapshotObserver {
                override fun changed(categories: List<Category>?, failure: TransactionReadFailure?) {
                    if (!active) return
                    if (failure != null || categories == null) trySend(unavailable())
                    else trySend(RepositoryResult.Success(categories.ifEmpty { DefaultCategories }))
                }
            })
            awaitClose { active = false; listener.cancel() }
        }
    }
    override suspend fun ensureDefaultCategories(userId: String, defaults: List<Category>) = RepositoryResult.Success(false)
    override suspend fun addCategory(userId: String, category: Category) = unavailable()
    override suspend fun updateCategory(userId: String, category: Category) = unavailable()
    override suspend fun deleteCategory(userId: String, categoryId: String) = unavailable()
    override suspend fun swapCategories(userId: String, first: Category, second: Category) = unavailable()
    private fun unavailable() = RepositoryResult.Failure(RepositoryError(RepositoryErrorCode.UNKNOWN, "Không thể tải danh mục. Vui lòng thử lại."))
}
