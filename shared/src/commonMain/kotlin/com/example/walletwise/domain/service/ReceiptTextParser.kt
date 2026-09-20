package com.example.walletwise.domain.service

import com.example.walletwise.domain.model.*
import com.example.walletwise.domain.util.normalizeVietnameseSearchText

class ReceiptTextParser(private val clock: DraftDateTimeProvider) {
    fun parse(rawOcr: String, draftId: String, userId: String, categories: List<Category>): ReceiptTransactionDraft {
        val lines = rawOcr.lines().map { it.trim() }.filter { it.isNotBlank() }
        val candidates = lines.filter { line ->
            val n = normalizeVietnameseSearchText(line)
            listOf("tong cong", "tong tien", "thanh toan", "grand total", "total").any { n.startsWith(it) } &&
                listOf("subtotal", "sub total", "vat", "khach dua", "tien thua", "change", "cash tendered").none { it in n }
        }.mapNotNull { line ->
            Regex("[0-9]+(?:[., ][0-9]{3})*").findAll(line).lastOrNull()?.value?.let(MoneyInput::amount)
        }.filter { it > 0 }.distinct()
        val merchant = lines.firstOrNull { line ->
            line.any { it.isLetter() } && !line.contains('@') && !line.contains("http") &&
                Regex("\\d{6,}").find(line) == null &&
                listOf("hoa don", "receipt", "total", "tong", "thanh toan", "vat", "ngay", "date").none {
                    normalizeVietnameseSearchText(line).startsWith(it)
                }
        }?.take(120)
        val dateMatch = Regex("(?<!\\d)(\\d{1,2})/(\\d{1,2})/(\\d{4})(?!\\d)").find(rawOcr)
        val timestamp = dateMatch?.let { clock.date(it.groupValues[3].toInt(), it.groupValues[2].toInt(), it.groupValues[1].toInt()) }
        val normalized = normalizeVietnameseSearchText(lines.joinToString(" "))
        val suggestion = when {
            listOf("ca phe", "coffee", "quan an", "pho", "com", "banh", "restaurant").any { Regex("\\b$it\\b").containsMatchIn(normalized) } -> "Ăn uống"
            listOf("sieu thi", "supermarket", "cua hang", "shop").any { it in normalized } -> "Mua sắm"
            "nha thuoc" in normalized || "pharmacy" in normalized -> "Y tế"
            else -> null
        }
        val category = categories.firstOrNull { it.type == "Chi" && it.name == suggestion }?.name
        val payment = when {
            "chuyen khoan" in normalized -> "Chuyển khoản"
            "visa" in normalized || "credit card" in normalized -> "Thẻ tín dụng"
            "tien mat" in normalized || Regex("\\bcash\\b").containsMatchIn(normalized) -> "Tiền mặt"
            else -> null
        }
        val amount = candidates.singleOrNull()
        val missing = buildSet {
            if (amount == null) add(DraftField.AMOUNT)
            if (category == null) add(DraftField.CATEGORY)
            if (timestamp == null) add(DraftField.DATE)
            if (payment == null) add(DraftField.PAYMENT_METHOD)
        }
        val warning = when {
            lines.isEmpty() -> "Không đọc được chữ. Hãy chụp rõ hơn hoặc nhập tay."
            candidates.isEmpty() -> "Chưa tìm thấy tổng thanh toán đáng tin cậy. Vui lòng nhập số tiền."
            candidates.size > 1 -> "Có nhiều tổng thanh toán. Hãy chọn số tiền đúng."
            else -> null
        }
        return ReceiptTransactionDraft(TransactionDraft(draftId, userId, amount, "Chi", category,
            merchant.orEmpty(), timestamp, payment, DraftSource.RECEIPT,
            DraftField.entries.associateWith { if (it in missing) 0f else 0.85f }, missing), merchant, candidates,
            warning = warning)
    }
}
