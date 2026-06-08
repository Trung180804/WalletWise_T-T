package com.example.walletwise.presentation.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.walletwise.presentation.auth.components.AuthBackground
import com.example.walletwise.presentation.auth.components.AuthHeader
import com.example.walletwise.presentation.auth.components.AuthTextField

//@Composable
//fun ForgotPasswordScreen(
//    viewModel: AuthViewModel,
//    onNavigateBackToLogin: () -> Unit
//) {
//    var email by remember { mutableStateOf("") }
//    var otpInput by remember { mutableStateOf("") }
//    var step by remember { mutableStateOf(1) } // 1: Nhập email, 2: Nhập OTP
//    val state by viewModel.state.collectAsState()
//
//    AuthBackground {
//        // Đổi tiêu đề linh hoạt theo Bước
//        AuthHeader(title = if (step == 1) "Quên Mật Khẩu" else "Xác Minh OTP")
//
//        if (step == 1) {
//            // ==========================================
//            // BƯỚC 1: NHẬP EMAIL ĐỂ NHẬN MÃ
//            // ==========================================
//            Text(
//                text = "Nhập email của bạn để nhận mã OTP xác thực.",
//                color = Color.LightGray,
//                fontSize = 14.sp,
//                modifier = Modifier.padding(bottom = 16.dp)
//            )
//
//            AuthTextField(
//                value = email,
//                onValueChange = { email = it },
//                label = "Email khôi phục",
//                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = Color.Gray) }
//            )
//
//            if (state.isLoading) {
//                CircularProgressIndicator(color = Color(0xFFFFD700), modifier = Modifier.padding(top = 16.dp))
//            } else {
//                Button(
//                    onClick = {
//                        if (email.isNotBlank()) {
//                            viewModel.sendOtpEmail(email)
//                            step = 2 // Chuyển sang giao diện nhập OTP
//                        } else {
//                            // TODO: Có thể hiện toast thông báo chưa nhập email
//                        }
//                    },
//                    modifier = Modifier.fillMaxWidth().height(50.dp),
//                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700))
//                ) {
//                    Text("GỬI MÃ OTP", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Bold)
//                }
//            }
//        } else {
//            // ==========================================
//            // BƯỚC 2: NHẬP MÃ OTP
//            // ==========================================
//            Text(
//                text = "Mã xác nhận đã được gửi tới:\n$email",
//                color = Color.White,
//                fontSize = 14.sp,
//                modifier = Modifier.padding(bottom = 16.dp)
//            )
//
//            AuthTextField(
//                value = otpInput,
//                onValueChange = { otpInput = it },
//                label = "Nhập mã 6 số",
//                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Color.Gray) }
//            )
//
//            if (state.isLoading) {
//                CircularProgressIndicator(color = Color(0xFF00C875), modifier = Modifier.padding(top = 16.dp))
//            } else {
//                Button(
//                    onClick = {
//                        viewModel.verifyOtp(email, otpInput) {
//                            // Khi OTP đúng, sẽ gọi hàm này.
//                            // Hiện tại đang quay về Login, sau này bạn có thể dẫn sang trang "Tạo Mật Khẩu Mới"
//                            onNavigateBackToLogin()
//                        }
//                    },
//                    modifier = Modifier.fillMaxWidth().height(50.dp),
//                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C875)) // Màu xanh lá cho bước xác nhận
//                ) {
//                    Text("XÁC NHẬN", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
//                }
//            }
//        }
//
//        // ==========================================
//        // KHU VỰC THÔNG BÁO LỖI / THÀNH CÔNG
//        // ==========================================
//        state.error?.let { msg ->
//            Spacer(modifier = Modifier.height(16.dp))
//            Text(
//                text = msg,
//                color = if (msg.contains("thành công") || msg.contains("Đã gửi")) Color.Green else Color.Red,
//                fontWeight = FontWeight.Medium
//            )
//        }
//
//        Spacer(modifier = Modifier.height(32.dp))
//
//        // Nút Quay lại
//        Text(
//            text = "Quay lại Đăng nhập",
//            color = Color(0xFFFFD700),
//            fontSize = 15.sp,
//            fontWeight = FontWeight.Bold,
//            modifier = Modifier.clickable {
//                // Reset lại lỗi khi quay ra
//                viewModel.clearError()
//                onNavigateBackToLogin()
//            }
//        )
//    }
//}

//============ĐỔi mật khẩu mới===========
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
            text = "Nhập email đã đăng ký. Firebase sẽ gửi liên kết đặt lại mật khẩu tới email của bạn.",
            color = Color.LightGray,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        AuthTextField(
            value = email,
            onValueChange = { email = it },
            label = "Email",
            leadingIcon = {
                Icon(
                    Icons.Default.Email,
                    contentDescription = null,
                    tint = Color.Gray
                )
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (state.isLoading) {

            CircularProgressIndicator(
                color = Color(0xFFFFD700)
            )

        } else {

            Button(
                onClick = {
                    viewModel.sendPasswordReset(email)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFD700)
                )
            ) {
                Text(
                    "GỬI EMAIL KHÔI PHỤC",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        state.error?.let { msg ->

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = msg,
                color = if (state.isSuccess)
                    Color.Green
                else
                    Color.Red,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Quay lại Đăng nhập",
            color = Color(0xFFFFD700),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable {
                viewModel.clearError()
                onNavigateBackToLogin()
            }
        )
    }
}