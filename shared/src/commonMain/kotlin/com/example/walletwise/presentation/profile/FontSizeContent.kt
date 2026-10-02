package com.example.walletwise.presentation.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.walletwise.shared.resources.Res
import com.example.walletwise.shared.resources.font_size_current
import com.example.walletwise.shared.resources.font_size_extra_large
import com.example.walletwise.shared.resources.font_size_large
import com.example.walletwise.shared.resources.font_size_medium
import com.example.walletwise.shared.resources.font_size_preview
import com.example.walletwise.shared.resources.font_size_preview_title
import com.example.walletwise.shared.resources.font_size_quick_presets
import com.example.walletwise.shared.resources.font_size_save
import com.example.walletwise.shared.resources.font_size_small
import com.example.walletwise.shared.resources.font_size_title
import com.example.walletwise.shared.resources.font_size_value
import org.jetbrains.compose.resources.stringResource

@Composable
fun FontSizeContent(
    state: FontSizeUiState,
    onSliderValueChanged: (Float) -> Unit,
    onPresetSelected: (FontSizePresetId) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopHeader(stringResource(Res.string.font_size_title), onBack)

        Column(
            modifier = Modifier
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        stringResource(Res.string.font_size_preview_title),
                        color = Color.Gray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        previewText(state.preview),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = state.fontSizeSp.sp,
                        lineHeight = (state.fontSizeSp * 1.5f).sp
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
            Text(
                stringResource(Res.string.font_size_current, state.fontSizeSp.toInt()),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(8.dp))
            Slider(
                value = state.fontSizeSp,
                onValueChange = onSliderValueChanged,
                valueRange = state.minFontSizeSp..state.maxFontSizeSp,
                steps = state.sliderSteps,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(Res.string.font_size_value, state.minFontSizeSp.toInt()),
                    color = Color.Gray,
                    fontSize = 12.sp
                )
                Text(
                    stringResource(Res.string.font_size_value, state.maxFontSizeSp.toInt()),
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }

            Spacer(Modifier.height(32.dp))
            Text(
                stringResource(Res.string.font_size_quick_presets),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                state.presets.forEach { preset ->
                    val isSelected = state.selectedPreset?.id == preset.id
                    val background = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                    val foreground = if (isSelected) {
                        Color.Black
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(background)
                            .clickable { onPresetSelected(preset.id) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            presetLabel(preset.id),
                            color = foreground,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    stringResource(Res.string.font_size_save),
                    color = Color.Black,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun previewText(preview: FontSizePreview): String = when (preview) {
    FontSizePreview.WALLETWISE_DESCRIPTION -> stringResource(Res.string.font_size_preview)
}

@Composable
private fun presetLabel(id: FontSizePresetId): String = when (id) {
    FontSizePresetId.SMALL -> stringResource(Res.string.font_size_small)
    FontSizePresetId.MEDIUM -> stringResource(Res.string.font_size_medium)
    FontSizePresetId.LARGE -> stringResource(Res.string.font_size_large)
    FontSizePresetId.EXTRA_LARGE -> stringResource(Res.string.font_size_extra_large)
}
