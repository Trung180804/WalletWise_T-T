package com.example.walletwise.data.time

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidTransactionDateTimeProviderTest {
    @Test
    fun epochIsConvertedThroughTheInjectedTimezone() {
        val provider = AndroidTransactionDateTimeProvider { ZoneId.of("Asia/Ho_Chi_Minh") }

        val value = requireNotNull(provider.localDateTime(1_725_840_000_000L))

        assertEquals(2024, value.year)
        assertEquals(9, value.month)
        assertEquals(9, value.day)
        assertEquals(7, value.hour)
        assertEquals(0, value.minute)
    }
}
