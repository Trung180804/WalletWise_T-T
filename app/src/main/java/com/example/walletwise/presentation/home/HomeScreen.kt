package com.example.walletwise.presentation.home

import android.app.Activity
import android.content.Intent
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.walletwise.R
import com.example.walletwise.domain.model.Transaction
import com.example.walletwise.domain.model.User
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.grid.items as gridItems

val primaryBlue = Color(0xFF2196F3)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: TransactionViewModel,
    user: User?,
    onNavigateToAdd: () -> Unit,
    onLogout: () -> Unit
) {
    val transactions by viewModel.transactions.collectAsState()
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

    LaunchedEffect(Unit) {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            db.collection("users").document(uid).addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    currentStreak = snapshot.getLong("currentStreak")?.toInt() ?: 0
                    lastRecordDate = snapshot.getString("lastRecordDate") ?: ""
                }
            }
        }
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
                            .background(if (selectedTab == 2) Color(0xFFFFD700) else cardColor)
                            .clickable { selectedTab = 2 },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = if (selectedTab == 2) Color.White else primaryBlue, modifier = Modifier.size(36.dp))
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

                    val displayedTransactions = transactions.filter { trans ->
                        val matchMethod = selectedFilter == "Tất cả" || trans.paymentMethod == selectedFilter
                        val txDate = Instant.ofEpochMilli(trans.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()

                        val matchDate = if (selectedDateFilter != null) {
                            txDate == selectedDateFilter
                        } else {
                            val threeDaysAgo = today.minusDays(2)
                            !txDate.isBefore(threeDaysAgo) && !txDate.isAfter(today)
                        }

                        matchMethod && matchDate
                    }.sortedByDescending { it.timestamp }

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

                            HomeBannerCarousel(user = user)

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

                                if (displayedTransactions.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(text = "Không có giao dịch nào.", color = subTextColor)
                                    }
                                } else {
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(2),
                                        contentPadding = PaddingValues(bottom = 100.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        gridItems(items = displayedTransactions) { trans ->
                                            TransactionGridItem(
                                                transaction = trans,
                                                formatMoney = formatMoney,
                                                cardColor = cardColor,
                                                textColor = textColor,
                                                subTextColor = subTextColor,
                                                onEdit = { viewModel.transactionToEdit = trans; onNavigateToAdd() },
                                                onDelete = { viewModel.deleteTransaction(trans.id) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> HistoryScreen(viewModel = viewModel, onDaySelected = { date -> selectedDateFilter = date; selectedTab = 0 })
                2 -> {
                    LaunchedEffect(Unit) {
                        onNavigateToAdd()
                    }
                }
                3 -> StatisticsScreen(transactions = transactions, formatMoney = formatMoney)
                4 -> ProfileScreen(viewModel = viewModel, user = user, onLogout = onLogout, onSubScreenChange = { isOpen -> isSubScreenOpen = isOpen })
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

@Composable
fun TransactionGridItem(
    transaction: Transaction,
    formatMoney: NumberFormat,
    cardColor: Color,
    textColor: Color,
    subTextColor: Color,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isIncome = transaction.type.trim().equals("Thu", ignoreCase = true)
    val sign = if (isIncome) "+" else "-"
    val amountColor = if (isIncome) Color(0xFF4CAF50) else textColor
    val imgBgColor = MaterialTheme.colorScheme.surfaceVariant

    val sdfTime = java.text.SimpleDateFormat("HH:mm", Locale.forLanguageTag("vi-VN"))
    val timeString = sdfTime.format(java.util.Date(transaction.timestamp))
    val sdfFullDate = java.text.SimpleDateFormat("dd/MM/yyyy - HH:mm", Locale.forLanguageTag("vi-VN"))
    val fullDateString = sdfFullDate.format(java.util.Date(transaction.timestamp))

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDetailsDialog by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth().clickable { showDetailsDialog = true }, colors = CardDefaults.cardColors(containerColor = cardColor), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(modifier = Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(12.dp)).background(imgBgColor), contentAlignment = Alignment.Center) {
                if (transaction.imageUrl.isNotEmpty()) {
                    AsyncImage(model = transaction.imageUrl, contentDescription = "Ảnh", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else { Text(text = "Không có ảnh", fontSize = 10.sp, color = subTextColor) }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "$sign${formatMoney.format(transaction.amount)}", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = amountColor)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = transaction.category, fontSize = 12.sp, color = subTextColor, maxLines = 1)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = timeString, fontSize = 11.sp, color = subTextColor)
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
                    Text(text = transaction.paymentMethod, fontSize = 10.sp, color = subTextColor, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false }, containerColor = cardColor,
            title = { Text(text = "Xác nhận xóa", fontWeight = FontWeight.Bold, color = textColor) },
            text = { Text(text = "Bạn có chắc chắn muốn xóa giao dịch này không?", color = textColor, fontSize = 16.sp) },
            confirmButton = { Button(onClick = { showDeleteDialog = false; onDelete() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))) { Text(text = "Xóa", color = Color.White, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text(text = "Hủy", color = subTextColor, fontWeight = FontWeight.Bold) } }
        )
    }

    if (showDetailsDialog) {
        Dialog(onDismissRequest = { showDetailsDialog = false }) {
            Surface(shape = RoundedCornerShape(24.dp), color = cardColor, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "Chi tiết", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = textColor)
                        IconButton(onClick = { showDetailsDialog = false }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Clear, contentDescription = "Đóng", tint = subTextColor) }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    if (transaction.imageUrl.isNotEmpty()) {
                        AsyncImage(model = transaction.imageUrl, contentDescription = "Hóa đơn", modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                    Text(text = "$sign${formatMoney.format(transaction.amount)}", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = amountColor)
                    Spacer(modifier = Modifier.height(24.dp))
                    Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(16.dp)) {
                        @Composable
                        fun InfoRow(label: String, value: String, valueColor: Color = textColor) {
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = label, color = subTextColor, fontSize = 14.sp)
                                Text(text = value, color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.End)
                            }
                        }
                        InfoRow("Loại giao dịch", transaction.type, amountColor)
                        InfoRow("Danh mục", transaction.category)
                        InfoRow("Nguồn tiền", transaction.paymentMethod)
                        InfoRow("Thời gian", fullDateString)
                        if (transaction.note.isNotBlank()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(text = "Ghi chú:", color = subTextColor, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = transaction.note, color = textColor, fontSize = 14.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                showDetailsDialog = false
                                showDeleteDialog = true
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                            border = BorderStroke(1.dp, Color.Red),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Xóa", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                showDetailsDialog = false
                                onEdit()
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = primaryBlue),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Sửa", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}