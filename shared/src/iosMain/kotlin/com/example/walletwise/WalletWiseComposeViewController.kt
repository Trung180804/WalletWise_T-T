package com.example.walletwise

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.window.ComposeUIViewController
import com.example.walletwise.presentation.auth.AuthRoute
import com.example.walletwise.presentation.auth.ConnectedAuthPresenter
import com.example.walletwise.presentation.auth.AuthOperationState
import com.example.walletwise.presentation.auth.AuthControllerSession
import com.example.walletwise.presentation.auth.AuthControllerObserver
import com.example.walletwise.domain.repository.CallbackAuthService
import com.example.walletwise.presentation.auth.ui.ForgotPasswordScreenContent
import com.example.walletwise.presentation.auth.ui.LoginScreenContent
import com.example.walletwise.presentation.auth.ui.RegisterScreenContent
import platform.UIKit.UIViewController

fun walletWiseComposeViewController(
    service: CallbackAuthService?,
    observer: AuthControllerObserver? = null
): UIViewController {
    val session = AuthControllerSession(service)
    val controller = ComposeUIViewController {
        DisposableEffect(session) { onDispose { session.dispose() } }
        MaterialTheme {
            WalletWiseAuthContent(session.presenter)
        }
    }
    observer?.created(session)
    return controller
}

@Composable
private fun WalletWiseAuthContent(presenter: ConnectedAuthPresenter) {
    val state by presenter.state.collectAsState()

    if (state.isAuthenticated) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Đã xác thực")
            Text(state.user?.displayLabel.orEmpty())
            (state.sessionOperation as? AuthOperationState.RepositoryError)?.let { Text(it.message) }
            Button(onClick = presenter::logout) { Text("Đăng xuất") }
        }
        return
    }

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
