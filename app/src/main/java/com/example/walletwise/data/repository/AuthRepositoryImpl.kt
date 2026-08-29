package com.example.walletwise.data.repository

import com.example.walletwise.domain.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import com.google.firebase.firestore.FirebaseFirestore

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
            val uid = result.user?.uid ?: throw Exception("Không lấy được UID")

            val userMap = mapOf(
                "id" to uid,
                "email" to email,
                "username" to username,
                "currentStreak" to 0,
                "lastRecordDate" to ""
            )
            FirebaseFirestore.getInstance().collection("users").document(uid).set(userMap).await()
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