package com.example.walletwise.reliability

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.example.walletwise.presentation.profile.ProfileLoadState
import com.example.walletwise.presentation.profile.ProfileMainContent
import com.example.walletwise.presentation.profile.ProfileUiState
import com.example.walletwise.shared.resources.Res
import com.example.walletwise.shared.resources.profile_title
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProfileRenderRegressionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun apkContainsReadableSharedStringsAndImages(): Unit = runBlocking {
        val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets
        listOf("values/strings.commonMain.cvr", "drawable/img.png", "drawable/logo.jpg").forEach { path ->
            assets.open("composeResources/com.example.walletwise.shared.resources/$path").use {
                assertTrue("Empty packaged resource: $path", it.read() >= 0)
            }
        }
        assertEquals("Hồ sơ", getString(Res.string.profile_title))
    }

    @Test
    fun profileRendersLoadingErrorEmptyLegacyAndLoggedOutWithoutCrash() {
        val state = mutableStateOf(ProfileUiState())
        compose.setContent {
            MaterialTheme { ProfileMainContent(state.value, {}, {}, {}, {}) }
        }
        compose.waitForIdle()
        compose.runOnIdle { state.value = ProfileUiState(loadState = ProfileLoadState.Error("Test profile error")) }
        compose.onNodeWithText("Test profile error").assertIsDisplayed()
        compose.runOnIdle {
            state.value = ProfileUiState(loadState = ProfileLoadState.Empty, isLoggedIn = true, userId = "missing")
        }
        compose.onNodeWithText("Hồ sơ").assertIsDisplayed()
        compose.runOnIdle {
            state.value = ProfileUiState(loadState = ProfileLoadState.Data, isLoggedIn = true, userId = "legacy", username = "Legacy")
        }
        compose.onNodeWithText("Legacy").assertIsDisplayed()
        compose.runOnIdle { state.value = ProfileUiState(loadState = ProfileLoadState.Data) }
        compose.onNodeWithText("Chưa đăng nhập").assertIsDisplayed()
    }
}
