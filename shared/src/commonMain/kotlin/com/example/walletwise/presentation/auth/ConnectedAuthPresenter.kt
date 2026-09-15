package com.example.walletwise.presentation.auth

import com.example.walletwise.domain.model.LoginInput
import com.example.walletwise.domain.model.RegisterInput
import com.example.walletwise.domain.model.ResetPasswordInput
import com.example.walletwise.domain.repository.AuthCancellation
import com.example.walletwise.domain.repository.AuthCompletion
import com.example.walletwise.domain.repository.AuthFailure
import com.example.walletwise.domain.repository.AuthIdentity
import com.example.walletwise.domain.repository.AuthStateObserver
import com.example.walletwise.domain.repository.CallbackAuthService
import com.example.walletwise.domain.validation.AuthValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ConnectedAuthUiState(
    val route: AuthRoute = AuthRoute.LOGIN,
    val auth: AuthUiState = AuthUiState(),
    val identity: AuthIdentity? = null,
    val sessionReady: Boolean = false,
    val sessionOperation: AuthOperationState = AuthOperationState.Idle
) {
    val isAuthenticated: Boolean get() = identity != null
}

/** Auth-only host; the existing Android presenter/profile flow is intentionally separate. */
class ConnectedAuthPresenter(private val service: CallbackAuthService?) {
    private val mutableState = MutableStateFlow(ConnectedAuthUiState(sessionReady = service == null))
    val state: StateFlow<ConnectedAuthUiState> = mutableState.asStateFlow()
    val snapshot: ConnectedAuthUiState get() = state.value
    private var disposed = false
    private var generation = 0L
    private var pending: AuthCancellation? = null
    private var observation: AuthCancellation? = null

    init {
        observation = service?.observe(object : AuthStateObserver {
            override fun changed(identity: AuthIdentity?) = acceptSession(identity)
        })
    }

    fun navigateTo(route: AuthRoute) {
        if (disposed || snapshot.isAuthenticated) return
        cancelSubmission()
        mutableState.value = snapshot.copy(route = route, auth = AuthUiState())
    }

    fun onEmailChanged(value: String) = edit {
        when (snapshot.route) {
            AuthRoute.LOGIN -> it.copy(login = it.login.copy(email = value, operation = AuthOperationState.Idle))
            AuthRoute.REGISTER -> it.copy(register = it.register.copy(email = value, operation = AuthOperationState.Idle))
            AuthRoute.FORGOT_PASSWORD -> it.copy(forgotPassword = it.forgotPassword.copy(email = value, operation = AuthOperationState.Idle))
        }
    }

    fun onPasswordChanged(value: String) = edit {
        when (snapshot.route) {
            AuthRoute.LOGIN -> it.copy(login = it.login.copy(password = value, operation = AuthOperationState.Idle))
            AuthRoute.REGISTER -> it.copy(register = it.register.copy(password = value, operation = AuthOperationState.Idle))
            AuthRoute.FORGOT_PASSWORD -> it
        }
    }

    fun onUsernameChanged(value: String) = edit {
        if (snapshot.route == AuthRoute.REGISTER) {
            it.copy(register = it.register.copy(username = value, operation = AuthOperationState.Idle))
        } else it
    }

    fun togglePasswordVisibility() = edit {
        if (snapshot.route == AuthRoute.LOGIN) it.copy(login = it.login.copy(passwordVisible = !it.login.passwordVisible)) else it
    }

