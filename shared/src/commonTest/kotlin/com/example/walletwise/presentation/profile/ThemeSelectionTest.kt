package com.example.walletwise.presentation.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThemeSelectionTest {
    @Test
    fun lightAndDarkSelection_areDerivedOnlyFromSharedBooleanState() {
        assertTrue(isThemeOptionSelected(currentIsDarkTheme = false, optionIsDarkTheme = false))
        assertFalse(isThemeOptionSelected(currentIsDarkTheme = false, optionIsDarkTheme = true))
        assertTrue(isThemeOptionSelected(currentIsDarkTheme = true, optionIsDarkTheme = true))
        assertFalse(isThemeOptionSelected(currentIsDarkTheme = true, optionIsDarkTheme = false))
    }

    @Test
    fun lightSelection_emitsFalseExactlyOnce() {
        var selected = true
        var callbackCount = 0

        dispatchThemeSelection(false) {
            selected = it
            callbackCount++
        }

        assertFalse(selected)
        assertEquals(1, callbackCount)
    }

    @Test
    fun darkSelection_emitsTrueExactlyOnce() {
        var selected = false
        var callbackCount = 0

        dispatchThemeSelection(true) {
            selected = it
            callbackCount++
        }

        assertTrue(selected)
        assertEquals(1, callbackCount)
    }

    @Test
    fun selectingCurrentTheme_isStillOneNormalCallback() {
        var currentTheme = true
        var callbackCount = 0

        dispatchThemeSelection(true) {
            currentTheme = it
            callbackCount++
        }

        assertTrue(currentTheme)
        assertEquals(1, callbackCount)
    }
}
