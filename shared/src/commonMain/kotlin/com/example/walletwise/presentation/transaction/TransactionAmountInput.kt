package com.example.walletwise.presentation.transaction

/** Whole VND, bounded so the established Double wire/model represents every entered integer exactly. */
object TransactionAmountInput {
    const val MAX_AMOUNT = 9_007_199_254_740_991L
    fun normalize(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return ""
        if (trimmed.any { it !in '0'..'9' && it !in "., " && it != '\u00a0' && it != '\u202f' }) return null
        val groups = trimmed.split(Regex("[., \\u00a0\\u202f]+"))
        if (groups.any(String::isEmpty)) return null
        if (groups.size > 1 && (groups.first().length !in 1..3 || groups.drop(1).any { it.length != 3 })) return null
        val digits = groups.joinToString("")
        val amount = digits.toLongOrNull() ?: return null
        return digits.takeIf { amount <= MAX_AMOUNT }
    }
    fun value(digits: String): Long? = digits.toLongOrNull()?.takeIf { it in 1..MAX_AMOUNT }
    fun format(digits: String): String = digits.reversed().chunked(3).joinToString(".").reversed()
    fun originalToFormatted(offset: Int, length: Int): Int = offset.coerceIn(0, length) +
        (1 until offset.coerceIn(0, length)).count { (length - it) % 3 == 0 }
    fun formattedToOriginal(offset: Int, digits: String): Int = format(digits).take(offset.coerceAtLeast(0)).count { it in '0'..'9' }
}
