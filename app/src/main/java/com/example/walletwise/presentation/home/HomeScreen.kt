
package com.example.walletwise.presentation.home

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
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
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.grid.items as gridItems

val LocalAppTheme = compositionLocalOf<MutableState<Boolean>> { error("No Theme Found") }

private val BannerGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFF1E3C72), // Blue đậm
        Color(0xFF2A5298)  // Blue sáng
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: TransactionViewModel,
    user: User?,
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
    var showAIModal by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    CompositionLocalProvider(LocalAppTheme provides isDarkTheme) {
        // Thay thế các biến trong HomeScreen của bạn bằng bộ màu mới này
        val bgColor = Color(0xFFF8F9FA) // Nền trắng xám nhạt
        val cardColor = Color.White
        val topHeaderBrush = Brush.verticalGradient(
            colors = listOf(Color(0xFFE3F2FD), Color(0xFFF8F9FA)) // Xanh rất nhạt chuyển sang Trắng
        )
        val footerBgColor = Color(0xFF2196F3) // Màu xanh đậm hơn một chút để làm điểm nhấn ở footer
        val textColor = Color(0xFF2D3436) // Chữ xám đậm (không dùng đen tuyền)

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
                                    color = Color(0xFF2196F3),
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.weight(1f)) // Đẩy phần streak sang góc phải

                                // Hiển thị lửa và số ngày
                                if ((user?.currentStreak ?: 0) > 0) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Filled.LocalFireDepartment,
                                            contentDescription = "Streak",
                                            tint = Color(0xFFFF9800), // Màu cam của lửa
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "${user?.currentStreak}",
                                            color = Color(0xFFFF9800),
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
                                Icon(Icons.Default.Search, contentDescription = "Tìm kiếm", tint = Color(0xFF2D3436))
                            }
                            IconButton(onClick = { showLogoutDialog = true }) {
                                Icon(Icons.Default.ExitToApp, contentDescription = "Đăng xuất", tint = Color(0xFF2D3436))
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
                        val today = LocalDate.now(ZoneId.systemDefault())

                        // 2. Lọc ra các giao dịch CHỈ TRONG HÔM NAY
                        val transactionsToday = transactions.filter { trans ->
                            val txDate = Instant.ofEpochMilli(trans.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
                            txDate == today
                        }

                        // 3. Tính Thu/Chi của riêng HÔM NAY
                        val totalIncomeToday = transactionsToday.filter { it.type == "Thu" }.sumOf { it.amount }
                        val totalExpenseToday = transactionsToday.filter { it.type == "Chi" }.sumOf { it.amount }

                        // 4. Tính SỐ DƯ TỔNG (Vẫn lấy từ danh sách 'transactions' tổng)
                        val totalBalanceAll = transactions.sumOf { if (it.type == "Thu") it.amount else -it.amount }

                        val displayedTransactions = transactions.filter { trans ->
                            val matchMethod = selectedFilter == "Tất cả" || trans.paymentMethod == selectedFilter
                            val txDate = Instant.ofEpochMilli(trans.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()

                            // Nếu không có bộ lọc ngày nào được chọn (null), mặc định chỉ lấy ngày hôm nay (today)
                            val matchDate = if (selectedDateFilter == null) {
                                txDate == today
                            } else {
                                txDate == selectedDateFilter
                            }
                            matchMethod && matchDate
                        }.sortedByDescending { it.timestamp }

                        Column(modifier = Modifier.fillMaxSize().background(topHeaderBrush)) {
                            Column(modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)) {

                                // GỌI BANNER TẠI ĐÂY
                                HomeBannerCarousel(user = user)

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val darkColor = Color(0xFF2D3436)
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(text = "Chi tiêu", color = darkColor.copy(alpha = 0.7f), fontSize = 12.sp)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(text = formatMoney.format(totalExpenseToday), color = darkColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(text = "Thu nhập", color = darkColor.copy(alpha = 0.7f), fontSize = 12.sp)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(text = formatMoney.format(totalIncomeToday), color = darkColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(text = "Số dư", color = darkColor.copy(alpha = 0.7f), fontSize = 12.sp)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(text = formatMoney.format(totalBalance), color = Color(0xFFFA3B70), fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
                                            Text(text = "Đang lọc ngày: ", color = Color.Gray, fontSize = 13.sp)
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
                                                colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFF4DD0E1), labelColor = Color.White)
                                            )
                                        }
                                    }
                                    LazyRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)

                                    ) {
                                        // Sử dụng lazyItems để tránh lỗi xung đột thư viện
                                        lazyItems(items = filters) { filter ->
                                            val isSelected = selectedFilter == filter
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(20.dp))
                                                    .background(if (isSelected) Color(0xFF4DD0E1) else cardColor)
                                                    .border(1.dp, if (isSelected) Color.Transparent else Color.LightGray, RoundedCornerShape(20.dp))
                                                    .clickable { selectedFilter = filter }
                                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(text = filter, color = if (isSelected) Color.Black else Color.Gray, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp)
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    if (displayedTransactions.isEmpty()) {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text(text = "Không có giao dịch nào.", color = Color.Gray)
                                        }
                                    } else {
                                        LazyVerticalGrid(
                                            columns = GridCells.Fixed(2),
                                            contentPadding = PaddingValues(bottom = 100.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            // Sử dụng gridItems để tránh lỗi xung đột
                                            gridItems(items = displayedTransactions) { trans ->
                                                TransactionGridItem(
                                                    transaction = trans,
                                                    formatMoney = formatMoney,
                                                    cardColor = cardColor,
                                                    textColor = textColor,
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
                    2 -> AddTransactionScreen(viewModel = viewModel, onNavigateBack = { selectedTab = 0 })
                    3 -> StatisticsScreen(transactions = transactions, formatMoney = formatMoney)
                    4 -> ProfileScreen(user = user, onLogout = onLogout, onSubScreenChange = { isOpen -> isSubScreenOpen = isOpen })
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
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFF4DD0E1))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(text = "Trợ lý đang suy nghĩ...", color = Color.Gray)
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
                                color = Color(0xFF4DD0E1).copy(alpha = 0.1f),
                                border = BorderStroke(1.dp, Color(0xFF4DD0E1).copy(alpha = 0.5f)),
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
                        TextButton(onClick = { showLogoutDialog = false }) { Text(text = "Hủy", color = Color.Gray, fontWeight = FontWeight.Bold) }
                    }
                )
            }
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
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF9C27B0), unfocusedBorderColor = Color.LightGray, focusedTextColor = textColor, unfocusedTextColor = textColor), maxLines = 4
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
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isIncome = transaction.type == "Thu"
    val sign = if (isIncome) "+" else "-"
    val amountColor = if (isIncome) Color(0xFF4CAF50) else textColor
    val sdfTime = java.text.SimpleDateFormat("HH:mm", Locale("vi", "VN"))
    val timeString = sdfTime.format(java.util.Date(transaction.timestamp))
    val sdfFullDate = java.text.SimpleDateFormat("dd/MM/yyyy - HH:mm", Locale("vi", "VN"))
    val fullDateString = sdfFullDate.format(java.util.Date(transaction.timestamp))

    // Đã xóa biến showMenu của nút 3 chấm
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDetailsDialog by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth().clickable { showDetailsDialog = true }, colors = CardDefaults.cardColors(containerColor = cardColor), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(modifier = Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF0F0F0)), contentAlignment = Alignment.Center) {
                if (!transaction.imageUrl.isNullOrEmpty()) {
                    AsyncImage(model = transaction.imageUrl, contentDescription = "Ảnh", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else { Text(text = "Không có ảnh", fontSize = 10.sp, color = Color.Gray) }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "$sign${formatMoney.format(transaction.amount)}", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = amountColor)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = transaction.category, fontSize = 12.sp, color = Color.Gray, maxLines = 1)
                }
                // Nút 3 chấm đã được gỡ bỏ khỏi đây để giao diện thoáng hơn
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = timeString, fontSize = 11.sp, color = Color.Gray)
                Surface(color = Color.LightGray.copy(alpha = 0.2f), shape = RoundedCornerShape(12.dp)) {
                    Text(text = transaction.paymentMethod, fontSize = 10.sp, color = Color.DarkGray, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
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
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text(text = "Hủy", color = Color.Gray, fontWeight = FontWeight.Bold) } }
        )
    }

    if (showDetailsDialog) {
        Dialog(onDismissRequest = { showDetailsDialog = false }) {
            Surface(shape = RoundedCornerShape(24.dp), color = cardColor, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "Chi tiết", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = textColor)
                        IconButton(onClick = { showDetailsDialog = false }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Clear, contentDescription = "Đóng", tint = Color.Gray) }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    if (!transaction.imageUrl.isNullOrEmpty()) {
                        AsyncImage(model = transaction.imageUrl, contentDescription = "Hóa đơn", modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                    Text(text = "$sign${formatMoney.format(transaction.amount)}", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = amountColor)
                    Spacer(modifier = Modifier.height(24.dp))
                    Column(modifier = Modifier.fillMaxWidth().background(Color.Gray.copy(alpha = 0.05f), RoundedCornerShape(12.dp)).padding(16.dp)) {
                        @Composable
                        fun InfoRow(label: String, value: String, valueColor: Color = textColor) {
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = label, color = Color.Gray, fontSize = 14.sp)
                                Text(text = value, color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.End)
                            }
                        }
                        InfoRow("Loại giao dịch", transaction.type, amountColor)
                        InfoRow("Danh mục", transaction.category)
                        InfoRow("Nguồn tiền", transaction.paymentMethod)
                        InfoRow("Thời gian", fullDateString)
                        if (transaction.note.isNotBlank()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.LightGray.copy(alpha = 0.5f))
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(text = "Ghi chú:", color = Color.Gray, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = transaction.note, color = textColor, fontSize = 14.sp)
                            }
                        }
                    }

                    // KHU VỰC NÚT SỬA/XÓA MỚI
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
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
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