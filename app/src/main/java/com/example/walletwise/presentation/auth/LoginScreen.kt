package com.example.walletwise.presentation.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.walletwise.presentation.auth.ui.LoginScreenContent

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onNavigateToRegister: () -> Unit,
    onNavigateToForgotPassword: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    val state = viewModel.authUiState.collectAsStateWithLifecycle().value
    val event = state.pendingEvent

    LaunchedEffect(event?.id) {
        event ?: return@LaunchedEffect
        when (event.event) {
            AuthUiEvent.NavigateHome -> onLoginSuccess()
            AuthUiEvent.NavigateToRegister -> onNavigateToRegister()
            AuthUiEvent.NavigateToForgotPassword -> onNavigateToForgotPassword()
            AuthUiEvent.NavigateToLogin -> Unit
        }
        viewModel.consumeAuthUiEvent(event.id)
    }

    LoginScreenContent(
        state = state,
        onEmailChanged = viewModel::onLoginEmailChanged,
        onPasswordChanged = viewModel::onLoginPasswordChanged,
        onTogglePasswordVisibility = viewModel::toggleLoginPasswordVisibility,
        onSubmit = viewModel::submitLogin,
        onNavigateToRegister = viewModel::navigateToRegister,
        onNavigateToForgotPassword = viewModel::navigateToForgotPassword
    )
}
