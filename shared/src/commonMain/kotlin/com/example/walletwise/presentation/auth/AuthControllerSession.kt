package com.example.walletwise.presentation.auth

import com.example.walletwise.domain.repository.CallbackAuthService

/** One owner per native controller, never cached by the Firebase bootstrap. */
class AuthControllerSession(service: CallbackAuthService?) {
    val presenter = ConnectedAuthPresenter(service)

    /** Presenter disposal is idempotent and never signs the SDK user out. */
    fun dispose() = presenter.dispose()
}

/** Gives a native host its own session so it can also dispose an unmounted controller. */
interface AuthControllerObserver {
    fun created(session: AuthControllerSession)
}
