package com.example.walletwise.presentation.profile.settings

import androidx.compose.runtime.Composable
import com.example.walletwise.presentation.profile.ProfileRoute
import com.example.walletwise.presentation.profile.SettingsMainContent
import com.example.walletwise.presentation.profile.toProfileRoute

@Composable
fun SettingsMainView(
    onNavigate: (ProfileRoute) -> Unit,
    onBack: () -> Unit
) {
    SettingsMainContent(
        onNavigate = { onNavigate(it.toProfileRoute()) },
        onBack = onBack
    )
}
