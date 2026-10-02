package com.example.walletwise.presentation.auth

import com.example.walletwise.domain.model.AuthSession
import com.example.walletwise.domain.model.ChangePasswordInput
import com.example.walletwise.domain.model.LoginInput
import com.example.walletwise.domain.model.RegisterInput
import com.example.walletwise.domain.model.ResetPasswordInput
import com.example.walletwise.domain.repository.AuthRepository
import com.example.walletwise.domain.result.RepositoryError
import com.example.walletwise.domain.result.RepositoryErrorCode
import com.example.walletwise.domain.result.RepositoryResult
import com.example.walletwise.domain.usecase.auth.LoginUseCase
import com.example.walletwise.domain.usecase.auth.RegisterUseCase
import com.example.walletwise.domain.usecase.auth.ResetPasswordUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.TestScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AuthUiPresenterTest {
    @Test
    fun inputStateAndPasswordVisibility_areOwnedByPresenter() = runTest {
        val presenter = presenter(FakePresenterAuthRepository())

        presenter.onLoginEmailChanged("login@example.com")
        presenter.onLoginPasswordChanged("secret")
        presenter.toggleLoginPasswordVisibility()
        presenter.onRegisterUsernameChanged("Wallet User")
        presenter.onRegisterEmailChanged("register@example.com")
        presenter.onRegisterPasswordChanged("123456")
        presenter.onForgotPasswordEmailChanged("reset@example.com")

        assertEquals("login@example.com", presenter.state.value.login.email)
        assertEquals("secret", presenter.state.value.login.password)
        assertTrue(presenter.state.value.login.passwordVisible)
        assertEquals("Wallet User", presenter.state.value.register.username)
        assertEquals("register@example.com", presenter.state.value.register.email)
        assertEquals("123456", presenter.state.value.register.password)
        assertEquals("reset@example.com", presenter.state.value.forgotPassword.email)
    }

    @Test
    fun validationError_isVisibleWithoutCallingRepository() = runTest {
        val repository = FakePresenterAuthRepository()
        val presenter = presenter(repository)

        presenter.submitLogin()

        val error = assertIs<AuthOperationState.ValidationError>(
            presenter.state.value.login.operation
        )
        assertEquals("Email và mật khẩu không được để trống!", error.message)
        assertEquals(0, repository.loginCalls)
        assertNull(presenter.state.value.pendingEvent)
    }

    @Test
    fun loadingStateBlocksRepeatedLoginSubmit() = runTest {
        val repository = FakePresenterAuthRepository().apply {
            loginGate = CompletableDeferred()
        }
        val presenter = presenter(repository)
        validLogin(presenter)

        presenter.submitLogin()
        presenter.submitLogin()
        assertTrue(presenter.state.value.isLoading)
        assertEquals(AuthSubmission.LOGIN, presenter.state.value.activeSubmission)
        runCurrent()
        assertEquals(1, repository.loginCalls)

        repository.loginGate?.complete(Unit)
        runCurrent()
        assertFalse(presenter.state.value.isLoading)
    }

    @Test
    fun loginSuccessEmitsNavigationOnceAndConsumedEventDoesNotReplay() = runTest {
        val repository = FakePresenterAuthRepository()
        var authenticatedCalls = 0
        val presenter = presenter(repository) { authenticatedCalls++ }
        validLogin(presenter)

        presenter.submitLogin()
        runCurrent()

        val event = presenter.state.value.pendingEvent!!
        assertIs<AuthUiEvent.NavigateHome>(event.event)
        assertEquals(1, authenticatedCalls)
        presenter.consumeEvent(event.id)
        assertNull(presenter.state.value.pendingEvent)
        presenter.consumeEvent(event.id)
        assertNull(presenter.state.value.pendingEvent)
    }

    @Test
    fun loginFailureDoesNotNavigate() = runTest {
        val repository = FakePresenterAuthRepository().apply {
            loginResult = failure("Đăng nhập thất bại!")
        }
        val presenter = presenter(repository)
        validLogin(presenter)

        presenter.submitLogin()
        runCurrent()

        assertIs<AuthOperationState.RepositoryError>(presenter.state.value.login.operation)
        assertNull(presenter.state.value.pendingEvent)
    }

    @Test
    fun registerSuccessAndFailureHaveExpectedNavigationBehavior() = runTest {
        val successRepository = FakePresenterAuthRepository()
        val successPresenter = presenter(successRepository)
        validRegister(successPresenter)
        successPresenter.submitRegister()
        runCurrent()
        assertIs<AuthUiEvent.NavigateHome>(successPresenter.state.value.pendingEvent?.event)

        val failedRepository = FakePresenterAuthRepository().apply {
            registerResult = failure("Đăng ký thất bại!")
        }
        val failedPresenter = presenter(failedRepository)
        validRegister(failedPresenter)
        failedPresenter.submitRegister()
        runCurrent()
        assertIs<AuthOperationState.RepositoryError>(failedPresenter.state.value.register.operation)
        assertNull(failedPresenter.state.value.pendingEvent)
    }

    @Test
    fun resetPasswordSuccessAndFailureStayInlineWithoutNavigation() = runTest {
        val successRepository = FakePresenterAuthRepository()
        val successPresenter = presenter(successRepository)
        successPresenter.onForgotPasswordEmailChanged("user@example.com")
        successPresenter.submitResetPassword()
        runCurrent()
        val success = assertIs<AuthOperationState.Success>(
            successPresenter.state.value.forgotPassword.operation
        )
        assertTrue(success.message.orEmpty().startsWith("Đã gửi email"))
        assertNull(successPresenter.state.value.pendingEvent)

        val failedRepository = FakePresenterAuthRepository().apply {
            resetResult = failure("Không thể gửi email khôi phục")
        }
        val failedPresenter = presenter(failedRepository)
        failedPresenter.onForgotPasswordEmailChanged("user@example.com")
        failedPresenter.submitResetPassword()
        runCurrent()
        assertIs<AuthOperationState.RepositoryError>(
            failedPresenter.state.value.forgotPassword.operation
        )
        assertNull(failedPresenter.state.value.pendingEvent)
    }

    @Test
    fun navigationCallbacksEmitLoginRegisterAndForgotEvents() = runTest {
        val presenter = presenter(FakePresenterAuthRepository())

        presenter.navigateToRegister()
        assertIs<AuthUiEvent.NavigateToRegister>(presenter.state.value.pendingEvent?.event)
        presenter.navigateToForgotPassword()
        assertIs<AuthUiEvent.NavigateToForgotPassword>(presenter.state.value.pendingEvent?.event)
        presenter.navigateFromRegisterToLogin()
        assertIs<AuthUiEvent.NavigateToLogin>(presenter.state.value.pendingEvent?.event)
        presenter.navigateFromForgotPasswordToLogin()
        assertIs<AuthUiEvent.NavigateToLogin>(presenter.state.value.pendingEvent?.event)
    }

    private fun TestScope.presenter(
        repository: FakePresenterAuthRepository,
        onAuthenticated: (AuthSession) -> Unit = {}
    ) = AuthUiPresenter(
        scope = this,
        login = LoginUseCase(repository),
        register = RegisterUseCase(repository),
        resetPassword = ResetPasswordUseCase(repository),
        onAuthenticated = onAuthenticated
    )

    private fun validLogin(presenter: AuthUiPresenter) {
        presenter.onLoginEmailChanged("user@example.com")
        presenter.onLoginPasswordChanged("123456")
    }

    private fun validRegister(presenter: AuthUiPresenter) {
        presenter.onRegisterUsernameChanged("Wallet User")
        presenter.onRegisterEmailChanged("user@example.com")
        presenter.onRegisterPasswordChanged("123456")
    }
}

