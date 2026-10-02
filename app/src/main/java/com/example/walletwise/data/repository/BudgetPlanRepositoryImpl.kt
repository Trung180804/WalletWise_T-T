package com.example.walletwise.data.repository

import com.example.walletwise.data.mapper.FirestoreSchema
import com.example.walletwise.data.mapper.FirestoreWireMapper
import com.example.walletwise.domain.model.BudgetPlan
import com.example.walletwise.domain.repository.BudgetPlanRepository
import com.example.walletwise.domain.result.RepositoryResult
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class BudgetPlanRepositoryImpl(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) : BudgetPlanRepository {
    override fun observeBudgetPlan(
        userId: String,
        monthYear: String
    ): Flow<RepositoryResult<BudgetPlan?>> = callbackFlow {
        val registration = budgetDocument(userId, monthYear).addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(RepositoryResult.Failure(error.toRepositoryError("Không thể tải kế hoạch ngân sách")))
                return@addSnapshotListener
            }
            val plan = if (snapshot?.exists() == true) {
                FirestoreWireMapper.budgetPlanFromMap(snapshot.id, snapshot.data.orEmpty())
            } else {
                null
            }
            trySend(RepositoryResult.Success(plan))
        }
        awaitClose { registration.remove() }
    }

    override suspend fun getBudgetPlan(
        userId: String,
        monthYear: String
    ): RepositoryResult<BudgetPlan?> = try {
        val snapshot = budgetDocument(userId, monthYear).get().await()
        RepositoryResult.Success(
            if (snapshot.exists()) {
                FirestoreWireMapper.budgetPlanFromMap(snapshot.id, snapshot.data.orEmpty())
            } else {
                null
            }
        )
    } catch (error: Throwable) {
        RepositoryResult.Failure(error.toRepositoryError("Không thể tải kế hoạch ngân sách"))
    }

    override suspend fun saveBudgetPlan(
        userId: String,
        plan: BudgetPlan
    ): RepositoryResult<Unit> = try {
        budgetDocument(userId, plan.monthYear)
            .set(FirestoreWireMapper.budgetPlanToMap(plan))
            .await()
        RepositoryResult.Success(Unit)
    } catch (error: Throwable) {
        RepositoryResult.Failure(error.toRepositoryError("Không thể lưu kế hoạch ngân sách"))
    }

    private fun budgetDocument(userId: String, monthYear: String) =
        db.collection(FirestoreSchema.USERS)
            .document(userId)
            .collection(FirestoreSchema.BUDGETS)
            .document(monthYear)
}