    fun submit() {
        if (disposed || !snapshot.sessionReady || snapshot.isAuthenticated || snapshot.auth.isLoading) return
        val route = snapshot.route
        val form = snapshot.auth
        val validatedEmail: String
        val password: String
        val submission: AuthSubmission
        val validation = when (route) {
            AuthRoute.LOGIN -> AuthValidator.validateLogin(LoginInput(form.login.email, form.login.password)).map { it.email }
            AuthRoute.REGISTER -> AuthValidator.validateRegister(RegisterInput(
                form.register.email, form.register.password, form.register.password, form.register.username
            )).map { it.email }
            AuthRoute.FORGOT_PASSWORD -> AuthValidator.validateResetPassword(ResetPasswordInput(form.forgotPassword.email)).map { it.email }
        }
        validatedEmail = validation.getOrElse {
            setOperation(route, AuthOperationState.ValidationError(it.message.orEmpty()))
            return
        }
        password = when (route) {
            AuthRoute.LOGIN -> form.login.password
            AuthRoute.REGISTER -> form.register.password
            AuthRoute.FORGOT_PASSWORD -> ""
        }
        submission = when (route) {
            AuthRoute.LOGIN -> AuthSubmission.LOGIN
            AuthRoute.REGISTER -> AuthSubmission.REGISTER
            AuthRoute.FORGOT_PASSWORD -> AuthSubmission.RESET_PASSWORD
        }
        val gateway = service ?: run {
            setOperation(route, AuthOperationState.RepositoryError(AuthFailure.NOT_CONNECTED.message()))
            return
        }
        val request = ++generation
        mutableState.value = snapshot.copy(auth = snapshot.auth.copy(activeSubmission = submission))
        setOperation(route, AuthOperationState.Loading)
        val completion = object : AuthCompletion {
            override fun complete(identity: AuthIdentity?, failure: AuthFailure?) {
                if (disposed || request != generation) return
                // A callback for an earlier account must never replace a newer SDK session.
                if (identity != null && snapshot.identity != null && snapshot.identity?.subject != identity.subject) return
                generation++ // Ignore duplicate completions, including synchronous implementations.
                pending = null
                mutableState.value = snapshot.copy(auth = snapshot.auth.copy(activeSubmission = null))
                when {
                    failure != null -> setOperation(route, AuthOperationState.RepositoryError(failure.message()))
                    route == AuthRoute.FORGOT_PASSWORD -> setOperation(route, AuthOperationState.Success("Yêu cầu đặt lại mật khẩu đã được chấp nhận."))
                    identity != null -> {
                        acceptSession(identity)
                        mutableState.value = snapshot.copy(sessionOperation = AuthOperationState.Success())
                    }
                    else -> setOperation(route, AuthOperationState.RepositoryError(AuthFailure.UNKNOWN.message()))
                }
            }
        }
        val cancellation = when (route) {
            AuthRoute.LOGIN -> gateway.login(validatedEmail, password, completion)
            AuthRoute.REGISTER -> gateway.register(validatedEmail, password, completion)
            AuthRoute.FORGOT_PASSWORD -> gateway.resetPassword(validatedEmail, completion)
        }
        if (request == generation && !disposed) pending = cancellation else cancellation.cancel()
    }

    fun logout() {
        if (disposed || !snapshot.isAuthenticated) return
        cancelSubmission()
        val failure = service?.logout()
        if (failure == null) {
            mutableState.value = ConnectedAuthUiState(sessionReady = true)
        } else {
            mutableState.value = snapshot.copy(sessionOperation = AuthOperationState.RepositoryError(failure.message()))
        }
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        cancelSubmission()
        observation?.cancel()
        observation = null
        // Remove form credentials even when a native request cannot be undone.
        mutableState.value = ConnectedAuthUiState()
    }

    private fun acceptSession(identity: AuthIdentity?) {
        if (disposed) return
        val previous = snapshot.identity
        val changed = previous?.subject != identity?.subject
        if (changed && previous != null) cancelSubmission()
        mutableState.value = snapshot.copy(
            identity = identity,
            sessionReady = true,
            route = if (changed) AuthRoute.LOGIN else snapshot.route,
            auth = if (changed) AuthUiState(activeSubmission = snapshot.auth.activeSubmission) else snapshot.auth,
            sessionOperation = if (changed) AuthOperationState.Idle else snapshot.sessionOperation
        )
    }

    private fun cancelSubmission() {
        generation++
        pending?.cancel()
        pending = null
        mutableState.value = snapshot.copy(auth = snapshot.auth.copy(activeSubmission = null))
    }

    private inline fun edit(transform: (AuthUiState) -> AuthUiState) {
        if (!disposed && !snapshot.isAuthenticated && !snapshot.auth.isLoading) {
            mutableState.value = snapshot.copy(auth = transform(snapshot.auth))
        }
    }

    private fun setOperation(route: AuthRoute, operation: AuthOperationState) {
        val auth = snapshot.auth
        mutableState.value = snapshot.copy(auth = when (route) {
            AuthRoute.LOGIN -> auth.copy(login = auth.login.copy(operation = operation))
            AuthRoute.REGISTER -> auth.copy(register = auth.register.copy(operation = operation))
            AuthRoute.FORGOT_PASSWORD -> auth.copy(forgotPassword = auth.forgotPassword.copy(operation = operation))
        })
    }
}

private fun AuthFailure.message(): String = when (this) {
    AuthFailure.INVALID_CREDENTIALS -> "Email hoặc mật khẩu không đúng."
    AuthFailure.EMAIL_ALREADY_USED -> "Không thể đăng ký tài khoản với email này."
    AuthFailure.WEAK_PASSWORD -> "Mật khẩu phải có ít nhất 6 ký tự."
    AuthFailure.NETWORK -> "Không thể kết nối dịch vụ xác thực. Vui lòng thử lại."
    AuthFailure.TOO_MANY_REQUESTS -> "Có quá nhiều yêu cầu. Vui lòng thử lại sau."
    AuthFailure.NOT_CONNECTED -> "Dịch vụ xác thực chưa được kết nối."
    AuthFailure.REQUEST_IN_PROGRESS -> "Một yêu cầu xác thực đang được xử lý."
    AuthFailure.UNKNOWN -> "Không thể hoàn tất xác thực. Vui lòng thử lại."
}
