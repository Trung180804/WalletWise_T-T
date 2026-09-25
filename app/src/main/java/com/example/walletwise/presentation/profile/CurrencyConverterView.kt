package com.example.walletwise.presentation.profile

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.walletwise.shared.resources.Res
import com.example.walletwise.shared.resources.currency_invalid_input
import com.example.walletwise.shared.resources.currency_number_too_large
import com.example.walletwise.ui.theme.LocalAppTheme
import org.jetbrains.compose.resources.stringResource

@Composable
fun CurrencyConverterView(onBack: () -> Unit) {
    val presenter = remember { CurrencyConverterPresenter() }
    val state by presenter.state.collectAsStateWithLifecycle()
    val isDark = LocalAppTheme.current.value
    val context = LocalContext.current
    val invalidInputMessage = stringResource(Res.string.currency_invalid_input)
    val tooLargeMessage = stringResource(Res.string.currency_number_too_large)
    val event = state.pendingEvent

    LaunchedEffect(event?.id) {
        event ?: return@LaunchedEffect
        val message = when (val value = event.event) {
            is CurrencyConverterUiEvent.InputRejected -> when (value.problem) {
                CurrencyInputProblem.INVALID -> invalidInputMessage
                CurrencyInputProblem.TOO_LARGE -> tooLargeMessage
            }
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        presenter.consumeEvent(event.id)
    }

    CurrencyConverterContent(
        state = state,
        isDark = isDark,
        onBack = onBack,
        onCurrencyRowSelected = presenter::onCurrencyRowSelected,
        onOpenCurrencyPicker = presenter::onOpenCurrencyPicker,
        onDismissCurrencyPicker = presenter::onDismissCurrencyPicker,
        onCurrencySelected = presenter::onCurrencySelected,
        onKeyPressed = presenter::onKeyPressed
    )
}
