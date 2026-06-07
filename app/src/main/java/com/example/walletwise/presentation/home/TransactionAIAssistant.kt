package com.example.walletwise.presentation.home

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig

class TransactionAIAssistant {

    private val apiKey = "AQ.Ab8RN6KhjAM-EsAbHv2c-BbkW0voqQyEUILMK_-fZp4OznNydw"

    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.5-flash",
        apiKey = apiKey,
        generationConfig = generationConfig {
            responseMimeType = "application/json"
            temperature = 0.1f // Giữ sự sáng tạo ở mức cực thấp để AI tập trung làm việc chính xác
        },
        systemInstruction = content {
            text(
                """
        Bạn là trợ lý tài chính của WalletWise. Nhiệm vụ của bạn là trích xuất thông tin giao dịch từ câu nói của người dùng để map vào Entity Transaction.
        Dưới đây là cấu trúc Entity mà bạn phải lấp đầy:
        - amount: Double (Số tiền)
        - type: "Thu" hoặc "Chi"
        - paymentMethod: "Tiền mặt", "Chuyển khoản", hoặc "Thẻ tín dụng" (Mặc định: "Tiền mặt")
        - category: (Danh mục, ví dụ: Ăn uống, Mua sắm,...)
        - note: (Ghi chú)
        - timestamp: (Nếu người dùng nhắc thời gian, hãy giữ nguyên câu chữ đó vào note)
        
        Logic xử lý:
        1. Kiểm tra nếu "amount" hoặc "category" bị thiếu, hãy đưa câu hỏi vào trường "missing_prompt" để hỏi người dùng.
        2. Nếu thiếu "paymentMethod", hãy tự gán là "Tiền mặt".
        3. Nếu thiếu "type", hãy suy luận dựa trên nội dung (VD: "Mua" -> "Chi", "Nhận" -> "Thu").
        4. Trả về JSON, nếu đã đủ thông tin thì "missing_prompt" để trống.
        """.trimIndent()
            )
        }
    )

    // Hàm nhận giọng nói (text) và trả về chuỗi JSON
    suspend fun analyzeTransactionText(userInput: String): String? {
        return try {
            val response = generativeModel.generateContent(userInput)
            response.text
        } catch (e: Exception) {
            android.util.Log.e("GeminiError", "Lỗi chi tiết từ Google: ${e.message}", e)
            null
        }
    }
}

