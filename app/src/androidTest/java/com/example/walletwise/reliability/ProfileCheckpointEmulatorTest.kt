package com.example.walletwise.reliability

import androidx.test.platform.app.InstrumentationRegistry
import com.example.walletwise.BuildConfig
import com.example.walletwise.testing.DebugFirebaseBootstrap
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import com.example.walletwise.data.mapper.FirestoreWireMapper
import com.example.walletwise.domain.model.Transaction
import com.example.walletwise.data.repository.BudgetPlanRepositoryImpl
import com.example.walletwise.domain.model.BudgetPlan
import com.example.walletwise.domain.result.RepositoryResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Isolated session preparation; never connects to production or deletes existing data. */
class ProfileCheckpointEmulatorTest {
    @Test
    fun prepareLegacyProfileSession(): Unit = runBlocking {
        assertTrue(BuildConfig.DEBUG && BuildConfig.USE_FIREBASE_EMULATOR)
        assertEquals("demo-walletwise", FirebaseApp.getInstance().options.projectId)
        val auth = FirebaseAuth.getInstance()
        auth.signOut()
        val uid = requireNotNull(auth.signInAnonymously().await().user).uid
        FirebaseFirestore.getInstance().collection("users").document(uid)
            .set(mapOf("username" to "Legacy checkpoint", "avatarUrl" to "https://invalid.example/avatar.png")).await()
    }

    @Test
    fun budgetPersistenceIsBackwardCompatibleAndIdempotent(): Unit = runBlocking {
        assertTrue(BuildConfig.DEBUG && BuildConfig.USE_FIREBASE_EMULATOR)
        assertEquals("demo-walletwise", FirebaseApp.getInstance().options.projectId)
        val uid = requireNotNull(FirebaseAuth.getInstance().currentUser).uid
        val month = "09-2036"
        val repository = BudgetPlanRepositoryImpl()
        val first = BudgetPlan(uid, month, 10_000_000.0, "JARS", 5_500_000.0, 1_000_000.0, 3_500_000.0)
        assertTrue(repository.saveBudgetPlan(uid, first) is RepositoryResult.Success)
        assertTrue(repository.saveBudgetPlan(uid, first.copy(totalBudget = 12_000_000.0)) is RepositoryResult.Success)
        val documents = FirebaseFirestore.getInstance().collection("users").document(uid)
            .collection("budgets").get().await().documents.filter { it.id == month }
        assertEquals(1, documents.size)
        val reread = repository.getBudgetPlan(uid, month)
        assertEquals(12_000_000.0, (reread as RepositoryResult.Success).value?.totalBudget)
        assertTrue(repository.saveBudgetPlan(uid, first.copy(ruleType = "50_30_20")) is RepositoryResult.Success)
        val changedMethod = repository.getBudgetPlan(uid, month) as RepositoryResult.Success
        assertEquals("50_30_20", changedMethod.value?.ruleType)
        assertEquals(1, FirebaseFirestore.getInstance().collection("users").document(uid)
            .collection("budgets").get().await().documents.count { it.id == month })

        val legacyMonth = "08-2036"
        FirebaseFirestore.getInstance().collection("users").document(uid).collection("budgets")
            .document(legacyMonth).set(mapOf("totalBudget" to 9_000_000L)).await()
        val legacy = repository.getBudgetPlan(uid, legacyMonth) as RepositoryResult.Success
        assertEquals("50_30_20", legacy.value?.ruleType)
        assertEquals(9_000_000.0, legacy.value?.totalBudget)
    }

    @Test
    fun seedSearchTransactions(): Unit = runBlocking {
        assertTrue(BuildConfig.DEBUG && BuildConfig.USE_FIREBASE_EMULATOR)
        assertEquals("demo-walletwise", FirebaseApp.getInstance().options.projectId)
        val uid = requireNotNull(FirebaseAuth.getInstance().currentUser).uid
        val transactions = listOf(
            Transaction("checkpoint-search-meal", uid, "Chi", "Tiền mặt", 100_000.0, "Ăn uống", "Ăn sáng", System.currentTimeMillis()),
            Transaction("checkpoint-search-salary", uid, "Thu", "Chuyển khoản", 5_000_000.0, "Lương", "Tháng 9", System.currentTimeMillis() - 1_000)
        )
        transactions.forEach { transaction ->
            FirebaseFirestore.getInstance().collection("users").document(uid)
                .collection("transactions").document(transaction.id)
                .set(FirestoreWireMapper.transactionToMap(transaction)).await()
        }
    }

    @Test
    fun prepareMissingProfileSession(): Unit = runBlocking {
        assertTrue(BuildConfig.DEBUG && BuildConfig.USE_FIREBASE_EMULATOR)
        assertEquals("demo-walletwise", FirebaseApp.getInstance().options.projectId)
        assertEquals("10.0.2.2", DebugFirebaseBootstrap.endpoint()?.host)
        val auth = FirebaseAuth.getInstance()
        auth.signOut()
        val uid = requireNotNull(auth.signInAnonymously().await().user).uid
        assertTrue(!FirebaseFirestore.getInstance().collection("users").document(uid).get().await().exists())
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("profile-checkpoint", 0).edit().putString("uid", uid).commit()
    }
}
