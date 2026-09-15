package com.example.walletwise.presentation.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UnconnectedAuthPresenterTest {
    @Test
    fun defaultRouteIsLoginWithEmptyForm() {
        val presenter = UnconnectedAuthPresenter()

        assertEquals(AuthRoute.LOGIN, presenter.state.value.route)
        assertEquals(AuthUiState(), presenter.state.value.auth)
    }

    @Test
    fun loginRegisterLoginUsesLocalRoutesWithoutNavigationEvents() {
        val presenter = UnconnectedAuthPresenter()

        presenter.navigateTo(AuthRoute.REGISTER)
        assertEquals(AuthRoute.REGISTER, presenter.state.value.route)
        assertNull(presenter.state.value.auth.pendingEvent)
        presenter.navigateTo(AuthRoute.LOGIN)
        assertEquals(AuthRoute.LOGIN, presenter.state.value.route)
        assertNull(presenter.state.value.auth.pendingEvent)
    }

    @Test
    fun loginForgotPasswordLoginUsesLocalRoutesWithoutNavigationEvents() {
        val presenter = UnconnectedAuthPresenter()

        presenter.navigateTo(AuthRoute.FORGOT_PASSWORD)
        assertEquals(AuthRoute.FORGOT_PASSWORD, presenter.state.value.route)
        assertNull(presenter.state.value.auth.pendingEvent)
        presenter.navigateTo(AuthRoute.LOGIN)
        assertEquals(AuthRoute.LOGIN, presenter.state.value.route)
        assertNull(presenter.state.value.auth.pendingEvent)
    }

    @Test
    fun validSubmissionsOnEveryRouteRemainUnconnectedAndDoNotNavigate() {
        val presenter = UnconnectedAuthPresenter()

        for (route in AuthRoute.entries) {
            presenter.navigateTo(route)
            presenter.onUsernameChanged("Wallet User")
            presenter.onEmailChanged("wallet@example.com")
            presenter.onPasswordChanged("valid-password")
            presenter.submit()
            presenter.submit()

            val state = presenter.state.value
            val operation = when (route) {
                AuthRoute.LOGIN -> state.auth.login.operation
                AuthRoute.REGISTER -> state.auth.register.operation
                AuthRoute.FORGOT_PASSWORD -> state.auth.forgotPassword.operation
            }
            assertTrue(assertIs<AuthOperationState.RepositoryError>(operation).message.isNotBlank())
            assertEquals(route, state.route)
            assertFalse(state.auth.isLoading)
            assertNull(state.auth.pendingEvent)
        }
    }

    @Test
    fun routeChangesClearCredentialsVisibilityAndMessages() {
        val presenter = UnconnectedAuthPresenter()
        presenter.onEmailChanged("login@example.com")
        presenter.onPasswordChanged("login-password")
        presenter.togglePasswordVisibility()
        presenter.submit()
        assertTrue(presenter.state.value.auth.login.passwordVisible)

        presenter.navigateTo(AuthRoute.REGISTER)
        assertEquals(AuthUiState(), presenter.state.value.auth)
        presenter.onUsernameChanged("Wallet User")
        presenter.onEmailChanged("register@example.com")
        presenter.onPasswordChanged("register-password")
        presenter.submit()

        presenter.navigateTo(AuthRoute.FORGOT_PASSWORD)
        assertEquals(AuthUiState(), presenter.state.value.auth)
        presenter.onEmailChanged("reset@example.com")
        presenter.submit()

        presenter.navigateTo(AuthRoute.LOGIN)
        assertEquals(AuthUiState(), presenter.state.value.auth)
    }

    @Test
    fun inactiveFormControlsCannotPopulateOtherRoutes() {
        val presenter = UnconnectedAuthPresenter()
        presenter.onUsernameChanged("Not a login field")
        assertEquals(RegisterUiState(), presenter.state.value.auth.register)

        presenter.navigateTo(AuthRoute.FORGOT_PASSWORD)
        presenter.onPasswordChanged("Not a reset field")
        presenter.onUsernameChanged("Not a reset field")
        presenter.togglePasswordVisibility()
        assertEquals(AuthUiState(), presenter.state.value.auth)
    }
}
