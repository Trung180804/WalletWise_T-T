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
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class AuthControllerSessionTest {
    private fun user(uid: String) = AuthUser.create(uid, null, null, false)!!
    private class FakeService : CallbackAuthService {
        val observers = mutableListOf<AuthStateObserver>()
        var observerCancellations = 0
        var requestCancellations = 0
        var logoutCalls = 0
        var completion: AuthCompletion? = null
        override fun observe(observer: AuthStateObserver): AuthCancellation {
            observers += observer
            observer.changed(null)
            return object : AuthCancellation {
                override fun cancel() { observerCancellations++ }
            }
        }
        override fun login(email: String, password: String, completion: AuthCompletion): AuthCancellation {
            this.completion = completion
            return object : AuthCancellation {
                override fun cancel() { requestCancellations++ }
            }
        }
        override fun register(email: String, password: String, displayName: String, completion: AuthCompletion) = login(email, password, completion)
        override fun resetPassword(email: String, completion: AuthCompletion) = login(email, "", completion)
        override fun logout(): AuthFailure? { logoutCalls++; return null }
    }

    @Test fun twoControllerOwnersHaveIndependentFormsAndRoutes() {
        val service = FakeService()
        val first = AuthControllerSession(service)
        val second = AuthControllerSession(service)
        assertNotSame(first.presenter, second.presenter)
        first.presenter.onPasswordChanged("first-private-form")
        first.presenter.navigateTo(AuthRoute.REGISTER)
        first.presenter.onPasswordChanged("register-private-form")
        second.presenter.onEmailChanged("second@example.invalid")
        assertEquals(AuthRoute.LOGIN, second.presenter.snapshot.route)
        assertEquals("", second.presenter.snapshot.auth.login.password)
        assertEquals("", first.presenter.snapshot.auth.register.email)
        assertEquals("second@example.invalid", second.presenter.snapshot.auth.login.email)
    }

    @Test fun disposingFirstControllerLeavesSecondObserverAndStateUsable() {
        val service = FakeService()
        val first = AuthControllerSession(service)
        val second = AuthControllerSession(service)
        second.presenter.onPasswordChanged("second-form")
        first.dispose()
        assertEquals("second-form", second.presenter.snapshot.auth.login.password)
        second.presenter.onPasswordChanged("still-editable")
        assertEquals("still-editable", second.presenter.snapshot.auth.login.password)
        service.observers[1].changed(user("current"))
        assertTrue(second.presenter.snapshot.isAuthenticated)
        assertFalse(first.presenter.snapshot.isAuthenticated)
        assertEquals(1, service.observerCancellations)
        second.dispose()
    }

    @Test fun newControllerNeverReusesDisposedPresenter() {
        val service = FakeService()
        val first = AuthControllerSession(service)
        first.presenter.onPasswordChanged("old-form")
        first.dispose()
        val replacement = AuthControllerSession(service)
        assertNotSame(first.presenter, replacement.presenter)
        assertEquals(AuthUiState(), replacement.presenter.snapshot.auth)
        assertTrue(replacement.presenter.snapshot.sessionReady)
        replacement.presenter.onPasswordChanged("new-form")
        assertEquals("new-form", replacement.presenter.snapshot.auth.login.password)
        assertEquals("", first.presenter.snapshot.auth.login.password)
        replacement.dispose()
    }

    @Test fun lateRequestAndObserverCallbacksCannotReviveDisposedController() {
        val service = FakeService()
        val first = AuthControllerSession(service)
        first.presenter.onEmailChanged("member@example.invalid")
        first.presenter.onPasswordChanged("test-password")
        first.presenter.submit()
        first.dispose()
        val disposed = first.presenter.snapshot
        val second = AuthControllerSession(service)
        second.presenter.onPasswordChanged("second-form")
        val other = second.presenter.snapshot
        service.completion!!.complete(user("late"), null)
        service.observers[0].changed(user("late"))
        assertEquals(disposed, first.presenter.snapshot)
        assertEquals(other, second.presenter.snapshot)
        assertEquals(1, service.requestCancellations)
        second.dispose()
    }

    @Test fun repeatedDisposeCancelsExactlyOnceAndNeverLogsOutSdkSession() {
        val service = FakeService()
        val owner = AuthControllerSession(service)
        service.observers.single().changed(user("signed-in"))
        owner.dispose()
        owner.dispose()
        owner.presenter.dispose()
        assertEquals(1, service.observerCancellations)
        assertEquals(0, service.logoutCalls)
        assertEquals(AuthUiState(), owner.presenter.snapshot.auth)
    }
}
