package com.example.walletwise.presentation.category

import com.example.walletwise.domain.model.Category
import com.example.walletwise.domain.repository.CategoryRepository
import com.example.walletwise.domain.result.RepositoryError
import com.example.walletwise.domain.result.RepositoryErrorCode
import com.example.walletwise.domain.result.RepositoryResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CategoryUiPresenterTest {
    @Test
    fun sessionMapsLoadingDataEmptyAndError_withoutDroppingOldDataOnFailure() = runTest {
        val category = category()
        val session = MutableStateFlow(CategorySessionState("uid", true, emptyList()))
        val presenter = CategoryUiPresenter(this, session, PresenterCategoryRepository())
        runCurrent()
        assertTrue(presenter.state.value.isLoading)

        session.value = CategorySessionState("uid", false, listOf(category))
        runCurrent()
        assertEquals(listOf(category), presenter.state.value.categories)
        assertFalse(presenter.state.value.isEmpty)

        val error = RepositoryError(RepositoryErrorCode.NETWORK, "offline")
        session.value = CategorySessionState("uid", false, listOf(category), error)
        runCurrent()
        assertEquals(listOf(category), presenter.state.value.categories)
        assertSame(error, presenter.state.value.repositoryError)

        session.value = CategorySessionState("uid", false, emptyList())
        runCurrent()
        assertTrue(presenter.state.value.isEmpty)
        presenter.close()
    }

    @Test
    fun tabAndAddEditor_openEditCancelAndKeepVerbatimName() = runTest {
        val existing = category(name = "Ăn uống", type = "Chi")
        val session = MutableStateFlow(CategorySessionState("uid", false, listOf(existing)))
        val presenter = CategoryUiPresenter(this, session, PresenterCategoryRepository())
        runCurrent()

        presenter.onTypeSelected("Thu")
        assertEquals("Thu", presenter.state.value.selectedType)
        presenter.onOpenAdd()
        presenter.onNameChanged("  Danh mục mới  ")
        presenter.onFormTypeSelected("Thu")
        presenter.onIconSelected("🎁")
        assertEquals(CategoryEditorMode.ADD, presenter.state.value.editorMode)
        assertEquals("  Danh mục mới  ", presenter.state.value.formName)
        presenter.onCancelEditor()
        assertEquals(CategoryEditorMode.NONE, presenter.state.value.editorMode)

        presenter.onOpenEdit(existing)
        assertEquals(CategoryEditorMode.EDIT, presenter.state.value.editorMode)
        assertEquals(existing.name, presenter.state.value.formName)
        presenter.onCancelEditor()
        assertEquals(CategoryEditorMode.NONE, presenter.state.value.editorMode)
        presenter.close()
    }

    @Test
    fun addAndUpdate_completeWriteBeforeClosingAndEmitConsumableEvent() = runTest {
        val existing = category()
        val repository = PresenterCategoryRepository()
        val session = MutableStateFlow(CategorySessionState("uid", false, listOf(existing)))
        val presenter = CategoryUiPresenter(this, session, repository)
        runCurrent()

        presenter.onOpenAdd()
        presenter.onNameChanged("Mới")
        presenter.onSubmit()
        runCurrent()
        assertEquals(1, repository.addCalls)
        assertEquals("Mới", repository.added?.name)
        assertEquals(CategoryEditorMode.NONE, presenter.state.value.editorMode)
        consumeExpectedMessage(presenter, CategoryMessage.ADDED)

        presenter.onOpenEdit(existing)
        presenter.onNameChanged("Đổi tên")
        presenter.onSubmit()
        runCurrent()
        assertEquals(1, repository.updateCalls)
        assertEquals("Đổi tên", repository.updated?.name)
        assertEquals(CategoryEditorMode.NONE, presenter.state.value.editorMode)
        consumeExpectedMessage(presenter, CategoryMessage.UPDATED)
        presenter.close()
    }

    @Test
    fun invalidOrFailedSubmit_keepsFormAndOldCategoryList() = runTest {
        val existing = category()
        val repository = PresenterCategoryRepository().apply { failWrites = true }
        val session = MutableStateFlow(CategorySessionState("uid", false, listOf(existing)))
        val presenter = CategoryUiPresenter(this, session, repository)
        runCurrent()

        presenter.onOpenAdd()
        presenter.onNameChanged("   ")
        presenter.onSubmit()
        runCurrent()
        assertEquals(CategoryEditorMode.ADD, presenter.state.value.editorMode)
        assertEquals("   ", presenter.state.value.formName)
        assertEquals(com.example.walletwise.domain.validation.CategoryValidationError.NAME_REQUIRED, presenter.state.value.validationError)
        assertEquals(0, repository.addCalls)

        presenter.onNameChanged("Hợp lệ")
        presenter.onSubmit()
        runCurrent()
        assertEquals(CategoryEditorMode.ADD, presenter.state.value.editorMode)
        assertEquals(listOf(existing), presenter.state.value.categories)
        assertEquals("write failed", presenter.state.value.repositoryError?.message)
        consumeExpectedMessage(presenter, CategoryMessage.ERROR)
        presenter.close()
    }

    @Test
    fun cancelDeleteDoesNotWrite_andSuccessOrFailureKeepsCorrectDialogState() = runTest {
        val existing = category()
        val repository = PresenterCategoryRepository()
        val session = MutableStateFlow(CategorySessionState("uid", false, listOf(existing)))
        val presenter = CategoryUiPresenter(this, session, repository)
        runCurrent()

        presenter.onRequestDelete(existing)
        presenter.onCancelDelete()
        assertEquals(0, repository.deleteCalls)

        repository.failWrites = true
        presenter.onRequestDelete(existing)
        presenter.onConfirmDelete()
        runCurrent()
        assertEquals(existing, presenter.state.value.deletingCategory)
        assertEquals(listOf(existing), presenter.state.value.categories)
        consumeExpectedMessage(presenter, CategoryMessage.ERROR)

        repository.failWrites = false
        presenter.onConfirmDelete()
        runCurrent()
        assertNull(presenter.state.value.deletingCategory)
        consumeExpectedMessage(presenter, CategoryMessage.DELETED)
        presenter.close()
    }

    @Test
    fun fastRepeatedSubmitAndDelete_areCoalesced() = runTest {
        val existing = category()
        val repository = PresenterCategoryRepository()
        val session = MutableStateFlow(CategorySessionState("uid", false, listOf(existing)))
        val presenter = CategoryUiPresenter(this, session, repository)
        runCurrent()

        repository.writeGate = CompletableDeferred()
        presenter.onOpenAdd()
        presenter.onNameChanged("Mới")
        presenter.onSubmit()
        presenter.onSubmit()
        runCurrent()
        assertEquals(1, repository.addCalls)
        assertTrue(presenter.state.value.isSubmitting)
        repository.writeGate?.complete(Unit)
        runCurrent()

        repository.writeGate = CompletableDeferred()
        presenter.onRequestDelete(existing)
        presenter.onConfirmDelete()
        presenter.onConfirmDelete()
        runCurrent()
        assertEquals(1, repository.deleteCalls)
        assertTrue(presenter.state.value.isDeleting)
        repository.writeGate?.complete(Unit)
        runCurrent()
        presenter.close()
    }

    @Test
    fun swapAndBack_emitOnce_andLegacyIconGetsSafeFallback() = runTest {
        val top = category(id = "top", sortOrder = 1)
        val custom = category(id = "custom", name = "Custom", sortOrder = 0)
        val repository = PresenterCategoryRepository()
        val session = MutableStateFlow(CategorySessionState("uid", false, listOf(top, custom)))
        val presenter = CategoryUiPresenter(this, session, repository)
        runCurrent()

        presenter.onSelectSwapCategory(custom)
        consumeExpectedMessage(presenter, CategoryMessage.SELECT_SWAP_TARGET)
        presenter.onSwapWith(top)
        runCurrent()
        assertEquals(1, repository.swapCalls)
        assertNull(presenter.state.value.selectedSwapCategory)
        consumeExpectedMessage(presenter, CategoryMessage.SWAPPED)

        presenter.onBack()
        val event = presenter.state.value.pendingEvent!!
        assertIs<CategoryUiEvent.NavigateBack>(event.event)
        presenter.consumeEvent(event.id)
        assertNull(presenter.state.value.pendingEvent)

        assertEquals("🎮", categoryIconForDisplay("legacy-unknown-key"))
        assertEquals("🍔", categoryIconForDisplay("🍔"))
        presenter.close()
    }

    private fun consumeExpectedMessage(presenter: CategoryUiPresenter, expected: CategoryMessage) {
        val envelope = presenter.state.value.pendingEvent!!
        assertEquals(expected, assertIs<CategoryUiEvent.Message>(envelope.event).kind)
        presenter.consumeEvent(envelope.id)
        assertNull(presenter.state.value.pendingEvent)
    }

    private fun category(
        id: String = "category-1",
        name: String = "Ăn uống",
        type: String = "Chi",
        sortOrder: Int = 1
    ) = Category(id, name, "🍔", type, true, sortOrder)
}

