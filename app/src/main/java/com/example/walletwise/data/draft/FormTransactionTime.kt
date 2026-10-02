package com.example.walletwise.data.draft

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Editing an amount must preserve the original time when the day was not changed. */
object FormTransactionTime {
    fun resolve(selected: LocalDate, original: Long?, receipt: Long?, now: Long, zone: ZoneId): Long {
        fun sameDay(timestamp: Long) = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate() == selected
        if (original != null && sameDay(original)) return original
        if (receipt != null && sameDay(receipt)) return receipt
        if (sameDay(now)) return now
        return selected.atStartOfDay(zone).toInstant().toEpochMilli()
    }
}
