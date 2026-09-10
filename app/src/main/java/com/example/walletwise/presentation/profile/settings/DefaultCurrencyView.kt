package com.example.walletwise.presentation.profile.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.walletwise.presentation.profile.DefaultCurrencyContent
import com.example.walletwise.presentation.profile.DefaultCurrencyPresenter

@Composable
fun DefaultCurrencyView(onBack: () -> Unit) {
    val presenter = remember { DefaultCurrencyPresenter() }
    val state by presenter.state.collectAsStateWithLifecycle()
    val event = state.pendingEvent

    LaunchedEffect(event?.id) {
        event ?: return@LaunchedEffect
        onBack()
        presenter.consumeEvent(event.id)
    }

    DefaultCurrencyContent(
        options = state.options,
        onCurrencySelected = presenter::onCurrencySelected,
        onBack = presenter::onBack
    )
}
