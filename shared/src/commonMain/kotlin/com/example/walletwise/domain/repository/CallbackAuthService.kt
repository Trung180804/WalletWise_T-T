package com.example.walletwise.domain.repository

/** An opaque session identity. Never display or log the subject. */
class AuthIdentity(val subject: String) {
    override fun toString(): String = "AuthIdentity(redacted)"
}

enum class AuthFailure {
    INVALID_CREDENTIALS, EMAIL_ALREADY_USED, WEAK_PASSWORD, NETWORK,
    TOO_MANY_REQUESTS, NOT_CONNECTED, REQUEST_IN_PROGRESS, UNKNOWN
}

/** Cancellation suppresses callbacks; it does not promise to undo a server request. */
interface AuthCancellation {
    fun cancel()
}

interface AuthCompletion {
    /** A null identity with no failure means a password-reset request was accepted. */
    fun complete(identity: AuthIdentity?, failure: AuthFailure?)
}

interface AuthStateObserver {
    fun changed(identity: AuthIdentity?)
}

/**
 * Callback boundary exported to Swift, independent of a platform SDK or profile storage.
 * Implementations deliver callbacks on the UI thread and serialize outgoing requests.
 */
interface CallbackAuthService {
    fun observe(observer: AuthStateObserver): AuthCancellation
    fun login(email: String, password: String, completion: AuthCompletion): AuthCancellation
    fun register(email: String, password: String, completion: AuthCompletion): AuthCancellation
    fun resetPassword(email: String, completion: AuthCompletion): AuthCancellation
    fun logout(): AuthFailure?
}
