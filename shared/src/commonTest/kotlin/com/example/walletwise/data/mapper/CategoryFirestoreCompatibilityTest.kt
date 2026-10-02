package com.example.walletwise.data.mapper

import com.example.walletwise.domain.model.CATEGORY_TYPE_EXPENSE
import com.example.walletwise.domain.model.CATEGORY_TYPE_INCOME
import com.example.walletwise.domain.model.Category
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CategoryFirestoreCompatibilityTest {
    @Test
    fun categoryPath_preservesExistingNestedCollection() {
        assertEquals("users/uid-1/categories", FirestoreSchema.categoriesCollection("uid-1"))
        assertEquals(
            "users/uid-1/categories/category-1",
            FirestoreSchema.categoryDocument("uid-1", "category-1")
        )
    }

    @Test
    fun categoryMap_preservesAllSixExistingFieldsAndWireValues() {
        val category = Category("category-1", "Lương", "💰", CATEGORY_TYPE_INCOME, true, 3)

        val map = FirestoreWireMapper.categoryToMap(category)

        assertEquals(setOf("id", "name", "icon", "type", "isCustom", "sortOrder"), map.keys)
        assertEquals(category, FirestoreWireMapper.categoryFromMap("ignored", map))
    }

    @Test
    fun legacyDocumentWithoutId_usesFirestoreDocumentId() {
        val category = FirestoreWireMapper.categoryFromMap(
            "legacy-doc",
            mapOf("name" to "Ăn uống", "icon" to "🍔", "type" to CATEGORY_TYPE_EXPENSE)
        )

        assertEquals("legacy-doc", category.id)
        assertFalse(category.isCustom)
        assertEquals(0, category.sortOrder)
    }

    @Test
    fun numericSortOrder_acceptsFirestoreLongAndDouble() {
        assertEquals(
            4,
            FirestoreWireMapper.categoryFromMap("a", mapOf("sortOrder" to 4L)).sortOrder
        )
        assertEquals(
            5,
            FirestoreWireMapper.categoryFromMap("b", mapOf("sortOrder" to 5.9)).sortOrder
        )
    }

    @Test
    fun unknownOrBlankIcon_isPreservedForPresentationFallbackAndValidation() {
        assertEquals(
            "legacy-key",
            FirestoreWireMapper.categoryFromMap("a", mapOf("icon" to "legacy-key")).icon
        )
        assertEquals("", FirestoreWireMapper.categoryFromMap("b", emptyMap()).icon)
    }
}
