package com.example.walletwise.data.mapper

import com.example.walletwise.domain.model.BudgetPlan
import com.example.walletwise.domain.model.BudgetRule
import com.example.walletwise.domain.model.Category
import com.example.walletwise.domain.model.RecurringTransaction
import com.example.walletwise.domain.model.Reminder
import com.example.walletwise.domain.model.REMINDER_FREQUENCY_DAILY
import com.example.walletwise.domain.model.RECURRING_FREQUENCY_MONTHLY
import com.example.walletwise.domain.model.TRANSACTION_TYPE_EXPENSE
import com.example.walletwise.domain.model.Transaction
import com.example.walletwise.domain.model.User
import com.example.walletwise.domain.model.UserProfileUpdate

/**
 * Pure Kotlin mapping at the Firestore boundary.
 *
 * Field names and primitive wire values intentionally match the existing
 * Android documents. Platform Firebase adapters are responsible only for
 * converting their native snapshot values to and from these maps.
 */
object FirestoreWireMapper {
    private const val ENABLED = "enabled"
    private const val IS_ENABLED = "isEnabled"

    fun userFromMap(documentId: String, data: Map<String, Any?>): User = User(
        id = data.string("id", documentId),
        email = data.string("email"),
        username = data.string("username"),
        avatarUrl = data.string("avatarUrl"),
        gender = data.string("gender", "Khác"),
        currentStreak = data.int("currentStreak"),
        lastRecordDate = data.string("lastRecordDate")
    )

    fun userToMap(user: User): Map<String, Any> = mapOf(
        "id" to user.id,
        "email" to user.email,
        "username" to user.username,
        "avatarUrl" to user.avatarUrl,
        "gender" to user.gender,
        "currentStreak" to user.currentStreak,
        "lastRecordDate" to user.lastRecordDate
    )

    /** Exact five-field payload historically created for a new Firebase user. */
    fun defaultUserToMap(user: User): Map<String, Any> = mapOf(
        "id" to user.id,
        "email" to user.email,
        "username" to user.username,
        "currentStreak" to user.currentStreak,
        "lastRecordDate" to user.lastRecordDate
    )

    fun userProfileUpdateToMap(update: UserProfileUpdate): Map<String, Any> = buildMap {
        update.username?.let { put("username", it) }
        update.gender?.let { put("gender", it) }
        update.avatarUrl?.let { put("avatarUrl", it) }
    }

    fun transactionFromMap(
        documentId: String,
        ownerUserId: String,
        data: Map<String, Any?>
    ): Transaction = Transaction(
        id = data.string("id", documentId),
        userId = data.string("userId", ownerUserId),
        type = data.string("type"),
        paymentMethod = data.string("paymentMethod", "Tiền mặt"),
        amount = data.double("amount"),
        category = data.string("category"),
        note = data.string("note"),
        timestamp = data.transactionTimestampOrNull("timestamp") ?: INVALID_TRANSACTION_TIMESTAMP,
        imageUrl = data.string("imageUrl"),
        categoryId = data.string("categoryId")
    )

    fun transactionToMap(transaction: Transaction): Map<String, Any> = mapOf(
        "id" to transaction.id,
        "userId" to transaction.userId,
        "type" to transaction.type,
        "paymentMethod" to transaction.paymentMethod,
        "amount" to transaction.amount,
        "category" to transaction.category,
        "note" to transaction.note,
        "timestamp" to transaction.timestamp,
        "imageUrl" to transaction.imageUrl
    ) + if (transaction.categoryId.isNotBlank()) mapOf("categoryId" to transaction.categoryId) else emptyMap()

    fun categoryFromMap(documentId: String, data: Map<String, Any?>): Category = Category(
        id = data.string("id", documentId),
        name = data.string("name"),
        icon = data.string("icon"),
        type = data.string("type", TRANSACTION_TYPE_EXPENSE),
        isCustom = data.boolean("isCustom") ?: false,
        sortOrder = data.int("sortOrder")
    )

    fun categoryToMap(category: Category): Map<String, Any> = mapOf(
        "id" to category.id,
        "name" to category.name,
        "icon" to category.icon,
        "type" to category.type,
        "isCustom" to category.isCustom,
        "sortOrder" to category.sortOrder
    )

    fun budgetPlanFromMap(documentId: String, data: Map<String, Any?>): BudgetPlan =
        BudgetPlan(
            id = data.string("id", documentId),
            monthYear = data.string("monthYear"),
            totalBudget = data.double("totalBudget"),
            ruleType = BudgetRule.fromWireValueOrDefault(
                data.string("ruleType", "50_30_20")
            ).wireValue,
            needsLimit = data.double("needsLimit"),
            wantsLimit = data.double("wantsLimit"),
            savingsLimit = data.double("savingsLimit"),
            needsSpent = data.double("needsSpent"),
            wantsSpent = data.double("wantsSpent"),
            savingsSpent = data.double("savingsSpent")
        )

