package com.example.walletwise

import com.example.walletwise.presentation.auth.AuthControllerObserver
import com.example.walletwise.presentation.auth.AuthControllerSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class WalletWiseComposeControllerTest {
    @Test fun nativeFactoryAlwaysCreatesIndependentSessionsAndReplacementControllers() {
        val sessions = mutableListOf<AuthControllerSession>()
        val observer = object : AuthControllerObserver {
            override fun created(session: AuthControllerSession) { sessions += session }
        }
        val first = walletWiseComposeViewController(null, observer)
        val second = walletWiseComposeViewController(null, observer)
        assertNotSame(first, second)
        assertNotSame(sessions[0].presenter, sessions[1].presenter)
        sessions[0].presenter.onPasswordChanged("first-form")
        assertEquals("", sessions[1].presenter.snapshot.auth.login.password)
        sessions[0].dispose()
        sessions[0].dispose()
        sessions[1].presenter.onPasswordChanged("second-form")
        assertEquals("second-form", sessions[1].presenter.snapshot.auth.login.password)
        val replacement = walletWiseComposeViewController(null, observer)
        assertNotSame(first, replacement)
        assertNotSame(sessions[0].presenter, sessions[2].presenter)
        assertTrue(sessions[2].presenter.snapshot.sessionReady)
        assertEquals("", sessions[2].presenter.snapshot.auth.login.password)
        sessions[1].dispose()
        sessions[2].dispose()
    }
}
