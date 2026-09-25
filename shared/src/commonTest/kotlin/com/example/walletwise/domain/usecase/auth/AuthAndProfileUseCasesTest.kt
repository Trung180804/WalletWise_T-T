package com.example.walletwise.domain.usecase.auth

import com.example.walletwise.domain.model.AuthSession
import com.example.walletwise.domain.model.ImageUpload
import com.example.walletwise.domain.model.LoginInput
import com.example.walletwise.domain.model.RegisterInput
import com.example.walletwise.domain.model.ResetPasswordInput
import com.example.walletwise.domain.model.User
import com.example.walletwise.domain.model.UserProfileUpdate
import com.example.walletwise.domain.repository.AuthRepository
import com.example.walletwise.domain.repository.ImageUploader
import com.example.walletwise.domain.repository.UserRepository
import com.example.walletwise.domain.result.AuthUseCaseResult
import com.example.walletwise.domain.result.RepositoryError
import com.example.walletwise.domain.result.RepositoryErrorCode
import com.example.walletwise.domain.result.RepositoryResult
import com.example.walletwise.domain.usecase.profile.GetUserProfileUseCase
import com.example.walletwise.domain.usecase.profile.UpdateAvatarUseCase
import com.example.walletwise.domain.usecase.profile.UpdateUserProfileUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class AuthAndProfileUseCasesTest {
    @Test
    fun loginSuccessAndFailure_areMappedWithoutPlatformExceptions() = runTest {
        val repository = FakeAuthRepository()
        val useCase = LoginUseCase(repository)

        assertIs<AuthUseCaseResult.Success<AuthSession>>(
            useCase(LoginInput("user@example.com", "123456"))
        )
        repository.loginResult = failure("Login failed")
        val failed = assertIs<AuthUseCaseResult.RepositoryFailure>(
            useCase(LoginInput("user@example.com", "123456"))
        )
        assertEquals("Login failed", failed.error.message)
    }

    @Test
    fun registerSuccessAndFailure_areMappedWithoutChangingInput() = runTest {
        val repository = FakeAuthRepository()
        val useCase = RegisterUseCase(repository)
        val input = RegisterInput("user@example.com", "123456", "123456", "  User  ")

        assertIs<AuthUseCaseResult.Success<AuthSession>>(useCase(input))
        assertEquals("User", repository.lastRegisterInput?.username)
        repository.registerResult = failure("Register failed")
        assertIs<AuthUseCaseResult.RepositoryFailure>(useCase(input))
    }

    @Test
    fun resetPasswordSuccessAndFailure_areMapped() = runTest {
        val repository = FakeAuthRepository()
        val useCase = ResetPasswordUseCase(repository)

        assertIs<AuthUseCaseResult.Success<Unit>>(
            useCase(ResetPasswordInput("user@example.com"))
        )
        repository.resetResult = failure("Reset failed")
        assertIs<AuthUseCaseResult.RepositoryFailure>(
            useCase(ResetPasswordInput("user@example.com"))
        )
    }

    @Test
    fun existingProfile_isReturnedWithoutDefaultWrite() = runTest {
        val existing = User("uid", "old@example.com", "Old")
        val repository = FakeUserRepository(existing)

        val result = GetUserProfileUseCase(repository)(session())

        assertEquals(existing, assertIs<AuthUseCaseResult.Success<User>>(result).value)
        assertEquals(0, repository.createCalls)
    }

    @Test
    fun missingProfile_createsHistoricalDefaultOnce() = runTest {
        val repository = FakeUserRepository(null)

        val result = GetUserProfileUseCase(repository)(session())

        val created = assertIs<AuthUseCaseResult.Success<User>>(result).value
        assertEquals("User", created.username)
        assertEquals(1, repository.createCalls)
    }

    @Test
    fun usernameAndGenderUpdates_areTrimmedAndDelegated() = runTest {
        val repository = FakeUserRepository(User(id = "uid"))
        val useCase = UpdateUserProfileUseCase(repository)

        assertIs<AuthUseCaseResult.Success<Unit>>(
            useCase("uid", UserProfileUpdate(username = "  New Name  "))
        )
        assertEquals("New Name", repository.updates.last().username)
        assertIs<AuthUseCaseResult.Success<Unit>>(
            useCase("uid", UserProfileUpdate(gender = "Bí mật"))
        )
        assertEquals("Bí mật", repository.updates.last().gender)
    }

    @Test
    fun avatarUploadSuccess_updatesOnlyNewUrl() = runTest {
        val repository = FakeUserRepository(User(id = "uid", avatarUrl = "old-url"))
        val uploader = FakeImageUploader(Result.success("new-url"))

        val result = UpdateAvatarUseCase(repository, uploader)(
            "uid",
            "old-url",
            image()
        )

        assertEquals("new-url", assertIs<AuthUseCaseResult.Success<String>>(result).value)
        assertEquals("new-url", repository.updates.single().avatarUrl)
    }

    @Test
    fun avatarUploadFailure_keepsOldUrlAndDoesNotWriteProfile() = runTest {
        val repository = FakeUserRepository(User(id = "uid", avatarUrl = "old-url"))
        val uploader = FakeImageUploader(Result.failure(IllegalStateException("Upload failed")))

        val result = UpdateAvatarUseCase(repository, uploader)(
            "uid",
            "old-url",
            image()
        )

        assertIs<AuthUseCaseResult.RepositoryFailure>(result)
        assertEquals("old-url", repository.storedUser?.avatarUrl)
        assertEquals(0, repository.updates.size)
    }

    private fun session() = AuthSession("uid", "user@example.com", "User")

    private fun image() = ImageUpload(byteArrayOf(1, 2, 3), "image/jpeg", "avatar.jpg")
}

