package com.example.walletwise

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.walletwise.presentation.common.AppNavigation
import com.example.walletwise.ui.theme.WalletWiseTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // Hỗ trợ tràn viền
        setContent {
            WalletWiseTheme {
                // Gọi bộ điều hướng trung tâm
                AppNavigation()
            }
        }
    }
}