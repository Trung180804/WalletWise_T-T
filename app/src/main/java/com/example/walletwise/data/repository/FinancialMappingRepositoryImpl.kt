package com.example.walletwise.data.repository

import com.example.walletwise.domain.model.BudgetRule
import com.example.walletwise.domain.repository.FinancialMappingRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

class FinancialMappingRepositoryImpl : FinancialMappingRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private fun document(userId: String) = db.collection("users").document(userId).collection("settings").document("financialCategoryMappings")
    override suspend fun load(userId: String, rule: BudgetRule): Result<Map<String, String>> = try {
        require(auth.currentUser?.uid == userId)
        val data = document(userId).get().await().get(rule.wireValue) as? Map<*, *>
        require(auth.currentUser?.uid == userId)
        Result.success(data.orEmpty().entries.mapNotNull { (key, value) ->
            if (key is String && value is String) key to value else null
        }.toMap())
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { Result.failure(IllegalStateException("Không tải được phân nhóm danh mục.")) }

    override suspend fun save(userId: String, rule: BudgetRule, mapping: Map<String, String>): Result<Boolean> = try {
        require(auth.currentUser?.uid == userId)
        document(userId).set(mapOf(rule.wireValue to mapping), SetOptions.merge()).await()
        require(auth.currentUser?.uid == userId)
        Result.success(true)
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { Result.failure(IllegalStateException("Chưa lưu được phân nhóm danh mục.")) }
}
