package com.example.walletwise.presentation.budget

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.example.walletwise.domain.service.FinancialBucketUsage
import com.example.walletwise.domain.service.formatFinancialPercent

@Composable
fun BudgetUsageTube(usage: FinancialBucketUsage, modifier: Modifier = Modifier, fillColor: Color = financialBucketPalette(usage.allocation.bucket.key).accent) {
    // Compose animation uses the platform MotionDurationScale, including zero duration.
    val fraction by animateFloatAsState(usage.usedFraction, tween(240), label = "budget-usage")
    val shape = RoundedCornerShape(50)
    Box(modifier.fillMaxWidth().padding(vertical = 8.dp).height(16.dp).clip(shape)
        .background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape).semantics {
            contentDescription = "${usage.allocation.bucket.name}: ${usage.allocation.bucket.percent}%"
            stateDescription = "Đã dùng ${formatFinancialPercent(usage.usedPercent)}; còn lại ${formatFinancialPercent(usage.remainingPercent)}"
            progressBarRangeInfo = ProgressBarRangeInfo(usage.usedFraction, 0f..1f)
        }) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(fillColor))
    }
}
