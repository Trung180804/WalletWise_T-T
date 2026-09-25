package com.example.walletwise.foundation

interface Platform {
    val name: String
}

expect fun currentPlatform(): Platform
