package com.example.walletwise.domain.usecase

import com.example.walletwise.domain.model.Category
import com.example.walletwise.domain.repository.CategoryRepository
import com.example.walletwise.domain.result.RepositoryError
import com.example.walletwise.domain.result.RepositoryResult
import com.example.walletwise.domain.validation.CategoryValidationError
import com.example.walletwise.domain.validation.CategoryValidator
import kotlinx.coroutines.flow.Flow

sealed interface CategoryUseCaseResult<out T> {
    data class Success<T>(val value: T) : CategoryUseCaseResult<T>
    data class ValidationFailure(val error: CategoryValidationError) : CategoryUseCaseResult<Nothing>
    data class RepositoryFailure(val error: RepositoryError) : CategoryUseCaseResult<Nothing>
}

class ObserveCategoriesUseCase(private val repository: CategoryRepository) {
    operator fun invoke(userId: String): Flow<RepositoryResult<List<Category>>> =
        repository.observeCategories(userId)
}

class AddCategoryUseCase(private val repository: CategoryRepository) {
    suspend operator fun invoke(userId: String, category: Category): CategoryUseCaseResult<Unit> {
        CategoryValidator.validateForAdd(category)?.let {
            return CategoryUseCaseResult.ValidationFailure(it)
        }
        return repository.addCategory(userId, category).toUseCaseResult()
    }
}

class UpdateCategoryUseCase(private val repository: CategoryRepository) {
    suspend operator fun invoke(userId: String, category: Category): CategoryUseCaseResult<Unit> {
        CategoryValidator.validateForUpdate(category)?.let {
            return CategoryUseCaseResult.ValidationFailure(it)
        }
        return repository.updateCategory(userId, category).toUseCaseResult()
    }
}

class DeleteCategoryUseCase(private val repository: CategoryRepository) {
    suspend operator fun invoke(userId: String, categoryId: String): CategoryUseCaseResult<Unit> {
        CategoryValidator.validateId(categoryId)?.let {
            return CategoryUseCaseResult.ValidationFailure(it)
        }
        return repository.deleteCategory(userId, categoryId).toUseCaseResult()
    }
}

class SwapCategoriesUseCase(private val repository: CategoryRepository) {
    suspend operator fun invoke(
        userId: String,
        first: Category,
        second: Category
    ): CategoryUseCaseResult<Unit> {
        CategoryValidator.validateForUpdate(first)?.let {
            return CategoryUseCaseResult.ValidationFailure(it)
        }
        CategoryValidator.validateForUpdate(second)?.let {
            return CategoryUseCaseResult.ValidationFailure(it)
        }
        return repository.swapCategories(userId, first, second).toUseCaseResult()
    }
}

class EnsureDefaultCategoriesUseCase(private val repository: CategoryRepository) {
    suspend operator fun invoke(
        userId: String,
        defaults: List<Category>
    ): CategoryUseCaseResult<Boolean> {
        if (userId.isBlank()) return CategoryUseCaseResult.ValidationFailure(CategoryValidationError.ID_REQUIRED)
        return when (val result = repository.ensureDefaultCategories(userId, defaults)) {
            is RepositoryResult.Success -> CategoryUseCaseResult.Success(result.value)
            is RepositoryResult.Failure -> CategoryUseCaseResult.RepositoryFailure(result.error)
        }
    }
}

private fun RepositoryResult<Unit>.toUseCaseResult(): CategoryUseCaseResult<Unit> = when (this) {
    is RepositoryResult.Success -> CategoryUseCaseResult.Success(Unit)
    is RepositoryResult.Failure -> CategoryUseCaseResult.RepositoryFailure(error)
}
