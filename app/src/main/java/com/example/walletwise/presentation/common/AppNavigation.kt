package com.example.walletwise.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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

    val isLoggedIn by authViewModel.isLoggedIn.collectAsState()

    val startRoute = if (isLoggedIn) "home" else "login"

    val userState by authViewModel.currentUser.collectAsState()

    LaunchedEffect(isLoggedIn) {
        if (!isLoggedIn) {
            navController.navigate("login") {
                popUpTo(0) { inclusive = true }
            }
        }
    }

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
                authViewModel = authViewModel,
                user = userState,
                onNavigateToAdd = { navController.navigate("add_transaction") },
                onLogout = {

                    authViewModel.logout()
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
