package com.example.walletwise.presentation.profile

import androidx.compose.runtime.Composable

@Composable
fun CustomerCareView(onBack: () -> Unit) {
    CustomerCareContent(
        onBack = onBack,
        onCallHotline = {},
        onEmailSupport = {},
        onOpenFaq = {},
        onFeedbackChanged = {},
        onSubmitFeedback = {}
    )
}
