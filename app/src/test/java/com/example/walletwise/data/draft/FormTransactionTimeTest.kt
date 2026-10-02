package com.example.walletwise.data.draft

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.Instant

class FormTransactionTimeTest {
    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    @Test fun editingSameDayKeepsOriginalHourAndReceiptKeepsRecognizedDay() {
        val original = Instant.parse("2026-09-16T13:41:00Z").toEpochMilli()
        val receipt = Instant.parse("2026-09-15T17:00:00Z").toEpochMilli()
        val today = LocalDate.of(2026,9,16)
        assertEquals(original, FormTransactionTime.resolve(today,original,null,original+60000,zone))
        assertEquals(receipt, FormTransactionTime.resolve(today,null,receipt,original,zone))
    }
    @Test fun newTodayUsesCurrentTimeAndExplicitOtherDayUsesLocalMidnight() {
        val now = Instant.parse("2026-09-16T13:41:00Z").toEpochMilli()
        assertEquals(now, FormTransactionTime.resolve(LocalDate.of(2026,9,16),null,null,now,zone))
        assertEquals(Instant.parse("2026-09-14T17:00:00Z").toEpochMilli(),
            FormTransactionTime.resolve(LocalDate.of(2026,9,15),null,null,now,zone))
    }
}
