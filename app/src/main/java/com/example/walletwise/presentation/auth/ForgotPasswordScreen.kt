package com.example.walletwise.presentation.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.walletwise.presentation.auth.ui.ForgotPasswordScreenContent

@Composable
fun ForgotPasswordScreen(
    viewModel: AuthViewModel,
    onNavigateBackToLogin: () -> Unit
) {
    val state = viewModel.authUiState.collectAsStateWithLifecycle().value
    val event = state.pendingEvent

    LaunchedEffect(event?.id) {
        event ?: return@LaunchedEffect
        when (event.event) {
            AuthUiEvent.NavigateToLogin -> onNavigateBackToLogin()
            AuthUiEvent.NavigateHome,
            AuthUiEvent.NavigateToRegister,
            AuthUiEvent.NavigateToForgotPassword -> Unit
        }
        viewModel.consumeAuthUiEvent(event.id)
    }

    ForgotPasswordScreenContent(
        state = state,
        onEmailChanged = viewModel::onForgotPasswordEmailChanged,
        onSubmit = viewModel::submitResetPassword,
        onNavigateToLogin = viewModel::navigateFromForgotPasswordToLogin
    )
}
