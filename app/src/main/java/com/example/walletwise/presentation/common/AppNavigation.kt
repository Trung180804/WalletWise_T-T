package com.example.walletwise.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.walletwise.data.repository.AuthRepositoryImpl
import com.example.walletwise.presentation.auth.*
import com.example.walletwise.presentation.home.AddTransactionScreen
import com.example.walletwise.presentation.home.HomeScreen
import com.example.walletwise.presentation.home.TransactionViewModel

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    // Khởi tạo Repository và ViewModel
    val authRepository = remember { AuthRepositoryImpl() }
    val authViewModel = remember { AuthViewModel(authRepository) }

    // ĐIỂM ĂN TIỀN: Kiểm tra đăng nhập vĩnh viễn (Offline)
    // Nếu Firebase đã có phiên đăng nhập, ném thẳng vào "home", ngược lại vào "login"
    val startRoute = if (authRepository.isUserLoggedIn()) "home" else "login"

    NavHost(navController = navController, startDestination = startRoute) {

        composable("login") {
            LoginScreen(
                viewModel = authViewModel,
                onNavigateToRegister = { navController.navigate("register") },
                onNavigateToForgotPassword = { navController.navigate("forgot_password") },
                onLoginSuccess = {
                    navController.navigate("home") {
                        // Xóa sạch lịch sử màn hình login để ấn nút Back không bị quay lại
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }

        composable("register") {
            RegisterScreen(
                viewModel = authViewModel,
                onNavigateToLogin = { navController.popBackStack() }, // Quay lại trang trước
                onRegisterSuccess = {
                    navController.navigate("home") {
                        popUpTo("login") { inclusive = true }
                        popUpTo("register") { inclusive = true }
                    }
                }
            )
        }

        composable("forgot_password") {
            ForgotPasswordScreen(
                viewModel = authViewModel,
                onNavigateBackToLogin = { navController.popBackStack() }
            )
        }

        composable("home") {
            // Khởi tạo ViewModel cho giao dịch
            val transactionViewModel = remember { TransactionViewModel() }

            HomeScreen(
                viewModel = transactionViewModel,
                onNavigateToAdd = {
                    navController.navigate("add_transaction")
                },
                onLogout = {
                    authRepository.logout()
                    navController.navigate("login") {
                        popUpTo("home") { inclusive = true }
                    }
                }
            )
        }

        composable("add_transaction") {
            // Dùng chung viewModel để dữ liệu đồng bộ với Home
            val transactionViewModel = remember { TransactionViewModel() }
            AddTransactionScreen(
                viewModel = transactionViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}