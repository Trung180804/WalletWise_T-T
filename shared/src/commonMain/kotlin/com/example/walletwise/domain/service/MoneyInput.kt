package com.example.walletwise.domain.service

/** Whole currency units; exact when converted to the existing Double wire model. */
object MoneyInput {
    const val MAX_EXACT_AMOUNT = 9_007_199_254_740_991L

    fun raw(text: String): String? {
        if (text.any { it !in '0'..'9' && it !in "., \u00a0\u202f" }) return null
        val digits = text.filter { it in '0'..'9' }
        if (digits.isEmpty()) return if (text.isBlank()) "" else null
        val value = digits.toLongOrNull() ?: return null
        return digits.takeIf { value <= MAX_EXACT_AMOUNT }
    }

    fun amount(text: String): Long? = raw(text)?.toLongOrNull()

    data class Edit(val raw: String, val selectionStart: Int, val selectionEnd: Int)
    fun edit(text: String, selectionStart: Int, selectionEnd: Int = selectionStart): Edit? {
        val digits = raw(text) ?: return null
        fun offset(position: Int) = text.take(position.coerceIn(0, text.length)).count { it in '0'..'9' }
        return Edit(digits, offset(selectionStart), offset(selectionEnd))
    }

    fun format(raw: String): String = buildString {
        raw.forEachIndexed { index, char ->
            if (index > 0 && (raw.length - index) % 3 == 0) append('.')
            append(char)
        }
    }

    fun originalToFormatted(raw: String, offset: Int): Int {
        val end = offset.coerceIn(0, raw.length)
        return end + (1 until raw.length).count { it <= end && (raw.length - it) % 3 == 0 }
    }

    fun formattedToOriginal(raw: String, offset: Int): Int =
        format(raw).take(offset.coerceIn(0, format(raw).length)).count { it != '.' }
}
