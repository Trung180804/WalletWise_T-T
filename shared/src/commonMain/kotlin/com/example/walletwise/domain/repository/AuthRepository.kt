package com.example.walletwise.domain.repository

import com.example.walletwise.domain.model.AuthSession
import com.example.walletwise.domain.model.ChangePasswordInput
import com.example.walletwise.domain.model.LoginInput
import com.example.walletwise.domain.model.RegisterInput
import com.example.walletwise.domain.model.ResetPasswordInput
import com.example.walletwise.domain.result.RepositoryResult
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val currentSession: AuthSession?

    fun observeAuthState(): Flow<AuthSession?>

    suspend fun register(input: RegisterInput): RepositoryResult<AuthSession>

    suspend fun login(input: LoginInput): RepositoryResult<AuthSession>

    suspend fun resetPassword(input: ResetPasswordInput): RepositoryResult<Unit>

    suspend fun changePassword(input: ChangePasswordInput): RepositoryResult<Unit>

    fun logout(): RepositoryResult<Unit>
}