private class PresenterCategoryRepository : CategoryRepository {
    var addCalls = 0
    var updateCalls = 0
    var deleteCalls = 0
    var swapCalls = 0
    var added: Category? = null
    var updated: Category? = null
    var failWrites = false
    var writeGate: CompletableDeferred<Unit>? = null

    private val failure = RepositoryResult.Failure(
        RepositoryError(RepositoryErrorCode.UNKNOWN, "write failed")
    )

    override fun observeCategories(userId: String): Flow<RepositoryResult<List<Category>>> = emptyFlow()

    override suspend fun addCategory(userId: String, category: Category): RepositoryResult<Unit> {
        addCalls++
        added = category
        writeGate?.await()
        return result()
    }

    override suspend fun updateCategory(userId: String, category: Category): RepositoryResult<Unit> {
        updateCalls++
        updated = category
        writeGate?.await()
        return result()
    }

    override suspend fun deleteCategory(userId: String, categoryId: String): RepositoryResult<Unit> {
        deleteCalls++
        writeGate?.await()
        return result()
    }

    override suspend fun swapCategories(userId: String, first: Category, second: Category): RepositoryResult<Unit> {
        swapCalls++
        writeGate?.await()
        return result()
    }

    override suspend fun ensureDefaultCategories(userId: String, defaults: List<Category>) =
        RepositoryResult.Success(false)

    private fun result(): RepositoryResult<Unit> =
        if (failWrites) failure else RepositoryResult.Success(Unit)
}
