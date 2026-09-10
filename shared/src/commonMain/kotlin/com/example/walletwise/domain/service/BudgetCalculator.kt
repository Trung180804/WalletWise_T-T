package com.example.walletwise.domain.service

import com.example.walletwise.domain.model.BudgetPlan
import com.example.walletwise.domain.model.BudgetRule
import com.example.walletwise.domain.model.Transaction
import kotlin.math.max

val BudgetNeedsCategories: Set<String> = setOf(
    "Ăn uống",
    "Nhà cửa",
    "Di chuyển",
    "Y tế",
    "Đi chợ",
    "Điện nước",
    "Xăng xe",
    "Tiền nhà",
    "Hóa đơn"
)

val BudgetSavingsCategories: Set<String> = setOf(
    "Tiết kiệm",
    "Đầu tư",
    "Quỹ khẩn cấp"
)

data class BudgetAllocation(
    val needs: Double,
    val wants: Double,
    val savings: Double
) {
    val total: Double get() = needs + wants + savings
}

data class BudgetSpent(
    val needs: Double = 0.0,
    val wants: Double = 0.0,
    val savings: Double = 0.0
) {
    val total: Double get() = needs + wants + savings
}

enum class BudgetProgressStatus { SAFE, WARNING, EXCEEDED }

data class BudgetGroupProgress(
    val limit: Double,
    val spent: Double,
    val remaining: Double,
    val fraction: Float,
    val percentUsed: Int,
    val status: BudgetProgressStatus
)

data class BudgetMetrics(
    val plan: BudgetPlan,
    val totalSpent: Double,
    val totalRemaining: Double,
    val remainingDays: Int,
    val dailySafeLimit: Double,
    val breakfastLimit: Double,
    val lunchLimit: Double,
    val dinnerLimit: Double,
    val needs: BudgetGroupProgress,
    val wants: BudgetGroupProgress,
    val savings: BudgetGroupProgress
)

object BudgetCalculator {
    fun allocate(totalBudget: Double, rule: BudgetRule): BudgetAllocation {
        if (!totalBudget.isFinite() || totalBudget <= 0.0) return BudgetAllocation(0.0, 0.0, 0.0)
        return when (rule) {
            BudgetRule.FIFTY_THIRTY_TWENTY -> BudgetAllocation(
                needs = totalBudget * 0.50,
                wants = totalBudget * 0.30,
                savings = totalBudget * 0.20
            )
            BudgetRule.JARS -> BudgetAllocation(
                needs = totalBudget * 0.55,
                wants = totalBudget * 0.10,
                savings = totalBudget * 0.35
            )
        }
    }

    fun calculateSpent(
        transactions: List<Transaction>,
        month: BudgetDate,
        dateProvider: BudgetDateProvider
    ): BudgetSpent {
        var needs = 0.0
        var wants = 0.0
        var savings = 0.0
        transactions.forEach { transaction ->
            if (transaction.type != "Chi" || !transaction.amount.isFinite() || transaction.amount <= 0.0) {
                return@forEach
            }
            val date = runCatching { dateProvider.localDateAt(transaction.timestamp) }.getOrNull()
                ?: return@forEach
            if (date.year != month.year || date.month != month.month) return@forEach
            when (transaction.category) {
                in BudgetNeedsCategories -> needs += transaction.amount
                in BudgetSavingsCategories -> savings += transaction.amount
                else -> wants += transaction.amount
            }
        }
        return BudgetSpent(needs = needs, wants = wants, savings = savings)
    }

    fun metrics(
        sourcePlan: BudgetPlan,
        transactions: List<Transaction>,
        date: BudgetDate,
        dateProvider: BudgetDateProvider
    ): BudgetMetrics {
        val spent = calculateSpent(transactions, date, dateProvider)
        val plan = sourcePlan.copy(
            needsSpent = spent.needs,
            wantsSpent = spent.wants,
            savingsSpent = spent.savings
        )
        val safeBudget = sourcePlan.totalBudget.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
        val totalSpent = spent.total.takeIf { it.isFinite() } ?: 0.0
        val totalRemaining = max(safeBudget - totalSpent, 0.0)
        val remainingDays = BudgetCalendar.remainingDays(date).coerceAtLeast(1)
        val daily = (totalRemaining / remainingDays).takeIf { it.isFinite() } ?: 0.0
        return BudgetMetrics(
            plan = plan,
            totalSpent = totalSpent,
            totalRemaining = totalRemaining,
            remainingDays = remainingDays,
            dailySafeLimit = daily,
            breakfastLimit = daily * 0.20,
            lunchLimit = daily * 0.35,
            dinnerLimit = daily * 0.45,
            needs = groupProgress(plan.needsLimit, spent.needs),
            wants = groupProgress(plan.wantsLimit, spent.wants),
            savings = groupProgress(plan.savingsLimit, spent.savings)
        )
    }

    fun groupProgress(limit: Double, spent: Double): BudgetGroupProgress {
        val safeLimit = limit.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
        val safeSpent = spent.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
        val ratio = if (safeLimit > 0.0) safeSpent / safeLimit else 0.0
        val status = when {
            safeSpent > safeLimit -> BudgetProgressStatus.EXCEEDED
            safeLimit > 0.0 && safeSpent >= safeLimit * 0.8 -> BudgetProgressStatus.WARNING
            else -> BudgetProgressStatus.SAFE
        }
        return BudgetGroupProgress(
            limit = safeLimit,
            spent = safeSpent,
            remaining = safeLimit - safeSpent,
            fraction = ratio.coerceIn(0.0, 1.0).toFloat(),
            percentUsed = if (safeLimit > 0.0) ratio.times(100.0).toInt() else 0,
            status = status
        )
    }
}
