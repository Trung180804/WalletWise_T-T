package com.example.walletwise.data.repository

import com.example.walletwise.domain.model.AuthSession
import com.example.walletwise.domain.model.ChangePasswordInput
import com.example.walletwise.domain.model.LoginInput
import com.example.walletwise.domain.model.RegisterInput
import com.example.walletwise.domain.model.ResetPasswordInput
import com.example.walletwise.domain.model.defaultUser
import com.example.walletwise.domain.repository.AuthRepository
import com.example.walletwise.domain.repository.UserRepository
import com.example.walletwise.domain.result.RepositoryError
import com.example.walletwise.domain.result.RepositoryErrorCode
import com.example.walletwise.domain.result.RepositoryResult
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class AuthRepositoryImpl(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val userRepository: UserRepository = UserRepositoryImpl()
) : AuthRepository {
    override val currentSession: AuthSession?
        get() = auth.currentUser?.toSession()

    override fun observeAuthState(): Flow<AuthSession?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser?.toSession())
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override suspend fun register(input: RegisterInput): RepositoryResult<AuthSession> = try {
        val result = auth.createUserWithEmailAndPassword(input.email, input.password).await()
        val session = result.user?.toSession()
            ?: return RepositoryResult.Failure(
                RepositoryError(RepositoryErrorCode.UNKNOWN, "Không lấy được UID")
            )
        val registeredSession = session.copy(displayName = input.username)
        when (val profile = userRepository.createUserIfMissing(registeredSession.defaultUser())) {
            is RepositoryResult.Success -> RepositoryResult.Success(registeredSession)
            is RepositoryResult.Failure -> profile
        }
    } catch (error: Exception) {
        RepositoryResult.Failure(error.toRepositoryError("Đăng ký thất bại!"))
    }

    override suspend fun login(input: LoginInput): RepositoryResult<AuthSession> = try {
        val result = auth.signInWithEmailAndPassword(input.email, input.password).await()
        val session = result.user?.toSession()
            ?: return RepositoryResult.Failure(
                RepositoryError(RepositoryErrorCode.INVALID_CREDENTIALS, "Đăng nhập thất bại!")
            )
        RepositoryResult.Success(session)
    } catch (error: Exception) {
        RepositoryResult.Failure(error.toRepositoryError("Đăng nhập thất bại!"))
    }

    override suspend fun resetPassword(input: ResetPasswordInput): RepositoryResult<Unit> = try {
        auth.sendPasswordResetEmail(input.email).await()
        RepositoryResult.Success(Unit)
    } catch (error: Exception) {
        RepositoryResult.Failure(error.toRepositoryError("Không thể gửi email khôi phục"))
    }

    override suspend fun changePassword(input: ChangePasswordInput): RepositoryResult<Unit> {
        val user = auth.currentUser
            ?: return RepositoryResult.Failure(
                RepositoryError(RepositoryErrorCode.NOT_AUTHENTICATED, "Chưa đăng nhập")
            )
        val email = user.email
            ?: return RepositoryResult.Failure(
                RepositoryError(RepositoryErrorCode.NOT_AUTHENTICATED, "Chưa đăng nhập")
            )

        try {
            user.reauthenticate(EmailAuthProvider.getCredential(email, input.currentPassword)).await()
        } catch (_: Exception) {
            return RepositoryResult.Failure(
                RepositoryError(
                    RepositoryErrorCode.INVALID_CREDENTIALS,
                    "Mật khẩu hiện tại không chính xác!"
                )
            )
        }

        return try {
            user.updatePassword(input.newPassword).await()
            RepositoryResult.Success(Unit)
        } catch (error: Exception) {
            RepositoryResult.Failure(error.toRepositoryError("Không thể đổi mật khẩu!"))
        }
    }

    override fun logout(): RepositoryResult<Unit> = try {
        auth.signOut()
        RepositoryResult.Success(Unit)
    } catch (error: Exception) {
        RepositoryResult.Failure(error.toRepositoryError("Đăng xuất thất bại!"))
    }

    private fun FirebaseUser.toSession(): AuthSession = AuthSession(
        userId = uid,
        email = email.orEmpty(),
        displayName = displayName.orEmpty()
    )
}
