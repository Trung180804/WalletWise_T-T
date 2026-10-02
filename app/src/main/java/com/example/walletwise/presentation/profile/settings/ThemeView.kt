package com.example.walletwise.presentation.profile.settings

import androidx.compose.runtime.Composable
import com.example.walletwise.presentation.profile.ThemeContent
import com.example.walletwise.ui.theme.LocalAppTheme

@Composable
fun ThemeView(onBack: () -> Unit) {
    val appTheme = LocalAppTheme.current

    ThemeContent(
        isDarkTheme = appTheme.value,
        onThemeSelected = { appTheme.value = it },
        onBack = onBack
    )
}
