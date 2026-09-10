package com.example.walletwise.presentation.profile

import com.example.walletwise.data.mapper.FirestoreWireMapper
import com.example.walletwise.domain.model.AuthSession
import com.example.walletwise.domain.model.ChangePasswordInput
import com.example.walletwise.domain.model.ImageUpload
import com.example.walletwise.domain.model.LoginInput
import com.example.walletwise.domain.model.RegisterInput
import com.example.walletwise.domain.model.ResetPasswordInput
import com.example.walletwise.domain.model.User
import com.example.walletwise.domain.model.UserProfileUpdate
import com.example.walletwise.domain.repository.AuthRepository
import com.example.walletwise.domain.repository.ImageUploader
import com.example.walletwise.domain.repository.UserRepository
import com.example.walletwise.domain.result.RepositoryError
import com.example.walletwise.domain.result.RepositoryErrorCode
import com.example.walletwise.domain.result.RepositoryResult
import com.example.walletwise.domain.usecase.auth.ChangePasswordUseCase
import com.example.walletwise.domain.usecase.profile.UpdateAvatarUseCase
import com.example.walletwise.domain.usecase.profile.UpdateUserProfileUseCase
import com.example.walletwise.presentation.auth.AuthOperationState
import com.example.walletwise.presentation.auth.AuthProfileUiState
import com.example.walletwise.presentation.auth.AuthStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileUiPresenterTest {
    @Test
    fun profileLoadingErrorAndData_areMappedFromSessionState() = runTest {
        val fixture = fixture(AuthProfileUiState())
        runCurrent()
        assertIs<ProfileLoadState.Loading>(fixture.presenter.state.value.loadState)

        fixture.profileState.value = AuthProfileUiState(
            authStatus = AuthStatus.Authenticated(session),
            operation = AuthOperationState.RepositoryError("Không tải được hồ sơ"),
            profile = user
        )
        runCurrent()
        val error = assertIs<ProfileLoadState.Error>(fixture.presenter.state.value.loadState)
        assertEquals("Không tải được hồ sơ", error.message)

        fixture.profileState.value = authenticatedState()
        runCurrent()
        assertIs<ProfileLoadState.Data>(fixture.presenter.state.value.loadState)
        assertEquals("Wallet User", fixture.presenter.state.value.username)
        assertEquals("user@example.com", fixture.presenter.state.value.email)
    }

    @Test
    fun legacyProfileMissingAvatarAndGender_usesCompatibleDefaults() = runTest {
        val legacy = FirestoreWireMapper.userFromMap(
            documentId = "legacy-user",
            data = mapOf(
                "email" to "legacy@example.com",
                "username" to "Legacy"
            )
        )
        val fixture = fixture(authenticatedState(legacy))
        runCurrent()

        assertEquals("", fixture.presenter.state.value.avatarUrl)
        assertEquals("Khác", fixture.presenter.state.value.gender)
        assertEquals("Legacy", fixture.presenter.state.value.username)
    }

    @Test
    fun nameEditorCanOpenCancelAndAcceptWithoutWritingUnchangedData() = runTest {
        val fixture = fixture(authenticatedState())
        runCurrent()

        fixture.presenter.openNameEditor()
        assertEquals("Wallet User", fixture.presenter.state.value.nameDraft)
        fixture.presenter.cancelNameEditor()
        assertNull(fixture.presenter.state.value.nameDraft)

        fixture.presenter.openNameEditor()
        fixture.presenter.onNameChanged("  Wallet User  ")
        fixture.presenter.submitName()
        runCurrent()

        assertNull(fixture.presenter.state.value.nameDraft)
        assertEquals(0, fixture.userRepository.updateCalls)
        assertNull(fixture.presenter.state.value.pendingEvent)
    }

    @Test
    fun blankNameIsRejectedWithoutRepositoryWrite() = runTest {
        val fixture = fixture(authenticatedState())
        runCurrent()
        fixture.presenter.openNameEditor()
        fixture.presenter.onNameChanged("   ")

        fixture.presenter.submitName()

        assertEquals(0, fixture.userRepository.updateCalls)
        assertNotNull(fixture.presenter.state.value.nameDraft)
        val message = assertIs<ProfileUiEvent.Message>(
            fixture.presenter.state.value.pendingEvent?.event
        )
        assertEquals(ProfileMessageKind.ERROR, message.kind)
        assertEquals("Họ và tên không được để trống!", message.text)
    }

    @Test
    fun nameUpdateSuccessFailureAndRepeatedSubmit_haveExpectedState() = runTest {
        val success = fixture(authenticatedState()).apply {
            userRepository.updateGate = CompletableDeferred()
        }
        runCurrent()
        success.presenter.openNameEditor()
        success.presenter.onNameChanged("New Name")
        success.presenter.submitName()
        success.presenter.submitName()
        runCurrent()

        assertTrue(success.presenter.state.value.isUpdatingProfile)
        assertEquals(1, success.userRepository.updateCalls)
        success.userRepository.updateGate?.complete(Unit)
        runCurrent()
        assertFalse(success.presenter.state.value.isUpdatingProfile)
        assertEquals("New Name", success.presenter.state.value.username)
        assertNull(success.presenter.state.value.nameDraft)
        assertEquals("New Name", success.userRepository.lastUpdate?.username)
        assertEquals(
            ProfileMessageKind.SUCCESS,
            assertIs<ProfileUiEvent.Message>(
                success.presenter.state.value.pendingEvent?.event
            ).kind
        )

        val failure = fixture(authenticatedState()).apply {
            userRepository.updateResult = repositoryFailure("Firestore unavailable")
        }
        runCurrent()
        failure.presenter.openNameEditor()
        failure.presenter.onNameChanged("Failed Name")
        failure.presenter.submitName()
        runCurrent()

        assertEquals("Wallet User", failure.presenter.state.value.username)
        assertEquals("Failed Name", failure.presenter.state.value.nameDraft)
        assertEquals(
            ProfileMessageKind.ERROR,
            assertIs<ProfileUiEvent.Message>(
                failure.presenter.state.value.pendingEvent?.event
            ).kind
        )
    }

    @Test
    fun genderSelectorCanCancelSelectAndSkipUnchangedValue() = runTest {
        val fixture = fixture(authenticatedState())
        runCurrent()

        fixture.presenter.openGenderSelector()
        assertTrue(fixture.presenter.state.value.isGenderDialogVisible)
        fixture.presenter.cancelGenderSelector()
        assertFalse(fixture.presenter.state.value.isGenderDialogVisible)

        fixture.presenter.openGenderSelector()
        fixture.presenter.selectGender("Khác")
        assertEquals(0, fixture.userRepository.updateCalls)
        assertFalse(fixture.presenter.state.value.isGenderDialogVisible)

        fixture.presenter.openGenderSelector()
        fixture.presenter.selectGender("Nữ giới")
        runCurrent()
        assertEquals(1, fixture.userRepository.updateCalls)
        assertEquals("Nữ giới", fixture.userRepository.lastUpdate?.gender)
        assertEquals("Nữ giới", fixture.presenter.state.value.gender)
        assertFalse(fixture.presenter.state.value.isGenderDialogVisible)
    }

    @Test
    fun avatarPickerCancelAndReadFailure_keepPreviousAvatar() = runTest {
        val fixture = fixture(authenticatedState(user.copy(avatarUrl = oldAvatar)))
        runCurrent()

        fixture.presenter.avatarPickerCancelled()
        assertEquals(oldAvatar, fixture.presenter.state.value.avatarUrl)
        assertEquals(0, fixture.imageUploader.uploadCalls)

        fixture.presenter.avatarReadFailed("Không thể đọc ảnh")
        assertEquals(oldAvatar, fixture.presenter.state.value.avatarUrl)
        assertEquals(0, fixture.imageUploader.uploadCalls)
        assertEquals(
            ProfileMessageKind.ERROR,
            assertIs<ProfileUiEvent.Message>(
                fixture.presenter.state.value.pendingEvent?.event
            ).kind
        )
    }

    @Test
    fun avatarUploadLoadingBlocksDuplicateAndSuccessUpdatesOnce() = runTest {
        val fixture = fixture(authenticatedState(user.copy(avatarUrl = oldAvatar))).apply {
            imageUploader.uploadGate = CompletableDeferred()
            imageUploader.uploadResult = Result.success(newAvatar)
        }
        runCurrent()
        val image = ImageUpload(byteArrayOf(1, 2, 3), "image/png", "avatar.png")

        fixture.presenter.uploadAvatar(image)
        fixture.presenter.uploadAvatar(image)
        runCurrent()

        assertTrue(fixture.presenter.state.value.isUploadingAvatar)
        assertEquals(oldAvatar, fixture.presenter.state.value.avatarUrl)
        assertEquals(1, fixture.imageUploader.uploadCalls)
        fixture.imageUploader.uploadGate?.complete(Unit)
        runCurrent()

        assertFalse(fixture.presenter.state.value.isUploadingAvatar)
        assertEquals(newAvatar, fixture.presenter.state.value.avatarUrl)
        assertEquals(1, fixture.userRepository.updateCalls)
        assertEquals(newAvatar, fixture.userRepository.lastUpdate?.avatarUrl)
    }

    @Test
    fun avatarUploadFailureKeepsPreviousAvatarAndDoesNotWriteProfile() = runTest {
        val fixture = fixture(authenticatedState(user.copy(avatarUrl = oldAvatar))).apply {
            imageUploader.uploadResult = Result.failure(IllegalStateException("Upload failed"))
        }
        runCurrent()

        fixture.presenter.uploadAvatar(
            ImageUpload(byteArrayOf(1), "image/jpeg", "avatar.jpg")
        )
        runCurrent()

        assertEquals(oldAvatar, fixture.presenter.state.value.avatarUrl)
        assertEquals(0, fixture.userRepository.updateCalls)
        assertFalse(fixture.presenter.state.value.isUploadingAvatar)
        assertEquals(
            ProfileMessageKind.ERROR,
            assertIs<ProfileUiEvent.Message>(
                fixture.presenter.state.value.pendingEvent?.event
            ).kind
        )
    }

    @Test
    fun oneTimeEventDoesNotReplayAfterConsume() = runTest {
        val fixture = fixture(authenticatedState())
        runCurrent()

        fixture.presenter.avatarReadFailed("Read failed")
        val event = assertNotNull(fixture.presenter.state.value.pendingEvent)
        fixture.presenter.consumeEvent(event.id)
        fixture.presenter.consumeEvent(event.id)

        assertNull(fixture.presenter.state.value.pendingEvent)
    }

    @Test
    fun everyMainMenuDestinationAndShareEmitTheExactEvent() = runTest {
        val fixture = fixture(authenticatedState())
        runCurrent()

        ProfileDestination.entries.forEach { destination ->
            fixture.presenter.requestNavigation(destination)
            val event = assertNotNull(fixture.presenter.state.value.pendingEvent)
            assertEquals(destination, assertIs<ProfileUiEvent.Navigate>(event.event).destination)
            fixture.presenter.consumeEvent(event.id)
        }

        fixture.presenter.requestShare()
        assertIs<ProfileUiEvent.Share>(fixture.presenter.state.value.pendingEvent?.event)
    }

    @Test
    fun logoutConfirmationEmitsOnlyOnceAndConsumedEventDoesNotReturn() = runTest {
        val fixture = fixture(authenticatedState())
        runCurrent()

        fixture.presenter.requestLogout()
        assertTrue(fixture.presenter.state.value.isLogoutDialogVisible)
        fixture.presenter.confirmLogout()
        val first = assertNotNull(fixture.presenter.state.value.pendingEvent)
        assertIs<ProfileUiEvent.Logout>(first.event)

        fixture.presenter.confirmLogout()
        assertEquals(first.id, fixture.presenter.state.value.pendingEvent?.id)
        fixture.presenter.consumeEvent(first.id)
        assertNull(fixture.presenter.state.value.pendingEvent)
    }

    @Test
    fun unauthenticatedStateCancelsOperationsAndClearsProfileEditors() = runTest {
        val fixture = fixture(authenticatedState()).apply {
            userRepository.updateGate = CompletableDeferred()
        }
        runCurrent()
        fixture.presenter.openNameEditor()
        fixture.presenter.onNameChanged("Pending Name")
        fixture.presenter.submitName()
        runCurrent()
        assertTrue(fixture.presenter.state.value.isUpdatingProfile)

        fixture.profileState.value = AuthProfileUiState(
            authStatus = AuthStatus.Unauthenticated
        )
        runCurrent()

        assertFalse(fixture.presenter.state.value.isLoggedIn)
        assertFalse(fixture.presenter.state.value.isUpdatingProfile)
        assertFalse(fixture.presenter.state.value.isUploadingAvatar)
        assertNull(fixture.presenter.state.value.nameDraft)
        assertEquals("", fixture.presenter.state.value.userId)
    }

    private fun TestScope.fixture(
        initialProfileState: AuthProfileUiState
    ): ProfileFixture {
        val profileState = MutableStateFlow(initialProfileState)
        val userRepository = FakeProfileUserRepository()
        val imageUploader = FakeProfileImageUploader()
        val authRepository = FakeProfileAuthRepository()
        return ProfileFixture(
            profileState = profileState,
            userRepository = userRepository,
            imageUploader = imageUploader,
            presenter = ProfileUiPresenter(
                scope = backgroundScope,
                profileState = profileState,
                updateProfile = UpdateUserProfileUseCase(userRepository),
                updateAvatar = UpdateAvatarUseCase(userRepository, imageUploader),
                changePassword = ChangePasswordUseCase(authRepository)
            )
        )
    }

    private companion object {
        val session = AuthSession("user-1", "user@example.com", "Wallet User")
        val user = User(
            id = "user-1",
            email = "user@example.com",
            username = "Wallet User",
            gender = "Khác"
        )
        const val oldAvatar = "https://example.com/old.png"
        const val newAvatar = "https://example.com/new.png"

        fun authenticatedState(profile: User = user) = AuthProfileUiState(
            authStatus = AuthStatus.Authenticated(session),
            profile = profile
        )
    }
}

