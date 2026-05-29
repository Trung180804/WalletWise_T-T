package com.example.walletwise.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
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

    val filters = listOf("Tất cả", "Tiền mặt", "Chuyển khoản", "Thẻ tín dụng")
    var selectedFilter by remember { mutableStateOf(filters[0]) }

    val daysInMonth = currentYearMonth.lengthOfMonth()
    val firstDayOfMonth = currentYearMonth.atDay(1)
    val startDayOffset = firstDayOfMonth.dayOfWeek.value - 1

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(top = 16.dp, start = 16.dp, end = 16.dp)
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filters) { filter ->
                val isSelected = selectedFilter == filter
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected) Color(0xFF4DD0E1) else Color(0xFF2C2C2C))
                        .clickable { selectedFilter = filter }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = filter,
                        color = if (isSelected) Color.Black else Color.White,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 14.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thay ChevronLeft bằng ArrowBack
            IconButton(onClick = { currentYearMonth = currentYearMonth.minusMonths(1) }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Trở lại", tint = Color(0xFF4DD0E1))
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { showMonthPicker = true }
            ) {
                Text(
                    text = "tháng ${currentYearMonth.monthValue} ${currentYearMonth.year}",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color.Gray)

                if (currentYearMonth != YearMonth.now()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Tháng hiện tại",
                        tint = Color(0xFFFFD700),
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { currentYearMonth = YearMonth.now() }
                    )
                }
            }

            // Thay ChevronRight bằng ArrowForward
            IconButton(onClick = { currentYearMonth = currentYearMonth.plusMonths(1) }) {
                Icon(Icons.Default.ArrowForward, contentDescription = "Tiếp", tint = Color(0xFF4DD0E1))
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
    val images = transactions.mapNotNull { it.imageUrl }.filter { it.isNotEmpty() }

    Box(
        modifier = Modifier
            .aspectRatio(0.8f)
            .padding(4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isToday) Color(0xFF2C2C2C) else Color.Transparent)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            color = if (isToday) Color(0xFFFFD700) else Color.White,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            fontSize = 14.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp)
        )

        if (images.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                if (images.size >= 2) {
                    AsyncImage(
                        model = images[1],
                        contentDescription = null,
                        modifier = Modifier
                            .size(36.dp)
                            .offset(x = (-8).dp, y = (-4).dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(2.dp, Color.White, RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                }

                AsyncImage(
                    model = images[0],
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(2.dp, Color.White, RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )

                if (images.size > 2) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-4).dp)
                            .size(18.dp)
                            .background(Color.Black.copy(alpha = 0.8f), CircleShape)
                            .border(1.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+${images.size - 2}",
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MonthPickerDialog(
    initialYearMonth: YearMonth,
    onDismiss: () -> Unit,
    onConfirm: (YearMonth) -> Unit
) {
    // Sửa lỗi cảnh báo màu vàng bằng cách dùng mutableIntStateOf
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
                .background(Color.White)
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
                        modifier = Modifier.background(Color(0xFFF0F0F0), CircleShape).size(36.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, "Năm trước", tint = Color.Black)
                    }
                    Text(text = tempYear.toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    IconButton(
                        onClick = { tempYear++ },
                        modifier = Modifier.background(Color(0xFFF0F0F0), CircleShape).size(36.dp)
                    ) {
                        Icon(Icons.Default.ArrowForward, "Năm sau", tint = Color.Black)
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
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0xFFFFD700) else Color(0xFFF8F9FA))
                                .clickable { tempMonth = month }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = month.toString(),
                                color = if (isSelected) Color.Black else Color.DarkGray,
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
                        Icon(Icons.Default.Check, contentDescription = "Xác nhận", tint = Color.Black, modifier = Modifier.size(32.dp))
                    }
                }
            }
        }
    }
}