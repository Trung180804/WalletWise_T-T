package com.example.walletwise.foundation

import java.util.UUID

actual fun currentEpochMilliseconds(): Long = System.currentTimeMillis()

actual fun randomUuidString(): String = UUID.randomUUID().toString()
