package com.example.walletwise.data.repository

import com.example.walletwise.domain.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

class AuthRepositoryImpl : AuthRepository {
    // Khởi tạo Firebase Auth
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    override fun isUserLoggedIn(): Boolean {
        // Nếu có currentUser nghĩa là đã đăng nhập vĩnh viễn (Token còn hạn)
        return auth.currentUser != null
    }

    override suspend fun register(email: String, pass: String, username: String): Result<Boolean> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, pass).await()
            // Lệnh .await() giúp code đứng đợi Firebase trả kết quả về
            Result.success(result.user != null)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun login(email: String, pass: String): Result<Boolean> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, pass).await()
            Result.success(result.user != null)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun resetPassword(email: String): Result<Boolean> {
        return try {
            auth.sendPasswordResetEmail(email).await()
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun logout() {
        auth.signOut()
    }
}