package com.example.walletwise.presentation.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.walletwise.presentation.auth.components.AuthBackground
import com.example.walletwise.presentation.auth.components.AuthHeader
import com.example.walletwise.presentation.auth.components.AuthTextField

@Composable
fun RegisterScreen(
    viewModel: AuthViewModel,
    onNavigateToLogin: () -> Unit,
    onRegisterSuccess: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.isSuccess) {
        if (state.isSuccess) {
            onRegisterSuccess()
            viewModel.resetState()
        }
    }

    AuthBackground {
        AuthHeader(title = "Đăng Ký")

        AuthTextField(
            value = username,
            onValueChange = { username = it },
            label = "Họ và tên",
            leadingIcon = { Icon(Icons.Default.AccountCircle, contentDescription = null, tint = Color.Gray) }
        )

        AuthTextField(
            value = email,
            onValueChange = { email = it },
            label = "Email",
            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = Color.Gray) }
        )

        AuthTextField(
            value = password,
            onValueChange = { password = it },
            label = "Mật khẩu",
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Color.Gray) },
            visualTransformation = PasswordVisualTransformation()
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (state.isLoading) {
            CircularProgressIndicator(color = Color(0xFFFFD700))
        } else {
            Button(
                onClick = { viewModel.register(email, password, username) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700))
            ) {
                Text("ĐĂNG KÝ", color = Color.Black, fontSize = 16.sp)
            }
        }

        state.error?.let {
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = it, color = Color.Red)
        }

        Spacer(modifier = Modifier.height(24.dp))
        Row {
            Text("Đã có tài khoản? ", color = Color.White)
            Text(
                text = "Đăng nhập",
                color = Color(0xFFFFD700),
                modifier = Modifier.clickable { onNavigateToLogin() }
            )
        }
    }
}