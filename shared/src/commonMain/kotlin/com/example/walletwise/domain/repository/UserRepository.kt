package com.example.walletwise.domain.repository

import com.example.walletwise.domain.model.User
import com.example.walletwise.domain.model.UserProfileUpdate
import com.example.walletwise.domain.result.RepositoryResult
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    suspend fun getUser(userId: String): RepositoryResult<User?>

    suspend fun createUserIfMissing(defaultUser: User): RepositoryResult<User>

    fun observeUser(userId: String): Flow<RepositoryResult<User?>>

    suspend fun updateUser(
        userId: String,
        update: UserProfileUpdate
    ): RepositoryResult<Unit>
}
