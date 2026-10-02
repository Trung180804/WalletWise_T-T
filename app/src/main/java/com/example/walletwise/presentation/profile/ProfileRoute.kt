package com.example.walletwise.presentation.profile

enum class ProfileRoute {
    MAIN,
    EDIT_PROFILE,
    SETTINGS,
    FONT_SIZE,
    CURRENCY,
    DEFAULT_CURRENCY,
    THEME,
    RECURRING,
    ABOUT_US,
    REMINDERS,
    CUSTOMER_CARE,
    CATEGORY_MANAGEMENT,
    SMART_BUDGET
}

internal val ProfileRoute.isSubScreen: Boolean
    get() = this != ProfileRoute.MAIN

internal fun ProfileRoute.backDestination(): ProfileRoute = when (this) {
    ProfileRoute.FONT_SIZE,
    ProfileRoute.THEME,
    ProfileRoute.RECURRING,
    ProfileRoute.REMINDERS,
    ProfileRoute.DEFAULT_CURRENCY,
    ProfileRoute.CATEGORY_MANAGEMENT -> ProfileRoute.SETTINGS
    else -> ProfileRoute.MAIN
}

internal fun SettingsDestination.toProfileRoute(): ProfileRoute = when (this) {
    SettingsDestination.FONT_SIZE -> ProfileRoute.FONT_SIZE
    SettingsDestination.CATEGORY_MANAGEMENT -> ProfileRoute.CATEGORY_MANAGEMENT
    SettingsDestination.DEFAULT_CURRENCY -> ProfileRoute.DEFAULT_CURRENCY
    SettingsDestination.REMINDERS -> ProfileRoute.REMINDERS
    SettingsDestination.RECURRING -> ProfileRoute.RECURRING
    SettingsDestination.THEME -> ProfileRoute.THEME
}

internal fun ProfileDestination.toProfileRoute(): ProfileRoute = when (this) {
    ProfileDestination.EDIT_PROFILE -> ProfileRoute.EDIT_PROFILE
    ProfileDestination.SMART_BUDGET -> ProfileRoute.SMART_BUDGET
    ProfileDestination.CURRENCY -> ProfileRoute.CURRENCY
    ProfileDestination.CUSTOMER_CARE -> ProfileRoute.CUSTOMER_CARE
    ProfileDestination.SETTINGS -> ProfileRoute.SETTINGS
    ProfileDestination.ABOUT_US -> ProfileRoute.ABOUT_US
}
