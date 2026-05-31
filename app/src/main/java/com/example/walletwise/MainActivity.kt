package com.example.walletwise

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect // Cần import này
import androidx.compose.runtime.collectAsState // Cần import này
import androidx.compose.runtime.getValue // Cần import này
import androidx.lifecycle.viewmodel.compose.viewModel // Cần import này
import com.example.walletwise.presentation.auth.AuthViewModel
import com.example.walletwise.presentation.home.TransactionViewModel
import com.example.walletwise.presentation.common.AppNavigation
import com.example.walletwise.ui.theme.WalletWiseTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WalletWiseTheme {
                // Khai báo ViewModel chuẩn
                val authViewModel: AuthViewModel = viewModel()
                val transactionViewModel: TransactionViewModel = viewModel()

                val user by authViewModel.currentUser.collectAsState()

                LaunchedEffect(Unit) {
                    authViewModel.loadUserProfile()
                }

                // Truyền đúng các tham số vào AppNavigation
                AppNavigation(
                    authViewModel = authViewModel,
                    transactionViewModel = transactionViewModel,
                    currentUser = user
                )
            }
        }
    }
}