package com.example.walletwise.presentation.profile

import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsDestinationTest {
    @Test
    fun menuOrderAndCallbacks_coverEverySettingsShellDestinationOnce() {
        assertEquals(
            listOf(
                SettingsDestination.FONT_SIZE,
                SettingsDestination.CATEGORY_MANAGEMENT,
                SettingsDestination.DEFAULT_CURRENCY,
                SettingsDestination.REMINDERS,
                SettingsDestination.RECURRING,
                SettingsDestination.THEME
            ),
            SettingsMenuDestinationOrder
        )
        assertEquals(SettingsDestination.entries.toSet(), SettingsMenuDestinationOrder.toSet())
    }
}
