package com.example.walletwise.presentation.transaction

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NoTransactionImage(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
        .semantics(mergeDescendants = true) { contentDescription = "Giao dịch không có ảnh" }, contentAlignment = Alignment.Center) {
        val compact = maxWidth < 56.dp || maxHeight < 56.dp
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(if (compact) 1.dp else 2.dp)) {
            Icon(Icons.Default.ImageNotSupported, null, Modifier.size(if (compact) 10.dp else 18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("No image", fontSize = if (compact) 7.sp else 10.sp, lineHeight = if (compact) 9.sp else 14.sp, letterSpacing = 0.sp,
                modifier = if (compact) Modifier.widthIn(max = 24.dp) else Modifier,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = if (compact) 2 else 1, softWrap = compact)
        }
    }
}
