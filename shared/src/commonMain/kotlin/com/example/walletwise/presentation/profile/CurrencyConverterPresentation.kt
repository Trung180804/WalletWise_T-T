package com.example.walletwise.presentation.profile

import kotlin.math.absoluteValue
import kotlin.math.round
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CurrencyDefinition(val code: String, val rateToUsd: Double)

val SupportedCurrencies: List<CurrencyDefinition> = listOf(
    CurrencyDefinition(code = "VND", rateToUsd = 25_400.0),
    CurrencyDefinition(code = "USD", rateToUsd = 1.0),
    CurrencyDefinition(code = "EUR", rateToUsd = 0.93),
    CurrencyDefinition(code = "JPY", rateToUsd = 155.0),
    CurrencyDefinition(code = "CNY", rateToUsd = 7.23)
)

val CurrencyKeypadKeys: List<String> = listOf(
    "C", "⌫", "%", "÷",
    "7", "8", "9", "×",
    "4", "5", "6", "-",
    "1", "2", "3", "+",
    "00", "0", ".", "="
)

enum class CurrencyInputProblem { INVALID, TOO_LARGE }

sealed interface CurrencyConverterUiEvent {
    data class InputRejected(val problem: CurrencyInputProblem) : CurrencyConverterUiEvent
}

data class CurrencyConverterEventEnvelope(
    val id: Long,
    val event: CurrencyConverterUiEvent
)

data class CurrencyConverterUiState(
    val rows: List<CurrencyDefinition> = listOf(
        SupportedCurrencies[0],
        SupportedCurrencies[2],
        SupportedCurrencies[1]
    ),
    val activeIndex: Int = 0,
    val rawInput: String = "1000",
    val pendingOperation: String = "",
    val pendingValue: Double = 0.0,
    val justComputed: Boolean = false,
    val dropdownRowIndex: Int? = null,
    val pendingEvent: CurrencyConverterEventEnvelope? = null
) {
    fun headerLabel(defaultLabel: String): String = if (pendingOperation.isNotEmpty()) {
        "${formatCurrencyNumber(pendingValue)} $pendingOperation ..."
    } else {
        defaultLabel
    }

    fun displayValue(rowIndex: Int): String {
        val target = rows.getOrNull(rowIndex) ?: return "0"
        val active = rows.getOrNull(activeIndex) ?: return "0"
        if (active.code == target.code) return displayInput(rawInput)

        val amount = parseCurrencyInput(rawInput) ?: 0.0
        val converted = amount / active.rateToUsd * target.rateToUsd
        return if (converted.isFinite()) formatCurrencyNumber(converted) else "0"
    }
}

class CurrencyConverterPresenter(
    initialState: CurrencyConverterUiState = CurrencyConverterUiState()
) {
    private val mutableState = MutableStateFlow(initialState.normalized())
    val state: StateFlow<CurrencyConverterUiState> = mutableState.asStateFlow()

    private var nextEventId = (initialState.pendingEvent?.id ?: 0L) + 1L

    fun onRawInputChanged(value: String) {
        mutableState.value = mutableState.value.copy(rawInput = value, justComputed = false)
        if (value.isBlank()) return

        val parsed = value.replace(",", "").toDoubleOrNull()
        when {
            parsed == null -> emitInputProblem(CurrencyInputProblem.INVALID)
            !parsed.isFinite() -> emitInputProblem(CurrencyInputProblem.TOO_LARGE)
        }
    }

    fun onKeyPressed(key: String) {
        val current = mutableState.value
        when (key) {
            "C" -> mutableState.value = current.copy(
                rawInput = "0",
                pendingOperation = "",
                pendingValue = 0.0,
                justComputed = false
            )
            "⌫" -> if (!current.justComputed) {
                mutableState.value = current.copy(
                    rawInput = if (current.rawInput.length > 1) {
                        current.rawInput.dropLast(1)
                    } else {
                        "0"
                    }
                )
            }
            "%" -> {
                val value = parseCurrencyInput(current.rawInput) ?: 0.0
                setComputedResult(value / 100.0, clearOperation = false)
            }
            "÷", "×", "-", "+" -> mutableState.value = current.copy(
                rawInput = "0",
                pendingOperation = key,
                pendingValue = parseCurrencyInput(current.rawInput) ?: 0.0,
                justComputed = false
            )
            "=" -> calculatePendingOperation()
            "." -> if (!current.rawInput.contains('.')) {
                mutableState.value = current.copy(
                    rawInput = current.rawInput + ".",
                    justComputed = false
                )
            }
            "00" -> if (!current.justComputed) {
                mutableState.value = current.copy(
                    rawInput = if (current.rawInput == "0") "0" else current.rawInput + "00"
                )
            }
            in "0".."9" -> mutableState.value = current.copy(
                rawInput = if (current.justComputed || current.rawInput == "0") {
                    key
                } else {
                    current.rawInput + key
                },
                justComputed = false
            )
            else -> emitInputProblem(CurrencyInputProblem.INVALID)
        }
    }

    fun onCurrencyRowSelected(rowIndex: Int) {
        val current = mutableState.value
        if (rowIndex !in current.rows.indices) return
        mutableState.value = current.copy(
            rawInput = current.displayValue(rowIndex).replace(",", ""),
            activeIndex = rowIndex,
            pendingOperation = "",
            pendingValue = 0.0,
            justComputed = false
        )
    }

    fun onOpenCurrencyPicker(rowIndex: Int) {
        if (rowIndex in mutableState.value.rows.indices) {
            mutableState.value = mutableState.value.copy(dropdownRowIndex = rowIndex)
        }
    }

    fun onDismissCurrencyPicker() {
        mutableState.value = mutableState.value.copy(dropdownRowIndex = null)
    }

    fun onCurrencySelected(currency: CurrencyDefinition) {
        val current = mutableState.value
        val rowIndex = current.dropdownRowIndex ?: return
        val supported = SupportedCurrencies.firstOrNull { it.code == currency.code } ?: return
        if (rowIndex !in current.rows.indices) return

        val updatedRows = current.rows.toMutableList().apply { this[rowIndex] = supported }
        mutableState.value = current.copy(rows = updatedRows, dropdownRowIndex = null)
    }

    fun consumeEvent(id: Long) {
        val current = mutableState.value
        if (current.pendingEvent?.id == id) {
            mutableState.value = current.copy(pendingEvent = null)
        }
    }

    private fun calculatePendingOperation() {
        val current = mutableState.value
        if (current.pendingOperation.isEmpty()) return

        val operand = parseCurrencyInput(current.rawInput) ?: 0.0
        val result = when (current.pendingOperation) {
            "÷" -> if (operand != 0.0) current.pendingValue / operand else 0.0
            "×" -> current.pendingValue * operand
            "-" -> current.pendingValue - operand
            "+" -> current.pendingValue + operand
            else -> operand
        }
        setComputedResult(result, clearOperation = true)
    }

    private fun setComputedResult(result: Double, clearOperation: Boolean) {
        val current = mutableState.value
        if (!result.isFinite()) {
            mutableState.value = current.copy(
                pendingOperation = if (clearOperation) "" else current.pendingOperation,
                pendingValue = if (clearOperation) 0.0 else current.pendingValue,
                justComputed = true
            )
            emitInputProblem(CurrencyInputProblem.TOO_LARGE)
            return
        }

        mutableState.value = current.copy(
            rawInput = formatCurrencyNumber(result).replace(",", ""),
            pendingOperation = if (clearOperation) "" else current.pendingOperation,
            pendingValue = if (clearOperation) 0.0 else current.pendingValue,
            justComputed = true
        )
    }

    private fun emitInputProblem(problem: CurrencyInputProblem) {
        mutableState.value = mutableState.value.copy(
            pendingEvent = CurrencyConverterEventEnvelope(
                id = nextEventId++,
                event = CurrencyConverterUiEvent.InputRejected(problem)
            )
        )
    }
}