private class FakeAuthRepository : AuthRepository {
    private val session = AuthSession("uid", "user@example.com", "User")
    override val currentSession: AuthSession? = session
    private val authState = MutableStateFlow<AuthSession?>(session)
    var loginResult: RepositoryResult<AuthSession> = RepositoryResult.Success(session)
    var registerResult: RepositoryResult<AuthSession> = RepositoryResult.Success(session)
    var resetResult: RepositoryResult<Unit> = RepositoryResult.Success(Unit)
    var lastRegisterInput: RegisterInput? = null

    override fun observeAuthState(): Flow<AuthSession?> = authState
    override suspend fun register(input: RegisterInput): RepositoryResult<AuthSession> {
        lastRegisterInput = input
        return registerResult
    }
    override suspend fun login(input: LoginInput): RepositoryResult<AuthSession> = loginResult
    override suspend fun resetPassword(input: ResetPasswordInput): RepositoryResult<Unit> = resetResult
    override suspend fun changePassword(input: com.example.walletwise.domain.model.ChangePasswordInput) =
        RepositoryResult.Success(Unit)
    override fun logout(): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
}

private class FakeUserRepository(initial: User?) : UserRepository {
    var storedUser: User? = initial
    var createCalls = 0
    val updates = mutableListOf<UserProfileUpdate>()

    override suspend fun getUser(userId: String): RepositoryResult<User?> =
        RepositoryResult.Success(storedUser)

    override suspend fun createUserIfMissing(defaultUser: User): RepositoryResult<User> {
        createCalls++
        if (storedUser == null) storedUser = defaultUser
        return RepositoryResult.Success(storedUser!!)
    }

    override fun observeUser(userId: String): Flow<RepositoryResult<User?>> = emptyFlow()

    override suspend fun updateUser(userId: String, update: UserProfileUpdate): RepositoryResult<Unit> {
        updates += update
        storedUser = storedUser?.copy(
            username = update.username ?: storedUser!!.username,
            gender = update.gender ?: storedUser!!.gender,
            avatarUrl = update.avatarUrl ?: storedUser!!.avatarUrl
        )
        return RepositoryResult.Success(Unit)
    }
}

private class FakeImageUploader(private val result: Result<String>) : ImageUploader {
    override suspend fun upload(image: ImageUpload): Result<String> = result
}

private fun <T> failure(message: String): RepositoryResult<T> = RepositoryResult.Failure(
    RepositoryError(RepositoryErrorCode.UNKNOWN, message)
)
