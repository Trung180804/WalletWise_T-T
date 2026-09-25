package com.example.walletwise.presentation.category

import com.example.walletwise.domain.model.Category
import com.example.walletwise.domain.repository.CategoryRepository
import com.example.walletwise.domain.result.RepositoryResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class CategorySessionControllerTest {
    @Test
    fun repeatedSameUid_startsOneCollectorAndSeedsOnce() = runTest {
        val repository = CountingCategoryRepository()
        val controller = CategorySessionController(this, repository)

        controller.setUserId("uid-1")
        controller.setUserId("uid-1")
        runCurrent()

        assertEquals(1, repository.ensureCalls)
        assertEquals(1, repository.observeCalls)
        assertEquals(1, repository.activeCollectors)
        controller.close()
        runCurrent()
        assertEquals(0, repository.activeCollectors)
    }

    @Test
    fun uidChangeAndLogout_cancelOldCollectorAndClearUserState() = runTest {
        val repository = CountingCategoryRepository()
        val controller = CategorySessionController(this, repository)

        controller.setUserId("uid-1")
        runCurrent()
        controller.setUserId("uid-2")
        assertEquals(16, controller.state.value.categories.size)
        runCurrent()

        assertEquals(2, repository.observeCalls)
        assertEquals(1, repository.activeCollectors)
        assertEquals("uid-2", controller.state.value.userId)

        controller.setUserId(null)
        runCurrent()
        assertEquals(0, repository.activeCollectors)
        assertEquals(null, controller.state.value.userId)
        controller.close()
    }

    @Test
    fun emptySnapshot_checksDefaultsAgainButWritesTheDefaultSetOnlyOnce() = runTest {
        val repository = CountingCategoryRepository(initialCategories = emptyList())
        val controller = CategorySessionController(this, repository)

        controller.setUserId("uid-1")
        runCurrent()

        assertEquals(2, repository.ensureCalls)
        assertEquals(16, repository.defaultsWritten)
        controller.close()
    }
}

private class CountingCategoryRepository(
    initialCategories: List<Category> = listOf(Category("category-1", "Ăn uống", "🍔"))
) : CategoryRepository {
    private val feed = MutableStateFlow<RepositoryResult<List<Category>>>(
        RepositoryResult.Success(initialCategories)
    )
    var ensureCalls = 0
    var observeCalls = 0
    var activeCollectors = 0
    var defaultsWritten = 0
    private var defaultsExist = initialCategories.isNotEmpty()

    override fun observeCategories(userId: String): Flow<RepositoryResult<List<Category>>> {
        observeCalls++
        return flow {
            activeCollectors++
            try {
                feed.collect { emit(it) }
            } finally {
                activeCollectors--
            }
        }
    }

    override suspend fun addCategory(userId: String, category: Category) = RepositoryResult.Success(Unit)
    override suspend fun updateCategory(userId: String, category: Category) = RepositoryResult.Success(Unit)
    override suspend fun deleteCategory(userId: String, categoryId: String) = RepositoryResult.Success(Unit)
    override suspend fun swapCategories(userId: String, first: Category, second: Category) = RepositoryResult.Success(Unit)
    override suspend fun ensureDefaultCategories(userId: String, defaults: List<Category>): RepositoryResult<Boolean> {
        ensureCalls++
        if (defaultsExist) return RepositoryResult.Success(false)
        defaultsExist = true
        defaultsWritten += defaults.size
        return RepositoryResult.Success(true)
    }
}
