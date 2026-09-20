package com.example.walletwise.domain.service

import com.example.walletwise.domain.model.*
import com.example.walletwise.domain.util.normalizeVietnameseSearchText

interface DraftDateTimeProvider {
    fun now(): Long
    fun relativeDay(days: Int): Long
    fun date(year: Int, month: Int, day: Int): Long?
}

interface TransactionTextAnalyzer {
    suspend fun analyze(text: String, draftId: String, userId: String, categories: List<Category>): TransactionDraft
}

class LocalTransactionTextAnalyzer(private val clock: DraftDateTimeProvider) : TransactionTextAnalyzer {
    override suspend fun analyze(text: String, draftId: String, userId: String, categories: List<Category>) =
        TransactionDraftParser(clock).parse(text, draftId, userId, categories)
}

class TransactionDraftParser(private val clock: DraftDateTimeProvider) {
    fun parse(text: String, draftId: String, userId: String, categories: List<Category>): TransactionDraft {
        val normalized = normalizeVietnameseSearchText(text)
        val moneyPattern = Regex("(?<![\\w/])([0-9]+(?:[., ][0-9]+)*)\\s*(nghin|ngan|trieu|tr|k|d|dong)?(?![a-z0-9/])")
        val amounts = moneyPattern.findAll(normalized).mapNotNull { match ->
            val unit = match.groupValues[2]
            val number = match.groupValues[1]
            val multiplier = when (unit) { "k", "nghin", "ngan" -> 1_000L; "tr", "trieu" -> 1_000_000L; else -> 1L }
            val base = MoneyInput.amount(number) ?: return@mapNotNull null
            val decimal = if (multiplier > 1) Regex("^[0-9]+[.,]([0-9]{1,2})$").find(number) else null
            val factor = if (decimal == null) multiplier else multiplier / if (decimal.groupValues[1].length == 1) 10 else 100
            if (base > MoneyInput.MAX_EXACT_AMOUNT / factor) null else base * factor
        }.toList()
        val income = listOf("nhan luong", "nhan thuong", "thu nhap", "duoc tra luong").any { it in normalized }
        val expense = listOf("mua", "chi ", "het ", "tra tien", "do xang", "an sang", "an trua", "an toi").any { it in normalized }
        val type = when { income && expense -> null; income -> "Thu"; expense -> "Chi"; else -> null }
        val suggested = when {
            income && "luong" in normalized -> "Lương"
            income && "thuong" in normalized -> "Thưởng"
            listOf("do an", "an sang", "an trua", "an toi", "ca phe", "pho", "com ").any { it in normalized } -> "Ăn uống"
            "xang" in normalized || "taxi" in normalized -> "Di chuyển"
            "hoa don" in normalized -> "Hóa đơn"
            else -> null
        }
        val candidates = categories.filter { it.type == type && (normalizeVietnameseSearchText(it.name) == suggested?.let(::normalizeVietnameseSearchText) ||
            Regex("\\b${Regex.escape(normalizeVietnameseSearchText(it.name))}\\b").containsMatchIn(normalized)) }.map { it.name }.distinct()
        val category = candidates.singleOrNull()
        val payment = when {
            "chuyen khoan" in normalized -> "Chuyển khoản"
            "the tin dung" in normalized || "visa" in normalized -> "Thẻ tín dụng"
            "tien mat" in normalized -> "Tiền mặt"
            else -> null
        }
        val uncertainDate = listOf("hom kia", "tuan truoc", "thang truoc", "ngay mai").any { it in normalized }
        val explicitDate = Regex("\\b(\\d{1,2})/(\\d{1,2})/(\\d{4})\\b").find(normalized)
        val timestamp = when {
            uncertainDate -> null
            explicitDate != null -> clock.date(explicitDate.groupValues[3].toInt(), explicitDate.groupValues[2].toInt(), explicitDate.groupValues[1].toInt())
            "hom qua" in normalized -> clock.relativeDay(-1)
            else -> clock.now()
        }
        val amount = amounts.singleOrNull()?.takeIf { it > 0 && !Regex("-\\s*\\d").containsMatchIn(normalized) &&
            listOf("khoang", "co the", "hoac").none { word -> Regex("\\b$word\\b").containsMatchIn(normalized) } }
        val missing = buildSet {
            if (amount == null) add(DraftField.AMOUNT)
            if (type == null) add(DraftField.TYPE)
            if (category == null) add(DraftField.CATEGORY)
            if (timestamp == null) add(DraftField.DATE)
            if (payment == null) add(DraftField.PAYMENT_METHOD)
        }
        return TransactionDraft(draftId, userId, amount, type, category, text.trim(), timestamp, payment,
            confidence = DraftField.entries.associateWith { if (it in missing) 0f else 0.9f }, missingFields = missing)
    }
}