    fun budgetPlanToMap(plan: BudgetPlan): Map<String, Any> = mapOf(
        "id" to plan.id,
        "monthYear" to plan.monthYear,
        "totalBudget" to plan.totalBudget,
        "ruleType" to BudgetRule.fromWireValueOrDefault(plan.ruleType).wireValue,
        "needsLimit" to plan.needsLimit,
        "wantsLimit" to plan.wantsLimit,
        "savingsLimit" to plan.savingsLimit,
        "needsSpent" to plan.needsSpent,
        "wantsSpent" to plan.wantsSpent,
        "savingsSpent" to plan.savingsSpent
    )

    fun reminderFromMap(
        documentId: String,
        ownerUserId: String,
        data: Map<String, Any?>
    ): Reminder = Reminder(
        id = documentId,
        userId = ownerUserId,
        title = data.string("title"),
        frequency = data.string("frequency", REMINDER_FREQUENCY_DAILY),
        startDate = data.string("startDate"),
        time = data.string("time", "20:15"),
        note = data.string("note"),
        isEnabled = data.enabledValue()
    )

    fun reminderToMap(reminder: Reminder): Map<String, Any> = mapOf(
        "id" to reminder.id,
        "userId" to reminder.userId,
        "title" to reminder.title,
        "frequency" to reminder.frequency,
        "startDate" to reminder.startDate,
        "time" to reminder.time,
        "note" to reminder.note,
        ENABLED to reminder.isEnabled
    )

    fun recurringFromMap(
        documentId: String,
        ownerUserId: String,
        data: Map<String, Any?>
    ): RecurringTransaction = RecurringTransaction(
        id = documentId,
        userId = ownerUserId,
        title = data.string("title"),
        amount = data.double("amount"),
        type = data.string("type", "Chi"),
        category = data.string("category", "Hóa đơn"),
        paymentMethod = data.string("paymentMethod", "Tiền mặt"),
        frequency = data.string("frequency", RECURRING_FREQUENCY_MONTHLY),
        timesCount = data.string("timesCount", com.example.walletwise.domain.model.RECURRING_TIMES_UNLIMITED),
        startDate = data.string("startDate"),
        time = data.string("time", "20:15"),
        note = data.string("note"),
        lastExecutedDate = data.string("lastExecutedDate"),
        isEnabled = data.enabledValue()
    )

    fun recurringToMap(recurring: RecurringTransaction): Map<String, Any> = mapOf(
        "id" to recurring.id,
        "userId" to recurring.userId,
        "title" to recurring.title,
        "amount" to recurring.amount,
        "type" to recurring.type,
        "category" to recurring.category,
        "paymentMethod" to recurring.paymentMethod,
        "frequency" to recurring.frequency,
        "timesCount" to recurring.timesCount,
        "startDate" to recurring.startDate,
        "time" to recurring.time,
        "note" to recurring.note,
        "lastExecutedDate" to recurring.lastExecutedDate,
        ENABLED to recurring.isEnabled
    )

    fun enabledFields(isEnabled: Boolean): Map<String, Any> =
        mapOf(ENABLED to isEnabled)

    private fun Map<String, Any?>.string(key: String, default: String = ""): String =
        when (val value = this[key]) {
            is String -> value
            is Number -> value.toString()
            else -> default
        }

    private fun Map<String, Any?>.double(key: String): Double =
        (this[key] as? Number)?.toDouble() ?: 0.0

    private fun Map<String, Any?>.int(key: String): Int =
        (this[key] as? Number)?.toInt() ?: 0

    private fun Map<String, Any?>.longOrNull(key: String): Long? =
        (this[key] as? Number)?.toLong()

    private fun Map<String, Any?>.transactionTimestampOrNull(key: String): Long? =
        when (val value = this[key]) {
            is Number -> value.toLong()
            is FirestoreTimestampValue -> value.toEpochMillisecondsOrNull()
            else -> null
        }

    private fun Map<String, Any?>.enabledValue(): Boolean =
        boolean(ENABLED) ?: boolean(IS_ENABLED) ?: true

    private fun Map<String, Any?>.boolean(key: String): Boolean? =
        when (val value = this[key]) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            is String -> value.toBooleanStrictOrNull()
            else -> null
        }

    private const val INVALID_TRANSACTION_TIMESTAMP = 0L
}

/** Platform-neutral form of a legacy Firestore Timestamp read by a Firebase adapter. */
data class FirestoreTimestampValue(
    val seconds: Long,
    val nanoseconds: Int
) {
    fun toEpochMillisecondsOrNull(): Long? {
        if (nanoseconds !in 0..999_999_999) return null
        if (seconds < Long.MIN_VALUE / 1_000L || seconds > Long.MAX_VALUE / 1_000L) return null
        val base = seconds * 1_000L
        val adjustment = nanoseconds / 1_000_000L
        if (base > Long.MAX_VALUE - adjustment) return null
        return base + adjustment
    }
}
