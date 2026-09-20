package com.example.walletwise.presentation.profile

import androidx.compose.runtime.Composable
import com.example.walletwise.presentation.support.OnlineSupportScreen
import com.example.walletwise.presentation.support.SupportViewModel

@Composable
fun CustomerCareView(model: SupportViewModel, onBack: () -> Unit) {
    OnlineSupportScreen(model, onBack)
}
