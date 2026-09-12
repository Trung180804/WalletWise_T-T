package com.example.walletwise.domain.service

import com.example.walletwise.domain.model.Transaction
import kotlin.test.Test
import kotlin.test.assertEquals

class TransactionOrderingTest {
    @Test
    fun newestFirst_isDeterministicWhenTimestampsMatch() {
        val sorted = listOf(
            Transaction(id = "z", timestamp = 20L),
            Transaction(id = "b", timestamp = 30L),
            Transaction(id = "a", timestamp = 30L)
        ).sortedTransactionsNewestFirst()

        assertEquals(listOf("a", "b", "z"), sorted.map(Transaction::id))
    }
}
