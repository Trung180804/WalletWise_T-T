package com.example.walletwise.data.time

import com.example.walletwise.domain.service.ReminderLocalDate
import com.example.walletwise.domain.service.ReminderLocalDateTime
import com.example.walletwise.domain.service.ReminderLocalTime
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class AndroidRecurringDateTimeProviderTest {
    @Test
    fun `device timezone provider changes local wall clock and round trips occurrence instant`() {
        val epoch = Instant.parse("2026-12-31T20:00:00Z").toEpochMilli()
        val utc = AndroidRecurringDateTimeProvider({ ZoneId.of("UTC") }, { epoch })
        val vietnam = AndroidRecurringDateTimeProvider({ ZoneId.of("Asia/Ho_Chi_Minh") }, { epoch })

        assertEquals(
            ReminderLocalDateTime(ReminderLocalDate(2026, 12, 31), ReminderLocalTime(20, 0)),
            utc.currentLocalDateTime()
        )
        assertEquals(
            ReminderLocalDateTime(ReminderLocalDate(2027, 1, 1), ReminderLocalTime(3, 0)),
            vietnam.currentLocalDateTime()
        )
        assertEquals(epoch, utc.toEpochMilliseconds(utc.currentLocalDateTime()))
        assertEquals(epoch, vietnam.toEpochMilliseconds(vietnam.currentLocalDateTime()))
    }
}
