package com.example.walletwise.domain.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransactionFallbackPolicyTest {
    @Test
    fun emptyPrimaryCollection_usesLegacyTransactions() {
        val legacy = listOf("legacy-1")

        assertTrue(TransactionFallbackPolicy.shouldLoadLegacy(0))
        assertEquals(legacy, TransactionFallbackPolicy.select(emptyList(), legacy))
    }

    @Test
    fun nonEmptyPrimaryCollection_neverMixesLegacyTransactions() {
        val primary = listOf("primary-1")

        assertFalse(TransactionFallbackPolicy.shouldLoadLegacy(primary.size))
        assertEquals(
            primary,
            TransactionFallbackPolicy.select(primary, listOf("legacy-1"))
        )
    }
}