private data class ProfileFixture(
    val profileState: MutableStateFlow<AuthProfileUiState>,
    val userRepository: FakeProfileUserRepository,
    val imageUploader: FakeProfileImageUploader,
    val presenter: ProfileUiPresenter
)

private class FakeProfileUserRepository : UserRepository {
    var updateCalls = 0
    var lastUpdate: UserProfileUpdate? = null
    var updateResult: RepositoryResult<Unit> = RepositoryResult.Success(Unit)
    var updateGate: CompletableDeferred<Unit>? = null

    override suspend fun getUser(userId: String): RepositoryResult<User?> =
        RepositoryResult.Success(null)

    override suspend fun createUserIfMissing(defaultUser: User): RepositoryResult<User> =
        RepositoryResult.Success(defaultUser)

    override fun observeUser(userId: String): Flow<RepositoryResult<User?>> =
        MutableStateFlow(RepositoryResult.Success(null))

    override suspend fun updateUser(
        userId: String,
        update: UserProfileUpdate
    ): RepositoryResult<Unit> {
        updateCalls += 1
        lastUpdate = update
        updateGate?.await()
        return updateResult
    }
}

private class FakeProfileImageUploader : ImageUploader {
    var uploadCalls = 0
    var uploadResult: Result<String> = Result.success("https://example.com/avatar.png")
    var uploadGate: CompletableDeferred<Unit>? = null

    override suspend fun upload(image: ImageUpload): Result<String> {
        uploadCalls += 1
        uploadGate?.await()
        return uploadResult
    }
}

private class FakeProfileAuthRepository : AuthRepository {
    override val currentSession: AuthSession = AuthSession("user-1", "user@example.com")

    override fun observeAuthState(): Flow<AuthSession?> = MutableStateFlow(currentSession)
    override suspend fun register(input: RegisterInput): RepositoryResult<AuthSession> =
        RepositoryResult.Success(currentSession)
    override suspend fun login(input: LoginInput): RepositoryResult<AuthSession> =
        RepositoryResult.Success(currentSession)
    override suspend fun resetPassword(input: ResetPasswordInput): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)
    override suspend fun changePassword(input: ChangePasswordInput): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)
    override fun logout(): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
}

private fun <T> repositoryFailure(message: String): RepositoryResult<T> =
    RepositoryResult.Failure(
        RepositoryError(RepositoryErrorCode.UNKNOWN, message)
    )
