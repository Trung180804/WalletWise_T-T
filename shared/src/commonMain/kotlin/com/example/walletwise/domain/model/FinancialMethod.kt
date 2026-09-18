package com.example.walletwise.domain.model

data class FinancialBucket(
    val key: String,
    val name: String,
    val percent: Int,
    val description: String
)

data class FinancialMethod(
    val rule: BudgetRule,
    val name: String,
    val description: String,
    val example: String,
    val buckets: List<FinancialBucket>
) {
    val totalPercent: Int get() = buckets.sumOf(FinancialBucket::percent)
}

data class FinancialBucketAllocation(
    val bucket: FinancialBucket,
    val amount: Long
)

data class FinancialAllocationPlan(
    val method: FinancialMethod,
    val income: Long,
    val allocations: List<FinancialBucketAllocation>
) {
    val totalPercent: Int get() = method.totalPercent
    val totalAmount: Long get() = allocations.sumOf(FinancialBucketAllocation::amount)
}

object FinancialMethods {
    val FiftyThirtyTwenty = FinancialMethod(
        BudgetRule.FIFTY_THIRTY_TWENTY,
        "Quy tắc 50/30/20",
        "Cân bằng nhu cầu, mong muốn và tương lai tài chính.",
        "Ví dụ: ưu tiên tiền nhà trước, rồi giải trí và tiết kiệm.",
        listOf(
            FinancialBucket("needs", "Nhu cầu thiết yếu", 50, "Nhà ở, ăn uống, đi lại"),
            FinancialBucket("wants", "Mong muốn", 30, "Giải trí và sở thích"),
            FinancialBucket("savings", "Tiết kiệm/Đầu tư", 20, "Mục tiêu dài hạn")
        )
    )
    val SixJars = FinancialMethod(
        BudgetRule.JARS,
        "Quy tắc 6 chiếc lọ",
        "Chia thu nhập thành sáu quỹ với mục đích rõ ràng.",
        "Ví dụ: mỗi khi có thu nhập, phân bổ ngay vào sáu quỹ.",
        listOf(
            FinancialBucket("necessities", "Nhu cầu thiết yếu", 55, "Chi phí sinh hoạt"),
            FinancialBucket("freedom", "Tự do tài chính", 10, "Tạo nguồn thu tương lai"),
            FinancialBucket("long_term", "Tiết kiệm dài hạn", 10, "Mục tiêu lớn và dự phòng"),
            FinancialBucket("education", "Giáo dục", 10, "Học tập và phát triển"),
            FinancialBucket("giving", "Cho đi", 5, "Chia sẻ với cộng đồng"),
            FinancialBucket("play", "Hưởng thụ", 10, "Niềm vui và trải nghiệm")
        )
    )
    val All = listOf(FiftyThirtyTwenty, SixJars)
    fun forRule(rule: BudgetRule): FinancialMethod = All.first { it.rule == rule }
}
