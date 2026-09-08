package com.example.walletwise.presentation.profile.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.walletwise.presentation.profile.ProfileRoute
import com.example.walletwise.presentation.profile.SettingsRowItem
import com.example.walletwise.presentation.profile.ThemedDivider
import com.example.walletwise.presentation.profile.TopHeader

@Composable
fun SettingsMainView(onNavigate: (ProfileRoute) -> Unit, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopHeader("Cài đặt", onBack)
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(8.dp))
            SettingsRowItem(Icons.Default.Edit,          "Cỡ chữ")             { onNavigate(ProfileRoute.FONT_SIZE) }
            SettingsRowItem(Icons.AutoMirrored.Filled.List, "Cài đặt danh mục") { onNavigate(ProfileRoute.CATEGORY_MANAGEMENT) }
            SettingsRowItem(Icons.Default.ShoppingCart, "Tiền tệ mặc định") { onNavigate(ProfileRoute.DEFAULT_CURRENCY) }
            SettingsRowItem(Icons.Default.Notifications, "Lời nhắc nhở")       { onNavigate(ProfileRoute.REMINDERS) }
            SettingsRowItem(Icons.Default.Refresh,       "Giao dịch định kỳ") { onNavigate(ProfileRoute.RECURRING) }
            ThemedDivider()
            SettingsRowItem(Icons.Default.Star,          "Chủ đề Sáng / Tối") { onNavigate(ProfileRoute.THEME) }
            Spacer(Modifier.height(40.dp))
        }
    }
}
