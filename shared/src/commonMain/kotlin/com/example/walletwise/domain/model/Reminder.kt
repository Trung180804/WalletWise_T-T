package com.example.walletwise.domain.model

import com.example.walletwise.foundation.randomUuidString

const val REMINDER_FREQUENCY_DAILY = "Hàng ngày"
const val REMINDER_FREQUENCY_WEEKLY = "Hàng tuần"
const val REMINDER_FREQUENCY_MONTHLY = "Hàng tháng"
const val DEFAULT_REMINDER_TITLE = "Lời nhắc nhở"
const val DEFAULT_REMINDER_NOTE = "Đừng quên ghi lại các khoản chi tiêu của bạn!"

enum class ReminderFrequency(val wireValue: String) {
    DAILY(REMINDER_FREQUENCY_DAILY),
    WEEKLY(REMINDER_FREQUENCY_WEEKLY),
    MONTHLY(REMINDER_FREQUENCY_MONTHLY);

    companion object {
        fun fromWireValue(value: String): ReminderFrequency? =
            entries.firstOrNull { it.wireValue == value }

        /** Unknown legacy values historically fell back to a daily schedule. */
        fun fromWireValueOrDaily(value: String): ReminderFrequency = when {
            value == REMINDER_FREQUENCY_WEEKLY || "tuần" in value.lowercase() -> WEEKLY
            value == REMINDER_FREQUENCY_MONTHLY || "tháng" in value.lowercase() -> MONTHLY
            else -> DAILY
        }
    }
}

data class Reminder(
    val id: String = randomUuidString(),
    val userId: String = "",
    val title: String = "",
    val frequency: String = REMINDER_FREQUENCY_DAILY,
    val startDate: String = "",
    val time: String = "20:15",
    val note: String = "",
    val isEnabled: Boolean = true
)
