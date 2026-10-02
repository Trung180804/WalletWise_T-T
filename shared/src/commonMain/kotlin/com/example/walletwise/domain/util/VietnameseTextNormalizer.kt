package com.example.walletwise.domain.util

/** Shared Vietnamese accent folding; preserves punctuation and currency digits. */
fun normalizeVietnameseSearchText(value: String): String = buildString(value.length) {
    value.trim().lowercase().forEach { character ->
        if (character in '\u0300'..'\u036f') return@forEach
        append(
            when (character) {
                in "àáạảãâầấậẩẫăằắặẳẵ" -> 'a'
                in "èéẹẻẽêềếệểễ" -> 'e'
                in "ìíịỉĩ" -> 'i'
                in "òóọỏõôồốộổỗơờớợởỡ" -> 'o'
                in "ùúụủũưừứựửữ" -> 'u'
                in "ỳýỵỷỹ" -> 'y'
                'đ' -> 'd'
                else -> character
            }
        )
    }
}.replace(Regex("\\s+"), " ")
