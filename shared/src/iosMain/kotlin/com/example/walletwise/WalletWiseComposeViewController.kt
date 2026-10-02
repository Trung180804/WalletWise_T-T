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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.walletwise.domain.repository.CallbackTransactionService
import com.example.walletwise.domain.repository.CallbackTransactionWriteService
import com.example.walletwise.domain.repository.CallbackCategoryService
import com.example.walletwise.domain.repository.CallbackSupportService
import com.example.walletwise.data.repository.CallbackCategoryRepository
import com.example.walletwise.data.repository.CallbackTransactionRepository
import com.example.walletwise.presentation.support.CallbackSupportImagePicker
import com.example.walletwise.presentation.support.IosSupportImage
import com.example.walletwise.presentation.support.SupportChatContent
import com.example.walletwise.presentation.support.SupportChatSession
import com.example.walletwise.presentation.support.formatIosSupportTime
import com.example.walletwise.presentation.transaction.*
import kotlinx.coroutines.*
import platform.Foundation.NSDate
import platform.Foundation.NSUUID

fun walletWiseComposeViewController(
    service: CallbackAuthService?,
    observer: AuthControllerObserver? = null,
    transactionService: CallbackTransactionService? = null,
    homeObserver: TransactionHomeObserver? = null,
    transactionWriter: CallbackTransactionWriteService? = null,
    categoryService: CallbackCategoryService? = null,
    supportService: CallbackSupportService? = null,
    supportImagePicker: CallbackSupportImagePicker? = null,
    supportImageBaseUrl: String = "",
    allowLocalSupportImages: Boolean = false
): UIViewController {
    val session = AuthControllerSession(service)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val home = TransactionHomeSession(scope, session.presenter, CallbackTransactionRepository(transactionService, transactionWriter), IosTransactionDateTimeProvider(), CallbackCategoryRepository(categoryService))
    val support = SupportChatSession(
        scope = scope,
        auth = session.presenter,
        service = supportService,
        imagePicker = supportImagePicker,
        newId = { NSUUID().UUIDString },
        now = { ((NSDate().timeIntervalSinceReferenceDate + 978_307_200.0) * 1000.0).toLong() }
    )
    fun dispose() { support.dispose(); home.dispose(); session.dispose(); scope.cancel() }
    val controller = ComposeUIViewController {
        DisposableEffect(session) { onDispose { dispose() } }
        MaterialTheme(colorScheme = if (androidx.compose.foundation.isSystemInDarkTheme()) androidx.compose.material3.darkColorScheme() else androidx.compose.material3.lightColorScheme()) {
            androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
                WalletWiseAuthContent(
                    session.presenter,
                    home,
                    support,
                    supportImageBaseUrl,
                    allowLocalSupportImages
                )
            }
        }
    }
    observer?.created(session)
    homeObserver?.createdHome(home)
    return controller
}

@Composable
private fun WalletWiseAuthContent(
    presenter: ConnectedAuthPresenter,
    home: TransactionHomeSession,
    support: SupportChatSession,
    supportImageBaseUrl: String,
    allowLocalSupportImages: Boolean
) {
    val state by presenter.state.collectAsState()

    if (state.isAuthenticated) {
        var supportVisible by remember(state.user?.uid) { mutableStateOf(false) }
        if (supportVisible) {
            SupportChatContent(
                presenter = support.chat,
                images = support.images,
                onBack = { supportVisible = false },
                timeFormatter = ::formatIosSupportTime,
                imageContent = { reference, modifier ->
                    IosSupportImage(
                        reference = reference,
                        baseUrl = supportImageBaseUrl,
                        allowLocalHttp = allowLocalSupportImages,
                        modifier = modifier
                    )
                }
            )
            return
        }
        val homeState by home.state.collectAsState()
        // Auth changes can be composed before a queued presentation update; mask by authoritative UID.
        val owned = homeState.takeIf { it.userId == state.user?.uid }
            ?: TransactionHomeState(userId = state.user?.uid, displayLabel = state.user?.displayLabel.orEmpty(), list = TransactionListUiState(userId = state.user?.uid, isLoading = true))
        androidx.compose.runtime.key(state.user?.uid) {
            TransactionHomeContent(
                owned,
                home,
                (state.sessionOperation as? AuthOperationState.RepositoryError)?.message,
                onOpenSupport = { supportVisible = true }
            )
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
