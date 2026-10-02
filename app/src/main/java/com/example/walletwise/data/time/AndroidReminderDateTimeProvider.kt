package com.example.walletwise.data.time

import com.example.walletwise.domain.service.ReminderDateTimeProvider
import com.example.walletwise.domain.service.ReminderLocalDate
import com.example.walletwise.domain.service.ReminderLocalDateTime
import com.example.walletwise.domain.service.ReminderLocalTime
import java.time.LocalDateTime
import java.time.ZoneId

class AndroidReminderDateTimeProvider(
    private val zoneId: ZoneId = ZoneId.systemDefault()
) : ReminderDateTimeProvider {
    override fun currentLocalDateTime(): ReminderLocalDateTime = LocalDateTime.now(zoneId).toReminderDateTime()

    private fun LocalDateTime.toReminderDateTime() = ReminderLocalDateTime(
        date = ReminderLocalDate(year, monthValue, dayOfMonth),
        time = ReminderLocalTime(hour, minute)
    )
}
