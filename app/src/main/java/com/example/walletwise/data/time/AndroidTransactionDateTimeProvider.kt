package com.example.walletwise.data.time

import com.example.walletwise.presentation.transaction.TransactionDateTimeProvider
import com.example.walletwise.presentation.transaction.TransactionLocalDateTime
import java.time.Instant
import java.time.ZoneId

class AndroidTransactionDateTimeProvider(
    private val zoneIdProvider: () -> ZoneId = ZoneId::systemDefault
) : TransactionDateTimeProvider {
    override fun localDateTime(epochMilliseconds: Long): TransactionLocalDateTime? = runCatching {
        Instant.ofEpochMilli(epochMilliseconds)
            .atZone(zoneIdProvider())
            .toLocalDateTime()
            .let {
                TransactionLocalDateTime(
                    year = it.year,
                    month = it.monthValue,
                    day = it.dayOfMonth,
                    hour = it.hour,
                    minute = it.minute
                )
            }
    }.getOrNull()
}
