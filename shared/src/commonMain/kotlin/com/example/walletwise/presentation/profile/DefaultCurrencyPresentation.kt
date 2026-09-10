package com.example.walletwise.presentation.profile

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class CurrencyDisplayName {
    VND,
    USD,
    EUR,
    GBP,
    CNY,
    JPY,
    CAD,
    AUD,
    HKD,
    KRW,
    SGD,
    INR
}

data class CurrencyOption(
    val code: String,
    val displayName: CurrencyDisplayName
)

val DefaultCurrencyOptions: List<CurrencyOption> = listOf(
    CurrencyOption("VND", CurrencyDisplayName.VND),
    CurrencyOption("USD", CurrencyDisplayName.USD),
    CurrencyOption("EUR", CurrencyDisplayName.EUR),
    CurrencyOption("GBP", CurrencyDisplayName.GBP),
    CurrencyOption("CNY", CurrencyDisplayName.CNY),
    CurrencyOption("JPY", CurrencyDisplayName.JPY),
    CurrencyOption("CAD", CurrencyDisplayName.CAD),
    CurrencyOption("AUD", CurrencyDisplayName.AUD),
    CurrencyOption("HKD", CurrencyDisplayName.HKD),
    CurrencyOption("KRW", CurrencyDisplayName.KRW),
    CurrencyOption("SGD", CurrencyDisplayName.SGD),
    CurrencyOption("INR", CurrencyDisplayName.INR)
)

sealed interface DefaultCurrencyUiEvent {
    data class CurrencySelected(val code: String) : DefaultCurrencyUiEvent
    data object Back : DefaultCurrencyUiEvent
}

data class DefaultCurrencyEventEnvelope(
    val id: Long,
    val event: DefaultCurrencyUiEvent
)

data class DefaultCurrencyUiState(
    val options: List<CurrencyOption> = DefaultCurrencyOptions,
    val pendingEvent: DefaultCurrencyEventEnvelope? = null
)

class DefaultCurrencyPresenter(
    initialState: DefaultCurrencyUiState = DefaultCurrencyUiState()
) {
    private val mutableState = MutableStateFlow(
        initialState.copy(options = DefaultCurrencyOptions)
    )
    val state: StateFlow<DefaultCurrencyUiState> = mutableState.asStateFlow()

    private var nextEventId = (initialState.pendingEvent?.id ?: 0L) + 1L

    fun onCurrencySelected(code: String) {
        if (DefaultCurrencyOptions.none { it.code == code }) return
        emit(DefaultCurrencyUiEvent.CurrencySelected(code))
    }

    fun onBack() {
        emit(DefaultCurrencyUiEvent.Back)
    }

    fun consumeEvent(id: Long) {
        val current = mutableState.value
        if (current.pendingEvent?.id == id) {
            mutableState.value = current.copy(pendingEvent = null)
        }
    }

    private fun emit(event: DefaultCurrencyUiEvent) {
        mutableState.value = mutableState.value.copy(
            pendingEvent = DefaultCurrencyEventEnvelope(
                id = nextEventId++,
                event = event
            )
        )
    }
}
