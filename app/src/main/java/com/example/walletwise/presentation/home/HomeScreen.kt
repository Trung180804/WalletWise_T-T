package com.example.walletwise.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.walletwise.domain.model.Transaction
import java.text.NumberFormat
import java.util.Locale

val LocalAppTheme = compositionLocalOf<MutableState<Boolean>> { error("No Theme Found") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: TransactionViewModel,
    onNavigateToAdd: () -> Unit,
    onLogout: () -> Unit
) {
    val transactions by viewModel.transactions.collectAsState()
    val totalBalance = transactions.sumOf { if (it.type == "Thu") it.amount else -it.amount }
    val formatMoney = NumberFormat.getCurrencyInstance(Locale("vi", "VN"))

    var selectedTab by remember { mutableStateOf(0) }
    val isDarkTheme = remember { mutableStateOf(false) }
    var isSubScreenOpen by remember { mutableStateOf(false) }

    val footerBgColor = Color(0xFF4DD0E1)

    // Khởi tạo các biến cho bộ lọc phương thức thanh toán
    val filters = listOf("Tất cả", "Tiền mặt", "Chuyển khoản", "Thẻ tín dụng")
    var selectedFilter by remember { mutableStateOf(filters[0]) }

    CompositionLocalProvider(LocalAppTheme provides isDarkTheme) {
        val bgColor = if (isDarkTheme.value) Color(0xFF121212) else Color(0xFFF4F6F8)
        val textColor = if (isDarkTheme.value) Color.White else Color.Black
        val cardColor = if (isDarkTheme.value) Color(0xFF1E1E1E) else Color.White

        Scaffold(
            topBar = {
                if (selectedTab == 0) {
                    TopAppBar(
                        title = { Text("WalletWise", color = Color(0xFFFFD700), fontWeight = FontWeight.Bold) },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor),
                        actions = {
                            IconButton(onClick = onLogout) {
                                Icon(Icons.Default.ExitToApp, contentDescription = "Đăng xuất", tint = textColor)
                            }
                        }
                    )
                }
            },
            bottomBar = {
                if (!isSubScreenOpen) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(footerBgColor, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BottomBarItem(icon = Icons.Default.Home, isSelected = selectedTab == 0) { selectedTab = 0 }
                        BottomBarItem(icon = Icons.Default.List, isSelected = selectedTab == 1) { selectedTab = 1 }
                        Box(
                            modifier = Modifier
                                .offset(y = (-18).dp)
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(if (selectedTab == 2) Color(0xFFFFD700) else Color.White)
                                .clickable { selectedTab = 2 },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = if (selectedTab == 2) Color.White else footerBgColor, modifier = Modifier.size(36.dp))
                        }
                        BottomBarItem(icon = Icons.Default.Settings, isSelected = selectedTab == 3) { selectedTab = 3 }
                        BottomBarItem(icon = Icons.Default.Person, isSelected = selectedTab == 4) { selectedTab = 4 }
                    }
                }
            },
            containerColor = bgColor
        ) { padding ->
            Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                when (selectedTab) {
                    0 -> {
                        // Lọc danh sách giao dịch dựa trên Filter được chọn
                        val displayedTransactions = if (selectedFilter == "Tất cả") {
                            transactions
                        } else {
                            // TODO: Thay "it.note" bằng trường Hình thức thanh toán (ví dụ: it.paymentMethod) khi bạn cập nhật lại Model
                            transactions.filter { it.note.contains(selectedFilter, ignoreCase = true) }
                        }

                        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                                colors = CardDefaults.cardColors(containerColor = cardColor),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Column(modifier = Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Tổng số dư hiện tại", color = Color.Gray, fontSize = 16.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = formatMoney.format(totalBalance),
                                        color = if (totalBalance >= 0) textColor else Color.Red,
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Text("Giao dịch gần đây", color = textColor, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(16.dp))

                            // 👉 ĐÃ THÊM BỘ LỌC VÀO ĐÂY
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(filters) { filter ->
                                    val isSelected = selectedFilter == filter
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(if (isSelected) Color(0xFF4DD0E1) else cardColor)
                                            .clickable { selectedFilter = filter }
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = filter,
                                            // Đổi màu chữ tương ứng với màu nền
                                            color = if (isSelected) Color.Black else textColor,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            LazyColumn {
                                // Hiển thị danh sách đã được lọc (displayedTransactions)
                                items(displayedTransactions) { trans ->
                                    TransactionItem(trans, formatMoney, cardColor, textColor)
                                }
                            }
                        }
                    }
                    1 -> HistoryScreen(viewModel = viewModel, onDaySelected = { selectedTab = 0 })
                    2 -> AddTransactionScreen(viewModel = viewModel, onNavigateBack = { selectedTab = 0 })
                    3 -> StatisticsScreen(transactions = transactions, formatMoney = formatMoney)
                    4 -> ProfileScreen(
                        onLogout = onLogout,
                        onSubScreenChange = { isOpen -> isSubScreenOpen = isOpen }
                    )
                }
            }
        }
    }
}

@Composable
fun BottomBarItem(icon: ImageVector, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (isSelected) Color(0xFFFFD700) else Color.Transparent)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
    }
}

@Composable
fun PlaceholderScreen(title: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(title, color = Color.Gray, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun TransactionItem(transaction: Transaction, formatMoney: NumberFormat, cardColor: Color, textColor: Color) {
    val isIncome = transaction.type == "Thu"
    val color = if (isIncome) Color(0xFF4CAF50) else Color(0xFFF44336)
    val sign = if (isIncome) "+" else "-"

    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(transaction.category, color = textColor, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                if (transaction.note.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(transaction.note, color = Color.Gray, fontSize = 14.sp)
                }
            }
            Text("$sign ${formatMoney.format(transaction.amount)}", color = color, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}