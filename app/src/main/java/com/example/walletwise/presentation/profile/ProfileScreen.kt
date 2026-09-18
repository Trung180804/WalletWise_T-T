package com.example.walletwise.presentation.profile

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.walletwise.presentation.support.SupportViewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.walletwise.presentation.auth.AuthViewModel
import com.example.walletwise.presentation.home.TransactionViewModel
import com.example.walletwise.presentation.profile.settings.CategoryManagementView
import com.example.walletwise.presentation.profile.settings.DefaultCurrencyView
import com.example.walletwise.presentation.profile.settings.FontSizeView
import com.example.walletwise.presentation.profile.settings.RecurringView
import com.example.walletwise.presentation.profile.settings.RemindersView
import com.example.walletwise.presentation.profile.settings.SettingsMainView
import com.example.walletwise.presentation.profile.settings.ThemeView

@Composable
fun ProfileScreen(
    viewModel: TransactionViewModel,
    authViewModel: AuthViewModel,
    onLogout: () -> Unit,
    onSubScreenChange: (Boolean) -> Unit
) {
    var currentRoute by remember { mutableStateOf(ProfileRoute.MAIN) }
    val supportModel: SupportViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SupportViewModel(authViewModel.uiState) as T
    })
    val context = LocalContext.current
    val state = authViewModel.profileUiState.collectAsStateWithLifecycle().value
    val event = state.pendingEvent

    LaunchedEffect(currentRoute) {
        onSubScreenChange(currentRoute.isSubScreen)
    }

    LaunchedEffect(event?.id) {
        event ?: return@LaunchedEffect
        when (val value = event.event) {
            is ProfileUiEvent.Navigate -> currentRoute = value.destination.toProfileRoute()
            is ProfileUiEvent.Message -> Toast.makeText(
                context,
                value.text,
                if (value.kind == ProfileMessageKind.ERROR) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
            ).show()
            ProfileUiEvent.Share -> shareAppWithFriends(context)
            ProfileUiEvent.Logout -> onLogout()
        }
        authViewModel.consumeProfileUiEvent(event.id)
    }

    BackHandler(enabled = currentRoute.isSubScreen) {
        currentRoute = currentRoute.backDestination()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (currentRoute) {
            ProfileRoute.MAIN -> ProfileMainContent(
                state = state,
                onNavigate = authViewModel::requestProfileNavigation,
                onLogoutRequest = authViewModel::requestProfileLogout,
                onCancelLogout = authViewModel::cancelProfileLogout,
                onConfirmLogout = authViewModel::confirmProfileLogout
            )
            ProfileRoute.EDIT_PROFILE -> EditProfileView(
                state = state,
                authViewModel = authViewModel,
                onBack = { currentRoute = ProfileRoute.MAIN }
            )
            ProfileRoute.SETTINGS -> SettingsMainView(
                onNavigate = { currentRoute = it },
                onBack = { currentRoute = ProfileRoute.MAIN }
            )
            ProfileRoute.FONT_SIZE -> FontSizeView { currentRoute = ProfileRoute.SETTINGS }
            ProfileRoute.CURRENCY -> CurrencyConverterView { currentRoute = ProfileRoute.MAIN }
            ProfileRoute.THEME -> ThemeView { currentRoute = ProfileRoute.SETTINGS }
            ProfileRoute.RECURRING -> RecurringView(
                viewModel = viewModel,
                onBack = { currentRoute = ProfileRoute.SETTINGS }
            )
            ProfileRoute.ABOUT_US -> AboutUsView { currentRoute = ProfileRoute.MAIN }
            ProfileRoute.REMINDERS -> RemindersView(viewModel = viewModel) {
                currentRoute = ProfileRoute.SETTINGS
            }
            ProfileRoute.DEFAULT_CURRENCY -> DefaultCurrencyView {
                currentRoute = ProfileRoute.SETTINGS
            }
            ProfileRoute.CUSTOMER_CARE -> CustomerCareView(supportModel) { currentRoute = ProfileRoute.MAIN }
            ProfileRoute.CATEGORY_MANAGEMENT -> CategoryManagementView(viewModel) {
                currentRoute = ProfileRoute.SETTINGS
            }
            ProfileRoute.SMART_BUDGET -> SmartBudgetPlannerView(viewModel = viewModel) {
                currentRoute = ProfileRoute.MAIN
            }
        }
    }
}
