package com.example.walletwise.domain.repository

import com.example.walletwise.domain.model.AuthUser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthRegistrationProgressTest {
    private fun user(uid: String = "created", name: String? = null) = AuthUser.create(uid, "member@example.invalid", name, false)!!
    private class Completion : AuthCompletion {
        val outcomes = mutableListOf<Pair<AuthUser?, AuthFailure?>>()
        override fun complete(user: AuthUser?, failure: AuthFailure?) { outcomes += user to failure }
    }

    @Test fun successRequiresCreateThenProfileUpdateThenReloadedName() {
        val completion = Completion()
        val progress = AuthRegistrationProgress(" Name ", completion)
        assertTrue(progress.created(user(), null))
        assertEquals(AuthRegistrationStage.UPDATING_PROFILE, progress.stage)
        assertTrue(completion.outcomes.isEmpty())
        assertTrue(progress.profileUpdated(null))
        assertEquals(AuthRegistrationStage.RELOADING_USER, progress.stage)
        assertTrue(completion.outcomes.isEmpty())
        progress.reloaded(user(name = "Name"), null)
        assertEquals(listOf<Pair<AuthUser?, AuthFailure?>>(user(name = "Name") to null), completion.outcomes)
        assertEquals(AuthRegistrationStage.COMPLETED, progress.stage)
    }

    @Test fun createFailureDoesNotRunProfileStepsOrAuthenticate() {
        val completion = Completion()
        val progress = AuthRegistrationProgress("Name", completion)
        assertFalse(progress.created(null, AuthFailure.EMAIL_ALREADY_USED))
        assertFalse(progress.profileUpdated(null))
        progress.reloaded(user(name = "Name"), null)
        assertEquals(listOf<Pair<AuthUser?, AuthFailure?>>(null to AuthFailure.EMAIL_ALREADY_USED), completion.outcomes)
    }

    @Test fun invalidCreatedUserFailsClosed() {
        val completion = Completion()
        val progress = AuthRegistrationProgress("Name", completion)
        assertFalse(progress.created(null, null))
        assertNull(completion.outcomes.single().first)
        assertEquals(AuthFailure.INVALID_USER, completion.outcomes.single().second)
    }

    @Test fun profileFailureReportsCreatedAccountButNeverRegistrationSuccess() {
        val completion = Completion()
        val progress = AuthRegistrationProgress("Name", completion)
        progress.created(user(), null)
        assertFalse(progress.profileUpdated(AuthFailure.NETWORK))
        assertEquals(AuthRegistrationStage.FAILED, progress.stage)
        assertEquals(listOf<Pair<AuthUser?, AuthFailure?>>(user() to AuthFailure.REGISTRATION_PROFILE_UPDATE_FAILED), completion.outcomes)
        progress.reloaded(user(name = "Name"), null)
        assertEquals(1, completion.outcomes.size)
    }

    @Test fun reloadFailureReportsPartialRegistration() {
        val completion = Completion()
        val progress = AuthRegistrationProgress("Name", completion)
        progress.created(user(), null)
        progress.profileUpdated(null)
        progress.reloaded(null, AuthFailure.NETWORK)
        assertEquals(AuthFailure.REGISTRATION_RELOAD_FAILED, completion.outcomes.single().second)
    }

    @Test fun reloadWithWrongAccountOrMissingNameCannotReportSuccess() {
        for (reloaded in listOf(user(uid = "other", name = "Name"), user(), user(name = "Wrong"))) {
            val completion = Completion()
            val progress = AuthRegistrationProgress("Name", completion)
            progress.created(user(), null)
            progress.profileUpdated(null)
            progress.reloaded(reloaded, null)
            assertEquals(AuthFailure.REGISTRATION_RELOAD_FAILED, completion.outcomes.single().second)
        }
    }

    @Test fun repeatedOrOutOfOrderCallbacksProduceOneOutcome() {
        val completion = Completion()
        val progress = AuthRegistrationProgress("Name", completion)
        assertFalse(progress.profileUpdated(null))
        progress.reloaded(user(name = "Name"), null)
        assertTrue(completion.outcomes.isEmpty())
        assertTrue(progress.created(user(), null))
        assertFalse(progress.created(user(), null))
        assertTrue(progress.profileUpdated(null))
        assertFalse(progress.profileUpdated(AuthFailure.NETWORK))
        progress.reloaded(user(name = "Name"), null)
        progress.reloaded(null, AuthFailure.NETWORK)
        assertEquals(1, completion.outcomes.size)
    }
}
