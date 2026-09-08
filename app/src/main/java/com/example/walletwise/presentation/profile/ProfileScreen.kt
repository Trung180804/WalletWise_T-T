package com.example.walletwise.presentation.profile
// màn hình profile
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.walletwise.domain.model.User
import com.example.walletwise.presentation.home.TransactionViewModel
import com.example.walletwise.presentation.profile.settings.*

enum class ProfileRoute {
    MAIN, EDIT_PROFILE, SETTINGS,
    FONT_SIZE, CURRENCY, DEFAULT_CURRENCY, THEME,
    RECURRING, ADD_RECURRING,
    ABOUT_US, REMINDERS, CUSTOMER_CARE, CATEGORY_MANAGEMENT,
    SMART_BUDGET
}

@Composable
fun ProfileScreen(
    viewModel: TransactionViewModel,
    user: User?,
    onLogout: () -> Unit,
    onSubScreenChange: (Boolean) -> Unit
) {
    var currentRoute by remember { mutableStateOf(ProfileRoute.MAIN) }
    val context = LocalContext.current

    LaunchedEffect(currentRoute) {
        onSubScreenChange(currentRoute != ProfileRoute.MAIN)
    }

    BackHandler(enabled = currentRoute != ProfileRoute.MAIN) {
        currentRoute = when (currentRoute) {
            ProfileRoute.ADD_RECURRING -> ProfileRoute.RECURRING
            ProfileRoute.FONT_SIZE, ProfileRoute.THEME, ProfileRoute.RECURRING,
            ProfileRoute.REMINDERS, ProfileRoute.DEFAULT_CURRENCY -> ProfileRoute.SETTINGS
            else -> ProfileRoute.MAIN
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (currentRoute) {
            ProfileRoute.MAIN -> MainProfileView(user, onLogout, onNavigate = { currentRoute = it }, onShare = { shareAppWithFriends(context) })
            ProfileRoute.EDIT_PROFILE -> EditProfileView(user = user) { currentRoute = ProfileRoute.MAIN }
            ProfileRoute.SETTINGS -> SettingsMainView(onNavigate = { currentRoute = it }, onBack = { currentRoute = ProfileRoute.MAIN })
            ProfileRoute.FONT_SIZE -> FontSizeView { currentRoute = ProfileRoute.SETTINGS }
            ProfileRoute.CURRENCY -> CurrencyConverterView { currentRoute = ProfileRoute.MAIN }
            ProfileRoute.THEME -> ThemeView { currentRoute = ProfileRoute.SETTINGS }
            ProfileRoute.RECURRING -> RecurringView(viewModel = viewModel, onAdd = { currentRoute = ProfileRoute.ADD_RECURRING }, onBack = { currentRoute = ProfileRoute.SETTINGS })
            ProfileRoute.ADD_RECURRING -> AddRecurringView(viewModel = viewModel) { currentRoute = ProfileRoute.RECURRING }
            ProfileRoute.ABOUT_US -> AboutUsView { currentRoute = ProfileRoute.MAIN }
            ProfileRoute.REMINDERS -> RemindersView(viewModel = viewModel) { currentRoute = ProfileRoute.SETTINGS }
            ProfileRoute.DEFAULT_CURRENCY -> DefaultCurrencyView { currentRoute = ProfileRoute.SETTINGS }
            ProfileRoute.CUSTOMER_CARE -> CustomerCareView { currentRoute = ProfileRoute.MAIN }
            ProfileRoute.CATEGORY_MANAGEMENT -> CategoryManagementView(viewModel) { currentRoute = ProfileRoute.SETTINGS }
            ProfileRoute.SMART_BUDGET -> SmartBudgetPlannerView(viewModel = viewModel) { currentRoute = ProfileRoute.MAIN }
        }
    }
}

@Composable
fun MainProfileView(
    user: User?,
    onLogout: () -> Unit,
    onNavigate: (ProfileRoute) -> Unit,
    onShare: () -> Unit = {}
) {
    val isLoggedIn = user != null
    val email = user?.email ?: "Chưa đăng nhập"
    val firstLetter = email.firstOrNull()?.uppercase() ?: "N"
    var showLogoutDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))

        // Avatar
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(Color(0xFFE91E63)),
            contentAlignment = Alignment.Center
        ) {
            Text(firstLetter, color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(16.dp))
        val displayName = user?.username?.takeIf { it.isNotBlank() } ?: "Thành viên"
        Text(
            text = if (isLoggedIn) displayName else "Người dùng",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Text(email, color = Color.Gray, fontSize = 14.sp)
        Spacer(Modifier.height(32.dp))

        // Menu items
        MenuRowItem(Icons.Default.Person,     "Hồ sơ")                    { onNavigate(ProfileRoute.EDIT_PROFILE) }
        MenuRowItem(Icons.Default.PieChart,   "Kế hoạch & Phân bổ")       { onNavigate(ProfileRoute.SMART_BUDGET) }
        MenuRowItem(Icons.Default.Refresh,    "Quy đổi tiền tệ")          { onNavigate(ProfileRoute.CURRENCY) }
        MenuRowItem(Icons.Default.SupportAgent, "Chăm sóc khách hàng")    { onNavigate(ProfileRoute.CUSTOMER_CARE) }
        MenuRowItem(Icons.Default.Settings,   "Cài đặt")                  { onNavigate(ProfileRoute.SETTINGS) }
        MenuRowItem(Icons.Default.Info,       "Về chúng tôi")             { onNavigate(ProfileRoute.ABOUT_US) }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick  = {
                if (isLoggedIn) {
                    showLogoutDialog = true
                } else {
                    onLogout()
                }
            },
            modifier = Modifier.fillMaxWidth().height(55.dp),
            colors   = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = if (isLoggedIn) "Đăng xuất" else "Đăng nhập",
                color = if (isLoggedIn) Color(0xFFFA3B70) else Color(0xFF00C875),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(100.dp))
    }

    if (showLogoutDialog && isLoggedIn) {
        Dialog(onDismissRequest = { showLogoutDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Đăng xuất", color = Color(0xFFFA3B70), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))

                    Text(
                        "Bạn có chắc chắn muốn đăng xuất khỏi ứng dụng không?",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(24.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { showLogoutDialog = false },
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Text("Hủy bỏ", fontWeight = FontWeight.Bold)
                        }

                        Spacer(Modifier.width(12.dp))

                        Button(
                            onClick = {
                                showLogoutDialog = false
                                onLogout()
                            },
                            modifier = Modifier.weight(1f).height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFA3B70)),
                            shape = CircleShape
                        ) {
                            Text("Đăng xuất", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
