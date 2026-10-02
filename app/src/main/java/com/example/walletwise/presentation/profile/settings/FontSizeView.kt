package com.example.walletwise.presentation.profile.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.walletwise.presentation.profile.FontSizeContent
import com.example.walletwise.presentation.profile.FontSizePresenter

@Composable
fun FontSizeView(onBack: () -> Unit) {
    val presenter = remember { FontSizePresenter() }
    val state by presenter.state.collectAsStateWithLifecycle()
    val event = state.pendingEvent

    LaunchedEffect(event?.id) {
        event ?: return@LaunchedEffect
        onBack()
        presenter.consumeEvent(event.id)
    }

    FontSizeContent(
        state = state,
        onSliderValueChanged = presenter::onSliderValueChanged,
        onPresetSelected = presenter::onPresetSelected,
        onSave = presenter::onSave,
        onBack = presenter::onBack
    )
}
