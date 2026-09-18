package com.example.walletwise.domain.repository

import com.example.walletwise.domain.model.AuthUser

enum class AuthFailure {
    INVALID_CREDENTIALS, EMAIL_ALREADY_USED, WEAK_PASSWORD, NETWORK,
    TOO_MANY_REQUESTS, NOT_CONNECTED, REQUEST_IN_PROGRESS, INVALID_USER,
    REGISTRATION_PROFILE_UPDATE_FAILED, REGISTRATION_RELOAD_FAILED, UNKNOWN
}

/** Cancellation suppresses callbacks; it does not promise to undo a server request. */
interface AuthCancellation {
    fun cancel()
}

interface AuthCompletion {
    /** A null user with no failure means a password-reset request was accepted. */
    /** A user together with a failure describes partial account creation, never success. */
    fun complete(user: AuthUser?, failure: AuthFailure?)
}

interface AuthStateObserver {
    fun changed(user: AuthUser?)
}

/**
 * Callback boundary exported to Swift, independent of a platform SDK or profile storage.
 * Implementations deliver callbacks on the UI thread and serialize outgoing requests.
 */
interface CallbackAuthService {
    fun observe(observer: AuthStateObserver): AuthCancellation
    fun login(email: String, password: String, completion: AuthCompletion): AuthCancellation
    fun register(email: String, password: String, displayName: String, completion: AuthCompletion): AuthCancellation
    fun resetPassword(email: String, completion: AuthCompletion): AuthCancellation
    /** Return only after the SDK sign-out finishes; a failure must retain the real session. */
    fun logout(): AuthFailure?
}
