package com.example.walletwise.presentation.budget

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.luminance

data class FinancialBucketPalette(val accent: Color, val container: Color, val icon: ImageVector)

@Composable
fun financialBucketPalette(key: String): FinancialBucketPalette {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val (color, icon) = when (key) {
        "needs", "necessities" -> Color(0xFF9B6100) to Icons.Default.Home
        "savings", "freedom" -> Color(0xFF23734A) to Icons.AutoMirrored.Filled.TrendingUp
        "long_term" -> Color(0xFF2568AA) to Icons.Default.Savings
        "education" -> Color(0xFF7952AB) to Icons.Default.School
        "giving" -> Color(0xFFB35343) to Icons.Default.Favorite
        else -> Color(0xFFB63770) to Icons.Default.Celebration
    }
    val accent = if (dark) lerp(color, Color.White, 0.42f) else color
    val container = lerp(MaterialTheme.colorScheme.surface, accent, if (dark) 0.12f else 0.08f)
    return FinancialBucketPalette(accent, container, icon)
}
