package com.example.walletwise.presentation.category

import com.example.walletwise.domain.model.CATEGORY_FALLBACK_ICON
import com.example.walletwise.domain.model.CATEGORY_TYPE_EXPENSE
import com.example.walletwise.domain.model.Category
import com.example.walletwise.domain.model.sortedForDisplay
import com.example.walletwise.domain.repository.CategoryRepository
import com.example.walletwise.domain.result.RepositoryError
import com.example.walletwise.domain.result.RepositoryErrorCode
import com.example.walletwise.domain.usecase.AddCategoryUseCase
import com.example.walletwise.domain.usecase.CategoryUseCaseResult
import com.example.walletwise.domain.usecase.DeleteCategoryUseCase
import com.example.walletwise.domain.usecase.SwapCategoriesUseCase
import com.example.walletwise.domain.usecase.UpdateCategoryUseCase
import com.example.walletwise.domain.validation.CategoryValidationError
import com.example.walletwise.foundation.randomUuidString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class CategoryEditorMode { NONE, ADD, EDIT }

enum class CategoryMessage {
    ADDED,
    UPDATED,
    DELETED,
    SWAPPED,
    SELECT_SWAP_TARGET,
    ERROR
}

sealed interface CategoryUiEvent {
    data object NavigateBack : CategoryUiEvent
    data class Message(
        val kind: CategoryMessage,
        val argument: String? = null
    ) : CategoryUiEvent
}

data class CategoryEventEnvelope(
    val id: Long,
    val event: CategoryUiEvent
)

data class CategoryUiState(
    val isLoading: Boolean = false,
    val categories: List<Category> = emptyList(),
    val selectedType: String = CATEGORY_TYPE_EXPENSE,
    val editorMode: CategoryEditorMode = CategoryEditorMode.NONE,
    val editingCategory: Category? = null,
    val deletingCategory: Category? = null,
    val selectedSwapCategory: Category? = null,
    val formName: String = "",
    val formType: String = CATEGORY_TYPE_EXPENSE,
    val formIcon: String = CATEGORY_FALLBACK_ICON,
    val validationError: CategoryValidationError? = null,
    val repositoryError: RepositoryError? = null,
    val isSubmitting: Boolean = false,
    val isDeleting: Boolean = false,
    val isSwapping: Boolean = false,
    val pendingEvent: CategoryEventEnvelope? = null
) {
    val displayedCategories: List<Category>
        get() = categories.filter { it.type == selectedType }.sortedForDisplay()

    val isEmpty: Boolean
        get() = !isLoading && displayedCategories.isEmpty()
}

