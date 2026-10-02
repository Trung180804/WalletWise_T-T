package com.example.walletwise.domain.service

import com.example.walletwise.domain.model.*
import com.example.walletwise.domain.util.normalizeVietnameseSearchText
import kotlin.math.round

data class FinancialBucketUsage(
    val allocation: FinancialBucketAllocation,
    val spent: Long
) {
    val remaining: Long get() = allocation.amount - spent
    val usedPercent: Double get() = if (allocation.amount > 0) spent.toDouble() / allocation.amount * 100 else if (spent > 0) 100.0 else 0.0
    val remainingPercent: Double get() = if (allocation.amount > 0) remaining.toDouble() / allocation.amount * 100 else if (spent > 0) -100.0 else 100.0
    fun remainingIncomePercent(income: Long): Double = if (income > 0) remaining.toDouble() / income * 100 else 0.0
    val status: BudgetProgressStatus get() = when { usedPercent >= 100 -> BudgetProgressStatus.EXCEEDED; usedPercent >= 80 -> BudgetProgressStatus.WARNING; else -> BudgetProgressStatus.SAFE }
    val usedFraction: Float get() = (usedPercent / 100).toFloat().coerceIn(0f, 1f)
}

data class LiveFinancialAllocation(
    val buckets: List<FinancialBucketUsage>,
    val totalSpent: Long = 0
)

object FinancialCategoryMapping {
    private val exactDefaults = mapOf(
        "an uong" to "necessities", "hoa don" to "necessities", "nha cua" to "necessities", "nha o" to "necessities",
        "y te" to "necessities", "di chuyen" to "necessities", "dau tu" to "freedom",
        "tiet kiem" to "long_term", "muc tieu dai han" to "long_term",
        "hoc tap" to "education", "sach" to "education", "khoa hoc" to "education",
        "tu thien" to "giving", "du lich" to "play", "giai tri" to "play",
        "dien" to "necessities", "nuoc" to "necessities", "internet" to "necessities",
        "dien nuoc" to "necessities", "dien/nuoc/internet" to "necessities", "dien nuoc internet" to "necessities",
        "chi phi sinh hoat bat buoc" to "necessities", "chi phi sinh hoat" to "necessities",
        "tien nha" to "necessities", "di cho" to "necessities", "xang xe" to "necessities",
        "mua sam khong thiet yeu" to "play", "mua sam" to "play", "lam dep" to "play", "so thich" to "play",
        "muc tieu tai chinh" to "long_term", "quy khan cap" to "long_term", "quy du phong" to "long_term",
        "tra no" to "long_term", "giao duc thiet yeu" to "necessities", "giao duc" to "education",
        "giup do" to "giving", "qua tang" to "giving", "tao tai san" to "freedom"
    )
    fun idKey(categoryId: String) = "id:${categoryId.trim()}"

    fun bucket(category: String, rule: BudgetRule, overrides: Map<String, String>, categoryId: String = ""): String {
        val normalized = normalizeVietnameseSearchText(category)
        val override = categoryId.takeIf { it.isNotBlank() }?.let { overrides[idKey(it)] }
            ?: overrides[category]
            ?: overrides.entries.firstOrNull { !it.key.startsWith("id:") && normalizeVietnameseSearchText(it.key) == normalized }?.value
        val validKeys = FinancialMethods.forRule(rule).buckets.map { it.key }
        if (override in validKeys) return requireNotNull(override)
        // Invalid/legacy overrides never exclude an expense from the plan.
        val jars = exactDefaults[normalized] ?: "play"
        val result = if (rule == BudgetRule.JARS) jars else when (jars) {
            "necessities" -> "needs"; "play", "giving", "education" -> "wants"; else -> "savings"
        }
        return result
    }

    fun resolveCategory(transaction: Transaction, categories: List<Category>): Category? {
        if (transaction.categoryId.isNotBlank()) return categories.firstOrNull { it.id == transaction.categoryId }
        categories.firstOrNull { it.id == transaction.category }?.let { return it }
        return categories.filter { it.type.trim().equals(transaction.type.trim(), ignoreCase = true) &&
            normalizeVietnameseSearchText(it.name) == normalizeVietnameseSearchText(transaction.category) }.singleOrNull()
    }
}

object LiveFinancialCalculator {
    fun calculate(plan: FinancialAllocationPlan, transactions: List<Transaction>, userId: String?, monthKey: String,
        dates: BudgetDateProvider, mapping: Map<String, String>, categories: List<Category> = emptyList()): LiveFinancialAllocation {
        val spent = mutableMapOf<String, Long>()
        var total = 0L
        transactions.filter { it.userId == userId && !userId.isNullOrBlank() && it.type.trim().equals("Chi", ignoreCase = true) && it.amount.isFinite() && it.amount > 0 &&
                runCatching { dates.localDateAt(it.timestamp) }.getOrNull()?.let(BudgetCalendar::monthKey) == monthKey }
            .distinctBy { it.id.ifBlank { "legacy:${it.userId}:${it.timestamp}:${it.amount}:${it.category}:${it.note}" } }
            .forEach { transaction ->
                val amount = round(transaction.amount).toLong().coerceAtLeast(0L)
                total = safeAdd(total, amount)
                val category = FinancialCategoryMapping.resolveCategory(transaction, categories)
                val stableId = transaction.categoryId.ifBlank { category?.id.orEmpty() }
                val bucket = FinancialCategoryMapping.bucket(category?.name ?: transaction.category, plan.method.rule, mapping, stableId)
                spent[bucket] = safeAdd(spent[bucket] ?: 0L, amount)
            }
        return LiveFinancialAllocation(plan.allocations.map { FinancialBucketUsage(it, spent[it.bucket.key] ?: 0L) }, total)
    }

    /** All presenter amounts and progress derive from this same session calculation. */
    fun metrics(plan: BudgetPlan, live: LiveFinancialAllocation, date: BudgetDate): BudgetMetrics {
        fun sum(vararg keys: String) = live.buckets.filter { it.allocation.bucket.key in keys }.sumOf { it.spent.toDouble() }
        val needs = sum("needs", "necessities")
        val wants = sum("wants", "play", "education", "giving")
        val savings = sum("savings", "freedom", "long_term")
        val remaining = plan.totalBudget - live.totalSpent
        val days = BudgetCalendar.remainingDays(date).coerceAtLeast(1)
        val daily = remaining.coerceAtLeast(0.0) / days
        return BudgetMetrics(plan.copy(needsSpent = needs, wantsSpent = wants, savingsSpent = savings),
            live.totalSpent.toDouble(), remaining, days, daily, daily * 0.2, daily * 0.35, daily * 0.45,
            BudgetCalculator.groupProgress(plan.needsLimit, needs), BudgetCalculator.groupProgress(plan.wantsLimit, wants), BudgetCalculator.groupProgress(plan.savingsLimit, savings))
    }
    private fun safeAdd(a: Long, b: Long) = if (b > Long.MAX_VALUE - a) Long.MAX_VALUE else a + b
}

/** Deterministic two-decimal percentage; no platform locale dependency. */
fun formatFinancialPercent(value: Double): String {
    val hundredths = round(value * 100).toLong()
    val sign = if (hundredths < 0) "-" else ""
    val positive = if (hundredths == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(hundredths)
    val fraction = (positive % 100).toString().padStart(2, '0').trimEnd('0')
    return sign + (positive / 100).toString() + if (fraction.isEmpty()) "%" else ",$fraction%"
}
