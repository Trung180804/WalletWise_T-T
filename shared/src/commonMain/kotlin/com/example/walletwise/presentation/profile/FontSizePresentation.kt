package com.example.walletwise.presentation.profile

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

const val DEFAULT_FONT_SIZE_SP = 16f
const val MIN_FONT_SIZE_SP = 12f
const val MAX_FONT_SIZE_SP = 26f
const val FONT_SIZE_SLIDER_STEPS = 6

enum class FontSizePresetId {
    SMALL,
    MEDIUM,
    LARGE,
    EXTRA_LARGE
}

data class FontSizePreset(
    val id: FontSizePresetId,
    val sizeSp: Float
)

val FontSizePresets: List<FontSizePreset> = listOf(
    FontSizePreset(FontSizePresetId.SMALL, 13f),
    FontSizePreset(FontSizePresetId.MEDIUM, 16f),
    FontSizePreset(FontSizePresetId.LARGE, 20f),
    FontSizePreset(FontSizePresetId.EXTRA_LARGE, 24f)
)

enum class FontSizePreview {
    WALLETWISE_DESCRIPTION
}

enum class FontSizeNavigationReason {
    BACK,
    SAVE
}

sealed interface FontSizeUiEvent {
    data class NavigateBack(val reason: FontSizeNavigationReason) : FontSizeUiEvent
}

data class FontSizeEventEnvelope(
    val id: Long,
    val event: FontSizeUiEvent
)

data class FontSizeUiState(
    val fontSizeSp: Float = DEFAULT_FONT_SIZE_SP,
    val minFontSizeSp: Float = MIN_FONT_SIZE_SP,
    val maxFontSizeSp: Float = MAX_FONT_SIZE_SP,
    val sliderSteps: Int = FONT_SIZE_SLIDER_STEPS,
    val presets: List<FontSizePreset> = FontSizePresets,
    val preview: FontSizePreview = FontSizePreview.WALLETWISE_DESCRIPTION,
    val pendingEvent: FontSizeEventEnvelope? = null
) {
    val selectedPreset: FontSizePreset?
        get() = presets.firstOrNull { it.sizeSp == fontSizeSp }
}

class FontSizePresenter(
    initialState: FontSizeUiState = FontSizeUiState()
) {
    private val mutableState = MutableStateFlow(initialState.normalized())
    val state: StateFlow<FontSizeUiState> = mutableState.asStateFlow()

    private var nextEventId = (initialState.pendingEvent?.id ?: 0L) + 1L

    fun onSliderValueChanged(value: Float) {
        if (!value.isFinite()) return
        mutableState.value = mutableState.value.copy(
            fontSizeSp = value.coerceIn(MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP)
        )
    }

    fun onPresetSelected(id: FontSizePresetId) {
        val preset = FontSizePresets.firstOrNull { it.id == id } ?: return
        mutableState.value = mutableState.value.copy(fontSizeSp = preset.sizeSp)
    }

    fun onBack() {
        emitNavigation(FontSizeNavigationReason.BACK)
    }

    fun onSave() {
        emitNavigation(FontSizeNavigationReason.SAVE)
    }

    fun consumeEvent(id: Long) {
        val current = mutableState.value
        if (current.pendingEvent?.id == id) {
            mutableState.value = current.copy(pendingEvent = null)
        }
    }

    private fun emitNavigation(reason: FontSizeNavigationReason) {
        mutableState.value = mutableState.value.copy(
            pendingEvent = FontSizeEventEnvelope(
                id = nextEventId++,
                event = FontSizeUiEvent.NavigateBack(reason)
            )
        )
    }
}

private fun FontSizeUiState.normalized(): FontSizeUiState = copy(
    fontSizeSp = if (fontSizeSp.isFinite()) {
        fontSizeSp.coerceIn(MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP)
    } else {
        DEFAULT_FONT_SIZE_SP
    },
    minFontSizeSp = MIN_FONT_SIZE_SP,
    maxFontSizeSp = MAX_FONT_SIZE_SP,
    sliderSteps = FONT_SIZE_SLIDER_STEPS,
    presets = FontSizePresets,
    preview = FontSizePreview.WALLETWISE_DESCRIPTION
)