private class FakePresenterAuthRepository : AuthRepository {
    private val session = AuthSession("uid", "user@example.com", "Wallet User")
    private val authState = MutableStateFlow<AuthSession?>(null)
    var loginResult: RepositoryResult<AuthSession> = RepositoryResult.Success(session)
    var registerResult: RepositoryResult<AuthSession> = RepositoryResult.Success(session)
    var resetResult: RepositoryResult<Unit> = RepositoryResult.Success(Unit)
    var loginGate: CompletableDeferred<Unit>? = null
    var loginCalls = 0

    override val currentSession: AuthSession? get() = authState.value
    override fun observeAuthState(): Flow<AuthSession?> = authState
    override suspend fun register(input: RegisterInput): RepositoryResult<AuthSession> = registerResult
    override suspend fun login(input: LoginInput): RepositoryResult<AuthSession> {
        loginCalls++
        loginGate?.await()
        return loginResult
    }
    override suspend fun resetPassword(input: ResetPasswordInput): RepositoryResult<Unit> = resetResult
    override suspend fun changePassword(input: ChangePasswordInput): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)
    override fun logout(): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
}

private fun <T> failure(message: String): RepositoryResult<T> = RepositoryResult.Failure(
    RepositoryError(RepositoryErrorCode.UNKNOWN, message)
)
