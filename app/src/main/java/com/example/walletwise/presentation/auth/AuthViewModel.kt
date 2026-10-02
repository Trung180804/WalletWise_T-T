package com.example.walletwise.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.walletwise.data.repository.AuthRepositoryImpl
import com.example.walletwise.data.repository.TransactionRepositoryImpl
import com.example.walletwise.data.repository.UserRepositoryImpl
import com.example.walletwise.domain.model.ImageUpload
import com.example.walletwise.domain.model.User
import com.example.walletwise.domain.model.defaultUser
import com.example.walletwise.domain.repository.AuthRepository
import com.example.walletwise.domain.repository.ImageUploader
import com.example.walletwise.domain.repository.UserRepository
import com.example.walletwise.domain.usecase.auth.ChangePasswordUseCase
import com.example.walletwise.domain.usecase.auth.LoginUseCase
import com.example.walletwise.domain.usecase.auth.LogoutUseCase
import com.example.walletwise.domain.usecase.auth.ObserveAuthStateUseCase
import com.example.walletwise.domain.usecase.auth.RegisterUseCase
import com.example.walletwise.domain.usecase.auth.ResetPasswordUseCase
import com.example.walletwise.domain.usecase.profile.GetUserProfileUseCase
import com.example.walletwise.domain.usecase.profile.ObserveUserProfileUseCase
import com.example.walletwise.domain.usecase.profile.UpdateAvatarUseCase
import com.example.walletwise.domain.usecase.profile.UpdateUserProfileUseCase
import com.example.walletwise.presentation.profile.ProfileDestination
import com.example.walletwise.presentation.profile.ProfileUiPresenter
import com.example.walletwise.presentation.profile.ProfileUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    private val userRepository: UserRepository = UserRepositoryImpl(),
    private val repository: AuthRepository = AuthRepositoryImpl(userRepository = userRepository),
    imageUploader: ImageUploader = TransactionRepositoryImpl().imageUploader()
) : ViewModel() {
    private val login = LoginUseCase(repository)
    private val register = RegisterUseCase(repository)
    private val logout = LogoutUseCase(repository)
    private val resetPassword = ResetPasswordUseCase(repository)
    private val changePassword = ChangePasswordUseCase(repository)
    private val updateProfile = UpdateUserProfileUseCase(userRepository)
    private val updateAvatar = UpdateAvatarUseCase(userRepository, imageUploader)
    private val sessionController = AuthProfileSessionController(
        scope = viewModelScope,
        observeAuthState = ObserveAuthStateUseCase(repository),
        getUserProfile = GetUserProfileUseCase(userRepository),
        observeUserProfile = ObserveUserProfileUseCase(userRepository),
        logout = logout
    )
    private val authUiPresenter = AuthUiPresenter(
        scope = viewModelScope,
        login = login,
        register = register,
        resetPassword = resetPassword,
        onAuthenticated = { session ->
            sessionController.observeProfile(session, ensureExists = true)
        }
    )
    private val profileUiPresenter = ProfileUiPresenter(
        scope = viewModelScope,
        profileState = sessionController.state,
        updateProfile = updateProfile,
        updateAvatar = updateAvatar,
        changePassword = changePassword
    )

    val authUiState: StateFlow<AuthUiState> = authUiPresenter.state
    val profileUiState: StateFlow<ProfileUiState> = profileUiPresenter.state

    private val _currentUser = MutableStateFlow(repository.currentSession?.defaultUser())
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(repository.currentSession != null)
    val isLoggedIn = _isLoggedIn.asStateFlow()

    private val _uiState = MutableStateFlow(sessionController.state.value)
    val uiState: StateFlow<AuthProfileUiState> = _uiState.asStateFlow()

    init {
        repository.currentSession?.let { sessionController.observeProfile(it, ensureExists = true) }
        sessionController.start()
        viewModelScope.launch {
            sessionController.state.collect { commonState ->
                _currentUser.value = commonState.profile
                _isLoggedIn.value = commonState.authStatus is AuthStatus.Authenticated
                _uiState.value = commonState.copy(operation = _uiState.value.operation)
            }
        }
    }

    fun logout() {
        sessionController.logoutAndClear()
        _currentUser.value = null
        _isLoggedIn.value = false
        _uiState.value = AuthProfileUiState(authStatus = AuthStatus.Unauthenticated)
    }

    fun openProfileNameEditor() = profileUiPresenter.openNameEditor()

    fun onProfileNameChanged(value: String) = profileUiPresenter.onNameChanged(value)

    fun cancelProfileNameEditor() = profileUiPresenter.cancelNameEditor()

    fun submitProfileName() = profileUiPresenter.submitName()

    fun openProfileGenderSelector() = profileUiPresenter.openGenderSelector()

    fun cancelProfileGenderSelector() = profileUiPresenter.cancelGenderSelector()

    fun selectProfileGender(value: String) = profileUiPresenter.selectGender(value)

    fun uploadProfileAvatar(image: ImageUpload) = profileUiPresenter.uploadAvatar(image)

    fun onProfileAvatarPickerCancelled() = profileUiPresenter.avatarPickerCancelled()

    fun onProfileAvatarReadFailed(message: String) = profileUiPresenter.avatarReadFailed(message)

    fun openProfilePasswordEditor() = profileUiPresenter.openPasswordEditor()

    fun onCurrentProfilePasswordChanged(value: String) =
        profileUiPresenter.onCurrentPasswordChanged(value)

    fun onNewProfilePasswordChanged(value: String) =
        profileUiPresenter.onNewPasswordChanged(value)

    fun onProfilePasswordConfirmationChanged(value: String) =
        profileUiPresenter.onPasswordConfirmationChanged(value)

    fun cancelProfilePasswordEditor() = profileUiPresenter.cancelPasswordEditor()

    fun submitProfilePasswordChange() = profileUiPresenter.submitPasswordChange()

    fun requestProfileNavigation(destination: ProfileDestination) =
        profileUiPresenter.requestNavigation(destination)

    fun requestProfileShare() = profileUiPresenter.requestShare()

    fun requestProfileLogout() = profileUiPresenter.requestLogout()

    fun cancelProfileLogout() = profileUiPresenter.cancelLogout()

    fun confirmProfileLogout() = profileUiPresenter.confirmLogout()

    fun consumeProfileUiEvent(id: Long) = profileUiPresenter.consumeEvent(id)

    fun loadUserProfile() {
        repository.currentSession?.let { sessionController.observeProfile(it, ensureExists = true) }
            ?: run {
                _currentUser.value = null
                _isLoggedIn.value = false
            }
    }

    fun onLoginEmailChanged(value: String) = authUiPresenter.onLoginEmailChanged(value)

    fun onLoginPasswordChanged(value: String) = authUiPresenter.onLoginPasswordChanged(value)

    fun toggleLoginPasswordVisibility() = authUiPresenter.toggleLoginPasswordVisibility()

    fun submitLogin() = authUiPresenter.submitLogin()

    fun onRegisterUsernameChanged(value: String) = authUiPresenter.onRegisterUsernameChanged(value)

    fun onRegisterEmailChanged(value: String) = authUiPresenter.onRegisterEmailChanged(value)

    fun onRegisterPasswordChanged(value: String) = authUiPresenter.onRegisterPasswordChanged(value)

    fun submitRegister() = authUiPresenter.submitRegister()

    fun onForgotPasswordEmailChanged(value: String) =
        authUiPresenter.onForgotPasswordEmailChanged(value)

    fun submitResetPassword() = authUiPresenter.submitResetPassword()

    fun navigateToRegister() = authUiPresenter.navigateToRegister()

    fun navigateToForgotPassword() = authUiPresenter.navigateToForgotPassword()

    fun navigateFromRegisterToLogin() = authUiPresenter.navigateFromRegisterToLogin()

    fun navigateFromForgotPasswordToLogin() =
        authUiPresenter.navigateFromForgotPasswordToLogin()

    fun consumeAuthUiEvent(id: Long) = authUiPresenter.consumeEvent(id)

    override fun onCleared() {
        profileUiPresenter.close()
        sessionController.close()
        super.onCleared()
    }
}
