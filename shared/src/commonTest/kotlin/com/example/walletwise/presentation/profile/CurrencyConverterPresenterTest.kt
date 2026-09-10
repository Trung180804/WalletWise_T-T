package com.example.walletwise.presentation.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CurrencyConverterPresenterTest {
    @Test
    fun defaults_preserveCurrenciesRatesRowsAndInput() {
        val state = CurrencyConverterPresenter().state.value

        assertEquals(
            listOf("VND", "USD", "EUR", "JPY", "CNY"),
            SupportedCurrencies.map(CurrencyDefinition::code)
        )
        assertEquals(listOf(25_400.0, 1.0, 0.93, 155.0, 7.23),
            SupportedCurrencies.map(CurrencyDefinition::rateToUsd))
        assertEquals(listOf("VND", "EUR", "USD"), state.rows.map(CurrencyDefinition::code))
        assertEquals(0, state.activeIndex)
        assertEquals("1000", state.rawInput)
        assertEquals(listOf("1,000", "0.04", "0.04"), state.rows.indices.map(state::displayValue))
    }

    @Test
    fun validEmptyNegativeZeroAndGroupedInput_areHandledSafely() {
        val presenter = CurrencyConverterPresenter()

        presenter.onRawInputChanged("25,400.5")
        assertEquals("25,400.5", presenter.state.value.displayValue(0))

        presenter.onRawInputChanged("")
        assertEquals("", presenter.state.value.displayValue(0))
        assertEquals("0", presenter.state.value.displayValue(1))

        presenter.onRawInputChanged("-25400")
        assertEquals(listOf("-25,400", "-0.93", "-1"),
            presenter.state.value.rows.indices.map(presenter.state.value::displayValue))

        presenter.onRawInputChanged("0")
        assertEquals(listOf("0", "0", "0"),
            presenter.state.value.rows.indices.map(presenter.state.value::displayValue))
    }

    @Test
    fun invalidAndTooLargeInput_emitTypedOneTimeEvents() {
        val presenter = CurrencyConverterPresenter()

        presenter.onRawInputChanged("không-phải-số")
        val invalidEnvelope = assertNotNull(presenter.state.value.pendingEvent)
        val invalid = assertIs<CurrencyConverterUiEvent.InputRejected>(invalidEnvelope.event)
        assertEquals(CurrencyInputProblem.INVALID, invalid.problem)

        presenter.consumeEvent(invalidEnvelope.id)
        assertNull(presenter.state.value.pendingEvent)
        presenter.onKeyPressed("1")
        assertNull(presenter.state.value.pendingEvent)

        presenter.onRawInputChanged("9".repeat(400))
        val hugeEnvelope = assertNotNull(presenter.state.value.pendingEvent)
        val huge = assertIs<CurrencyConverterUiEvent.InputRejected>(hugeEnvelope.event)
        assertEquals(CurrencyInputProblem.TOO_LARGE, huge.problem)
        assertTrue(hugeEnvelope.id > invalidEnvelope.id)
    }

    @Test
    fun largeFiniteInput_formatsWithoutJvmFormattingApis() {
        val presenter = CurrencyConverterPresenter()

        presenter.onRawInputChanged("1000000000000000")

        assertEquals("1,000,000,000,000,000", presenter.state.value.displayValue(0))
        assertEquals("1.12", formatCurrencyNumber(1.125))
        assertEquals("1.38", formatCurrencyNumber(1.375))
        assertEquals("1,234.57", formatCurrencyNumber(1234.567))
    }

    @Test
    fun changingSourceAndTargetCurrencies_preservesConversionFormula() {
        val presenter = CurrencyConverterPresenter()
        presenter.onRawInputChanged("25400")

        assertEquals("0.93", presenter.state.value.displayValue(1))
        assertEquals("1", presenter.state.value.displayValue(2))

        presenter.onOpenCurrencyPicker(0)
        presenter.onCurrencySelected(SupportedCurrencies.first { it.code == "USD" })

        assertEquals(listOf("USD", "EUR", "USD"),
            presenter.state.value.rows.map(CurrencyDefinition::code))
        assertEquals("25,400", presenter.state.value.displayValue(0))
        assertEquals("23,622", presenter.state.value.displayValue(1))
        assertEquals("25,400", presenter.state.value.displayValue(2))
    }

    @Test
    fun identicalCurrencies_andSwitchingActiveRow_areHandled() {
        val presenter = CurrencyConverterPresenter()
        presenter.onOpenCurrencyPicker(1)
        presenter.onCurrencySelected(SupportedCurrencies.first { it.code == "VND" })

        assertEquals("1,000", presenter.state.value.displayValue(1))
        presenter.onCurrencyRowSelected(1)

        assertEquals(1, presenter.state.value.activeIndex)
        assertEquals("1000", presenter.state.value.rawInput)
        assertEquals("1,000", presenter.state.value.displayValue(0))
        assertEquals("1,000", presenter.state.value.displayValue(1))
    }

    @Test
    fun keypadCalculator_preservesOperationsPercentAndRounding() {
        val presenter = CurrencyConverterPresenter()
        presenter.onRawInputChanged("10")
        presenter.onKeyPressed("+")
        assertEquals("10 + ...", presenter.state.value.headerLabel("Ngoại tệ"))
        presenter.onKeyPressed("3")
        presenter.onKeyPressed("=")
        assertEquals("13", presenter.state.value.rawInput)
        assertTrue(presenter.state.value.justComputed)

        presenter.onRawInputChanged("1000")
        presenter.onKeyPressed("%")
        assertEquals("10", presenter.state.value.rawInput)

        presenter.onRawInputChanged("5")
        presenter.onKeyPressed("÷")
        presenter.onKeyPressed("0")
        presenter.onKeyPressed("=")
        assertEquals("0", presenter.state.value.rawInput)
    }

    @Test
    fun presenterState_survivesRepeatedReadsLikeRecomposition() {
        val presenter = CurrencyConverterPresenter()
        presenter.onKeyPressed("C")
        presenter.onKeyPressed("7")
        val firstRead = presenter.state.value
        val secondRead = presenter.state.value

        assertEquals("7", firstRead.rawInput)
        assertEquals(firstRead, secondRead)
    }
}
