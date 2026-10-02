package com.example.walletwise.presentation.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class AuthRoute {
    LOGIN,
    REGISTER,
    FORGOT_PASSWORD
}

data class UnconnectedAuthUiState(
    val route: AuthRoute = AuthRoute.LOGIN,
    val auth: AuthUiState = AuthUiState()
)

/**
 * Local auth UI boundary until an iOS authentication adapter is available.
 * It owns form state and routes only, and never authenticates or emits navigation events.
 */
class UnconnectedAuthPresenter {
    private val mutableState = MutableStateFlow(UnconnectedAuthUiState())
    val state: StateFlow<UnconnectedAuthUiState> = mutableState.asStateFlow()

    fun navigateTo(route: AuthRoute) {
        mutableState.value = UnconnectedAuthUiState(route = route)
    }

    fun onEmailChanged(value: String) {
        mutableState.update { current ->
            val auth = current.auth
            current.copy(auth = when (current.route) {
                AuthRoute.LOGIN -> auth.copy(login = auth.login.copy(email = value))
                AuthRoute.REGISTER -> auth.copy(register = auth.register.copy(email = value))
                AuthRoute.FORGOT_PASSWORD -> auth.copy(
                    forgotPassword = auth.forgotPassword.copy(email = value)
                )
            })
        }
    }

    fun onPasswordChanged(value: String) {
        mutableState.update { current ->
            val auth = current.auth
            current.copy(auth = when (current.route) {
                AuthRoute.LOGIN -> auth.copy(login = auth.login.copy(password = value))
                AuthRoute.REGISTER -> auth.copy(register = auth.register.copy(password = value))
                AuthRoute.FORGOT_PASSWORD -> auth
            })
        }
    }

    fun onUsernameChanged(value: String) {
        mutableState.update { current ->
            if (current.route != AuthRoute.REGISTER) current else current.copy(
                auth = current.auth.copy(register = current.auth.register.copy(username = value))
            )
        }
    }

    fun togglePasswordVisibility() {
        mutableState.update { current ->
            if (current.route != AuthRoute.LOGIN) current else current.copy(
                auth = current.auth.copy(login = current.auth.login.copy(
                    passwordVisible = !current.auth.login.passwordVisible
                ))
            )
        }
    }

    fun submit() {
        mutableState.update { current ->
            val unavailable = AuthOperationState.RepositoryError(
                "Dịch vụ xác thực chưa được kết nối."
            )
            val auth = current.auth
            current.copy(auth = when (current.route) {
                AuthRoute.LOGIN -> auth.copy(login = auth.login.copy(operation = unavailable))
                AuthRoute.REGISTER -> auth.copy(register = auth.register.copy(operation = unavailable))
                AuthRoute.FORGOT_PASSWORD -> auth.copy(
                    forgotPassword = auth.forgotPassword.copy(operation = unavailable)
                )
            })
        }
    }
}
