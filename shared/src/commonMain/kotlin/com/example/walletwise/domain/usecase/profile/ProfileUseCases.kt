package com.example.walletwise.domain.usecase.profile

import com.example.walletwise.domain.model.AuthSession
import com.example.walletwise.domain.model.ImageUpload
import com.example.walletwise.domain.model.User
import com.example.walletwise.domain.model.UserProfileUpdate
import com.example.walletwise.domain.model.defaultUser
import com.example.walletwise.domain.repository.ImageUploader
import com.example.walletwise.domain.repository.UserRepository
import com.example.walletwise.domain.result.AuthUseCaseResult
import com.example.walletwise.domain.result.RepositoryError
import com.example.walletwise.domain.result.RepositoryErrorCode
import com.example.walletwise.domain.result.RepositoryResult
import com.example.walletwise.domain.usecase.auth.toAuthResult
import com.example.walletwise.domain.validation.AuthValidationException
import com.example.walletwise.domain.validation.AuthValidator
import kotlinx.coroutines.flow.Flow

class GetUserProfileUseCase(private val repository: UserRepository) {
    suspend operator fun invoke(session: AuthSession): AuthUseCaseResult<User> {
        return when (val result = repository.getUser(session.userId)) {
            is RepositoryResult.Failure -> AuthUseCaseResult.RepositoryFailure(result.error)
            is RepositoryResult.Success -> {
                val existing = result.value
                if (existing != null) {
                    AuthUseCaseResult.Success(existing)
                } else {
                    repository.createUserIfMissing(session.defaultUser()).toAuthResult()
                }
            }
        }
    }
}

class ObserveUserProfileUseCase(private val repository: UserRepository) {
    operator fun invoke(userId: String): Flow<RepositoryResult<User?>> =
        repository.observeUser(userId)
}

class UpdateUserProfileUseCase(private val repository: UserRepository) {
    suspend operator fun invoke(
        userId: String,
        update: UserProfileUpdate
    ): AuthUseCaseResult<Unit> {
        val validated = AuthValidator.validateProfileUpdate(update).getOrElse { error ->
            return error.asValidationFailure()
        }
        return repository.updateUser(userId, validated).toAuthResult()
    }
}

class UpdateAvatarUseCase(
    private val repository: UserRepository,
    private val imageUploader: ImageUploader
) {
    suspend operator fun invoke(
        userId: String,
        previousAvatarUrl: String,
        image: ImageUpload
    ): AuthUseCaseResult<String> {
        val uploadedUrl = imageUploader.upload(image).getOrElse { error ->
            return AuthUseCaseResult.RepositoryFailure(
                RepositoryError(
                    code = RepositoryErrorCode.NETWORK,
                    message = error.message ?: "Không thể tải ảnh đại diện lên máy chủ!"
                )
            )
        }
        if (uploadedUrl.isBlank()) {
            return AuthUseCaseResult.RepositoryFailure(
                RepositoryError(
                    code = RepositoryErrorCode.UNKNOWN,
                    message = "Không thể tải ảnh đại diện lên máy chủ!"
                )
            )
        }

        return when (
            val update = repository.updateUser(
                userId,
                UserProfileUpdate(avatarUrl = uploadedUrl)
            )
        ) {
            is RepositoryResult.Success -> AuthUseCaseResult.Success(uploadedUrl)
            is RepositoryResult.Failure -> AuthUseCaseResult.RepositoryFailure(update.error)
        }
    }
}

private fun Throwable.asValidationFailure(): AuthUseCaseResult.ValidationFailure =
    if (this is AuthValidationException) {
        AuthUseCaseResult.ValidationFailure(message.orEmpty(), error.name)
    } else {
        AuthUseCaseResult.ValidationFailure(message.orEmpty(), "UNKNOWN")
    }
