package com.example.walletwise.domain.model

data class User(
    val id: String = "",
    val email: String = "",
    val username: String = "",
    val avatarUrl: String = "",
    val gender: String = "Khác",
    val currentStreak: Int = 0,
    val lastRecordDate: String = ""
)