package com.example.walletwise.presentation.auth

import com.example.walletwise.domain.model.AuthUser
import com.example.walletwise.domain.repository.AuthCancellation
import com.example.walletwise.domain.repository.AuthCompletion
import com.example.walletwise.domain.repository.AuthFailure
import com.example.walletwise.domain.repository.AuthRegistrationProgress
import com.example.walletwise.domain.repository.AuthStateObserver
import com.example.walletwise.domain.repository.CallbackAuthService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConnectedAuthUserSessionTest {
    private fun user(uid: String = "A", name: String? = "Name A", email: String? = "A@example.invalid") = AuthUser.create(uid, email, name, true)!!
    private class Service : CallbackAuthService {
        val observers = mutableListOf<AuthStateObserver>()
        lateinit var completion: AuthCompletion
        var displayName = ""
        var failure: AuthFailure? = null
        var beforeLogout: (() -> Unit)? = null
        override fun observe(observer: AuthStateObserver): AuthCancellation {
            observers += observer
            observer.changed(null)
            return object : AuthCancellation { override fun cancel() {} }
        }
        override fun login(email: String, password: String, completion: AuthCompletion): AuthCancellation {
            this.completion = completion
            return object : AuthCancellation { override fun cancel() {} }
        }
        override fun register(email: String, password: String, displayName: String, completion: AuthCompletion): AuthCancellation {
            this.displayName = displayName
            return login(email, password, completion)
        }
        override fun resetPassword(email: String, completion: AuthCompletion) = login(email, "", completion)
        override fun logout(): AuthFailure? { beforeLogout?.invoke(); return failure }
    }
    private fun ConnectedAuthPresenter.fillRegister() {
        navigateTo(AuthRoute.REGISTER)
        onEmailChanged("A@example.invalid")
        onUsernameChanged(" Name A ")
        onPasswordChanged("temporary-password")
    }

    @Test fun listenerRestoresFullUserWithoutAnyCompletionOrRpc() {
        val service = Service()
        val presenter = ConnectedAuthPresenter(service)
        service.observers.single().changed(user())
        assertEquals(user(), presenter.snapshot.user)
        assertTrue(presenter.snapshot.isAuthenticated)
        assertEquals("Name A", presenter.snapshot.user?.displayLabel)
    }

    @Test fun successfulCompletionCannotEstablishSessionWithoutListener() {
        val service = Service()
        val presenter = ConnectedAuthPresenter(service)
        presenter.onEmailChanged("A@example.invalid")
        presenter.onPasswordChanged("temporary-password")
        presenter.submit()
        service.completion.complete(user(), null)
        assertNull(presenter.snapshot.user)
        assertFalse(presenter.snapshot.isAuthenticated)
        assertIs<AuthOperationState.Loading>(presenter.snapshot.sessionOperation)
        assertEquals(AuthUiState(), presenter.snapshot.auth)
        service.observers.single().changed(user())
        assertTrue(presenter.snapshot.isAuthenticated)
        assertIs<AuthOperationState.Success>(presenter.snapshot.sessionOperation)
    }

    @Test fun registerWaitsForSequenceAndFreshListenerMetadataBeforeSuccess() {
        val service = Service()
        val presenter = ConnectedAuthPresenter(service)
        presenter.fillRegister()
        presenter.submit()
        assertEquals("Name A", service.displayName)
        val sequence = AuthRegistrationProgress(service.displayName, service.completion)
        service.observers.single().changed(user(name = null))
        sequence.created(user(name = null), null)
        assertFalse(presenter.snapshot.isAuthenticated)
        assertTrue(presenter.snapshot.auth.isLoading)
        assertEquals("", presenter.snapshot.auth.register.password)
        sequence.profileUpdated(null)
        assertFalse(presenter.snapshot.isAuthenticated)
        sequence.reloaded(user(), null)
        assertFalse(presenter.snapshot.isAuthenticated)
        assertIs<AuthOperationState.Loading>(presenter.snapshot.sessionOperation)
        service.observers.single().changed(user())
        assertTrue(presenter.snapshot.isAuthenticated)
        assertIs<AuthOperationState.Success>(presenter.snapshot.sessionOperation)
        assertEquals(AuthUiState(), presenter.snapshot.auth)
    }

    @Test fun partialProfileFailureRetainsActualAccountButReportsIncompleteRegistration() {
        val service = Service()
        val presenter = ConnectedAuthPresenter(service)
        presenter.fillRegister()
        presenter.submit()
        val sequence = AuthRegistrationProgress(service.displayName, service.completion)
        sequence.created(user(name = null), null)
        sequence.profileUpdated(AuthFailure.NETWORK)
        assertFalse(presenter.snapshot.isAuthenticated)
        assertTrue(presenter.snapshot.registrationIncomplete)
        service.observers.single().changed(user(name = null))
        assertTrue(presenter.snapshot.isAuthenticated)
        assertTrue(presenter.snapshot.registrationIncomplete)
        assertEquals("A@example.invalid", presenter.snapshot.user?.displayLabel)
        assertIs<AuthOperationState.RepositoryError>(presenter.snapshot.sessionOperation)
        assertEquals("", presenter.snapshot.auth.register.password)
        assertNull(presenter.snapshot.auth.pendingEvent)
    }

    @Test fun logoutClearsOnlyAfterServiceHasCompletedSuccessfully() {
        val service = Service()
        val presenter = ConnectedAuthPresenter(service)
        service.observers.single().changed(user())
        service.beforeLogout = { assertTrue(presenter.snapshot.isAuthenticated); assertEquals(user(), presenter.snapshot.user) }
        presenter.logout()
        assertNull(presenter.snapshot.session)
        assertEquals(AuthUiState(), presenter.snapshot.auth)
        assertFalse(presenter.snapshot.registrationIncomplete)
    }

    @Test fun failedLogoutKeepsSdkUserAndSafeError() {
        val service = Service().apply { failure = AuthFailure.NETWORK }
        val presenter = ConnectedAuthPresenter(service)
        service.observers.single().changed(user())
        presenter.logout()
        assertEquals(user(), presenter.snapshot.user)
        assertIs<AuthOperationState.RepositoryError>(presenter.snapshot.sessionOperation)
    }

    @Test fun userAToLogoutToUserBClearsAllDisplayAndFormState() {
        val service = Service()
        val presenter = ConnectedAuthPresenter(service)
        service.observers.single().changed(user())
        presenter.logout()
        presenter.fillRegister()
        presenter.submit()
        service.completion.complete(null, AuthFailure.NETWORK)
        service.observers.single().changed(user("B", null, "B@example.invalid"))
        assertEquals("B@example.invalid", presenter.snapshot.user?.displayLabel)
        assertNull(presenter.snapshot.user?.displayName)
        assertEquals(AuthUiState(), presenter.snapshot.auth)
        assertIs<AuthOperationState.Idle>(presenter.snapshot.sessionOperation)
        assertFalse(presenter.snapshot.registrationIncomplete)
    }

    @Test fun disposedOwnerCallbacksCannotUpdateReplacementOwner() {
        val service = Service()
        val first = AuthControllerSession(service)
        first.presenter.fillRegister()
        first.presenter.submit()
        first.dispose()
        val replacement = AuthControllerSession(service)
        val original = replacement.presenter.snapshot
        service.completion.complete(user(), null)
        service.observers[0].changed(user())
        assertEquals(original, replacement.presenter.snapshot)
        assertNull(first.presenter.snapshot.user)
        service.observers[1].changed(user("B", null, "B@example.invalid"))
        assertEquals("B", replacement.presenter.snapshot.user?.uid)
        replacement.dispose()
        replacement.dispose()
    }
}
