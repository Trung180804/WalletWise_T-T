package com.example.walletwise.presentation.home

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig

class TransactionAIAssistant(private val apiKey: String) {
    private val generativeModel by lazy { GenerativeModel(
        modelName = "gemini-2.5-flash",
        apiKey = apiKey,
        generationConfig = generationConfig {
            responseMimeType = "application/json"
            temperature = 0.1f
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
    ) }

    // Hàm nhận giọng nói (text) và trả về chuỗi JSON
    suspend fun analyzeTransactionText(userInput: String): AiAnalysisResult {
        if (apiKey.isBlank()) return AiAnalysisResult.Failure(null, "NOT_CONFIGURED")
        return try {
            val response = generativeModel.generateContent(userInput)
            response.text?.let { AiAnalysisResult.Success(it) } ?: AiAnalysisResult.Failure(null, "EMPTY_RESPONSE")
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled } catch (e: Exception) {
            val message = e.message.orEmpty()
            val status = listOf(401, 403, 404, 429, 500, 503).firstOrNull { it.toString() in message }
            AiAnalysisResult.Failure(status, when (status) {
                401, 403 -> "AUTHENTICATION"; 429 -> "QUOTA"; 404 -> "MODEL_UNAVAILABLE"; else -> "NETWORK_OR_PROVIDER"
            })
        }
    }
}


sealed interface AiAnalysisResult {
    data class Success(val json: String) : AiAnalysisResult
    data class Failure(val httpStatus: Int?, val reason: String) : AiAnalysisResult
}