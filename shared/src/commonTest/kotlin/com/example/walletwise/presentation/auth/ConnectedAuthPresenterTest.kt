package com.example.walletwise.presentation.auth

import com.example.walletwise.domain.repository.AuthCancellation
import com.example.walletwise.domain.repository.AuthCompletion
import com.example.walletwise.domain.repository.AuthFailure
import com.example.walletwise.domain.model.AuthUser
import com.example.walletwise.domain.repository.AuthStateObserver
import com.example.walletwise.domain.repository.CallbackAuthService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConnectedAuthPresenterTest {
    private fun user(uid: String) = AuthUser.create(uid, null, null, false)!!
    private class Cancellation : AuthCancellation {
        var cancelled = false
        override fun cancel() { cancelled = true }
    }

    private class FakeAuth : CallbackAuthService {
        lateinit var observer: AuthStateObserver
        val observation = Cancellation()
        val requests = mutableListOf<String>()
        val completions = mutableListOf<AuthCompletion>()
        val cancellations = mutableListOf<Cancellation>()
        var submittedEmail = ""
        var submittedPassword = ""
        var logoutFailure: AuthFailure? = null
        override fun observe(observer: AuthStateObserver): AuthCancellation {
            this.observer = observer
            observer.changed(null)
            return observation
        }
        private fun request(kind: String, email: String, password: String, completion: AuthCompletion): AuthCancellation {
            requests += kind
            submittedEmail = email
            submittedPassword = password
            completions += completion
            return Cancellation().also { cancellations += it }
        }
        override fun login(email: String, password: String, completion: AuthCompletion) = request("login", email, password, completion)
        override fun register(email: String, password: String, displayName: String, completion: AuthCompletion) = request("register", email, password, completion)
        override fun resetPassword(email: String, completion: AuthCompletion) = request("reset", email, "", completion)
        override fun logout() = logoutFailure
    }

    private fun ConnectedAuthPresenter.fill(route: AuthRoute = AuthRoute.LOGIN, password: String = "test-password") {
        navigateTo(route)
        onUsernameChanged("Test member")
        onEmailChanged("member@example.invalid")
        onPasswordChanged(password)
    }

    @Test fun startsAtLoginWithNoSessionOrNavigation() {
        val presenter = ConnectedAuthPresenter(FakeAuth())
        assertEquals(AuthRoute.LOGIN, presenter.snapshot.route)
        assertTrue(presenter.snapshot.sessionReady)
        assertFalse(presenter.snapshot.isAuthenticated)
        assertNull(presenter.snapshot.auth.pendingEvent)
    }

    @Test fun invalidEmailDoesNotReachServiceForAnyRoute() {
        for (route in AuthRoute.entries) {
            val auth = FakeAuth()
            val presenter = ConnectedAuthPresenter(auth)
            presenter.fill(route)
            presenter.onEmailChanged("invalid")
            presenter.submit()
            assertTrue(auth.requests.isEmpty())
            assertIs<AuthOperationState.ValidationError>(presenter.operation())
        }
    }

    @Test fun requiredLoginPasswordAndShortRegisterPasswordAreRejected() {
        for ((route, password) in listOf(AuthRoute.LOGIN to "", AuthRoute.REGISTER to "short")) {
            val auth = FakeAuth()
            val presenter = ConnectedAuthPresenter(auth)
            presenter.fill(route, password)
            presenter.submit()
            assertTrue(auth.requests.isEmpty())
            assertIs<AuthOperationState.ValidationError>(presenter.operation())
        }
    }

    @Test fun registrationRequiresUsername() {
        val auth = FakeAuth()
        val presenter = ConnectedAuthPresenter(auth)
        presenter.fill(AuthRoute.REGISTER)
        presenter.onUsernameChanged("")
        presenter.submit()
        assertTrue(auth.requests.isEmpty())
        assertIs<AuthOperationState.ValidationError>(presenter.operation())
    }

    @Test fun emailIsNormalizedButPasswordIsSentVerbatim() {
        val auth = FakeAuth()
        val presenter = ConnectedAuthPresenter(auth)
        presenter.fill(password = " test-password ")
        presenter.onEmailChanged(" member@example.invalid ")
        presenter.submit()
        assertEquals("member@example.invalid", auth.submittedEmail)
        assertEquals(" test-password ", auth.submittedPassword)
    }

    @Test fun loadingBlocksDoubleSubmitAndEditsForEveryRequest() {
        for (route in AuthRoute.entries) {
            val auth = FakeAuth()
            val presenter = ConnectedAuthPresenter(auth)
            presenter.fill(route)
            presenter.submit()
            val loading = presenter.snapshot
            presenter.submit()
            presenter.onEmailChanged("other@example.invalid")
            presenter.onPasswordChanged("other-password")
            assertEquals(1, auth.requests.size)
            assertEquals(loading, presenter.snapshot)
            assertTrue(presenter.snapshot.auth.isLoading)
            assertIs<AuthOperationState.Loading>(presenter.operation())
        }
    }

    @Test fun loginSuccessRequiresAnIdentityAndClearsCredentialsWithoutHomeEvent() {
        val auth = FakeAuth()
        val presenter = ConnectedAuthPresenter(auth)
        presenter.fill()
        presenter.submit()
        assertFalse(presenter.snapshot.isAuthenticated)
        auth.observer.changed(user("first"))
        auth.completions.single().complete(user("first"), null)
        assertTrue(presenter.snapshot.isAuthenticated)
        assertEquals(AuthUiState(), presenter.snapshot.auth)
        assertIs<AuthOperationState.Success>(presenter.snapshot.sessionOperation)
        assertNull(presenter.snapshot.auth.pendingEvent)
    }

    @Test fun loginFailureDoesNotAuthenticateAndUsesStableMessage() {
        val auth = FakeAuth()
        val presenter = ConnectedAuthPresenter(auth)
        presenter.fill()
        presenter.submit()
        auth.completions.single().complete(null, AuthFailure.INVALID_CREDENTIALS)
        assertFalse(presenter.snapshot.isAuthenticated)
        assertFalse(presenter.snapshot.auth.isLoading)
        assertEquals(AuthOperationState.RepositoryError("Email hoặc mật khẩu không đúng."), presenter.operation())
        assertNull(presenter.snapshot.auth.pendingEvent)
    }

    @Test fun registerSuccessComesOnlyFromService() {
        val auth = FakeAuth()
        val presenter = ConnectedAuthPresenter(auth)
        presenter.fill(AuthRoute.REGISTER)
        presenter.submit()
        assertFalse(presenter.snapshot.isAuthenticated)
        auth.observer.changed(user("created"))
        auth.completions.single().complete(user("created"), null)
        assertTrue(presenter.snapshot.isAuthenticated)
        assertEquals("created", presenter.snapshot.user?.uid)
        assertEquals(AuthUiState(), presenter.snapshot.auth)
        assertNull(presenter.snapshot.auth.pendingEvent)
    }

    @Test fun registerFailureDoesNotAuthenticate() {
        val auth = FakeAuth()
        val presenter = ConnectedAuthPresenter(auth)
        presenter.fill(AuthRoute.REGISTER)
        presenter.submit()
        auth.completions.single().complete(null, AuthFailure.EMAIL_ALREADY_USED)
        assertFalse(presenter.snapshot.isAuthenticated)
        assertIs<AuthOperationState.RepositoryError>(presenter.operation())
    }

    @Test fun resetSuccessMeansAcceptanceWithoutAnAuthenticatedSession() {
        val auth = FakeAuth()
        val presenter = ConnectedAuthPresenter(auth)
        presenter.fill(AuthRoute.FORGOT_PASSWORD)
        presenter.submit()
        auth.completions.single().complete(null, null)
        assertIs<AuthOperationState.Success>(presenter.operation())
        assertFalse(presenter.snapshot.isAuthenticated)
        assertNull(presenter.snapshot.auth.pendingEvent)
    }

    @Test fun resetFailureDoesNotReportSuccess() {
        val auth = FakeAuth()
        val presenter = ConnectedAuthPresenter(auth)
        presenter.fill(AuthRoute.FORGOT_PASSWORD)
        presenter.submit()
        auth.completions.single().complete(null, AuthFailure.NETWORK)
        assertIs<AuthOperationState.RepositoryError>(presenter.operation())
        assertFalse(presenter.snapshot.isAuthenticated)
    }

    @Test fun loginOrRegisterCompletionWithoutUserIsNeverSuccess() {
        for (route in listOf(AuthRoute.LOGIN, AuthRoute.REGISTER)) {
            val auth = FakeAuth()
            val presenter = ConnectedAuthPresenter(auth)
            presenter.fill(route)
            presenter.submit()
            auth.completions.single().complete(null, null)
            assertFalse(presenter.snapshot.isAuthenticated)
            assertIs<AuthOperationState.RepositoryError>(presenter.operation())
        }
    }

    @Test fun everyRouteChangeClearsFormsPasswordVisibilityAndErrors() {
        val auth = FakeAuth()
        val presenter = ConnectedAuthPresenter(auth)
        for (route in AuthRoute.entries) {
            presenter.fill(route)
            presenter.togglePasswordVisibility()
            presenter.submit()
            auth.completions.last().complete(null, AuthFailure.NETWORK)
            for (destination in AuthRoute.entries) {
                presenter.navigateTo(destination)
                assertEquals(AuthUiState(), presenter.snapshot.auth)
                assertEquals(destination, presenter.snapshot.route)
            }
        }
    }

    @Test fun logoutClearsSessionAndAllForms() {
        val auth = FakeAuth()
        val presenter = ConnectedAuthPresenter(auth)
        auth.observer.changed(user("first"))
        presenter.logout()
        assertFalse(presenter.snapshot.isAuthenticated)
        assertEquals(AuthUiState(), presenter.snapshot.auth)
        assertEquals(AuthRoute.LOGIN, presenter.snapshot.route)
    }

    @Test fun logoutFailureRetainsTheRealSessionAndReportsSafeError() {
        val auth = FakeAuth().apply { logoutFailure = AuthFailure.UNKNOWN }
        val presenter = ConnectedAuthPresenter(auth)
        auth.observer.changed(user("first"))
        presenter.logout()
        assertTrue(presenter.snapshot.isAuthenticated)
        assertIs<AuthOperationState.RepositoryError>(presenter.snapshot.sessionOperation)
    }

    @Test fun sessionsDoNotLeakFormsErrorsOrUserIdentity() {
        val auth = FakeAuth()
        val presenter = ConnectedAuthPresenter(auth)
        presenter.fill()
        auth.observer.changed(user("first"))
        assertEquals(AuthUiState(), presenter.snapshot.auth)
        presenter.logout()
        presenter.fill(AuthRoute.REGISTER)
        auth.observer.changed(user("second"))
        assertEquals("second", presenter.snapshot.user?.uid)
        assertEquals(AuthUiState(), presenter.snapshot.auth)
        auth.observer.changed(null)
        assertNull(presenter.snapshot.user)
        assertEquals(AuthUiState(), presenter.snapshot.auth)
    }

    @Test fun cancelledRouteRequestCannotChangeNewRouteState() {
        val auth = FakeAuth()
        val presenter = ConnectedAuthPresenter(auth)
        presenter.fill()
        presenter.submit()
        presenter.navigateTo(AuthRoute.REGISTER)
        val next = presenter.snapshot
        assertTrue(auth.cancellations.single().cancelled)
        auth.completions.single().complete(user("late"), null)
        auth.completions.single().complete(null, AuthFailure.UNKNOWN)
        assertEquals(next, presenter.snapshot)
    }

    @Test fun disposedPresenterIgnoresLateRequestAndObserverCallbacks() {
        val auth = FakeAuth()
        val presenter = ConnectedAuthPresenter(auth)
        presenter.fill()
        presenter.submit()
        presenter.dispose()
        val disposed = presenter.snapshot
        assertTrue(auth.observation.cancelled)
        assertTrue(auth.cancellations.single().cancelled)
        auth.completions.single().complete(user("late"), null)
        auth.observer.changed(user("late"))
        assertEquals(disposed, presenter.snapshot)
        assertEquals(AuthUiState(), presenter.snapshot.auth)
    }

    @Test fun duplicateCompletionCannotChangeFirstOutcome() {
        val auth = FakeAuth()
        val presenter = ConnectedAuthPresenter(auth)
        presenter.fill()
        presenter.submit()
        auth.completions.single().complete(null, AuthFailure.INVALID_CREDENTIALS)
        val failed = presenter.snapshot
        auth.completions.single().complete(user("duplicate"), null)
        assertEquals(failed, presenter.snapshot)
    }

    @Test fun oldAccountCompletionCannotReplaceNewAccount() {
        val auth = FakeAuth()
        val presenter = ConnectedAuthPresenter(auth)
        presenter.fill()
        presenter.submit()
        auth.observer.changed(user("first"))
        auth.observer.changed(user("second"))
        val second = presenter.snapshot
        auth.completions.single().complete(user("first"), null)
        assertEquals(second, presenter.snapshot)
        assertEquals("second", presenter.snapshot.user?.uid)
    }

    @Test fun unavailableAdapterFailsClosed() {
        val presenter = ConnectedAuthPresenter(null)
        presenter.fill()
        presenter.submit()
        assertFalse(presenter.snapshot.isAuthenticated)
        assertEquals(AuthOperationState.RepositoryError("Dịch vụ xác thực chưa được kết nối."), presenter.operation())
    }

    @Test fun userStringRepresentationIsRedacted() {
        assertEquals("AuthUser(redacted)", user("private-subject").toString())
    }

    private fun ConnectedAuthPresenter.operation(): AuthOperationState = when (snapshot.route) {
        AuthRoute.LOGIN -> snapshot.auth.login.operation
        AuthRoute.REGISTER -> snapshot.auth.register.operation
        AuthRoute.FORGOT_PASSWORD -> snapshot.auth.forgotPassword.operation
    }
}
