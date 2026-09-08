package com.example.walletwise.presentation.profile.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.walletwise.presentation.profile.TopHeader
import com.example.walletwise.ui.theme.LocalAppTheme

@Composable
fun ThemeView(onBack: () -> Unit) {
    val appTheme = LocalAppTheme.current
    val isDark   = appTheme.value

    Column(modifier = Modifier.fillMaxSize()) {
        TopHeader("Chủ đề", onBack)

        Column(modifier = Modifier.padding(24.dp)) {
            Text("Chọn giao diện hiển thị", color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ThemeOptionCard("SÁNG", Color.White, Color.Black, !isDark) { appTheme.value = false }
                ThemeOptionCard("TỐI", Color(0xFF121212), Color.White, isDark) { appTheme.value = true }
            }
            Spacer(Modifier.height(32.dp))
            Text("Giao diện sẽ được áp dụng ngay lập tức.", color = Color.Gray, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun ThemeOptionCard(label: String, bg: Color, fg: Color, isSelected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) {
        Box(
            modifier = Modifier
                .size(110.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                    shape = RoundedCornerShape(16.dp)
                )
                .background(bg),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, color = fg, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (isSelected) {
                    Spacer(Modifier.height(6.dp))
                    Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (isSelected) Text("Đang dùng", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
