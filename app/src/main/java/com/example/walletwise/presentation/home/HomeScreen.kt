package com.example.walletwise.presentation.home

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.walletwise.R
import com.example.walletwise.domain.model.User
import com.example.walletwise.presentation.auth.AuthViewModel
import com.example.walletwise.presentation.profile.ProfileScreen
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import androidx.compose.foundation.lazy.items as lazyItems

val primaryBlue = Color(0xFF2196F3)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: TransactionViewModel,
    authViewModel: AuthViewModel,
    user: User?,
    onNavigateToAdd: () -> Unit,
    onLogout: () -> Unit
) {
    val transactions by viewModel.transactions.collectAsState()
    val reminders by viewModel.reminders.collectAsState()
    val recurringTransactions by viewModel.recurringTransactions.collectAsState()
    val formatMoney = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("vi-VN"))

    var selectedTab by remember { mutableIntStateOf(0) }
    var isSubScreenOpen by remember { mutableStateOf(false) }

    val filters = listOf("Tất cả", "Tiền mặt", "Chuyển khoản", "Thẻ tín dụng")
    var selectedFilter by remember { mutableStateOf(filters[0]) }
    var selectedDateFilter by remember { mutableStateOf<LocalDate?>(null) }

    var showLogoutDialog by remember { mutableStateOf(false) }
    var showAIModal by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val cyanColor = Color(0xFF4DD0E1)
    val bgColor = MaterialTheme.colorScheme.background
    val cardColor = MaterialTheme.colorScheme.surface
    val textColor = MaterialTheme.colorScheme.onBackground
    val subTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    var currentStreak by remember { mutableIntStateOf(user?.currentStreak ?: 0) }
    var lastRecordDate by remember { mutableStateOf(user?.lastRecordDate ?: "") }

    LaunchedEffect(user?.currentStreak, user?.lastRecordDate) {
        currentStreak = user?.currentStreak ?: 0
        lastRecordDate = user?.lastRecordDate.orEmpty()
    }

    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {}
    )

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        viewModel.loadTransactions()
        viewModel.initializeScheduledAutomation(context)
    }

    val todayStr = LocalDate.now(ZoneId.systemDefault()).toString()
    val isUpdatedToday = lastRecordDate == todayStr


    val topHeaderBrush = Brush.verticalGradient(
        colors = listOf(primaryBlue.copy(alpha = 0.1f), bgColor)
    )

    Scaffold(
        topBar = {
            if (selectedTab == 0) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.logo),
                                contentDescription = "Logo WalletWise",
                                modifier = Modifier.size(36.dp).clip(CircleShape)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "WalletWise",
                                color = primaryBlue, // Giữ màu xanh không bị đổi
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.weight(1f))

                            // ... trong TopAppBar, thay thế khối hiển thị Streak bằng đoạn này:
                            if ((user?.currentStreak ?: 0) > 0) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.LocalFireDepartment,
                                        contentDescription = "Streak",
                                        tint = if (isUpdatedToday) Color(0xFFFF9800) else Color.Gray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${user?.currentStreak}",
                                        color = if (isUpdatedToday) Color(0xFFFF9800) else Color.Gray,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    actions = {
                        IconButton(onClick = { /* TODO */ }) {
                            Icon(Icons.Default.Search, contentDescription = "Tìm kiếm", tint = textColor)
                        }
                        IconButton(onClick = { showLogoutDialog = true }) {
                            Icon(Icons.Default.ExitToApp, contentDescription = "Đăng xuất", tint = textColor)
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (selectedTab == 0 && !isSubScreenOpen) {
                Box(
                    modifier = Modifier
                        .offset(y = (-30).dp)
                        .size(60.dp)
                        .shadow(8.dp, CircleShape)
                        .background(Brush.linearGradient(listOf(Color(0xFF9C27B0), Color(0xFFFA3B70))), CircleShape)
                        .clickable { showAIModal = true },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "🤖", fontSize = 28.sp)
                }
            }
        },
        bottomBar = {
            if (!isSubScreenOpen) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(primaryBlue, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
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
                            .background(cardColor)
                            .clickable {
                                selectedTab = 0
                                onNavigateToAdd()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = primaryBlue, modifier = Modifier.size(36.dp))
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
                    val today = LocalDate.now(ZoneId.systemDefault())
                    LaunchedEffect(selectedFilter, selectedDateFilter, today) {
                        val firstDate = selectedDateFilter ?: today.minusDays(2)
                        val lastDate = selectedDateFilter ?: today
                        val zoneId = ZoneId.systemDefault()
                        viewModel.updateTransactionListFilters(
                            paymentMethod = selectedFilter.takeUnless { it == filters.first() },
                            startEpochMilliseconds = firstDate.atStartOfDay(zoneId).toInstant().toEpochMilli(),
                            endEpochMilliseconds = lastDate.plusDays(1)
                                .atStartOfDay(zoneId)
                                .toInstant()
                                .toEpochMilli() - 1L
                        )
                    }

                    val transactionsToday = transactions.filter { trans ->
                        val txDate = Instant.ofEpochMilli(trans.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
                        txDate == today
                    }

                    val totalIncomeToday = transactionsToday
                        .filter { it.type.trim().equals("Thu", ignoreCase = true) }
                        .sumOf { it.amount }

                    val totalExpenseToday = transactionsToday
                        .filter { it.type.trim().equals("Chi", ignoreCase = true) }
                        .sumOf { it.amount }

                    val totalBalanceAll = transactions.sumOf { trans ->
                        if (trans.type.trim().equals("Thu", ignoreCase = true)) trans.amount else -trans.amount
                    }

                    /*
                    / 1. TÍNH TOÁN HEADER: Lọc ra các giao dịch TRONG THÁNG NÀY
                    val transactionsThisMonth = transactions.filter { trans ->
                    val txDate = Instant.ofEpochMilli(trans.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
                        txDate.monthValue == currentMonth && txDate.year == currentYear
                    }
                    val totalIncomeThisMonth = transactionsThisMonth
                    .filter { it.type.trim().equals("Thu", ignoreCase = true) }
                    .sumOf { it.amount }
                    val totalExpenseThisMonth = transactionsThisMonth
                    .filter { it.type.trim().equals("Chi", ignoreCase = true) }
                    .sumOf { it.amount }
                    // 2. TÍNH SỐ DƯ TỔNG (Toàn bộ lịch sử)
                    val totalBalanceThisMonth = totalIncomeThisMonth - totalExpenseThisMonth
                    */

                    Column(modifier = Modifier.fillMaxSize().background(topHeaderBrush)) {
                        Column(modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)) {

                            HomeBannerCarousel(
                                user = user,
                                reminders = reminders,
                                recurringTransactions = recurringTransactions
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(text = "Chi tiêu", color = subTextColor, fontSize = 12.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = formatMoney.format(totalExpenseToday), color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(text = "Thu nhập", color = subTextColor, fontSize = 12.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = formatMoney.format(totalIncomeToday), color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(text = "Số dư", color = subTextColor, fontSize = 12.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = formatMoney.format(totalBalanceAll), color = Color(0xFFFA3B70), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                            color = bgColor
                        ) {
                            Column(modifier = Modifier.padding(top = 10.dp, start = 16.dp, end = 16.dp)) {
                                if (selectedDateFilter != null) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    ) {
                                        Text(text = "Đang lọc ngày: ", color = subTextColor, fontSize = 13.sp)
                                        AssistChip(
                                            onClick = { selectedDateFilter = null },
                                            label = {
                                                Text(
                                                    text = "${selectedDateFilter?.dayOfMonth}/${selectedDateFilter?.monthValue}",
                                                    fontWeight = FontWeight.Bold
                                                )
                                            },
                                            trailingIcon = {
                                                Icon(Icons.Default.Clear, contentDescription = "Xóa", modifier = Modifier.size(16.dp))
                                            },
                                            colors = AssistChipDefaults.assistChipColors(containerColor = cyanColor, labelColor = Color.White)
                                        )
                                    }
                                } else {
                                    Text(text = "Giao dịch 3 ngày gần đây", color = subTextColor, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
                                }

                                LazyRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    lazyItems(items = filters) { filter ->
                                        val isSelected = selectedFilter == filter
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(20.dp))
                                                .background(if (isSelected) cyanColor else cardColor)
                                                .border(1.dp, if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                                                .clickable { selectedFilter = filter }
                                                .padding(horizontal = 16.dp, vertical = 4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(text = filter, color = if (isSelected) Color.White else textColor, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                AndroidTransactionList(
                                    viewModel = viewModel,
                                    onNavigateToAdd = onNavigateToAdd,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }

                1 -> HistoryScreen(viewModel = viewModel, onDaySelected = { date -> selectedDateFilter = date; selectedTab = 0 })
                2 -> { /* Thao tác bấm nút '+' đã chuyển thẳng tới onNavigateToAdd() và quay lại Tab 0 */ }
                3 -> StatisticsScreen(transactions = transactions, formatMoney = formatMoney)
                4 -> ProfileScreen(
                    viewModel = viewModel,
                    authViewModel = authViewModel,
                    onLogout = onLogout,
                    onSubScreenChange = { isOpen -> isSubScreenOpen = isOpen }
                )
            }
        }

        // HỘP THOẠI TRỢ LÝ AI
        if (showAIModal) {
            ModalBottomSheet(
                onDismissRequest = { showAIModal = false; viewModel.resetAIState() },
                sheetState = sheetState,
                containerColor = cardColor
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp, start = 16.dp, end = 16.dp).imePadding()) {
                    Text(text = "✨ Trợ lý Tài chính WalletWise", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = textColor)
                    Spacer(modifier = Modifier.height(16.dp))

                    if (viewModel.isAIProcessing) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = cyanColor)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = "Trợ lý đang suy nghĩ...", color = subTextColor)
                        }
                    } else {
                        Text(text = viewModel.aiFeedbackMessage, fontSize = 16.sp, color = Color(0xFFFA3B70), fontWeight = FontWeight.Medium)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (viewModel.aiPendingTransaction != null) {
                        val tx = viewModel.aiPendingTransaction!!
                        val context = LocalContext.current
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = cyanColor.copy(alpha = 0.1f),
                            border = BorderStroke(1.dp, cyanColor.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(text = "📝 Giao dịch mới:", fontWeight = FontWeight.Bold, color = textColor)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = "• Số tiền: ${formatMoney.format(tx.amount)}", color = textColor)
                                Text(text = "• Phân loại: ${tx.category} (${tx.type})", color = textColor)
                                Text(text = "• Nguồn: ${tx.paymentMethod}", color = textColor)
                                Text(text = "• Ghi chú: ${tx.note}", color = textColor)

                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        viewModel.addTransaction(
                                            amount = tx.amount, type = tx.type, category = tx.category,
                                            note = tx.note, paymentMethod = tx.paymentMethod, imageUri = null, context = context
                                        ) { showAIModal = false; viewModel.resetAIState() }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                                ) {
                                    Text(text = "Xác nhận & Lưu", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    AIAssistantInputBar(textColor = textColor, onSend = { userInput -> viewModel.processAITransaction(userInput) })
                }
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
                        Text(text = "Đăng xuất", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLogoutDialog = false }) { Text(text = "Hủy", color = subTextColor, fontWeight = FontWeight.Bold) }
                }
            )
        }
    }
}

@Composable
fun AIAssistantInputBar(textColor: Color, onSend: (String) -> Unit) {
    var textInput by remember { mutableStateOf(TextFieldValue("")) }
    val context = LocalContext.current
    val speechLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (spokenText != null) { textInput = TextFieldValue(spokenText) }
        }
    }
    fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Đang nghe... (VD: Đổ xăng 50k)")
        }
        try { speechLauncher.launch(intent) } catch (e: Exception) { android.widget.Toast.makeText(context, "Thiết bị không hỗ trợ giọng nói", android.widget.Toast.LENGTH_SHORT).show() }
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = textInput, onValueChange = { textInput = it }, modifier = Modifier.weight(1f),
            placeholder = { Text(text = "Nhập nội dung...", color = Color.Gray) }, shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF9C27B0),
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedTextColor = textColor,
                unfocusedTextColor = textColor
            ), maxLines = 4
        )
        Spacer(modifier = Modifier.width(12.dp))
        if (textInput.text.isBlank()) {
            IconButton(onClick = { startListening() }, modifier = Modifier.size(50.dp).background(Color(0xFFFA3B70), CircleShape)) { Icon(Icons.Default.Mic, contentDescription = "Mic", tint = Color.White) }
        } else {
            IconButton(onClick = { onSend(textInput.text); textInput = TextFieldValue("") }, modifier = Modifier.size(50.dp).background(Color(0xFF4CAF50), CircleShape)) { Icon(Icons.Default.Send, contentDescription = "Gửi", tint = Color.White) }
        }
    }
}

@Composable
fun BottomBarItem(icon: ImageVector, isSelected: Boolean, onClick: () -> Unit) {
    Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(if (isSelected) Color(0xFFFFD700) else Color.Transparent).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
    }
}
