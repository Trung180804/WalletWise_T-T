package com.example.walletwise.presentation.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class DefaultCurrencyPresenterTest {
    @Test
    fun options_preserveAllTwelveCurrenciesAndTheirOrder() {
        assertEquals(12, DefaultCurrencyOptions.size)
        assertEquals(
            listOf("VND", "USD", "EUR", "GBP", "CNY", "JPY", "CAD", "AUD", "HKD", "KRW", "SGD", "INR"),
            DefaultCurrencyOptions.map(CurrencyOption::code)
        )
        assertEquals(
            listOf(
                CurrencyDisplayName.VND,
                CurrencyDisplayName.USD,
                CurrencyDisplayName.EUR,
                CurrencyDisplayName.GBP,
                CurrencyDisplayName.CNY,
                CurrencyDisplayName.JPY,
                CurrencyDisplayName.CAD,
                CurrencyDisplayName.AUD,
                CurrencyDisplayName.HKD,
                CurrencyDisplayName.KRW,
                CurrencyDisplayName.SGD,
                CurrencyDisplayName.INR
            ),
            DefaultCurrencyOptions.map(CurrencyOption::displayName)
        )
    }

    @Test
    fun selectingEveryCurrency_emitsItsExactCodeOnce() {
        val presenter = DefaultCurrencyPresenter()

        DefaultCurrencyOptions.forEach { option ->
            presenter.onCurrencySelected(option.code)
            val envelope = presenter.state.value.pendingEvent!!
            val event = assertIs<DefaultCurrencyUiEvent.CurrencySelected>(envelope.event)
            assertEquals(option.code, event.code)

            presenter.consumeEvent(envelope.id)
            assertNull(presenter.state.value.pendingEvent)
            assertNull(presenter.state.value.pendingEvent)
        }
    }

    @Test
    fun back_emitsOnceAndDoesNotReplayAfterConsume() {
        val presenter = DefaultCurrencyPresenter()

        presenter.onBack()
        val envelope = presenter.state.value.pendingEvent!!
        assertIs<DefaultCurrencyUiEvent.Back>(envelope.event)
        presenter.consumeEvent(envelope.id)

        assertNull(presenter.state.value.pendingEvent)
        assertNull(presenter.state.value.pendingEvent)
    }

    @Test
    fun unsupportedCode_doesNotEmitSelectionOrCreateFakeSavedState() {
        val presenter = DefaultCurrencyPresenter()

        presenter.onCurrencySelected("UNKNOWN")

        assertNull(presenter.state.value.pendingEvent)
        assertEquals(DefaultCurrencyOptions, presenter.state.value.options)
    }
}
