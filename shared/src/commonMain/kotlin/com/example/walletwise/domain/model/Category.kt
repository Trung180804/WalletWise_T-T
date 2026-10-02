package com.example.walletwise.domain.model

import com.example.walletwise.foundation.randomUuidString

const val CATEGORY_TYPE_EXPENSE = "Chi"
const val CATEGORY_TYPE_INCOME = "Thu"
const val CATEGORY_FALLBACK_ICON = "🎮"

data class Category(
    val id: String = randomUuidString(),
    val name: String = "",
    val icon: String = "",
    val type: String = CATEGORY_TYPE_EXPENSE,
    val isCustom: Boolean = false,
    val sortOrder: Int = 0
)

val DefaultExpenseCategories: List<Category> = listOf(
    Category(name = "Ăn uống", icon = "🍔", sortOrder = 1),
    Category(name = "Mua sắm", icon = "🛒", sortOrder = 2),
    Category(name = "Nhà cửa", icon = "🏠", sortOrder = 3),
    Category(name = "Di chuyển", icon = "🚗", sortOrder = 4),
    Category(name = "Y tế", icon = "💊", sortOrder = 5),
    Category(name = "Giải trí", icon = "🎮", sortOrder = 6),
    Category(name = "Hóa đơn", icon = "💳", sortOrder = 7),
    Category(name = "Học tập", icon = "📚", sortOrder = 8)
)

val DefaultIncomeCategories: List<Category> = listOf(
    Category(name = "Lương", icon = "💰", type = CATEGORY_TYPE_INCOME, sortOrder = 1),
    Category(name = "Thưởng", icon = "🎁", type = CATEGORY_TYPE_INCOME, sortOrder = 2),
    Category(name = "Đầu tư", icon = "📈", type = CATEGORY_TYPE_INCOME, sortOrder = 3),
    Category(name = "Kinh doanh", icon = "💼", type = CATEGORY_TYPE_INCOME, sortOrder = 4),
    Category(name = "Part-time", icon = "🎯", type = CATEGORY_TYPE_INCOME, sortOrder = 5),
    Category(name = "Giải thưởng", icon = "🏆", type = CATEGORY_TYPE_INCOME, sortOrder = 6),
    Category(name = "Quà tặng", icon = "💝", type = CATEGORY_TYPE_INCOME, sortOrder = 7),
    Category(name = "Khác", icon = "🔄", type = CATEGORY_TYPE_INCOME, sortOrder = 8)
)

val DefaultCategories: List<Category> = DefaultExpenseCategories + DefaultIncomeCategories

fun List<Category>.sortedForDisplay(): List<Category> = sortedWith(
    compareBy({ if (it.sortOrder == 0) Int.MAX_VALUE else it.sortOrder }, { it.name })
)
