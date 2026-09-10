package com.example.walletwise.presentation.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileRouteTest {
    @Test
    fun mainAndSubScreenState_matchBottomNavigationContract() {
        assertFalse(ProfileRoute.MAIN.isSubScreen)
        ProfileRoute.entries
            .filterNot { it == ProfileRoute.MAIN }
            .forEach { assertTrue(it.isSubScreen) }
    }

    @Test
    fun backDestination_preservesNestedRouteBehavior() {
        assertEquals(ProfileRoute.RECURRING, ProfileRoute.ADD_RECURRING.backDestination())
        listOf(
            ProfileRoute.FONT_SIZE,
            ProfileRoute.THEME,
            ProfileRoute.RECURRING,
            ProfileRoute.REMINDERS,
            ProfileRoute.DEFAULT_CURRENCY,
            ProfileRoute.CATEGORY_MANAGEMENT
        ).forEach { route ->
            assertEquals(ProfileRoute.SETTINGS, route.backDestination())
        }
        listOf(
            ProfileRoute.EDIT_PROFILE,
            ProfileRoute.SETTINGS,
            ProfileRoute.CURRENCY,
            ProfileRoute.ABOUT_US,
            ProfileRoute.CUSTOMER_CARE,
            ProfileRoute.SMART_BUDGET
        ).forEach { route ->
            assertEquals(ProfileRoute.MAIN, route.backDestination())
        }
    }

    @Test
    fun sharedSettingsDestinations_mapToTheCorrectAndroidRoutes() {
        assertEquals(ProfileRoute.FONT_SIZE, SettingsDestination.FONT_SIZE.toProfileRoute())
        assertEquals(
            ProfileRoute.CATEGORY_MANAGEMENT,
            SettingsDestination.CATEGORY_MANAGEMENT.toProfileRoute()
        )
        assertEquals(
            ProfileRoute.DEFAULT_CURRENCY,
            SettingsDestination.DEFAULT_CURRENCY.toProfileRoute()
        )
        assertEquals(ProfileRoute.REMINDERS, SettingsDestination.REMINDERS.toProfileRoute())
        assertEquals(ProfileRoute.RECURRING, SettingsDestination.RECURRING.toProfileRoute())
        assertEquals(ProfileRoute.THEME, SettingsDestination.THEME.toProfileRoute())
    }

    @Test
    fun sharedMenuDestinations_mapToTheCorrectAndroidRoutes() {
        assertEquals(ProfileRoute.EDIT_PROFILE, ProfileDestination.EDIT_PROFILE.toProfileRoute())
        assertEquals(ProfileRoute.SMART_BUDGET, ProfileDestination.SMART_BUDGET.toProfileRoute())
        assertEquals(ProfileRoute.CURRENCY, ProfileDestination.CURRENCY.toProfileRoute())
        assertEquals(
            ProfileRoute.CUSTOMER_CARE,
            ProfileDestination.CUSTOMER_CARE.toProfileRoute()
        )
        assertEquals(ProfileRoute.SETTINGS, ProfileDestination.SETTINGS.toProfileRoute())
        assertEquals(ProfileRoute.ABOUT_US, ProfileDestination.ABOUT_US.toProfileRoute())
    }
}
