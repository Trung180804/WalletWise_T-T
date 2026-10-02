package com.example.walletwise.foundation

import kotlin.test.Test
import kotlin.test.assertTrue

class FoundationTest {
    @Test
    fun platformNameIsAvailable() {
        assertTrue(currentPlatform().name.isNotBlank())
    }
}
