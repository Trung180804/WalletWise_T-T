package com.example.walletwise.presentation.auth

import com.example.walletwise.domain.model.AuthSession
import com.example.walletwise.domain.model.defaultUser
import com.example.walletwise.domain.result.AuthUseCaseResult
import com.example.walletwise.domain.result.RepositoryResult
import com.example.walletwise.domain.usecase.auth.LogoutUseCase
import com.example.walletwise.domain.usecase.auth.ObserveAuthStateUseCase
import com.example.walletwise.domain.usecase.profile.GetUserProfileUseCase
import com.example.walletwise.domain.usecase.profile.ObserveUserProfileUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AuthProfileSessionController(
    private val scope: CoroutineScope,
    private val observeAuthState: ObserveAuthStateUseCase,
    private val getUserProfile: GetUserProfileUseCase,
    private val observeUserProfile: ObserveUserProfileUseCase,
    private val logout: LogoutUseCase
) {
    private val _state = MutableStateFlow(AuthProfileUiState())
    val state: StateFlow<AuthProfileUiState> = _state.asStateFlow()

    private var authJob: Job? = null
    private var profileJob: Job? = null
    private var profileLoadJob: Job? = null
    private var observedUserId: String? = null
    private var createMissingProfile = false

    fun start() {
        if (authJob?.isActive == true) return
        authJob = scope.launch {
            observeAuthState().collectLatest { session ->
                if (session == null) {
                    clearProfile()
                } else {
                    observeProfile(session, ensureExists = false)
                }
            }
        }
    }

    fun observeProfile(session: AuthSession, ensureExists: Boolean = true) {
        if (observedUserId == session.userId && profileJob?.isActive == true) {
            if (ensureExists) {
                createMissingProfile = true
                ensureProfile(session)
            }
            return
        }
        profileJob?.cancel()
        profileLoadJob?.cancel()
        observedUserId = session.userId
        createMissingProfile = ensureExists
        _state.value = _state.value.copy(
            authStatus = AuthStatus.Authenticated(session),
            profile = _state.value.profile?.takeIf { it.id == session.userId }
                ?: session.defaultUser()
        )
        profileJob = scope.launch {
            observeUserProfile(session.userId).collect { result ->
                when (result) {
                    is RepositoryResult.Success -> if (result.value != null) {
                        _state.value = _state.value.copy(
                            profile = result.value,
                            operation = AuthOperationState.Idle
                        )
                    } else if (createMissingProfile) {
                        ensureProfile(session)
                    }
                    is RepositoryResult.Failure ->
                        _state.value = _state.value.copy(
                            operation = AuthOperationState.RepositoryError(result.error.message)
                        )
                }
            }
        }
        if (ensureExists) ensureProfile(session)
    }

    fun logoutAndClear(): AuthUseCaseResult<Unit> {
        val result = logout()
        if (result is AuthUseCaseResult.Success) clearProfile()
        return result
    }

    fun close() {
        profileJob?.cancel()
        profileJob = null
        profileLoadJob?.cancel()
        profileLoadJob = null
        authJob?.cancel()
        authJob = null
        observedUserId = null
        createMissingProfile = false
    }

    private fun clearProfile() {
        profileJob?.cancel()
        profileJob = null
        profileLoadJob?.cancel()
        profileLoadJob = null
        observedUserId = null
        createMissingProfile = false
        _state.value = AuthProfileUiState(authStatus = AuthStatus.Unauthenticated)
    }

    private fun ensureProfile(session: AuthSession) {
        if (profileLoadJob?.isActive == true) return
        profileLoadJob = scope.launch {
            when (val initial = getUserProfile(session)) {
                is AuthUseCaseResult.Success ->
                    _state.value = _state.value.copy(
                        profile = initial.value,
                        operation = AuthOperationState.Idle
                    )
                is AuthUseCaseResult.ValidationFailure ->
                    _state.value = _state.value.copy(
                        operation = AuthOperationState.ValidationError(initial.message)
                    )
                is AuthUseCaseResult.RepositoryFailure ->
                    _state.value = _state.value.copy(
                        operation = AuthOperationState.RepositoryError(initial.error.message)
                    )
            }
        }
    }
}
