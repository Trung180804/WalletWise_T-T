package com.example.walletwise.reliability

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.walletwise.presentation.home.AssistantComposer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AssistantActionsRenderTest {
    @get:Rule val compose = createComposeRule()
    @Test fun cameraPickerMicrophoneOrderCallbacksAndSmallScreenTouchTargets() {
        val calls = mutableListOf<String>()
        compose.setContent { MaterialTheme { AssistantComposer("", {}, true, { calls += "receipt-capture" }, { calls += "receipt-picker" }, { calls += "speech" }, {}, Modifier.width(320.dp)) } }
        val labels = listOf("Chụp hóa đơn", "Chọn hóa đơn từ thư viện", "Nhập giọng nói")
        val bounds = labels.map { compose.onNodeWithContentDescription(it).assertIsDisplayed().fetchSemanticsNode().boundsInRoot }
        assertTrue(bounds[0].left < bounds[1].left && bounds[1].left < bounds[2].left)
        labels.forEach { compose.onNodeWithContentDescription(it).performClick() }
        assertEquals(listOf("receipt-capture", "receipt-picker", "speech"), calls)
        compose.onNodeWithText("Nhập nội dung").assertIsDisplayed()
        val density = compose.density.density
        bounds.forEach { assertTrue(it.width >= 48 * density); assertTrue(it.height >= 48 * density) }
    }
    @Test fun disabledActionsDoNotLaunchAndInputCanSubmit() {
        var calls = 0
        val enabled = mutableStateOf(false)
        val input = mutableStateOf("")
        compose.setContent { MaterialTheme { AssistantComposer(input.value, { input.value = it }, enabled.value, { calls++ }, { calls++ }, { calls++ }, { calls++ }) } }
        listOf("Chụp hóa đơn", "Chọn hóa đơn từ thư viện", "Nhập giọng nói").forEach { compose.onNodeWithContentDescription(it).assertIsNotEnabled() }
        assertEquals(0, calls)
        compose.runOnIdle { enabled.value = true }
        compose.onNodeWithTag("assistant-input").performTextInput("mua do an 50k")
        compose.onNodeWithContentDescription("Ghi giao dịch").performClick()
        assertEquals(1, calls)
    }
}
