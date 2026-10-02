package com.example.walletwise.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.walletwise.domain.model.Transaction
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@Composable
fun HistoryScreen(
    viewModel: TransactionViewModel,
    onDaySelected: (LocalDate) -> Unit
) {
    val transactions by viewModel.transactions.collectAsState()

    val today = LocalDate.now()
    var currentYearMonth by remember { mutableStateOf(YearMonth.now()) }
    var showMonthPicker by remember { mutableStateOf(false) }

    val daysInMonth = currentYearMonth.lengthOfMonth()
    val firstDayOfMonth = currentYearMonth.atDay(1)
    val startDayOffset = firstDayOfMonth.dayOfWeek.value - 1

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 16.dp, start = 16.dp, end = 16.dp)
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { currentYearMonth = currentYearMonth.minusMonths(1) }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Trở lại", tint = MaterialTheme.colorScheme.primary)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { showMonthPicker = true }
            ) {
                Text(
                    text = "tháng ${currentYearMonth.monthValue} ${currentYearMonth.year}",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color.Gray)

                if (currentYearMonth != YearMonth.now()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Tháng hiện tại",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { currentYearMonth = YearMonth.now() }
                    )
                }
            }

            IconButton(onClick = { currentYearMonth = currentYearMonth.plusMonths(1) }) {
                Icon(Icons.Default.ArrowForward, contentDescription = "Tiếp", tint = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN").forEach { day ->
                Text(text = day, color = Color.Gray, fontSize = 12.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val totalCells = daysInMonth + startDayOffset
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            items(totalCells) { index ->
                if (index < startDayOffset) {
                    Box(modifier = Modifier.aspectRatio(0.8f))
                } else {
                    val dayOfMonth = index - startDayOffset + 1
                    val dateOfCell = currentYearMonth.atDay(dayOfMonth)

                    val dailyTransactions = transactions.filter {
                        val txDate = Instant.ofEpochMilli(it.timestamp)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                        txDate == dateOfCell
                    }

                    DayCell(
                        date = dateOfCell,
                        isToday = dateOfCell == today,
                        transactions = dailyTransactions,
                        onClick = { onDaySelected(dateOfCell) }
                    )
                }
            }
        }
    }

    if (showMonthPicker) {
        MonthPickerDialog(
            initialYearMonth = currentYearMonth,
            onDismiss = { showMonthPicker = false },
            onConfirm = { selectedYM ->
                currentYearMonth = selectedYM
                showMonthPicker = false
            }
        )
    }
}

@Composable
fun DayCell(
    date: LocalDate,
    isToday: Boolean,
    transactions: List<Transaction>,
    onClick: () -> Unit
) {
    // Lấy danh sách ảnh từ các giao dịch trong ngày
    val images = transactions.map { it.imageUrl.trim() }.filter { it.isNotEmpty() }

    val todayBgColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
    Box(
        modifier = Modifier
            .aspectRatio(0.65f) // Chỉnh tỷ lệ ô lịch dài hơn một chút để chứa vừa ảnh và số ngày
            .padding(2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isToday) todayBgColor else Color.Transparent)
            .clickable { onClick() },
        contentAlignment = Alignment.TopCenter // Đẩy nội dung lên trên cùng
    ) {
        // 1. KHU VỰC HIỂN THỊ ẢNH
        if (transactions.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, start = 4.dp, end = 4.dp)
                    .aspectRatio(1f), // Ép khu vực chứa ảnh thành hình vuông
                contentAlignment = Alignment.Center
            ) {
                // Ảnh nền (Nếu có từ 2 ảnh trở lên, tạo hiệu ứng xếp chồng)
                if (images.size >= 2) {
                    TransactionPhoto(
                        imageUrl = images[1],
                        modifier = Modifier
                            .fillMaxSize(0.85f) // Nhỏ hơn ảnh chính một chút
                            .offset(x = (-6).dp, y = (-6).dp) // Lệch về góc trái trên
                            .clip(RoundedCornerShape(12.dp))
                            .border(2.dp, MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)),
                    )
                }

                // Ảnh chính (Nằm trên cùng)
                TransactionPhoto(
                    imageUrl = images.firstOrNull(),
                    modifier = Modifier
                        .fillMaxSize(0.9f) // Chiếm 90% diện tích ô vuông
                        .clip(RoundedCornerShape(12.dp))
                        .border(2.dp, MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)),
                )

                // Badge đếm số lượng ảnh (+1, +2...)
                if (images.size > 1) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd) // Neo vào góc trên cùng bên phải
                            .offset(x = 6.dp, y = (-4).dp)
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp)) // Nền trắng bo góc như ảnh mẫu
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+${images.size - 1}",
                            color = MaterialTheme.colorScheme.onSurface, // Chữ đen nổi bật trên nền trắng
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }

        // 2. HIỂN THỊ NGÀY (Nằm ở dưới đáy ô lịch)
        Text(
            text = date.dayOfMonth.toString(),
            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            fontSize = 13.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp)
        )
    }
}

@Composable
fun MonthPickerDialog(
    initialYearMonth: YearMonth,
    onDismiss: () -> Unit,
    onConfirm: (YearMonth) -> Unit
) {
    var tempYear by remember { mutableIntStateOf(initialYearMonth.year) }
    var tempMonth by remember { mutableIntStateOf(initialYearMonth.monthValue) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { tempYear-- },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape).size(36.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, "Năm trước", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(text = tempYear.toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    IconButton(
                        onClick = { tempYear++ },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape).size(36.dp)
                    ) {
                        Icon(Icons.Default.ArrowForward, "Năm sau", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                val totalMonths = 12
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.height(180.dp)
                ) {
                    items(totalMonths) { index ->
                        val month = index + 1
                        val isSelected = tempMonth == month
                        val bgBtn = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        val fgBtn = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(bgBtn)
                                .clickable { tempMonth = month }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = month.toString(),
                                color = fgBtn,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 16.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Hủy", tint = Color.Gray, modifier = Modifier.size(32.dp))
                    }
                    IconButton(onClick = { onConfirm(YearMonth.of(tempYear, tempMonth)) }) {
                        Icon(Icons.Default.Check, contentDescription = "Xác nhận", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    }
                }
            }
        }
    }
}
