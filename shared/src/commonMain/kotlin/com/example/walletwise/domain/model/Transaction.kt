package com.example.walletwise.domain.model

import com.example.walletwise.foundation.currentEpochMilliseconds

data class Transaction(
    val id: String = "",
    val userId: String = "",
    val type: String = "", // "Thu" hoặc "Chi"
    val paymentMethod: String = "Tiền mặt",
    val amount: Double = 0.0,
    val category: String = "",
    val note: String = "",
    val timestamp: Long = currentEpochMilliseconds(),
    val imageUrl: String = "", // Đường dẫn ảnh local hoặc Firebase Storage
    val categoryId: String = "" // Optional stable reference; legacy records still use category name.
)
