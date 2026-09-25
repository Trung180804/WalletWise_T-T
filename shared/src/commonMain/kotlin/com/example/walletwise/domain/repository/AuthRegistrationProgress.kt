package com.example.walletwise.domain.repository

import com.example.walletwise.domain.model.AuthUser

enum class AuthRegistrationStage { CREATING_USER, UPDATING_PROFILE, RELOADING_USER, COMPLETED, FAILED }

/** Gates the native SDK sequence; creation alone is never registration success. */
class AuthRegistrationProgress(displayName: String, private val completion: AuthCompletion) {
    private val expectedName = displayName.trim().takeIf { it.isNotEmpty() }
    private var createdUser: AuthUser? = null
    var stage: AuthRegistrationStage = AuthRegistrationStage.CREATING_USER
        private set

    fun created(user: AuthUser?, failure: AuthFailure?): Boolean {
        if (stage != AuthRegistrationStage.CREATING_USER) return false
        if (failure != null || user == null) {
            fail(failure ?: AuthFailure.INVALID_USER)
            return false
        }
        createdUser = user
        stage = AuthRegistrationStage.UPDATING_PROFILE
        return true
    }

    fun profileUpdated(failure: AuthFailure?): Boolean {
        if (stage != AuthRegistrationStage.UPDATING_PROFILE) return false
        if (failure != null) {
            fail(AuthFailure.REGISTRATION_PROFILE_UPDATE_FAILED)
            return false
        }
        stage = AuthRegistrationStage.RELOADING_USER
        return true
    }

    fun reloaded(user: AuthUser?, failure: AuthFailure?) {
        if (stage != AuthRegistrationStage.RELOADING_USER) return
        if (failure != null || user == null || user.uid != createdUser?.uid || user.displayName != expectedName) {
            fail(AuthFailure.REGISTRATION_RELOAD_FAILED)
            return
        }
        stage = AuthRegistrationStage.COMPLETED
        completion.complete(user, null)
    }

    private fun fail(failure: AuthFailure) {
        stage = AuthRegistrationStage.FAILED
        completion.complete(createdUser, failure)
    }
}
