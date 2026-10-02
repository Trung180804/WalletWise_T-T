package com.example.walletwise.domain.service

import com.example.walletwise.domain.model.Transaction

/** Stable newest-first ordering shared by every transaction consumer. */
fun List<Transaction>.sortedTransactionsNewestFirst(): List<Transaction> =
    sortedWith(
        compareByDescending<Transaction> { it.timestamp }
            .thenBy(Transaction::id)
    )
