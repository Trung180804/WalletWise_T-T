package com.example.walletwise.domain.model

import com.example.walletwise.foundation.randomUuidString

const val TRANSACTION_TYPE_INCOME = "Thu"
const val TRANSACTION_TYPE_EXPENSE = "Chi"

const val RECURRING_FREQUENCY_DAILY = "Hàng ngày"
const val RECURRING_FREQUENCY_WEEKLY = "Hàng tuần"
const val RECURRING_FREQUENCY_MONTHLY = "Hàng tháng"
const val RECURRING_FREQUENCY_YEARLY = "Hàng năm"
const val RECURRING_TIMES_UNLIMITED = "Khác"

val RECURRING_FREQUENCY_WIRE_VALUES: List<String> = listOf(
    RECURRING_FREQUENCY_DAILY,
    RECURRING_FREQUENCY_WEEKLY,
    RECURRING_FREQUENCY_MONTHLY,
    RECURRING_FREQUENCY_YEARLY
)

val RECURRING_TIMES_COUNT_WIRE_VALUES: List<String> =
    (1..7).map(Int::toString) + RECURRING_TIMES_UNLIMITED

enum class RecurringFrequency(val wireValue: String) {
    DAILY(RECURRING_FREQUENCY_DAILY),
    WEEKLY(RECURRING_FREQUENCY_WEEKLY),
    MONTHLY(RECURRING_FREQUENCY_MONTHLY),
    YEARLY(RECURRING_FREQUENCY_YEARLY);

    companion object {
        fun fromWireValue(value: String): RecurringFrequency? =
            entries.firstOrNull { it.wireValue == value }

        /** Keeps the scheduler compatible with labels written by old mojibake builds. */
        fun fromWireValueOrDaily(value: String): RecurringFrequency {
            fromWireValue(value)?.let { return it }
            val normalized = value.lowercase()
            return when {
                "tuần" in normalized || "tuan" in normalized || "tuã" in normalized -> WEEKLY
                "tháng" in normalized || "thang" in normalized || "thã" in normalized -> MONTHLY
                "năm" in normalized || "nam" in normalized || "nä" in normalized -> YEARLY
                else -> DAILY
            }
        }
    }
}

data class RecurringTransaction(
    val id: String = randomUuidString(),
    val userId: String = "",
    val title: String = "",
    val amount: Double = 0.0,
    val type: String = TRANSACTION_TYPE_EXPENSE,
    val category: String = "Hóa đơn",
    val paymentMethod: String = "Tiền mặt",
    val frequency: String = RECURRING_FREQUENCY_MONTHLY,
    val timesCount: String = RECURRING_TIMES_UNLIMITED,
    val startDate: String = "",
    val time: String = "20:15",
    val note: String = "",
    val lastExecutedDate: String = "",
    val isEnabled: Boolean = true
)
