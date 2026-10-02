package com.example.walletwise.presentation.auth

import com.example.walletwise.domain.model.AuthSession
import com.example.walletwise.domain.model.ChangePasswordInput
import com.example.walletwise.domain.model.LoginInput
import com.example.walletwise.domain.model.RegisterInput
import com.example.walletwise.domain.model.ResetPasswordInput
import com.example.walletwise.domain.model.User
import com.example.walletwise.domain.model.UserProfileUpdate
import com.example.walletwise.domain.repository.AuthRepository
import com.example.walletwise.domain.repository.UserRepository
import com.example.walletwise.domain.result.RepositoryResult
import com.example.walletwise.domain.usecase.auth.LogoutUseCase
import com.example.walletwise.domain.usecase.auth.ObserveAuthStateUseCase
import com.example.walletwise.domain.usecase.profile.GetUserProfileUseCase
import com.example.walletwise.domain.usecase.profile.ObserveUserProfileUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class AuthProfileSessionControllerTest {
    @Test
    fun missingProfileWithoutCreationUsesDefaultsAndOneListener() = runTest {
        val auth = CountingAuthRepository()
        val users = CountingUserRepository(null)
        val controller = controller(this, auth, users)
        controller.observeProfile(auth.session, ensureExists = false)
        runCurrent()
        assertEquals("uid", controller.state.value.profile?.id)
        assertEquals("User", controller.state.value.profile?.username)
        assertEquals(1, users.observeCalls)
        controller.close()
    }

    @Test
    fun uidChangeAndDisposeRejectLateProfileLoads() = runTest {
        val auth = CountingAuthRepository()
        val users = CountingUserRepository(null)
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        users.getGate = gate
        val controller = controller(this, auth, users)
        controller.observeProfile(auth.session)
        runCurrent()
        controller.observeProfile(AuthSession("other", "b@example.com", "B"), ensureExists = false)
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertEquals("other", controller.state.value.profile?.id)
        assertEquals(1, users.activeCollectors)
        controller.close()
        runCurrent()
        assertNull(controller.state.value.profile)
        assertEquals(0, users.activeCollectors)
    }

    @Test
    fun repeatedStartAndProfileLoad_doNotCreateDuplicateListeners() = runTest {
        val auth = CountingAuthRepository()
        val users = CountingUserRepository(User("uid", "user@example.com", "User"))
        val controller = controller(this, auth, users)

        controller.start()
        controller.start()
        runCurrent()
        controller.observeProfile(auth.session)
        controller.observeProfile(auth.session)
        runCurrent()

        assertEquals(1, auth.observeCalls)
        assertEquals(1, users.observeCalls)
        controller.close()
    }

    @Test
    fun logoutClearsCachedUserAndStopsProfileListener() = runTest {
        val auth = CountingAuthRepository()
        val users = CountingUserRepository(User("uid", "user@example.com", "User"))
        val controller = controller(this, auth, users)
        controller.start()
        runCurrent()
        assertIs<AuthStatus.Authenticated>(controller.state.value.authStatus)

        controller.logoutAndClear()
        runCurrent()

        assertEquals(1, auth.logoutCalls)
        assertIs<AuthStatus.Unauthenticated>(controller.state.value.authStatus)
        assertNull(controller.state.value.profile)
        assertEquals(0, users.activeCollectors)
        controller.close()
    }

    private fun controller(
        scope: CoroutineScope,
        auth: CountingAuthRepository,
        users: CountingUserRepository
    ) = AuthProfileSessionController(
        scope = scope,
        observeAuthState = ObserveAuthStateUseCase(auth),
        getUserProfile = GetUserProfileUseCase(users),
        observeUserProfile = ObserveUserProfileUseCase(users),
        logout = LogoutUseCase(auth)
    )
}

private class CountingAuthRepository : AuthRepository {
    val session = AuthSession("uid", "user@example.com", "User")
    private val state = MutableStateFlow<AuthSession?>(session)
    var observeCalls = 0
    var logoutCalls = 0

    override val currentSession: AuthSession? get() = state.value
    override fun observeAuthState(): Flow<AuthSession?> {
        observeCalls++
        return state
    }
    override suspend fun register(input: RegisterInput) = RepositoryResult.Success(session)
    override suspend fun login(input: LoginInput) = RepositoryResult.Success(session)
    override suspend fun resetPassword(input: ResetPasswordInput) = RepositoryResult.Success(Unit)
    override suspend fun changePassword(input: ChangePasswordInput) = RepositoryResult.Success(Unit)
    override fun logout(): RepositoryResult<Unit> {
        logoutCalls++
        state.value = null
        return RepositoryResult.Success(Unit)
    }
}

private class CountingUserRepository(initial: User?) : UserRepository {
    private val state = MutableStateFlow<RepositoryResult<User?>>(RepositoryResult.Success(initial))
    var observeCalls = 0
    var activeCollectors = 0
    var getGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    override suspend fun getUser(userId: String): RepositoryResult<User?> {
        kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { getGate?.await() }
        return state.value
    }
    override suspend fun createUserIfMissing(defaultUser: User): RepositoryResult<User> {
        state.value = RepositoryResult.Success(defaultUser)
        return RepositoryResult.Success(defaultUser)
    }
    override fun observeUser(userId: String): Flow<RepositoryResult<User?>> {
        observeCalls++
        return kotlinx.coroutines.flow.flow {
            activeCollectors++
            try {
                state.collect { emit(it) }
            } finally {
                activeCollectors--
            }
        }
    }
    override suspend fun updateUser(userId: String, update: UserProfileUpdate) =
        RepositoryResult.Success(Unit)
}
