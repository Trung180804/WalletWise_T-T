package com.example.walletwise

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.example.walletwise.presentation.auth.AuthRoute
import com.example.walletwise.presentation.auth.UnconnectedAuthPresenter
import com.example.walletwise.presentation.auth.ui.ForgotPasswordScreenContent
import com.example.walletwise.presentation.auth.ui.LoginScreenContent
import com.example.walletwise.presentation.auth.ui.RegisterScreenContent
import platform.UIKit.UIViewController

fun walletWiseComposeViewController(): UIViewController = ComposeUIViewController {
    MaterialTheme {
        WalletWiseAuthContent()
    }
}

@Composable
private fun WalletWiseAuthContent() {
    val presenter = remember { UnconnectedAuthPresenter() }
    val state by presenter.state.collectAsState()

    key(state.route) {
        when (state.route) {
            AuthRoute.LOGIN -> LoginScreenContent(
                state = state.auth,
                onEmailChanged = presenter::onEmailChanged,
                onPasswordChanged = presenter::onPasswordChanged,
                onTogglePasswordVisibility = presenter::togglePasswordVisibility,
                onSubmit = presenter::submit,
                onNavigateToRegister = { presenter.navigateTo(AuthRoute.REGISTER) },
                onNavigateToForgotPassword = { presenter.navigateTo(AuthRoute.FORGOT_PASSWORD) }
            )
            AuthRoute.REGISTER -> RegisterScreenContent(
                state = state.auth,
                onUsernameChanged = presenter::onUsernameChanged,
                onEmailChanged = presenter::onEmailChanged,
                onPasswordChanged = presenter::onPasswordChanged,
                onSubmit = presenter::submit,
                onNavigateToLogin = { presenter.navigateTo(AuthRoute.LOGIN) }
            )
            AuthRoute.FORGOT_PASSWORD -> ForgotPasswordScreenContent(
                state = state.auth,
                onEmailChanged = presenter::onEmailChanged,
                onSubmit = presenter::submit,
                onNavigateToLogin = { presenter.navigateTo(AuthRoute.LOGIN) }
            )
        }
    }
}
