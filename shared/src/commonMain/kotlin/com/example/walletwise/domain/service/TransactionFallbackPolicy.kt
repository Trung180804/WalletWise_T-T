package com.example.walletwise.domain.service

object TransactionFallbackPolicy {
    fun shouldLoadLegacy(primaryTransactionsCount: Int): Boolean =
        primaryTransactionsCount == 0

    fun <T> select(primary: List<T>, legacy: List<T>): List<T> =
        if (primary.isEmpty()) legacy else primary
}
