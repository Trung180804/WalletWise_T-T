package com.example.walletwise.domain.model

import java.util.UUID

data class RecurringTransaction(
    val id: String = UUID.randomUUID().toString(),
    val userId: String = "",
    val title: String = "",
    val amount: Double = 0.0,
    val type: String = "Chi",
    val category: String = "Hóa đơn",
    val paymentMethod: String = "Tiền mặt",
    val frequency: String = "Hàng tháng",
    val timesCount: String = "1",
    val startDate: String = "",
    val time: String = "20:15",
    val note: String = "",
    val lastExecutedDate: String = "",
    val isEnabled: Boolean = true
)
