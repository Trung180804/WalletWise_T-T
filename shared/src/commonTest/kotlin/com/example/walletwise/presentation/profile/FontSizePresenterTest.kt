package com.example.walletwise.presentation.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class FontSizePresenterTest {
    @Test
    fun defaults_preserveRangeStepsPreviewAndMediumPreset() {
        val state = FontSizePresenter().state.value

        assertEquals(16f, state.fontSizeSp)
        assertEquals(12f, state.minFontSizeSp)
        assertEquals(26f, state.maxFontSizeSp)
        assertEquals(6, state.sliderSteps)
        assertEquals(FontSizePresetId.MEDIUM, state.selectedPreset?.id)
        assertEquals(FontSizePreview.WALLETWISE_DESCRIPTION, state.preview)
    }

    @Test
    fun slider_clampsOutsideRangeAndIgnoresNonFiniteValues() {
        val presenter = FontSizePresenter()

        presenter.onSliderValueChanged(2f)
        assertEquals(12f, presenter.state.value.fontSizeSp)
        presenter.onSliderValueChanged(80f)
        assertEquals(26f, presenter.state.value.fontSizeSp)
        presenter.onSliderValueChanged(Float.NaN)
        assertEquals(26f, presenter.state.value.fontSizeSp)
        presenter.onSliderValueChanged(Float.POSITIVE_INFINITY)
        assertEquals(26f, presenter.state.value.fontSizeSp)

        val normalized = FontSizePresenter(
            FontSizeUiState(fontSizeSp = Float.NEGATIVE_INFINITY)
        )
        assertEquals(16f, normalized.state.value.fontSizeSp)
    }

    @Test
    fun slider_updatesCurrentValueAndClearsPresetSelectionForCustomValue() {
        val presenter = FontSizePresenter()

        presenter.onSliderValueChanged(18f)

        assertEquals(18f, presenter.state.value.fontSizeSp)
        assertNull(presenter.state.value.selectedPreset)
    }

    @Test
    fun allFourPresets_selectTheirExactValues() {
        val presenter = FontSizePresenter()
        val expected = mapOf(
            FontSizePresetId.SMALL to 13f,
            FontSizePresetId.MEDIUM to 16f,
            FontSizePresetId.LARGE to 20f,
            FontSizePresetId.EXTRA_LARGE to 24f
        )

        expected.forEach { (id, size) ->
            presenter.onPresetSelected(id)
            assertEquals(size, presenter.state.value.fontSizeSp)
            assertEquals(id, presenter.state.value.selectedPreset?.id)
        }
    }

    @Test
    fun saveAndBack_emitOnceAndDoNotReplayAfterConsume() {
        val presenter = FontSizePresenter()

        presenter.onSave()
        val saveEnvelope = presenter.state.value.pendingEvent!!
        assertEquals(
            FontSizeNavigationReason.SAVE,
            assertIs<FontSizeUiEvent.NavigateBack>(saveEnvelope.event).reason
        )
        presenter.consumeEvent(saveEnvelope.id)
        assertNull(presenter.state.value.pendingEvent)
        presenter.onSliderValueChanged(20f)
        assertNull(presenter.state.value.pendingEvent)

        presenter.onBack()
        val backEnvelope = presenter.state.value.pendingEvent!!
        assertEquals(
            FontSizeNavigationReason.BACK,
            assertIs<FontSizeUiEvent.NavigateBack>(backEnvelope.event).reason
        )
        presenter.consumeEvent(backEnvelope.id)
        assertNull(presenter.state.value.pendingEvent)
    }
}
