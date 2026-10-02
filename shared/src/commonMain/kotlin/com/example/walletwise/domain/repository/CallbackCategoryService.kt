package com.example.walletwise.domain.repository

import com.example.walletwise.domain.model.Category

interface CategorySnapshotObserver {
    fun changed(categories: List<Category>?, failure: TransactionReadFailure?)
}

/** Read-only existing users/{uid}/categories schema. Never creates a category. */
interface CallbackCategoryService {
    fun observeCategories(userId: String, observer: CategorySnapshotObserver): TransactionCancellation
}
