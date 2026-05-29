package com.example.walletwise.domain.model

data class Transaction(
    val id: String = "",
    val userId: String = "",
    val type: String = "", // "Thu" hoặc "Chi"
    val amount: Double = 0.0,
    val category: String = "",
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val imageUrl: String = "" // Lưu đường dẫn ảnh local hoặc Firebase Storage
)