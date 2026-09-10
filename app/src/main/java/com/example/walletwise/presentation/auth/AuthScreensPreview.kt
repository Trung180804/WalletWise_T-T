package com.example.walletwise.presentation.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.walletwise.presentation.auth.ui.ForgotPasswordScreenContent
import com.example.walletwise.presentation.auth.ui.LoginScreenContent
import com.example.walletwise.presentation.auth.ui.RegisterScreenContent

@Preview(showBackground = true)
@Composable
private fun LoginScreenPreview() {
    LoginScreenContent(
        state = AuthUiState(),
        onEmailChanged = {},
        onPasswordChanged = {},
        onTogglePasswordVisibility = {},
        onSubmit = {},
        onNavigateToRegister = {},
        onNavigateToForgotPassword = {}
    )
}

@Preview(showBackground = true)
@Composable
private fun RegisterScreenPreview() {
    RegisterScreenContent(
        state = AuthUiState(),
        onUsernameChanged = {},
        onEmailChanged = {},
        onPasswordChanged = {},
        onSubmit = {},
        onNavigateToLogin = {}
    )
}

@Preview(showBackground = true)
@Composable
private fun ForgotPasswordScreenPreview() {
    ForgotPasswordScreenContent(
        state = AuthUiState(),
        onEmailChanged = {},
        onSubmit = {},
        onNavigateToLogin = {}
    )
}