class CategoryUiPresenter(
    private val scope: CoroutineScope,
    private val sessionState: StateFlow<CategorySessionState>,
    repository: CategoryRepository
) {
    private val addCategory = AddCategoryUseCase(repository)
    private val updateCategory = UpdateCategoryUseCase(repository)
    private val deleteCategory = DeleteCategoryUseCase(repository)
    private val swapCategories = SwapCategoriesUseCase(repository)

    private val mutableState = MutableStateFlow(CategoryUiState())
    val state: StateFlow<CategoryUiState> = mutableState.asStateFlow()
    private val sessionJob: Job
    private var nextEventId = 1L

    init {
        sessionJob = scope.launch {
            sessionState.collect { session ->
                mutableState.value = mutableState.value.copy(
                    isLoading = session.isLoading,
                    categories = session.categories,
                    repositoryError = session.error
                )
            }
        }
    }

    fun onTypeSelected(type: String) {
        if (type != "Chi" && type != "Thu") return
        mutableState.value = mutableState.value.copy(
            selectedType = type,
            selectedSwapCategory = null
        )
    }

    fun onOpenAdd() {
        if (mutableState.value.isSubmitting) return
        mutableState.value = mutableState.value.copy(
            editorMode = CategoryEditorMode.ADD,
            editingCategory = null,
            formName = "",
            formType = CATEGORY_TYPE_EXPENSE,
            formIcon = CATEGORY_FALLBACK_ICON,
            validationError = null,
            repositoryError = null
        )
    }

    fun onOpenEdit(category: Category) {
        if (mutableState.value.isSubmitting) return
        mutableState.value = mutableState.value.copy(
            editorMode = CategoryEditorMode.EDIT,
            editingCategory = category,
            formName = category.name,
            formType = category.type,
            formIcon = category.icon.ifBlank { CATEGORY_FALLBACK_ICON },
            validationError = null,
            repositoryError = null
        )
    }

    fun onCancelEditor() {
        if (mutableState.value.isSubmitting) return
        mutableState.value = mutableState.value.resetEditor()
    }

    fun onNameChanged(value: String) {
        mutableState.value = mutableState.value.copy(formName = value, validationError = null)
    }

    fun onFormTypeSelected(type: String) {
        if (type != "Chi" && type != "Thu") return
        mutableState.value = mutableState.value.copy(formType = type, validationError = null)
    }

    fun onIconSelected(icon: String) {
        mutableState.value = mutableState.value.copy(formIcon = icon, validationError = null)
    }

    fun onSubmit() {
        val current = mutableState.value
        if (current.isSubmitting || current.editorMode == CategoryEditorMode.NONE) return
        val userId = sessionState.value.userId ?: return emitAuthenticationError()
        val candidate = when (current.editorMode) {
            CategoryEditorMode.ADD -> Category(
                id = randomUuidString(),
                name = current.formName,
                icon = current.formIcon,
                type = current.formType,
                isCustom = true,
                sortOrder = 0
            )
            CategoryEditorMode.EDIT -> current.editingCategory?.copy(
                name = current.formName,
                icon = current.formIcon,
                type = current.formType
            ) ?: return
            CategoryEditorMode.NONE -> return
        }

        mutableState.value = current.copy(isSubmitting = true, repositoryError = null)
        scope.launch {
            val result = if (current.editorMode == CategoryEditorMode.ADD) {
                addCategory(userId, candidate)
            } else {
                updateCategory(userId, candidate)
            }
            handleSubmitResult(result, current.editorMode)
        }
    }

    fun onRequestDelete(category: Category) {
        if (mutableState.value.isDeleting) return
        mutableState.value = mutableState.value.copy(deletingCategory = category, repositoryError = null)
    }

    fun onCancelDelete() {
        if (mutableState.value.isDeleting) return
        mutableState.value = mutableState.value.copy(deletingCategory = null)
    }

    fun onConfirmDelete() {
        val current = mutableState.value
        if (current.isDeleting) return
        val category = current.deletingCategory ?: return
        val userId = sessionState.value.userId ?: return emitAuthenticationError()
        mutableState.value = current.copy(isDeleting = true, repositoryError = null)
        scope.launch {
            when (val result = deleteCategory(userId, category.id)) {
                is CategoryUseCaseResult.Success -> {
                    mutableState.value = mutableState.value.copy(
                        deletingCategory = null,
                        isDeleting = false
                    )
                    emitMessage(CategoryMessage.DELETED)
                }
                is CategoryUseCaseResult.ValidationFailure -> setValidationFailure(result.error, isDelete = true)
                is CategoryUseCaseResult.RepositoryFailure -> setRepositoryFailure(result.error, isDelete = true)
            }
        }
    }

    fun onSelectSwapCategory(category: Category) {
        if (mutableState.value.isSwapping) return
        mutableState.value = mutableState.value.copy(selectedSwapCategory = category)
        emitMessage(CategoryMessage.SELECT_SWAP_TARGET, category.name)
    }

    fun onCancelSwap() {
        if (mutableState.value.isSwapping) return
        mutableState.value = mutableState.value.copy(selectedSwapCategory = null)
    }

    fun onSwapWith(target: Category) {
        val current = mutableState.value
        if (current.isSwapping) return
        val selected = current.selectedSwapCategory ?: return
        if (selected.id == target.id) return
        val userId = sessionState.value.userId ?: return emitAuthenticationError()
        val displayList = current.displayedCategories
        val firstOrder = selected.sortOrder.takeIf { it != 0 }
            ?: (displayList.indexOfFirst { it.id == selected.id } + 1)
        val secondOrder = target.sortOrder.takeIf { it != 0 }
            ?: (displayList.indexOfFirst { it.id == target.id } + 1)
        val first = selected.copy(sortOrder = secondOrder)
        val second = target.copy(sortOrder = firstOrder)
        mutableState.value = current.copy(isSwapping = true, repositoryError = null)
        scope.launch {
            when (val result = swapCategories(userId, first, second)) {
                is CategoryUseCaseResult.Success -> {
                    mutableState.value = mutableState.value.copy(
                        selectedSwapCategory = null,
                        isSwapping = false
                    )
                    emitMessage(CategoryMessage.SWAPPED)
                }
                is CategoryUseCaseResult.ValidationFailure -> setValidationFailure(result.error, isSwap = true)
                is CategoryUseCaseResult.RepositoryFailure -> setRepositoryFailure(result.error, isSwap = true)
            }
        }
    }

    fun onBack() {
        emit(CategoryUiEvent.NavigateBack)
    }

    fun consumeEvent(id: Long) {
        val current = mutableState.value
        if (current.pendingEvent?.id == id) {
            mutableState.value = current.copy(pendingEvent = null)
        }
    }

    fun close() {
        sessionJob.cancel()
    }

    private fun handleSubmitResult(
        result: CategoryUseCaseResult<Unit>,
        mode: CategoryEditorMode
    ) {
        when (result) {
            is CategoryUseCaseResult.Success -> {
                mutableState.value = mutableState.value.resetEditor()
                emitMessage(if (mode == CategoryEditorMode.ADD) CategoryMessage.ADDED else CategoryMessage.UPDATED)
            }
            is CategoryUseCaseResult.ValidationFailure -> setValidationFailure(result.error)
            is CategoryUseCaseResult.RepositoryFailure -> setRepositoryFailure(result.error)
        }
    }

    private fun setValidationFailure(
        error: CategoryValidationError,
        isDelete: Boolean = false,
        isSwap: Boolean = false
    ) {
        mutableState.value = mutableState.value.copy(
            validationError = error,
            isSubmitting = false,
            isDeleting = if (isDelete) false else mutableState.value.isDeleting,
            isSwapping = if (isSwap) false else mutableState.value.isSwapping
        )
    }

    private fun setRepositoryFailure(
        error: RepositoryError,
        isDelete: Boolean = false,
        isSwap: Boolean = false
    ) {
        mutableState.value = mutableState.value.copy(
            repositoryError = error,
            isSubmitting = false,
            isDeleting = if (isDelete) false else mutableState.value.isDeleting,
            isSwapping = if (isSwap) false else mutableState.value.isSwapping
        )
        emitMessage(CategoryMessage.ERROR, error.message)
    }

    private fun emitAuthenticationError() {
        setRepositoryFailure(
            RepositoryError(RepositoryErrorCode.NOT_AUTHENTICATED, "Bạn cần đăng nhập để quản lý danh mục")
        )
    }

    private fun emitMessage(kind: CategoryMessage, argument: String? = null) {
        emit(CategoryUiEvent.Message(kind, argument))
    }

    private fun emit(event: CategoryUiEvent) {
        mutableState.value = mutableState.value.copy(
            pendingEvent = CategoryEventEnvelope(nextEventId++, event)
        )
    }
}

private fun CategoryUiState.resetEditor(): CategoryUiState = copy(
    editorMode = CategoryEditorMode.NONE,
    editingCategory = null,
    formName = "",
    formType = CATEGORY_TYPE_EXPENSE,
    formIcon = CATEGORY_FALLBACK_ICON,
    validationError = null,
    repositoryError = null,
    isSubmitting = false
)
