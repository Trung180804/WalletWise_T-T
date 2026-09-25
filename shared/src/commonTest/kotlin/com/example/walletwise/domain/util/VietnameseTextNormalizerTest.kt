package com.example.walletwise.domain.util

import kotlin.test.Test
import kotlin.test.assertEquals

class VietnameseTextNormalizerTest {
    @Test fun foldsEveryVietnameseVowelAndDStroke() {
        assertEquals("a".repeat(17), normalizeVietnameseSearchText("àáạảãâầấậẩẫăằắặẳẵ"))
        assertEquals("e".repeat(11), normalizeVietnameseSearchText("èéẹẻẽêềếệểễ"))
        assertEquals("i".repeat(5), normalizeVietnameseSearchText("ìíịỉĩ"))
        assertEquals("o".repeat(17), normalizeVietnameseSearchText("òóọỏõôồốộổỗơờớợởỡ"))
        assertEquals("u".repeat(11), normalizeVietnameseSearchText("ùúụủũưừứựửữ"))
        assertEquals("y".repeat(5), normalizeVietnameseSearchText("ỳýỵỷỹ"))
        assertEquals("d", normalizeVietnameseSearchText("Đ"))
    }
    @Test fun trimsFoldsCaseAndCollapsesWhitespace() {
        assertEquals("an uong nha cua", normalizeVietnameseSearchText("  ĂN\t UỐNG\n  NHÀ CỬA  "))
    }
    @Test fun acceptsPrecomposedAndCombiningAccents() {
        assertEquals(normalizeVietnameseSearchText("Giáo dục"), normalizeVietnameseSearchText("Gia\u0301o du\u0323c"))
        assertEquals(normalizeVietnameseSearchText("Ăn uống"), normalizeVietnameseSearchText("A\u0306n uo\u0302\u0301ng"))
    }
    @Test fun preservesAmountsPunctuationAndUnrelatedCharacters() {
        assertEquals("chi 50.000 ₫ - 18/09/2026!", normalizeVietnameseSearchText("Chi 50.000 ₫ - 18/09/2026!"))
        assertEquals("钱包 🙂", normalizeVietnameseSearchText("钱包 🙂"))
    }
    @Test fun blankAndAlreadyNormalizedInputRemainStable() {
        assertEquals("", normalizeVietnameseSearchText(" \t\n "))
        val once = normalizeVietnameseSearchText("  Tiết kiệm / ĐẦU TƯ ")
        assertEquals("tiet kiem / dau tu", once)
        assertEquals(once, normalizeVietnameseSearchText(once))
    }
}
