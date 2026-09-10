package com.example.walletwise.data.mapper

import com.example.walletwise.domain.model.BudgetPlan
import com.example.walletwise.domain.model.RecurringTransaction
import com.example.walletwise.domain.model.Reminder
import com.example.walletwise.domain.model.Transaction
import com.example.walletwise.domain.model.User
import com.example.walletwise.domain.model.UserProfileUpdate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FirestoreCompatibilityTest {
    @Test
    fun existingCollectionPaths_areUnchanged() {
        assertEquals("users/user-1", FirestoreSchema.userDocument("user-1"))
        assertEquals("users/user-1/transactions", FirestoreSchema.transactionsCollection("user-1"))
        assertEquals(
            "users/user-1/transactions/tx-1",
            FirestoreSchema.transactionDocument("user-1", "tx-1")
        )
        assertEquals("TRANSACTIONS", FirestoreSchema.LEGACY_ROOT_TRANSACTIONS)
        assertEquals(
            "TRANSACTIONS/tx-1",
            FirestoreSchema.legacyTransactionDocument("tx-1")
        )
        assertEquals("users/user-1/budgets", FirestoreSchema.budgetsCollection("user-1"))
        assertEquals("users/user-1/reminders", FirestoreSchema.remindersCollection("user-1"))
        assertEquals(
            "users/user-1/recurring_transactions",
            FirestoreSchema.recurringTransactionsCollection("user-1")
        )
    }

    @Test
    fun oldUserDocument_readsAndWritesTheSameFields() {
        val old = mapOf<String, Any?>(
            "id" to "user-1",
            "email" to "old@example.com",
            "username" to "Old User",
            "avatarUrl" to "https://example.com/avatar.png",
            "gender" to "Khác",
            "currentStreak" to 7L,
            "lastRecordDate" to "09/09/2026"
        )

        val user = FirestoreWireMapper.userFromMap("user-1", old)

        assertEquals(User("user-1", "old@example.com", "Old User", "https://example.com/avatar.png", "Khác", 7, "09/09/2026"), user)
        assertEquals(old.keys, FirestoreWireMapper.userToMap(user).keys)
    }

    @Test
    fun newUserAndProfileUpdatePayloads_keepHistoricalFieldNames() {
        val user = User(id = "user-1", email = "old@example.com", username = "Old User")

        assertEquals(
            setOf("id", "email", "username", "currentStreak", "lastRecordDate"),
            FirestoreWireMapper.defaultUserToMap(user).keys
        )
        assertEquals(
            mapOf("username" to "New User", "gender" to "Bí mật", "avatarUrl" to "https://avatar"),
            FirestoreWireMapper.userProfileUpdateToMap(
                UserProfileUpdate("New User", "Bí mật", "https://avatar")
            )
        )
    }

    @Test
    fun legacyUserMissingOptionalFields_usesExistingDefaults() {
        val user = FirestoreWireMapper.userFromMap(
            "legacy-user",
            mapOf(
                "email" to "legacy@example.com",
                "username" to "Legacy",
                "currentStreak" to 2L,
                "lastRecordDate" to ""
            )
        )

        assertEquals("legacy-user", user.id)
        assertEquals("", user.avatarUrl)
        assertEquals(User().gender, user.gender)
    }

    @Test
    fun oldTransactionDocument_acceptsFirestoreNumericTypes() {
        val old = mapOf<String, Any?>(
            "id" to "tx-1",
            "userId" to "user-1",
            "type" to "Chi",
            "paymentMethod" to "Tiền mặt",
            "amount" to 125_000L,
            "category" to "Ăn uống",
            "note" to "Bữa trưa",
            "timestamp" to 1_725_840_000_000L,
            "imageUrl" to ""
        )

        val transaction = FirestoreWireMapper.transactionFromMap("tx-1", "user-1", old)

        assertEquals(125_000.0, transaction.amount)
        assertEquals(1_725_840_000_000L, transaction.timestamp)
        val wireMap = FirestoreWireMapper.transactionToMap(transaction)
        assertEquals(old.keys, wireMap.keys)
        assertIs<Double>(wireMap["amount"])
        assertIs<Long>(wireMap["timestamp"])
    }

    @Test
    fun legacyRootTransactionWithoutPayloadId_usesDocumentIdAndOwner() {
        val legacy = mapOf<String, Any?>(
            "userId" to "user-1",
            "type" to "Thu",
            "paymentMethod" to "Chuyển khoản",
            "amount" to 2_000_000.0,
            "category" to "Lương",
            "note" to "",
            "timestamp" to 1_725_840_000_000L,
            "imageUrl" to ""
        )

        val transaction = FirestoreWireMapper.transactionFromMap(
            documentId = "legacy-tx-1",
            ownerUserId = "user-1",
            data = legacy
        )

        assertEquals("legacy-tx-1", transaction.id)
        assertEquals("user-1", transaction.userId)
        assertEquals("Thu", transaction.type)
        assertEquals(2_000_000.0, transaction.amount)
    }

    @Test
    fun oldBudgetDocument_keepsRuleAndNumericFields() {
        val old = mapOf<String, Any?>(
            "id" to "09-2026",
            "monthYear" to "09-2026",
            "totalBudget" to 10_000_000L,
            "ruleType" to "50_30_20",
            "needsLimit" to 5_000_000L,
            "wantsLimit" to 3_000_000L,
            "savingsLimit" to 2_000_000L,
            "needsSpent" to 1_000_000L,
            "wantsSpent" to 500_000L,
            "savingsSpent" to 250_000L
        )

        val plan = FirestoreWireMapper.budgetPlanFromMap("09-2026", old)

        assertEquals("50_30_20", plan.ruleType)
        assertEquals(10_000_000.0, plan.totalBudget)
        assertEquals(old.keys, FirestoreWireMapper.budgetPlanToMap(plan).keys)
    }

    @Test
    fun reminderReadsEstablishedEnabledAndBrokenIsEnabledButWritesOnlyEnabled() {
        val established = FirestoreWireMapper.reminderFromMap(
            "reminder-1",
            "user-1",
            mapOf("title" to "Ghi chép", "enabled" to false)
        )
        val transitional = FirestoreWireMapper.reminderFromMap(
            "reminder-2",
            "user-1",
            mapOf("title" to "Ghi chép", "isEnabled" to 1L)
        )

        assertFalse(established.isEnabled)
        assertTrue(transitional.isEnabled)
        assertEquals("reminder-1", established.id)
        assertEquals("user-1", established.userId)
        assertEquals(
            setOf("id", "userId", "title", "frequency", "startDate", "time", "note", "enabled"),
            FirestoreWireMapper.reminderToMap(established).keys
        )
    }

    @Test
    fun recurringDocumentPreservesAllExistingWireFields() {
        val old = mapOf<String, Any?>(
            "id" to "ignored-payload-id",
            "userId" to "ignored-payload-owner",
            "title" to "Tiền nhà",
            "amount" to 4_000_000L,
            "type" to "Chi",
            "category" to "Hóa đơn",
            "paymentMethod" to "Tiền mặt",
            "frequency" to "Hàng tháng",
            "timesCount" to "1",
            "startDate" to "09/09/2026",
            "time" to "20:15",
            "note" to "",
            "lastExecutedDate" to "",
            "enabled" to true
        )

        val recurring = FirestoreWireMapper.recurringFromMap("recurring-1", "user-1", old)

        assertEquals("recurring-1", recurring.id)
        assertEquals("user-1", recurring.userId)
        assertEquals(4_000_000.0, recurring.amount)
        assertEquals(old.keys, FirestoreWireMapper.recurringToMap(recurring).keys)
    }

    @Test
    fun modelDefaultWireValues_remainCompatible() {
        assertEquals("Khác", User().gender)
        assertEquals("Tiền mặt", Transaction(timestamp = 1L).paymentMethod)
        assertEquals("50_30_20", BudgetPlan().ruleType)
        assertEquals("Hàng ngày", Reminder(id = "fixed").frequency)
        assertEquals("Hàng tháng", RecurringTransaction(id = "fixed").frequency)
    }
}
