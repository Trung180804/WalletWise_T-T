package com.example.walletwise.data.image

import com.example.walletwise.domain.model.ImageUpload
import com.example.walletwise.presentation.home.AiAnalysisResult
import com.example.walletwise.presentation.home.TransactionAIAssistant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveProviderConfigurationTest {
    @Test
    fun missingGeminiCredentialFailsClosedWithoutCallingTheProvider() = runBlocking {
        val result = TransactionAIAssistant("").analyzeTransactionText("mua đồ ăn 50k")

        assertEquals("NOT_CONFIGURED", (result as AiAnalysisResult.Failure).reason)
    }

    @Test
    fun missingImageCredentialFailsBeforeBuildingOrSendingARequest() = runBlocking {
        val result = ImgBbImageUploader("").upload(
            ImageUpload(bytes = byteArrayOf(1), contentType = "image/jpeg", fileName = "receipt.jpg")
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("chưa được cấu hình") == true)
    }
}
