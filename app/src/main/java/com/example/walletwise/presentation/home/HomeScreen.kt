package com.example.walletwise.presentation.home

import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import com.google.firebase.auth.FirebaseAuth
import com.example.walletwise.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.walletwise.domain.model.Transaction
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val LocalAppTheme = compositionLocalOf<MutableState<Boolean>> { error("No Theme Found") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: TransactionViewModel,
    user: com.example.walletwise.domain.model.User?,
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

    val filters = listOf("Tất cả", "Tiền mặt", "Chuyển khoản", "Thẻ tín dụng")
    var selectedFilter by remember { mutableStateOf(filters[0]) }

    var selectedDateFilter by remember { mutableStateOf<LocalDate?>(null) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    CompositionLocalProvider(LocalAppTheme provides isDarkTheme) {
        val bgColor = if (isDarkTheme.value) Color(0xFF121212) else Color(0xFFF4F6F8)
        val textColor = if (isDarkTheme.value) Color.White else Color.Black
        val cardColor = if (isDarkTheme.value) Color(0xFF1E1E1E) else Color.White

        // Màu nền xanh đen đặc trưng của phần Header giống ảnh mẫu
        val topHeaderColor = Color(0xFF1A1A2E)

        Scaffold(
            topBar = {
                if (selectedTab == 0) {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // 👉 Chèn Logo vào đây
                                Image(
                                    painter = painterResource(id = R.drawable.logo),
                                    contentDescription = "Logo WalletWise",
                                    modifier = Modifier
                                        .size(36.dp) // Kích thước logo
                                        .clip(CircleShape) // Bo tròn logo (nếu muốn giữ hình vuông thì xóa dòng clip này đi)
                                )
                                Spacer(modifier = Modifier.width(12.dp)) // Khoảng cách giữa logo và chữ
                                Text("WalletWise", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = topHeaderColor),
                        actions = {
                            IconButton(onClick = { /* TODO: Xử lý tìm kiếm */ }) {
                                Icon(Icons.Default.Search, contentDescription = "Tìm kiếm", tint = Color.White)
                            }
                            IconButton(onClick = { showLogoutDialog = true }) {
                                Icon(Icons.Default.ExitToApp, contentDescription = "Đăng xuất", tint = Color.White)
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
                        // Tính toán Thu / Chi TỔNG QUÁT cho phần Header
                        val totalIncomeAll = transactions.filter { it.type == "Thu" }.sumOf { it.amount }
                        val totalExpenseAll = transactions.filter { it.type == "Chi" }.sumOf { it.amount }

                        val displayedTransactions = transactions.filter { trans ->
                            val matchMethod = selectedFilter == "Tất cả" || trans.paymentMethod == selectedFilter
                            val matchDate = if (selectedDateFilter == null) true else {
                                val txDate = Instant.ofEpochMilli(trans.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
                                txDate == selectedDateFilter
                            }
                            matchMethod && matchDate
                        }

                        // Bao bọc toàn bộ trang Home
                        Column(modifier = Modifier.fillMaxSize().background(topHeaderColor)) {

                            // --- HEADER: HỒ SƠ & THỐNG KÊ (NỀN XANH ĐEN) ---
                            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {

                                // 👉 TRÍCH XUẤT THÔNG TIN THÔNG MINH TỪ FIREBASE
                                val email = user?.email ?: "Chưa đăng nhập"
                                val userName = user?.username?.takeIf { it.isNotBlank() } ?: "Người dùng"
                                val firstLetter = userName.filter { it.isLetter() }.firstOrNull()?.toString()?.uppercase() ?: "P"

                                // Ảnh đại diện chữ cái đầu và Tên User
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFFA3B70)), // Màu hồng cá tính
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = firstLetter, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text("Xin chào,", color = Color.LightGray, fontSize = 13.sp)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(text = userName, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                // Hàng Thống kê (Chi tiêu, Thu nhập, Số dư)
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Chi tiêu", color = Color.LightGray, fontSize = 12.sp)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(formatMoney.format(totalExpenseAll), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Thu nhập", color = Color.LightGray, fontSize = 12.sp)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(formatMoney.format(totalIncomeAll), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Số dư", color = Color.LightGray, fontSize = 12.sp)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(formatMoney.format(totalBalance), color = Color(0xFFFA3B70), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // --- BODY: MÀN HÌNH MÀU TRẮNG BO GÓC CHỒNG LÊN ---
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                                color = bgColor
                            ) {
                                Column(modifier = Modifier.padding(top = 24.dp, start = 16.dp, end = 16.dp)) {

                                    // Bộ lọc Tiền mặt / Chuyển khoản
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
                                                    .border(1.dp, if (isSelected) Color.Transparent else Color.LightGray, RoundedCornerShape(20.dp))
                                                    .clickable { selectedFilter = filter }
                                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = filter,
                                                    color = if (isSelected) Color.Black else Color.Gray,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    fontSize = 13.sp
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Hiển thị ngày đang được lọc
                                    if (selectedDateFilter != null) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color(0xFF4DD0E1).copy(alpha = 0.2f),
                                            onClick = { selectedDateFilter = null },
                                            modifier = Modifier.padding(bottom = 12.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Text(
                                                    text = selectedDateFilter!!.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                                                    color = Color(0xFF4DD0E1),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(Icons.Default.Clear, contentDescription = "Xóa lọc", tint = Color(0xFF4DD0E1), modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    }

                                    // Hiển thị danh sách dạng Grid (2 cột)
                                    if (displayedTransactions.isEmpty()) {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text("Không có giao dịch nào.", color = Color.Gray)
                                        }
                                    } else {
                                        LazyVerticalGrid(
                                            columns = GridCells.Fixed(2), // Lưới 2 cột giống ảnh
                                            contentPadding = PaddingValues(bottom = 100.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            items(displayedTransactions) { trans ->
                                                TransactionGridItem(trans, formatMoney, cardColor, textColor)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    1 -> HistoryScreen(viewModel = viewModel, onDaySelected = { date ->
                        selectedDateFilter = date
                        selectedTab = 0
                    })
                    2 -> AddTransactionScreen(viewModel = viewModel, onNavigateBack = { selectedTab = 0 })
                    3 -> StatisticsScreen(transactions = transactions, formatMoney = formatMoney)
                    4 -> ProfileScreen(
                        user = user,
                        onLogout = onLogout,
                        onSubScreenChange = { isOpen -> isSubScreenOpen = isOpen }
                    )
                }
            }

            if (showLogoutDialog) {
                AlertDialog(
                    onDismissRequest = { showLogoutDialog = false },
                    containerColor = cardColor,
                    title = { Text(text = "Xác nhận đăng xuất", fontWeight = FontWeight.Bold, color = textColor) },
                    text = { Text(text = "Bạn có chắc chắn muốn đăng xuất khỏi WalletWise không?", color = textColor, fontSize = 16.sp) },
                    confirmButton = {
                        Button(onClick = { showLogoutDialog = false; onLogout() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))) {
                            Text("Đăng xuất", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showLogoutDialog = false }) { Text("Hủy", color = Color.Gray, fontWeight = FontWeight.Bold) }
                    }
                )
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

// 👉 HÀM MỚI: Vẽ Card Giao Dịch Dạng Lưới có nền Gradient Pastel
@Composable
fun TransactionGridItem(transaction: Transaction, formatMoney: NumberFormat, cardColor: Color, textColor: Color) {
    val isIncome = transaction.type == "Thu"
    val sign = if (isIncome) "+" else "-"
    // Màu chữ số tiền tùy thuộc Thu/Chi
    val amountColor = if (isIncome) Color(0xFF4CAF50) else textColor

    val sdfTime = java.text.SimpleDateFormat("HH:mm", Locale("vi", "VN"))
    val timeString = sdfTime.format(java.util.Date(transaction.timestamp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF0F0F0)), // Màu nền nhẹ nhàng
                contentAlignment = Alignment.Center
            ) {
                if (!transaction.imageUrl.isNullOrEmpty()) {
                    // Hiển thị ảnh thật từ Firebase
                    AsyncImage(
                        model = transaction.imageUrl,
                        contentDescription = "Ảnh giao dịch",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // Nếu không có ảnh thì hiện icon hoặc text mặc định
                    Text("Không có ảnh", fontSize = 10.sp, color = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Số tiền
            Text(
                text = "$sign${formatMoney.format(transaction.amount)}",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = amountColor
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Tên danh mục (Ghi chú / Thể loại)
            Text(
                text = transaction.category,
                fontSize = 12.sp,
                color = Color.Gray,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Hàng dưới cùng: Giờ + Nhãn Wallet (Momo, Wallet...)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(timeString, fontSize = 11.sp, color = Color.Gray)
                Surface(
                    color = Color.LightGray.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = transaction.paymentMethod,
                        fontSize = 10.sp,
                        color = Color.DarkGray,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}