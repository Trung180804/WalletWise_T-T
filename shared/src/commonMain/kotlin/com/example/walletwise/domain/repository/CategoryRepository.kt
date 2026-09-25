package com.example.walletwise.domain.repository

import com.example.walletwise.domain.model.Category
import com.example.walletwise.domain.result.RepositoryResult
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    fun observeCategories(userId: String): Flow<RepositoryResult<List<Category>>>

    suspend fun addCategory(userId: String, category: Category): RepositoryResult<Unit>

    suspend fun updateCategory(userId: String, category: Category): RepositoryResult<Unit>

    suspend fun deleteCategory(userId: String, categoryId: String): RepositoryResult<Unit>

    suspend fun swapCategories(
        userId: String,
        first: Category,
        second: Category
    ): RepositoryResult<Unit>

    /** Returns true only when the default set was actually written. */
    suspend fun ensureDefaultCategories(
        userId: String,
        defaults: List<Category>
    ): RepositoryResult<Boolean>
}
