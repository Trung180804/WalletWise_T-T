package com.example.walletwise

import com.example.walletwise.presentation.transaction.TransactionDateTimeProvider
import com.example.walletwise.presentation.transaction.TransactionLocalDateTime
import platform.Foundation.*

internal class IosTransactionDateTimeProvider : TransactionDateTimeProvider {
    override fun localDateTime(epochMilliseconds: Long): TransactionLocalDateTime? = runCatching {
        val date = NSDate.dateWithTimeIntervalSince1970(epochMilliseconds / 1000.0)
        val values = NSCalendar.currentCalendar.components(
            NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay or NSCalendarUnitHour or NSCalendarUnitMinute,
            fromDate = date
        )
        TransactionLocalDateTime(values.year.toInt(), values.month.toInt(), values.day.toInt(), values.hour.toInt(), values.minute.toInt())
    }.getOrNull()
}
