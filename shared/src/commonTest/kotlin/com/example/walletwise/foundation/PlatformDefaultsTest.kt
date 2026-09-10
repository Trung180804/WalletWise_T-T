package com.example.walletwise.foundation

import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PlatformDefaultsTest {
    @Test
    fun currentEpochMilliseconds_isPositive() {
        assertTrue(currentEpochMilliseconds() > 0L)
    }

    @Test
    fun randomUuidString_returnsUniqueNonBlankValues() {
        val first = randomUuidString()
        val second = randomUuidString()

        assertTrue(first.isNotBlank())
        assertNotEquals(first, second)
    }
}
