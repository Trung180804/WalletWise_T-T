package com.example.walletwise.presentation.profile

import com.example.walletwise.domain.model.ChangePasswordInput
import com.example.walletwise.domain.model.ImageUpload
import com.example.walletwise.domain.model.UserProfileUpdate
import com.example.walletwise.domain.model.defaultUser
import com.example.walletwise.domain.result.AuthUseCaseResult
import com.example.walletwise.domain.usecase.auth.ChangePasswordUseCase
import com.example.walletwise.domain.usecase.profile.UpdateAvatarUseCase
import com.example.walletwise.domain.usecase.profile.UpdateUserProfileUseCase
import com.example.walletwise.presentation.auth.AuthOperationState
import com.example.walletwise.presentation.auth.AuthProfileUiState
import com.example.walletwise.presentation.auth.AuthStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

sealed interface ProfileLoadState {
    data object Loading : ProfileLoadState
    data object Empty : ProfileLoadState
    data object Data : ProfileLoadState
    data class Error(val message: String) : ProfileLoadState
}

enum class ProfileDestination {
    EDIT_PROFILE,
    SMART_BUDGET,
    CURRENCY,
    CUSTOMER_CARE,
    SETTINGS,
    ABOUT_US
}

enum class ProfileMessageKind {
    SUCCESS,
    ERROR
}

sealed interface ProfileUiEvent {
    data class Navigate(val destination: ProfileDestination) : ProfileUiEvent
    data object Share : ProfileUiEvent
    data object Logout : ProfileUiEvent
    data class Message(
        val text: String,
        val kind: ProfileMessageKind
    ) : ProfileUiEvent
}

data class ProfileUiEventEnvelope(
    val id: Long,
    val event: ProfileUiEvent
)

data class ProfilePasswordEditorState(
    val currentPassword: String = "",
    val newPassword: String = "",
    val confirmation: String = "",
    val isSubmitting: Boolean = false
)

data class ProfileUiState(
    val loadState: ProfileLoadState = ProfileLoadState.Loading,
    val isLoggedIn: Boolean = false,
    val userId: String = "",
    val email: String = "",
    val username: String = "",
    val gender: String = "Khác",
    val avatarUrl: String = "",
    val nameDraft: String? = null,
    val isGenderDialogVisible: Boolean = false,
    val passwordEditor: ProfilePasswordEditorState? = null,
    val isUpdatingProfile: Boolean = false,
    val isUploadingAvatar: Boolean = false,
    val isLogoutDialogVisible: Boolean = false,
    val pendingEvent: ProfileUiEventEnvelope? = null
) {
    val mainDisplayName: String
        get() = if (isLoggedIn) username.ifBlank { "Thành viên" } else "Người dùng"

    val mainEmail: String
        get() = if (isLoggedIn) email else "Chưa đăng nhập"

    val editDisplayName: String
        get() = username.ifBlank { "Người dùng" }

    val editEmail: String
        get() = email.ifBlank { "Chưa có Email" }

    val editUserId: String
        get() = userId.ifBlank { "Chưa có ID" }

    val normalizedGender: String
        get() = gender.ifBlank { "Khác" }

    val mainAvatarInitial: String
        get() = mainEmail.firstOrNull()?.uppercase() ?: "N"

    val editAvatarInitial: String
        get() = editDisplayName.firstOrNull { it.isLetter() }
            ?.toString()
            ?.uppercase()
            ?: "U"
}

