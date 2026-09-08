package com.example.walletwise.presentation.profile
// Chăm sóc khách hàng
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CustomerCareView(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopHeader("Chăm sóc khách hàng", onBack)
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(16.dp))
            Text("Chúng tôi có thể giúp gì cho bạn?", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))

            // Các nút liên hệ UI
            SettingsRowItem(Icons.Default.Call, "Gọi Hotline (Miễn phí)") { /* TODO */ }
            SettingsRowItem(Icons.Default.Email, "Gửi Email hỗ trợ") { /* TODO */ }
            SettingsRowItem(Icons.Default.QuestionAnswer, "Câu hỏi thường gặp (FAQ)") { /* TODO */ }

            Spacer(Modifier.height(32.dp))
            FormInputBlock("Gửi phản hồi trực tiếp", "", {}, "Nhập vấn đề bạn đang gặp phải...")
            Button(
                onClick = { /* TODO */ },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Gửi yêu cầu", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}
