package com.example.walletwise.utils

import com.example.walletwise.domain.model.RecurringTransaction
import com.example.walletwise.domain.model.Reminder

/**
 * Explicit Firestore mapping for settings records.
 *
 * Kotlin Boolean properties whose name starts with `is` are ambiguous to
 * JavaBean-based mappers. WalletWise's established schema uses `enabled`,
 * while one broken build also wrote `isEnabled`. Reading both but writing only
 * the established key avoids two sources of truth and guarantees that the
 * Firestore document id is the id used by each UI switch and alarm.
 */
object SettingsFirestoreMapper {
    private const val ENABLED = "enabled"
    private const val IS_ENABLED = "isEnabled"

    fun reminderFromMap(
        documentId: String,
        ownerUserId: String,
        data: Map<String, Any?>
    ): Reminder = Reminder(
        id = documentId,
        userId = ownerUserId,
        title = data.string("title"),
        frequency = data.string("frequency", "Hàng ngày"),
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
        amount = (data["amount"] as? Number)?.toDouble() ?: 0.0,
        type = data.string("type", "Chi"),
        category = data.string("category", "Hóa đơn"),
        paymentMethod = data.string("paymentMethod", "Tiền mặt"),
        frequency = data.string("frequency", "Hàng tháng"),
        timesCount = data.string("timesCount", "1"),
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

    fun enabledFields(isEnabled: Boolean): Map<String, Any> = mapOf(ENABLED to isEnabled)

    private fun Map<String, Any?>.string(key: String, default: String = ""): String =
        when (val value = this[key]) {
            is String -> value
            is Number -> value.toString()
            else -> default
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
}
