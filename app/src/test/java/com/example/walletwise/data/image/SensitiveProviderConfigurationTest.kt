package com.example.walletwise.data.image

import com.example.walletwise.presentation.home.AiAnalysisResult
import com.example.walletwise.presentation.home.TransactionAIAssistant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SensitiveProviderConfigurationTest {
    @Test
    fun missingGeminiCredentialFailsClosedWithoutCallingTheProvider() = runBlocking {
        val result = TransactionAIAssistant("").analyzeTransactionText("mua đồ ăn 50k")

        assertEquals("NOT_CONFIGURED", (result as AiAnalysisResult.Failure).reason)
    }
}
