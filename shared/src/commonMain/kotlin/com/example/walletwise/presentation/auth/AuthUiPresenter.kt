package com.example.walletwise.presentation.auth

import com.example.walletwise.domain.model.AuthSession
import com.example.walletwise.domain.model.LoginInput
import com.example.walletwise.domain.model.RegisterInput
import com.example.walletwise.domain.model.ResetPasswordInput
import com.example.walletwise.domain.result.AuthUseCaseResult
import com.example.walletwise.domain.usecase.auth.LoginUseCase
import com.example.walletwise.domain.usecase.auth.RegisterUseCase
import com.example.walletwise.domain.usecase.auth.ResetPasswordUseCase
import com.example.walletwise.domain.validation.AuthValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class AuthUiPresenter(
    private val scope: CoroutineScope,
    private val login: LoginUseCase,
    private val register: RegisterUseCase,
    private val resetPassword: ResetPasswordUseCase,
    private val onAuthenticated: (AuthSession) -> Unit
) {
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    private var nextEventId = 0L

    fun onLoginEmailChanged(value: String) {
        _state.value = _state.value.copy(login = _state.value.login.copy(email = value))
    }

    fun onLoginPasswordChanged(value: String) {
        _state.value = _state.value.copy(login = _state.value.login.copy(password = value))
    }

    fun toggleLoginPasswordVisibility() {
        _state.value = _state.value.copy(
            login = _state.value.login.copy(
                passwordVisible = !_state.value.login.passwordVisible
            )
        )
    }

    fun onRegisterUsernameChanged(value: String) {
        _state.value = _state.value.copy(register = _state.value.register.copy(username = value))
    }

    fun onRegisterEmailChanged(value: String) {
        _state.value = _state.value.copy(register = _state.value.register.copy(email = value))
    }

    fun onRegisterPasswordChanged(value: String) {
        _state.value = _state.value.copy(register = _state.value.register.copy(password = value))
    }

    fun onForgotPasswordEmailChanged(value: String) {
        _state.value = _state.value.copy(
            forgotPassword = _state.value.forgotPassword.copy(email = value)
        )
    }

    fun submitLogin() {
        if (_state.value.isLoading) return
        val input = LoginInput(_state.value.login.email, _state.value.login.password)
        AuthValidator.validateLogin(input).exceptionOrNull()?.let { error ->
            setLoginOperation(AuthOperationState.ValidationError(error.message.orEmpty()))
            return
        }

        beginSubmission(AuthSubmission.LOGIN)
        scope.launch {
            try {
                when (val result = withTimeout(AUTH_TIMEOUT_MILLIS) { login(input) }) {
                    is AuthUseCaseResult.Success -> {
                        finishLogin(AuthOperationState.Success())
                        onAuthenticated(result.value)
                        emitEvent(AuthUiEvent.NavigateHome)
                    }
                    is AuthUseCaseResult.ValidationFailure ->
                        finishLogin(AuthOperationState.ValidationError(result.message))
                    is AuthUseCaseResult.RepositoryFailure -> finishLogin(
                        AuthOperationState.RepositoryError(
                            result.error.message.ifBlank { "Đăng nhập thất bại!" }
                        )
                    )
                }
            } catch (_: TimeoutCancellationException) {
                finishLogin(
                    AuthOperationState.RepositoryError(
                        "Mạng yếu hoặc phản hồi từ Firebase quá lâu. Vui lòng thử lại!"
                    )
                )
            } catch (error: Exception) {
                finishLogin(
                    AuthOperationState.RepositoryError(error.message ?: "Đăng nhập thất bại!")
                )
            }
        }
    }

    fun submitRegister() {
        if (_state.value.isLoading) return
        val form = _state.value.register
        val input = RegisterInput(form.email, form.password, form.password, form.username)
        AuthValidator.validateRegister(input).exceptionOrNull()?.let { error ->
            setRegisterOperation(AuthOperationState.ValidationError(error.message.orEmpty()))
            return
        }

        beginSubmission(AuthSubmission.REGISTER)
        scope.launch {
            try {
                when (val result = withTimeout(AUTH_TIMEOUT_MILLIS) { register(input) }) {
                    is AuthUseCaseResult.Success -> {
                        finishRegister(AuthOperationState.Success())
                        onAuthenticated(result.value)
                        emitEvent(AuthUiEvent.NavigateHome)
                    }
                    is AuthUseCaseResult.ValidationFailure ->
                        finishRegister(AuthOperationState.ValidationError(result.message))
                    is AuthUseCaseResult.RepositoryFailure -> finishRegister(
                        AuthOperationState.RepositoryError(
                            result.error.message.ifBlank { "Đăng ký thất bại!" }
                        )
                    )
                }
            } catch (_: TimeoutCancellationException) {
                finishRegister(
                    AuthOperationState.RepositoryError(
                        "Thời gian phản hồi quá lâu. Vui lòng kiểm tra lại mạng!"
                    )
                )
            } catch (error: Exception) {
                finishRegister(
                    AuthOperationState.RepositoryError(error.message ?: "Đăng ký thất bại!")
                )
            }
        }
    }

    fun submitResetPassword() {
        if (_state.value.isLoading) return
        val input = ResetPasswordInput(_state.value.forgotPassword.email)
        AuthValidator.validateResetPassword(input).exceptionOrNull()?.let { error ->
            setForgotPasswordOperation(
                AuthOperationState.ValidationError(error.message.orEmpty())
            )
            return
        }

        beginSubmission(AuthSubmission.RESET_PASSWORD)
        scope.launch {
            try {
                when (val result = resetPassword(input)) {
                    is AuthUseCaseResult.Success -> finishResetPassword(
                        AuthOperationState.Success(
                            "Đã gửi email khôi phục mật khẩu. Vui lòng kiểm tra hộp thư."
                        )
                    )
                    is AuthUseCaseResult.ValidationFailure -> finishResetPassword(
                        AuthOperationState.ValidationError(result.message)
                    )
                    is AuthUseCaseResult.RepositoryFailure -> finishResetPassword(
                        AuthOperationState.RepositoryError(
                            result.error.message.ifBlank { "Không thể gửi email khôi phục" }
                        )
                    )
                }
            } catch (error: Exception) {
                finishResetPassword(
                    AuthOperationState.RepositoryError(
                        error.message ?: "Không thể gửi email khôi phục"
                    )
                )
            }
        }
    }

    fun navigateToRegister() = emitEvent(AuthUiEvent.NavigateToRegister)

    fun navigateToForgotPassword() = emitEvent(AuthUiEvent.NavigateToForgotPassword)

    fun navigateFromRegisterToLogin() = emitEvent(AuthUiEvent.NavigateToLogin)

    fun navigateFromForgotPasswordToLogin() {
        clearMessages()
        emitEvent(AuthUiEvent.NavigateToLogin)
    }

    fun consumeEvent(id: Long) {
        if (_state.value.pendingEvent?.id == id) {
            _state.value = _state.value.copy(pendingEvent = null)
        }
    }

    fun clearMessages() {
        _state.value = _state.value.copy(
            login = _state.value.login.copy(operation = AuthOperationState.Idle),
            register = _state.value.register.copy(operation = AuthOperationState.Idle),
            forgotPassword = _state.value.forgotPassword.copy(operation = AuthOperationState.Idle)
        )
    }

    private fun beginSubmission(submission: AuthSubmission) {
        _state.value = when (submission) {
            AuthSubmission.LOGIN -> _state.value.copy(
                activeSubmission = submission,
                login = _state.value.login.copy(operation = AuthOperationState.Loading)
            )
            AuthSubmission.REGISTER -> _state.value.copy(
                activeSubmission = submission,
                register = _state.value.register.copy(operation = AuthOperationState.Loading)
            )
            AuthSubmission.RESET_PASSWORD -> _state.value.copy(
                activeSubmission = submission,
                forgotPassword = _state.value.forgotPassword.copy(
                    operation = AuthOperationState.Loading
                )
            )
        }
    }

    private fun finishLogin(operation: AuthOperationState) {
        _state.value = _state.value.copy(
            activeSubmission = null,
            login = _state.value.login.copy(operation = operation)
        )
    }

    private fun finishRegister(operation: AuthOperationState) {
        _state.value = _state.value.copy(
            activeSubmission = null,
            register = _state.value.register.copy(operation = operation)
        )
    }

    private fun finishResetPassword(operation: AuthOperationState) {
        _state.value = _state.value.copy(
            activeSubmission = null,
            forgotPassword = _state.value.forgotPassword.copy(operation = operation)
        )
    }

    private fun setLoginOperation(operation: AuthOperationState) {
        _state.value = _state.value.copy(login = _state.value.login.copy(operation = operation))
    }

    private fun setRegisterOperation(operation: AuthOperationState) {
        _state.value = _state.value.copy(
            register = _state.value.register.copy(operation = operation)
        )
    }

    private fun setForgotPasswordOperation(operation: AuthOperationState) {
        _state.value = _state.value.copy(
            forgotPassword = _state.value.forgotPassword.copy(operation = operation)
        )
    }

    private fun emitEvent(event: AuthUiEvent) {
        nextEventId += 1
        _state.value = _state.value.copy(
            pendingEvent = AuthUiEventEnvelope(nextEventId, event)
        )
    }

    private companion object {
        const val AUTH_TIMEOUT_MILLIS = 15_000L
    }
}
