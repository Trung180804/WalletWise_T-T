package com.example.walletwise.domain.repository

import com.example.walletwise.domain.model.AuthSession
import com.example.walletwise.domain.model.SupportMessage
import com.example.walletwise.domain.model.SupportSnapshot
import com.example.walletwise.domain.result.RepositoryResult
import kotlinx.coroutines.flow.Flow

interface SupportRepository {
    fun observeMessages(userId: String): Flow<RepositoryResult<SupportSnapshot>>
    suspend fun send(session: AuthSession, message: SupportMessage): RepositoryResult<Unit>
}
