package com.example.walletwise.presentation.transaction

import com.example.walletwise.domain.model.Category
import com.example.walletwise.domain.model.Transaction
import com.example.walletwise.domain.model.sortedForDisplay

data class TransactionCategoryChoices(val firstEight: List<Category>, val currentOutside: Category?)

fun transactionCategoryChoices(categories: List<Category>, type: String, selected: String, editing: Transaction?): TransactionCategoryChoices {
    val ordered = categories.filter { it.type == type }.distinctBy { it.id }.sortedForDisplay()
    val firstEight = ordered.take(8)
    val outside = ordered.firstOrNull { it.name == selected && firstEight.none { first -> first.id == it.id } }
        ?: editing?.takeIf { it.type == type && it.category == selected && firstEight.none { first -> first.name == selected } }
            ?.let { Category(id = it.categoryId, name = selected, icon = "🏷️", type = type) }
    return TransactionCategoryChoices(firstEight, outside)
}
