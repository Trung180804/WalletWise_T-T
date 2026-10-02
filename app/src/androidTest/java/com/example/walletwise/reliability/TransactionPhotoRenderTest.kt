package com.example.walletwise.reliability

import android.graphics.Bitmap
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import coil.ImageLoader
import coil.request.ImageRequest
import com.example.walletwise.presentation.home.TransactionPhoto
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class TransactionPhotoRenderTest {
    @get:Rule val compose = createComposeRule()
    @Test fun compactCalendarPlaceholderShowsFullLabelWithinFixedFrame() {
        compose.setContent { MaterialTheme { TransactionPhoto(null, Modifier.size(38.dp).testTag("calendar-photo")) } }
        val label = compose.onNodeWithText("No image", useUnmergedTree = true)
        label.assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        label.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(layouts)) }
        assertFalse("Calendar label must not be clipped: lines=${layouts.single().lineCount} size=${layouts.single().size}", layouts.single().hasVisualOverflow)
        assertEquals("Small calendar label wraps away from rounded corners", 2, layouts.single().lineCount)
        val frame = compose.onNodeWithTag("calendar-photo").fetchSemanticsNode().boundsInRoot
        val text = label.fetchSemanticsNode().boundsInRoot
        assertTrue(text.left >= frame.left && text.right <= frame.right && text.top >= frame.top && text.bottom <= frame.bottom)
        assertTrue("Keep text clear of rounded border", text.left - frame.left >= frame.width * 0.15f && frame.right - text.right >= frame.width * 0.15f)
        compose.onNodeWithContentDescription("Giao dịch không có ảnh").assertExists()
    }
    @Test fun nullBlankWhitespaceNeverLoadAndRealImageAndErrorKeepFrameAndAccessibility() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var loads = 0
        var failed = false
        val loader = ImageLoader.Builder(context).eventListener(object : coil.EventListener {
            override fun onStart(request: ImageRequest) { loads++ }
            override fun onError(request: ImageRequest, result: coil.request.ErrorResult) { failed = true }
        }).build()
        val url = mutableStateOf<String?>(null)
        val dark = mutableStateOf(false)
        val image = File(context.cacheDir, "synthetic_photo.png")
        Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.YELLOW) }
            .let { bitmap -> image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle() }
        try {
            compose.setContent { MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                TransactionPhoto(url.value, Modifier.size(100.dp).testTag("photo-frame"), loader)
            } }
            val original = compose.onNodeWithTag("photo-frame").fetchSemanticsNode().boundsInRoot
            for (blank in listOf(null, "", "  \t\n ")) {
                compose.runOnIdle { url.value = blank }
                compose.onNodeWithText("No image").assertIsDisplayed()
                compose.onNodeWithContentDescription("Giao dịch không có ảnh").assertExists()
                assertEquals(original.size, compose.onNodeWithTag("photo-frame").fetchSemanticsNode().boundsInRoot.size)
            }
            assertEquals(0, loads)
            compose.runOnIdle { url.value = image.toURI().toString() }
            compose.waitUntil(10000) { compose.onAllNodesWithText("No image").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithContentDescription("Ảnh giao dịch").assertIsDisplayed()
            assertTrue(loads > 0)
            compose.runOnIdle { dark.value = true; url.value = "file:///missing-checkpoint-image.png" }
            compose.waitUntil(10000) { failed }
            compose.onNodeWithText("No image").assertIsDisplayed()
            assertEquals(original.size, compose.onNodeWithTag("photo-frame").fetchSemanticsNode().boundsInRoot.size)
        } finally { image.delete(); loader.shutdown() }
    }
}
