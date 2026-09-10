package com.example.walletwise.presentation.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.walletwise.presentation.auth.ui.RegisterScreenContent

@Composable
fun RegisterScreen(
    viewModel: AuthViewModel,
    onNavigateToLogin: () -> Unit,
    onRegisterSuccess: () -> Unit
) {
    val state = viewModel.authUiState.collectAsStateWithLifecycle().value
    val event = state.pendingEvent

    LaunchedEffect(event?.id) {
        event ?: return@LaunchedEffect
        when (event.event) {
            AuthUiEvent.NavigateHome -> onRegisterSuccess()
            AuthUiEvent.NavigateToLogin -> onNavigateToLogin()
            AuthUiEvent.NavigateToRegister,
            AuthUiEvent.NavigateToForgotPassword -> Unit
        }
        viewModel.consumeAuthUiEvent(event.id)
    }

    RegisterScreenContent(
        state = state,
        onUsernameChanged = viewModel::onRegisterUsernameChanged,
        onEmailChanged = viewModel::onRegisterEmailChanged,
        onPasswordChanged = viewModel::onRegisterPasswordChanged,
        onSubmit = viewModel::submitRegister,
        onNavigateToLogin = viewModel::navigateFromRegisterToLogin
    )
}
