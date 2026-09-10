package com.example.walletwise.foundation

import platform.Foundation.NSDate
import platform.Foundation.NSUUID

actual fun currentEpochMilliseconds(): Long =
    (NSDate().timeIntervalSince1970 * 1_000.0).toLong()

actual fun randomUuidString(): String = NSUUID().UUIDString
