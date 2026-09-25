package com.example.walletwise.data.draft

import com.example.walletwise.domain.service.DraftDateTimeProvider
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.ZoneId

class AndroidDraftDateTimeProvider : DraftDateTimeProvider {
    override fun now() = System.currentTimeMillis()
    override fun relativeDay(days: Int) = ZonedDateTime.now().plusDays(days.toLong()).toInstant().toEpochMilli()
    override fun date(year: Int, month: Int, day: Int): Long? = try {
        LocalDate.of(year, month, day).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    } catch (_: java.time.DateTimeException) { null }
}
