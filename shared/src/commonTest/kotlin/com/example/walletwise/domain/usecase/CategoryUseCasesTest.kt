package com.example.walletwise.domain.usecase

import com.example.walletwise.domain.model.CATEGORY_TYPE_EXPENSE
import com.example.walletwise.domain.model.Category
import com.example.walletwise.domain.model.DefaultCategories
import com.example.walletwise.domain.repository.CategoryRepository
import com.example.walletwise.domain.result.RepositoryError
import com.example.walletwise.domain.result.RepositoryErrorCode
import com.example.walletwise.domain.result.RepositoryResult
import com.example.walletwise.domain.validation.CategoryValidationError
import com.example.walletwise.domain.validation.CategoryValidator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CategoryUseCasesTest {
    @Test
    fun validation_preservesRequiredFieldsAndAllowsExistingDuplicateSemantics() {
        assertEquals(
            CategoryValidationError.NAME_REQUIRED,
            CategoryValidator.validateForAdd(category(name = "   "))
        )
        assertEquals(
            CategoryValidationError.TYPE_INVALID,
            CategoryValidator.validateForAdd(category(type = "expense"))
        )
        assertEquals(
            CategoryValidationError.ICON_REQUIRED,
            CategoryValidator.validateForAdd(category(icon = ""))
        )
        assertEquals(
            CategoryValidationError.ID_REQUIRED,
            CategoryValidator.validateForUpdate(category(id = ""))
        )
        assertEquals(null, CategoryValidator.validateForAdd(category(name = "  Hợp lệ  ")))
        assertFalse(CategoryValidator.hasNameConflict(listOf(category(name = "Ăn uống")), category(name = "Ăn uống")))
        assertFalse(CategoryValidator.hasNameConflict(listOf(category(name = "Ăn uống")), category(name = "ăn uống")))
    }

    @Test
    fun addUpdateDeleteAndSwap_delegateOnlyAfterValidation() = runTest {
        val repository = RecordingCategoryRepository()
        val original = category()
        val updated = original.copy(name = "Tên mới")

        assertIs<CategoryUseCaseResult.Success<Unit>>(AddCategoryUseCase(repository)("uid", original))
        assertIs<CategoryUseCaseResult.Success<Unit>>(UpdateCategoryUseCase(repository)("uid", updated))
        assertIs<CategoryUseCaseResult.Success<Unit>>(DeleteCategoryUseCase(repository)("uid", original.id))
        assertIs<CategoryUseCaseResult.Success<Unit>>(
            SwapCategoriesUseCase(repository)("uid", original, updated.copy(id = "category-2"))
        )

        assertEquals(1, repository.addCalls)
        assertEquals(1, repository.updateCalls)
        assertEquals(1, repository.deleteCalls)
        assertEquals(1, repository.swapCalls)
    }

    @Test
    fun repositoryFailures_areNotSwallowed() = runTest {
        val repository = RecordingCategoryRepository().apply { failWrites = true }

        val add = AddCategoryUseCase(repository)("uid", category())
        val update = UpdateCategoryUseCase(repository)("uid", category())
        val delete = DeleteCategoryUseCase(repository)("uid", "category-1")

        assertIs<CategoryUseCaseResult.RepositoryFailure>(add)
        assertIs<CategoryUseCaseResult.RepositoryFailure>(update)
        assertIs<CategoryUseCaseResult.RepositoryFailure>(delete)
    }

    @Test
    fun defaults_keepExactCountOrderTypesAndDoNotOverwriteExistingData() = runTest {
        assertEquals(16, DefaultCategories.size)
        assertEquals((1..8).toList(), DefaultCategories.take(8).map { it.sortOrder })
        assertTrue(DefaultCategories.take(8).all { it.type == "Chi" })
        assertTrue(DefaultCategories.drop(8).all { it.type == "Thu" })

        val repository = RecordingCategoryRepository().apply { defaultsAlreadyExist = true }
        val result = EnsureDefaultCategoriesUseCase(repository)("uid", DefaultCategories)

        assertEquals(false, assertIs<CategoryUseCaseResult.Success<Boolean>>(result).value)
        assertEquals(1, repository.ensureCalls)
        assertEquals(0, repository.defaultsWritten)
    }

    private fun category(
        id: String = "category-1",
        name: String = "Ăn uống",
        icon: String = "🍔",
        type: String = CATEGORY_TYPE_EXPENSE
    ) = Category(id, name, icon, type, true, 0)
}

private class RecordingCategoryRepository : CategoryRepository {
    var addCalls = 0
    var updateCalls = 0
    var deleteCalls = 0
    var swapCalls = 0
    var ensureCalls = 0
    var defaultsWritten = 0
    var failWrites = false
    var defaultsAlreadyExist = false

    private val failure = RepositoryResult.Failure(
        RepositoryError(RepositoryErrorCode.UNKNOWN, "write failed")
    )

    override fun observeCategories(userId: String): Flow<RepositoryResult<List<Category>>> =
        flowOf(RepositoryResult.Success(emptyList()))

    override suspend fun addCategory(userId: String, category: Category): RepositoryResult<Unit> {
        addCalls++
        return if (failWrites) failure else RepositoryResult.Success(Unit)
    }

    override suspend fun updateCategory(userId: String, category: Category): RepositoryResult<Unit> {
        updateCalls++
        return if (failWrites) failure else RepositoryResult.Success(Unit)
    }

    override suspend fun deleteCategory(userId: String, categoryId: String): RepositoryResult<Unit> {
        deleteCalls++
        return if (failWrites) failure else RepositoryResult.Success(Unit)
    }

    override suspend fun swapCategories(userId: String, first: Category, second: Category): RepositoryResult<Unit> {
        swapCalls++
        return if (failWrites) failure else RepositoryResult.Success(Unit)
    }

    override suspend fun ensureDefaultCategories(userId: String, defaults: List<Category>): RepositoryResult<Boolean> {
        ensureCalls++
        if (failWrites) return failure
        if (defaultsAlreadyExist) return RepositoryResult.Success(false)
        defaultsWritten += defaults.size
        defaultsAlreadyExist = true
        return RepositoryResult.Success(true)
    }
}
