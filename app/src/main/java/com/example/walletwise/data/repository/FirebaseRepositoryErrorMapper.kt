package com.example.walletwise.data.repository

import com.example.walletwise.domain.result.RepositoryError
import com.example.walletwise.domain.result.RepositoryErrorCode
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestoreException

internal fun Throwable.toRepositoryError(fallbackMessage: String): RepositoryError {
    val code = when (this) {
        is FirebaseAuthInvalidCredentialsException,
        is FirebaseAuthInvalidUserException -> RepositoryErrorCode.INVALID_CREDENTIALS
        is FirebaseAuthUserCollisionException -> RepositoryErrorCode.EMAIL_ALREADY_IN_USE
        is FirebaseAuthWeakPasswordException -> RepositoryErrorCode.WEAK_PASSWORD
        is FirebaseNetworkException -> RepositoryErrorCode.NETWORK
        is FirebaseFirestoreException -> when (this.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED -> RepositoryErrorCode.PERMISSION_DENIED
            FirebaseFirestoreException.Code.NOT_FOUND -> RepositoryErrorCode.NOT_FOUND
            FirebaseFirestoreException.Code.UNAVAILABLE -> RepositoryErrorCode.NETWORK
            else -> RepositoryErrorCode.UNKNOWN
        }
        else -> RepositoryErrorCode.UNKNOWN
    }
    return RepositoryError(code, localizedMessage ?: fallbackMessage)
}
