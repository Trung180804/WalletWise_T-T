package com.example.walletwise.domain.model

import java.util.UUID

data class Reminder(
    val id: String = UUID.randomUUID().toString(),
    val userId: String = "",
    val title: String = "",
    val frequency: String = "Hàng ngày",
    val startDate: String = "",
    val time: String = "20:15",
    val note: String = "",
    val isEnabled: Boolean = true
)
