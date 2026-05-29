package com.example.walletwise.domain.repository

interface AuthRepository {
    fun isUserLoggedIn(): Boolean
    suspend fun register(email: String, pass: String, username: String): Result<Boolean>
    suspend fun login(email: String, pass: String): Result<Boolean>
    suspend fun resetPassword(email: String): Result<Boolean>
    fun logout()
}