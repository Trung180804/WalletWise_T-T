package com.example.walletwise.data.image

import android.content.Context
import android.net.Uri
import com.example.walletwise.domain.result.AuthUseCaseResult
import com.example.walletwise.domain.usecase.profile.UpdateAvatarUseCase

class AndroidAvatarUpdater(
    private val imageReader: AndroidImageReader,
    private val updateAvatar: UpdateAvatarUseCase
) {
    suspend fun update(
        userId: String,
        previousAvatarUrl: String,
        uri: Uri,
        context: Context
    ): AuthUseCaseResult<String> {
        val image = imageReader.read(uri, context).getOrElse { error ->
            return AuthUseCaseResult.RepositoryFailure(
                com.example.walletwise.domain.result.RepositoryError(
                    code = com.example.walletwise.domain.result.RepositoryErrorCode.UNKNOWN,
                    message = error.message ?: "Lỗi tải ảnh"
                )
            )
        }
        return updateAvatar(userId, previousAvatarUrl, image)
    }
}
