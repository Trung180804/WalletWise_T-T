package com.example.walletwise.domain.model

import com.example.walletwise.foundation.randomUuidString

enum class DraftField { AMOUNT, TYPE, CATEGORY, DATE, PAYMENT_METHOD }
enum class DraftSource { TEXT, VOICE, MANUAL }

data class TransactionDraft(
    val id: String = randomUuidString(),
    val userId: String,
    val amount: Long? = null,
    val type: String? = null,
    val category: String? = null,
    val note: String = "",
    val timestamp: Long? = null,
    val paymentMethod: String? = null,
    val source: DraftSource = DraftSource.TEXT,
    val confidence: Map<DraftField, Float> = emptyMap(),
    val missingFields: Set<DraftField> = emptySet()
) {
    fun transaction(): Transaction? {
        val money = amount?.takeIf { it > 0L } ?: return null
        return Transaction(id, userId, type ?: return null, paymentMethod ?: return null,
            money.toDouble(), category ?: return null, note, timestamp ?: return null)
    }
}
