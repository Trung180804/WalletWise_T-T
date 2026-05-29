package com.example.walletwise.presentation.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.walletwise.presentation.auth.components.AuthBackground
import com.example.walletwise.presentation.auth.components.AuthHeader
import com.example.walletwise.presentation.auth.components.AuthTextField

@Composable
fun ForgotPasswordScreen(
    viewModel: AuthViewModel,
    onNavigateBackToLogin: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    val state by viewModel.state.collectAsState()

    AuthBackground {
        AuthHeader(title = "Quên Mật Khẩu")

        Text(
            text = "Nhập email của bạn để nhận liên kết đặt lại mật khẩu hệ thống.",
            color = Color.LightGray,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        AuthTextField(
            value = email,
            onValueChange = { email = it },
            label = "Email khôi phục",
            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = Color.Gray) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (state.isLoading) {
            CircularProgressIndicator(color = Color(0xFFFFD700))
        } else {
            Button(
                onClick = { viewModel.resetPassword(email) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700))
            ) {
                Text("GỬI YÊU CẦU", color = Color.Black, fontSize = 16.sp)
            }
        }

        state.error?.let {
            Spacer(modifier = Modifier.height(16.dp))
            // Chữ thông báo (Màu xanh nếu gửi thành công, đỏ nếu lỗi)
            Text(
                text = it,
                color = if (it.contains("Đã gửi")) Color.Green else Color.Red
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Quay lại Đăng nhập",
            color = Color(0xFFFFD700),
            modifier = Modifier.clickable { onNavigateBackToLogin() }
        )
    }
}