fun formatCurrencyNumber(value: Double): String {
    if (!value.isFinite()) return "0"

    val absolute = value.absoluteValue
    val rounded = if (absolute < 1_000_000_000_000_000.0) {
        round(absolute * 100.0) / 100.0
    } else {
        absolute
    }
    if (rounded == 0.0) return "0"

    val plain = expandScientificNotation(rounded.toString())
    val integerPart = plain.substringBefore('.').trimStart('0').ifEmpty { "0" }
    val fractionPart = plain.substringAfter('.', "").trimEnd('0').take(2)
    val groupedInteger = integerPart.reversed().chunked(3).joinToString(",").reversed()
    val sign = if (value < 0.0) "-" else ""
    return if (fractionPart.isEmpty()) sign + groupedInteger
    else "$sign$groupedInteger.$fractionPart"
}

internal fun parseCurrencyInput(value: String): Double? = value
    .replace(",", "")
    .takeUnless { it.isBlank() }
    ?.toDoubleOrNull()
    ?.takeIf { it.isFinite() }

private fun displayInput(value: String): String = when {
    value.isBlank() -> value
    else -> parseCurrencyInput(value)?.let(::formatCurrencyNumber) ?: value
}

private fun CurrencyConverterUiState.normalized(): CurrencyConverterUiState {
    val safeRows = if (rows.size == 3 && rows.all { candidate ->
            SupportedCurrencies.any { it.code == candidate.code }
        }
    ) {
        rows.map { candidate -> SupportedCurrencies.first { it.code == candidate.code } }
    } else {
        CurrencyConverterUiState().rows
    }
    return copy(
        rows = safeRows,
        activeIndex = activeIndex.coerceIn(safeRows.indices),
        dropdownRowIndex = dropdownRowIndex?.takeIf { it in safeRows.indices }
    )
}

private fun expandScientificNotation(value: String): String {
    val exponentMarker = value.indexOfFirst { it == 'e' || it == 'E' }
    if (exponentMarker < 0) return value

    val coefficient = value.substring(0, exponentMarker)
    val exponent = value.substring(exponentMarker + 1).toIntOrNull() ?: return value
    val decimalIndex = coefficient.indexOf('.').let { if (it < 0) coefficient.length else it }
    val digits = coefficient.replace(".", "")
    val shiftedDecimalIndex = decimalIndex + exponent

    return when {
        shiftedDecimalIndex <= 0 -> "0." + "0".repeat(-shiftedDecimalIndex) + digits
        shiftedDecimalIndex >= digits.length -> digits + "0".repeat(shiftedDecimalIndex - digits.length)
        else -> digits.substring(0, shiftedDecimalIndex) + "." +
            digits.substring(shiftedDecimalIndex)
    }
}
