package com.example.walletwise.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

// 1. Tạo biến LocalAppTheme dùng chung cho toàn app
val LocalAppTheme = compositionLocalOf { mutableStateOf(false) }

private val DarkColorScheme = darkColorScheme(
    primary = GoldPrimary, // 👉 Đảm bảo bạn đã khai báo GoldPrimary bên file Color.kt
    background = DarkBackground,
    surface = DarkSurface,
    onBackground = TextDark,
    onSurface = TextDark
)

private val LightColorScheme = lightColorScheme(
    primary = GoldPrimary,
    background = LightBackground,
    surface = LightSurface,
    onBackground = TextLight,
    onSurface = TextLight
)

@Composable
fun WalletWiseTheme(
    content: @Composable () -> Unit
) {
    // 2. Lấy theme của hệ thống làm mặc định khi mới mở app
    val isSystemDark = isSystemInDarkTheme()
    val appThemeState = remember { mutableStateOf(isSystemDark) }

    // 3. Cung cấp appThemeState cho toàn bộ ứng dụng
    CompositionLocalProvider(LocalAppTheme provides appThemeState) {

        // 4. Lắng nghe sự thay đổi. Khi bạn bấm nút ở ProfileScreen, biến này sẽ thay đổi theo!
        val isDark = LocalAppTheme.current.value
        val colorScheme = if (isDark) DarkColorScheme else LightColorScheme

        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography, // Đảm bảo bạn đã có file Type.kt chứa Typography
            content = content
        )
    }
}