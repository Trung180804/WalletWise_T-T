package com.example.walletwise.data.repository

import com.example.walletwise.data.mapper.FirestoreSchema
import com.example.walletwise.data.mapper.FirestoreWireMapper
import com.example.walletwise.domain.model.User
import com.example.walletwise.domain.model.UserProfileUpdate
import com.example.walletwise.domain.repository.UserRepository
import com.example.walletwise.domain.result.RepositoryResult
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class UserRepositoryImpl(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : UserRepository {
    override suspend fun getUser(userId: String): RepositoryResult<User?> = try {
        val snapshot = firestore.collection(FirestoreSchema.USERS)
            .document(userId)
            .get()
            .await()
        RepositoryResult.Success(
            if (snapshot.exists()) {
                FirestoreWireMapper.userFromMap(snapshot.id, snapshot.data.orEmpty())
            } else {
                null
            }
        )
    } catch (error: Exception) {
        RepositoryResult.Failure(error.toRepositoryError("Không thể tải hồ sơ người dùng."))
    }

    override suspend fun createUserIfMissing(defaultUser: User): RepositoryResult<User> = try {
        val reference = firestore.collection(FirestoreSchema.USERS).document(defaultUser.id)
        val resolvedUser = firestore.runTransaction { transaction ->
            val snapshot = transaction.get(reference)
            if (snapshot.exists()) {
                FirestoreWireMapper.userFromMap(snapshot.id, snapshot.data.orEmpty())
            } else {
                transaction.set(reference, FirestoreWireMapper.defaultUserToMap(defaultUser))
                defaultUser
            }
        }.await()
        RepositoryResult.Success(resolvedUser)
    } catch (error: Exception) {
        RepositoryResult.Failure(error.toRepositoryError("Không thể tạo hồ sơ người dùng."))
    }

    override fun observeUser(userId: String): Flow<RepositoryResult<User?>> = callbackFlow {
        val registration = firestore.collection(FirestoreSchema.USERS)
            .document(userId)
            .addSnapshotListener { snapshot, error ->
                when {
                    error != null -> trySend(
                        RepositoryResult.Failure(
                            error.toRepositoryError("Không thể lắng nghe hồ sơ người dùng.")
                        )
                    )
                    snapshot == null || !snapshot.exists() ->
                        trySend(RepositoryResult.Success(null))
                    else -> runCatching {
                        FirestoreWireMapper.userFromMap(snapshot.id, snapshot.data.orEmpty())
                    }.fold(
                        onSuccess = { trySend(RepositoryResult.Success(it)) },
                        onFailure = {
                            trySend(
                                RepositoryResult.Failure(
                                    it.toRepositoryError("Dữ liệu hồ sơ người dùng không hợp lệ.")
                                )
                            )
                        }
                    )
                }
            }
        awaitClose { registration.remove() }
    }

    override suspend fun updateUser(
        userId: String,
        update: UserProfileUpdate
    ): RepositoryResult<Unit> = try {
        val fields = FirestoreWireMapper.userProfileUpdateToMap(update)
        if (fields.isNotEmpty()) {
            firestore.collection(FirestoreSchema.USERS)
                .document(userId)
                .set(fields, SetOptions.merge())
                .await()
        }
        RepositoryResult.Success(Unit)
    } catch (error: Exception) {
        RepositoryResult.Failure(error.toRepositoryError("Cập nhật hồ sơ thất bại!"))
    }
}
