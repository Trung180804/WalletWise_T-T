package com.example.walletwise.presentation.category

import com.example.walletwise.domain.model.Category
import com.example.walletwise.domain.model.DefaultCategories
import com.example.walletwise.domain.repository.CategoryRepository
import com.example.walletwise.domain.result.RepositoryError
import com.example.walletwise.domain.result.RepositoryResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CategorySessionState(
    val userId: String? = null,
    val isLoading: Boolean = false,
    val categories: List<Category> = DefaultCategories,
    val error: RepositoryError? = null
)

/** Owns the single cold repository observation and cancels it on UID changes/logout. */
class CategorySessionController(
    private val scope: CoroutineScope,
    private val repository: CategoryRepository,
    private val defaults: List<Category> = DefaultCategories
) {
    private val mutableState = MutableStateFlow(CategorySessionState())
    val state: StateFlow<CategorySessionState> = mutableState.asStateFlow()

    private var observedUserId: String? = null
    private var observationJob: Job? = null
    private var generation = 0L
    private var closed = false

    fun setUserId(userId: String?) {
        if (closed) return
        val normalized = userId?.takeIf { it.isNotBlank() }
        if (normalized == observedUserId && observationJob?.isActive == true) return

        observationJob?.cancel()
        val currentGeneration = ++generation
        observationJob = null
        observedUserId = normalized

        if (normalized == null) {
            mutableState.value = CategorySessionState(categories = defaults)
            return
        }

        mutableState.value = CategorySessionState(
            userId = normalized,
            isLoading = true,
            categories = defaults,
            error = null
        )
        observationJob = scope.launch {
            val ensured = repository.ensureDefaultCategories(normalized, defaults)
            if (closed || generation != currentGeneration) return@launch
            when (ensured) {
                is RepositoryResult.Failure -> mutableState.value = mutableState.value.copy(
                    isLoading = false,
                    error = ensured.error
                )
                is RepositoryResult.Success -> Unit
            }

            repository.observeCategories(normalized).collect { result ->
                if (closed || generation != currentGeneration) return@collect
                when (result) {
                    is RepositoryResult.Success -> {
                        if (result.value.isEmpty()) {
                            when (val ensured = repository.ensureDefaultCategories(normalized, defaults)) {
                                is RepositoryResult.Success -> mutableState.value = CategorySessionState(
                                    userId = normalized,
                                    isLoading = ensured.value,
                                    categories = if (ensured.value) mutableState.value.categories else emptyList(),
                                    error = null
                                )
                                is RepositoryResult.Failure -> mutableState.value = mutableState.value.copy(
                                    userId = normalized,
                                    isLoading = false,
                                    error = ensured.error
                                )
                            }
                        } else {
                            mutableState.value = CategorySessionState(
                                userId = normalized,
                                isLoading = false,
                                categories = result.value,
                                error = null
                            )
                        }
                    }
                    is RepositoryResult.Failure -> mutableState.value = mutableState.value.copy(
                        userId = normalized,
                        isLoading = false,
                        error = result.error
                    )
                }
            }
        }
    }

    fun close() {
        if (closed) return
        closed = true
        generation++
        observationJob?.cancel()
        observationJob = null
        observedUserId = null
        mutableState.value = CategorySessionState(categories = defaults)
    }

    fun refresh() {
        val uid = observedUserId ?: return
        observationJob?.cancel()
        observationJob = null
        observedUserId = null
        setUserId(uid)
    }
}
