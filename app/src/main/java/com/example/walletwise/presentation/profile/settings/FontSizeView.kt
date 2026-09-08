package com.example.walletwise.presentation.profile.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.walletwise.presentation.profile.TopHeader

@Composable
fun FontSizeView(onBack: () -> Unit) {
    var fontSize by remember { mutableFloatStateOf(16f) }
    val presets = listOf("Nhỏ" to 13f, "Vừa" to 16f, "Lớn" to 20f, "Rất lớn" to 24f)

    Column(modifier = Modifier.fillMaxSize()) {
        TopHeader("Cỡ chữ", onBack)

        Column(
            modifier = Modifier
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape  = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "Xem trước văn bản",
                        color = Color.Gray, fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        "WalletWise giúp bạn quản lý chi tiêu thông minh hơn mỗi ngày.",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = fontSize.sp,
                        lineHeight = (fontSize * 1.5f).sp
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
            Text("Cỡ chữ: ${fontSize.toInt()}sp", color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Slider(
                value          = fontSize,
                onValueChange  = { fontSize = it },
                valueRange     = 12f..26f,
                steps          = 6,
                colors         = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth()
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("12sp", color = Color.Gray, fontSize = 12.sp)
                Text("26sp", color = Color.Gray, fontSize = 12.sp)
            }

            Spacer(Modifier.height(32.dp))
            Text("Preset nhanh", color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                presets.forEach { (label, size) ->
                    val isSelected = fontSize == size
                    val bgBtn = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    val fgBtn = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(bgBtn)
                            .clickable { fontSize = size }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, color = fgBtn, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.Center)
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary), shape = RoundedCornerShape(14.dp)) {
                Text("Lưu cài đặt", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
