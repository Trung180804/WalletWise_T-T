package com.example.walletwise.presentation.auth

import com.example.walletwise.domain.model.AuthSession
import com.example.walletwise.domain.model.User

sealed interface AuthStatus {
    data object Loading : AuthStatus
    data class Authenticated(val session: AuthSession) : AuthStatus
    data object Unauthenticated : AuthStatus
}

sealed interface AuthOperationState {
    data object Idle : AuthOperationState
    data object Loading : AuthOperationState
    data class Success(val message: String? = null) : AuthOperationState
    data class ValidationError(val message: String) : AuthOperationState
    data class RepositoryError(val message: String) : AuthOperationState
}

data class AuthProfileUiState(
    val authStatus: AuthStatus = AuthStatus.Loading,
    val operation: AuthOperationState = AuthOperationState.Idle,
    val profile: User? = null
)

sealed interface AuthUiEvent {
    data object NavigateHome : AuthUiEvent
    data object NavigateToRegister : AuthUiEvent
    data object NavigateToForgotPassword : AuthUiEvent
    data object NavigateToLogin : AuthUiEvent
}

enum class AuthSubmission {
    LOGIN,
    REGISTER,
    RESET_PASSWORD
}

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val passwordVisible: Boolean = false,
    val operation: AuthOperationState = AuthOperationState.Idle
)

data class RegisterUiState(
    val username: String = "",
    val email: String = "",
    val password: String = "",
    val operation: AuthOperationState = AuthOperationState.Idle
)

data class ForgotPasswordUiState(
    val email: String = "",
    val operation: AuthOperationState = AuthOperationState.Idle
)

data class AuthUiEventEnvelope(
    val id: Long,
    val event: AuthUiEvent
)

data class AuthUiState(
    val login: LoginUiState = LoginUiState(),
    val register: RegisterUiState = RegisterUiState(),
    val forgotPassword: ForgotPasswordUiState = ForgotPasswordUiState(),
    val activeSubmission: AuthSubmission? = null,
    val pendingEvent: AuthUiEventEnvelope? = null
) {
    val isLoading: Boolean
        get() = activeSubmission != null
}
