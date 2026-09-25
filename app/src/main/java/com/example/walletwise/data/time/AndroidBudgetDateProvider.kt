package com.example.walletwise.data.time

import com.example.walletwise.domain.service.BudgetDate
import com.example.walletwise.domain.service.BudgetDateProvider
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class AndroidBudgetDateProvider(
    private val zoneId: ZoneId = ZoneId.systemDefault()
) : BudgetDateProvider {
    override fun currentLocalDate(): BudgetDate = LocalDate.now(zoneId).toBudgetDate()

    override fun localDateAt(epochMilliseconds: Long): BudgetDate? = runCatching {
        Instant.ofEpochMilli(epochMilliseconds).atZone(zoneId).toLocalDate().toBudgetDate()
    }.getOrNull()

    private fun LocalDate.toBudgetDate(): BudgetDate = BudgetDate(
        year = year,
        month = monthValue,
        dayOfMonth = dayOfMonth
    )
}