class ProfileUiPresenter(
    private val scope: CoroutineScope,
    profileState: StateFlow<AuthProfileUiState>,
    private val updateProfile: UpdateUserProfileUseCase,
    private val updateAvatar: UpdateAvatarUseCase,
    private val changePassword: ChangePasswordUseCase
) {
    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    private var nextEventId = 0L
    private var sessionGeneration = 0L
    private var activeUserId: String? = null
    private var profileUpdateJob: Job? = null
    private var avatarUploadJob: Job? = null
    private var passwordUpdateJob: Job? = null
    private val profileStateJob: Job = scope.launch {
        profileState.collect(::applyProfileState)
    }

    fun openNameEditor() {
        if (_state.value.nameDraft != null || _state.value.isUpdatingProfile) return
        _state.value = _state.value.copy(nameDraft = _state.value.editDisplayName)
    }

    fun onNameChanged(value: String) {
        if (_state.value.nameDraft == null || _state.value.isUpdatingProfile) return
        _state.value = _state.value.copy(nameDraft = value)
    }

    fun cancelNameEditor() {
        if (_state.value.isUpdatingProfile) return
        _state.value = _state.value.copy(nameDraft = null)
    }

    fun submitName() {
        val snapshot = _state.value
        val draft = snapshot.nameDraft ?: return
        if (snapshot.isUpdatingProfile) return

        val normalizedName = draft.trim()
        if (normalizedName.isBlank()) {
            emitMessage("Họ và tên không được để trống!", ProfileMessageKind.ERROR)
            return
        }
        if (normalizedName == snapshot.username.trim()) {
            _state.value = snapshot.copy(nameDraft = null)
            return
        }
        if (snapshot.userId.isBlank()) {
            emitMessage("Chưa đăng nhập", ProfileMessageKind.ERROR)
            return
        }

        _state.value = snapshot.copy(isUpdatingProfile = true)
        val generation = sessionGeneration
        profileUpdateJob = scope.launch {
            when (
                val result = updateProfile(
                    snapshot.userId,
                    UserProfileUpdate(username = normalizedName)
                ).also {
                    coroutineContext.ensureActive()
                    if (generation != sessionGeneration) return@launch
                }
            ) {
                is AuthUseCaseResult.Success -> {
                    _state.value = _state.value.copy(
                        username = normalizedName,
                        nameDraft = null,
                        isUpdatingProfile = false
                    )
                    emitMessage("Đã cập nhật Họ và tên!", ProfileMessageKind.SUCCESS)
                }

                is AuthUseCaseResult.ValidationFailure -> {
                    _state.value = _state.value.copy(isUpdatingProfile = false)
                    emitMessage(result.message, ProfileMessageKind.ERROR)
                }

                is AuthUseCaseResult.RepositoryFailure -> {
                    _state.value = _state.value.copy(isUpdatingProfile = false)
                    emitMessage(
                        result.error.message.ifBlank { "Lỗi cập nhật" },
                        ProfileMessageKind.ERROR
                    )
                }
            }
        }
    }

    fun openGenderSelector() {
        if (_state.value.isUpdatingProfile) return
        _state.value = _state.value.copy(isGenderDialogVisible = true)
    }

    fun cancelGenderSelector() {
        if (_state.value.isUpdatingProfile) return
        _state.value = _state.value.copy(isGenderDialogVisible = false)
    }

    fun selectGender(value: String) {
        val snapshot = _state.value
        if (!snapshot.isGenderDialogVisible || snapshot.isUpdatingProfile) return
        if (value == snapshot.normalizedGender) {
            _state.value = snapshot.copy(isGenderDialogVisible = false)
            return
        }
        if (snapshot.userId.isBlank()) {
            emitMessage("Chưa đăng nhập", ProfileMessageKind.ERROR)
            return
        }

        _state.value = snapshot.copy(isUpdatingProfile = true)
        val generation = sessionGeneration
        profileUpdateJob = scope.launch {
            when (
                val result = updateProfile(
                    snapshot.userId,
                    UserProfileUpdate(gender = value)
                ).also {
                    coroutineContext.ensureActive()
                    if (generation != sessionGeneration) return@launch
                }
            ) {
                is AuthUseCaseResult.Success -> {
                    _state.value = _state.value.copy(
                        gender = value,
                        isGenderDialogVisible = false,
                        isUpdatingProfile = false
                    )
                    emitMessage("Cập nhật thành công!", ProfileMessageKind.SUCCESS)
                }

                is AuthUseCaseResult.ValidationFailure -> {
                    _state.value = _state.value.copy(isUpdatingProfile = false)
                    emitMessage(result.message, ProfileMessageKind.ERROR)
                }

                is AuthUseCaseResult.RepositoryFailure -> {
                    _state.value = _state.value.copy(isUpdatingProfile = false)
                    emitMessage(
                        result.error.message.ifBlank { "Cập nhật thất bại!" },
                        ProfileMessageKind.ERROR
                    )
                }
            }
        }
    }

    fun uploadAvatar(image: ImageUpload) {
        val snapshot = _state.value
        if (snapshot.isUploadingAvatar) return
        if (snapshot.userId.isBlank()) {
            emitMessage("Chưa đăng nhập", ProfileMessageKind.ERROR)
            return
        }

        _state.value = snapshot.copy(isUploadingAvatar = true)
        val generation = sessionGeneration
        avatarUploadJob = scope.launch {
            when (
                val result = updateAvatar(
                    userId = snapshot.userId,
                    previousAvatarUrl = snapshot.avatarUrl,
                    image = image
                ).also {
                    coroutineContext.ensureActive()
                    if (generation != sessionGeneration) return@launch
                }
            ) {
                is AuthUseCaseResult.Success -> {
                    _state.value = _state.value.copy(
                        avatarUrl = result.value,
                        isUploadingAvatar = false
                    )
                    emitMessage("Cập nhật thành công!", ProfileMessageKind.SUCCESS)
                }

                is AuthUseCaseResult.ValidationFailure -> {
                    _state.value = _state.value.copy(isUploadingAvatar = false)
                    emitMessage(result.message, ProfileMessageKind.ERROR)
                }

                is AuthUseCaseResult.RepositoryFailure -> {
                    _state.value = _state.value.copy(isUploadingAvatar = false)
                    emitMessage(
                        result.error.message.ifBlank { "Lỗi tải ảnh" },
                        ProfileMessageKind.ERROR
                    )
                }
            }
        }
    }

    fun avatarPickerCancelled() = Unit

    fun avatarReadFailed(message: String) {
        if (_state.value.isUploadingAvatar) return
        emitMessage(message.ifBlank { "Lỗi tải ảnh" }, ProfileMessageKind.ERROR)
    }

    fun openPasswordEditor() {
        if (_state.value.passwordEditor != null) return
        _state.value = _state.value.copy(passwordEditor = ProfilePasswordEditorState())
    }

    fun onCurrentPasswordChanged(value: String) = updatePasswordEditor {
        copy(currentPassword = value)
    }

    fun onNewPasswordChanged(value: String) = updatePasswordEditor {
        copy(newPassword = value)
    }

    fun onPasswordConfirmationChanged(value: String) = updatePasswordEditor {
        copy(confirmation = value)
    }

    fun cancelPasswordEditor() {
        if (_state.value.passwordEditor?.isSubmitting == true) return
        _state.value = _state.value.copy(passwordEditor = null)
    }

    fun submitPasswordChange() {
        val snapshot = _state.value
        val editor = snapshot.passwordEditor ?: return
        if (editor.isSubmitting) return

        if (editor.currentPassword.isBlank()) {
            emitMessage("Vui lòng nhập mật khẩu hiện tại!", ProfileMessageKind.ERROR)
            return
        }
        if (editor.newPassword.length < 6) {
            emitMessage(
                "Mật khẩu mới phải từ 6 ký tự trở lên!",
                ProfileMessageKind.ERROR
            )
            return
        }
        if (editor.newPassword != editor.confirmation) {
            emitMessage(
                "Mật khẩu nhắc lại không trùng khớp!",
                ProfileMessageKind.ERROR
            )
            return
        }

        _state.value = snapshot.copy(passwordEditor = editor.copy(isSubmitting = true))
        val generation = sessionGeneration
        passwordUpdateJob = scope.launch {
            when (
                val result = changePassword(
                    ChangePasswordInput(
                        currentPassword = editor.currentPassword,
                        newPassword = editor.newPassword,
                        confirmPassword = editor.confirmation
                    )
                ).also {
                    coroutineContext.ensureActive()
                    if (generation != sessionGeneration) return@launch
                }
            ) {
                is AuthUseCaseResult.Success -> {
                    _state.value = _state.value.copy(passwordEditor = null)
                    emitMessage("Đổi mật khẩu thành công!", ProfileMessageKind.SUCCESS)
                }

                is AuthUseCaseResult.ValidationFailure -> {
                    setPasswordSubmitting(false)
                    emitMessage(result.message, ProfileMessageKind.ERROR)
                }

                is AuthUseCaseResult.RepositoryFailure -> {
                    setPasswordSubmitting(false)
                    emitMessage(
                        result.error.message.ifBlank { "Không thể đổi mật khẩu!" },
                        ProfileMessageKind.ERROR
                    )
                }
            }
        }
    }

    fun requestNavigation(destination: ProfileDestination) {
        emitEvent(ProfileUiEvent.Navigate(destination))
    }

    fun requestShare() {
        emitEvent(ProfileUiEvent.Share)
    }

    fun requestLogout() {
        val snapshot = _state.value
        if (snapshot.isLoggedIn) {
            _state.value = snapshot.copy(isLogoutDialogVisible = true)
        } else {
            emitLogoutOnce()
        }
    }

    fun cancelLogout() {
        _state.value = _state.value.copy(isLogoutDialogVisible = false)
    }

    fun confirmLogout() {
        if (!_state.value.isLogoutDialogVisible) return
        _state.value = _state.value.copy(isLogoutDialogVisible = false)
        emitLogoutOnce()
    }

    fun consumeEvent(id: Long) {
        if (_state.value.pendingEvent?.id == id) {
            _state.value = _state.value.copy(pendingEvent = null)
        }
    }

    fun close() {
        sessionGeneration++
        profileUpdateJob?.cancel()
        avatarUploadJob?.cancel()
        passwordUpdateJob?.cancel()
        profileStateJob.cancel()
        _state.value = ProfileUiState()
    }

    private fun applyProfileState(commonState: AuthProfileUiState) {
        val session = (commonState.authStatus as? AuthStatus.Authenticated)?.session
        if (activeUserId != session?.userId) {
            sessionGeneration++
            activeUserId = session?.userId
            profileUpdateJob?.cancel()
            avatarUploadJob?.cancel()
            passwordUpdateJob?.cancel()
            _state.value = ProfileUiState()
        }
        if (commonState.authStatus is AuthStatus.Unauthenticated) {
            profileUpdateJob?.cancel()
            profileUpdateJob = null
            avatarUploadJob?.cancel()
            avatarUploadJob = null
            passwordUpdateJob?.cancel()
            passwordUpdateJob = null
            _state.value = ProfileUiState(
                loadState = ProfileLoadState.Data,
                pendingEvent = _state.value.pendingEvent?.takeIf {
                    it.event is ProfileUiEvent.Logout
                }
            )
            return
        }

        val profile = commonState.profile ?: session?.defaultUser()
        val loadState = when (commonState.authStatus) {
            AuthStatus.Loading -> ProfileLoadState.Loading
            AuthStatus.Unauthenticated -> ProfileLoadState.Data
            is AuthStatus.Authenticated -> when {
                commonState.operation is AuthOperationState.ValidationError ->
                    ProfileLoadState.Error(commonState.operation.message)
                commonState.operation is AuthOperationState.RepositoryError ->
                    ProfileLoadState.Error(commonState.operation.message)
                commonState.profile != null -> ProfileLoadState.Data
                else -> ProfileLoadState.Empty
            }
        }
        val isLoggedIn = commonState.authStatus is AuthStatus.Authenticated

        _state.value = _state.value.copy(
            loadState = loadState,
            isLoggedIn = isLoggedIn,
            userId = profile?.id.orEmpty(),
            email = profile?.email.orEmpty(),
            username = profile?.username.orEmpty(),
            gender = profile?.gender?.takeIf { it.isNotBlank() } ?: "Khác",
            avatarUrl = profile?.avatarUrl.orEmpty()
        )
    }

    private fun updatePasswordEditor(transform: ProfilePasswordEditorState.() -> ProfilePasswordEditorState) {
        val editor = _state.value.passwordEditor ?: return
        if (editor.isSubmitting) return
        _state.value = _state.value.copy(passwordEditor = editor.transform())
    }

    private fun setPasswordSubmitting(isSubmitting: Boolean) {
        val editor = _state.value.passwordEditor ?: return
        _state.value = _state.value.copy(
            passwordEditor = editor.copy(isSubmitting = isSubmitting)
        )
    }

    private fun emitLogoutOnce() {
        if (_state.value.pendingEvent?.event is ProfileUiEvent.Logout) return
        emitEvent(ProfileUiEvent.Logout)
    }

    private fun emitMessage(text: String, kind: ProfileMessageKind) {
        emitEvent(ProfileUiEvent.Message(text, kind))
    }

    private fun emitEvent(event: ProfileUiEvent) {
        nextEventId += 1
        _state.value = _state.value.copy(
            pendingEvent = ProfileUiEventEnvelope(nextEventId, event)
        )
    }
}
