package com.example.walletwise.domain.validation

import com.example.walletwise.domain.model.CATEGORY_TYPE_EXPENSE
import com.example.walletwise.domain.model.CATEGORY_TYPE_INCOME
import com.example.walletwise.domain.model.Category

enum class CategoryValidationError {
    NAME_REQUIRED,
    TYPE_INVALID,
    ICON_REQUIRED,
    ID_REQUIRED
}

object CategoryValidator {
    fun validateForAdd(category: Category): CategoryValidationError? = when {
        category.name.isBlank() -> CategoryValidationError.NAME_REQUIRED
        category.type != CATEGORY_TYPE_EXPENSE && category.type != CATEGORY_TYPE_INCOME ->
            CategoryValidationError.TYPE_INVALID
        category.icon.isBlank() -> CategoryValidationError.ICON_REQUIRED
        else -> null
    }

    fun validateForUpdate(category: Category): CategoryValidationError? =
        if (category.id.isBlank()) CategoryValidationError.ID_REQUIRED else validateForAdd(category)

    fun validateId(categoryId: String): CategoryValidationError? =
        if (categoryId.isBlank()) CategoryValidationError.ID_REQUIRED else null

    /** The Android implementation historically allowed duplicate names verbatim. */
    fun hasNameConflict(@Suppress("UNUSED_PARAMETER") categories: List<Category>, @Suppress("UNUSED_PARAMETER") candidate: Category): Boolean = false
}
