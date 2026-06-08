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
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

@Composable
fun AppNavigation(
    authViewModel: AuthViewModel,
    transactionViewModel: TransactionViewModel,
    currentUser: com.example.walletwise.domain.model.User? // Nhận user từ MainActivity
) {
    val navController = rememberNavController()

    val authRepository = remember { AuthRepositoryImpl() }
    val startRoute = if (authRepository.isUserLoggedIn()) "home" else "login"

    val userState by authViewModel.currentUser.collectAsState()

    NavHost(navController = navController, startDestination = startRoute) {

        composable("login") {
            LoginScreen(
                viewModel = authViewModel,
                onNavigateToRegister = { navController.navigate("register") },
                onNavigateToForgotPassword = { navController.navigate("forgot_password") },
                onLoginSuccess = {
                    authViewModel.loadUserProfile()
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
                    authViewModel.loadUserProfile()
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
            HomeScreen(
                viewModel = transactionViewModel,
                user = userState,
                onNavigateToAdd = { navController.navigate("add_transaction") },
                onLogout = {

                    authViewModel.logout()
                    // 1. Đăng xuất Firebase trực tiếp
                    FirebaseAuth.getInstance().signOut()

                    // 2. Chuyển màn hình và "dọn sạch" toàn bộ stack cũ
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true } // Lệnh này xóa hết các màn hình cũ, không bị treo nữa
                    }
                }
            )
        }

        composable("add_transaction") {
            AddTransactionScreen(
                viewModel = transactionViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}