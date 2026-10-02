package com.example.walletwise.data.time

import com.example.walletwise.domain.service.RecurringDateTimeProvider
import com.example.walletwise.domain.service.ReminderLocalDate
import com.example.walletwise.domain.service.ReminderLocalDateTime
import com.example.walletwise.domain.service.ReminderLocalTime
import java.time.LocalDateTime
import java.time.Instant
import java.time.ZoneId

class AndroidRecurringDateTimeProvider(
    private val zoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() },
    private val epochMillisecondsProvider: () -> Long = { System.currentTimeMillis() }
) : RecurringDateTimeProvider {
    override fun currentLocalDateTime(): ReminderLocalDateTime =
        Instant.ofEpochMilli(epochMillisecondsProvider()).atZone(zoneIdProvider()).toLocalDateTime().toShared()

    override fun toEpochMilliseconds(dateTime: ReminderLocalDateTime): Long =
        dateTime.toJava().atZone(zoneIdProvider()).toInstant().toEpochMilli()

    override fun currentEpochMilliseconds(): Long = epochMillisecondsProvider()

    private fun LocalDateTime.toShared() = ReminderLocalDateTime(
        date = ReminderLocalDate(year, monthValue, dayOfMonth),
        time = ReminderLocalTime(hour, minute)
    )

    private fun ReminderLocalDateTime.toJava(): LocalDateTime = LocalDateTime.of(
        date.year,
        date.month,
        date.dayOfMonth,
        time.hour,
        time.minute
    )
}
