package com.example.walletwise.presentation.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.walletwise.domain.model.Transaction
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// --- HÀM PHỤ TRỢ: Dùng để tạo tên nhãn hiển thị (Dùng chung cho Main & Dialog) ---
fun getTimeLabel(timeTab: Int, offset: Int): String {
    val calendar = Calendar.getInstance()
    return when (timeTab) {
        0 -> {
            calendar.add(Calendar.WEEK_OF_YEAR, offset)
            calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
            val df = SimpleDateFormat("dd/MM", Locale.getDefault())
            val start = df.format(calendar.time)
            calendar.add(Calendar.DAY_OF_YEAR, 6)
            val end = df.format(calendar.time)
            "$start - $end"
        }
        1 -> {
            calendar.add(Calendar.MONTH, offset)
            "Tháng ${calendar.get(Calendar.MONTH) + 1}, ${calendar.get(Calendar.YEAR)}"
        }
        2 -> {
            calendar.add(Calendar.YEAR, offset)
            "Năm ${calendar.get(Calendar.YEAR)}"
        }
        else -> ""
    }
}

@Composable
fun StatisticsScreen(transactions: List<Transaction>, formatMoney: NumberFormat) {
    var isIncome by remember { mutableStateOf(false) }
    var expandedDropdown by remember { mutableStateOf(false) }
    var selectedTimeTab by remember { mutableIntStateOf(1) }
    var timeOffset by remember { mutableIntStateOf(0) }

    // Biến trạng thái để Bật/Tắt Dialog chọn nhanh thời gian
    var showTimePicker by remember { mutableStateOf(false) }

    LaunchedEffect(selectedTimeTab) { timeOffset = 0 }

    val darkBgColor = Color(0xFF191919)

    val timeData = remember(selectedTimeTab, timeOffset) {
        val calendar = Calendar.getInstance()
        var start = 0L
        var end = 0L

        when (selectedTimeTab) {
            0 -> {
                calendar.add(Calendar.WEEK_OF_YEAR, timeOffset)
                calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.clear(Calendar.MINUTE)
                calendar.clear(Calendar.SECOND)
                calendar.clear(Calendar.MILLISECOND)
                start = calendar.timeInMillis

                calendar.add(Calendar.DAY_OF_YEAR, 6)
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                end = calendar.timeInMillis
            }
            1 -> {
                calendar.add(Calendar.MONTH, timeOffset)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.clear(Calendar.MINUTE)
                calendar.clear(Calendar.SECOND)
                calendar.clear(Calendar.MILLISECOND)
                start = calendar.timeInMillis

                calendar.add(Calendar.MONTH, 1)
                calendar.add(Calendar.MILLISECOND, -1)
                end = calendar.timeInMillis
            }
            2 -> {
                calendar.add(Calendar.YEAR, timeOffset)
                calendar.set(Calendar.DAY_OF_YEAR, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.clear(Calendar.MINUTE)
                calendar.clear(Calendar.SECOND)
                calendar.clear(Calendar.MILLISECOND)
                start = calendar.timeInMillis

                calendar.add(Calendar.YEAR, 1)
                calendar.add(Calendar.MILLISECOND, -1)
                end = calendar.timeInMillis
            }
        }
        Pair(start, end)
    }

    val displayLabel = getTimeLabel(selectedTimeTab, timeOffset)
    val startTime = timeData.first
    val endTime = timeData.second

    val currentTransactions = transactions.filter {
        it.type == (if (isIncome) "Thu" else "Chi") && it.timestamp in startTime..endTime
    }

    val categoryTotals = currentTransactions
        .groupBy { it.category }
        .mapValues { (_, transList) -> transList.sumOf { it.amount } }
        .toList()
        .sortedByDescending { it.second }

    val totalAmount = categoryTotals.sumOf { it.second }

    // --- DIALOG CHỌN NHANH THỜI GIAN ---
    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            containerColor = Color(0xFF2A2A2A),
            title = { Text("Chọn thời gian", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                // Cho phép cuộn tối đa 24 mốc thời gian trong quá khứ
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    val offsets = (0 downTo -24).toList()
                    items(offsets.size) { index ->
                        val offset = offsets[index]
                        val label = getTimeLabel(selectedTimeTab, offset)
                        Text(
                            text = label,
                            color = if (offset == timeOffset) Color(0xFFFFD700) else Color.White,
                            fontSize = 16.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    timeOffset = offset
                                    showTimePicker = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Đóng", color = Color(0xFFFFD700))
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(darkBgColor)) {
        // --- 1. HEADER: Dropdown Thu/Chi ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(24.dp))
            Box {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { expandedDropdown = true }
                ) {
                    Text(text = if (isIncome) "Thu nhập" else "Chi tiêu", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White)
                }
                DropdownMenu(
                    expanded = expandedDropdown,
                    onDismissRequest = { expandedDropdown = false },
                    modifier = Modifier.background(Color(0xFF2A2A2A))
                ) {
                    DropdownMenuItem(text = { Text("Chi tiêu", color = Color.White) }, onClick = { isIncome = false; expandedDropdown = false })
                    DropdownMenuItem(text = { Text("Thu nhập", color = Color.White) }, onClick = { isIncome = true; expandedDropdown = false })
                }
            }
            Icon(Icons.Default.DateRange, contentDescription = "Lịch", tint = Color.White)
        }

        // --- 2. THANH CHỌN THỜI GIAN ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).border(1.dp, Color.Gray, RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp)),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val tabs = listOf("Tuần", "Tháng", "Năm")
            tabs.forEachIndexed { index, title ->
                Box(
                    modifier = Modifier.weight(1f).background(if (selectedTimeTab == index) Color.White else Color.Transparent).clickable { selectedTimeTab = index }.padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = title, color = if (selectedTimeTab == index) Color.Black else Color.White, fontWeight = if (selectedTimeTab == index) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }

        // --- 3. ĐIỀU HƯỚNG TỚI LUI THỜI GIAN CỤ THỂ ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { timeOffset -= 1 }) {
                Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Trở về trước", tint = Color.White)
            }

            // 👉 CLICK VÀO ĐÂY ĐỂ MỞ MENU CHỌN NHANH
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showTimePicker = true }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(text = displayLabel, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Icon(Icons.Default.ArrowDropDown, contentDescription = "Chọn nhanh", tint = Color.Gray, modifier = Modifier.size(20.dp))
            }

            IconButton(onClick = { timeOffset += 1 }, enabled = timeOffset < 0) {
                Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Tiếp theo", tint = if (timeOffset < 0) Color.White else Color.Transparent)
            }
        }

        Divider(color = Color(0xFF333333), thickness = 1.dp)

        // --- 4. BODY: Biểu đồ và Danh sách ---
        CombinedChartAndList(categoryTotals, totalAmount, formatMoney)
    }
}

@Composable
fun CombinedChartAndList(categoryTotals: List<Pair<String, Double>>, totalAmount: Double, formatMoney: NumberFormat) {
    // Bảng màu (Vàng, Xanh lam, Hồng, Xanh lá, Tím)
    val colors = listOf(Color(0xFFFFD700), Color(0xFF4DD0E1), Color(0xFFF48FB1), Color(0xFF81C784), Color(0xFFBA68C8))

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- PHẦN 1: BIỂU ĐỒ DONUT ---
        item {
            if (totalAmount == 0.0) {
                Text("Chưa có dữ liệu giao dịch", color = Color.Gray, modifier = Modifier.padding(32.dp))
            } else {
                Spacer(modifier = Modifier.height(24.dp))
                Box(contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.size(200.dp).padding(16.dp)) {
                        var startAngle = -90f
                        categoryTotals.forEachIndexed { index, (_, amount) ->
                            val sweepAngle = ((amount / totalAmount) * 360f).toFloat()
                            drawArc(
                                color = colors[index % colors.size],
                                startAngle = startAngle,
                                sweepAngle = sweepAngle,
                                useCenter = false,
                                style = Stroke(width = 45f, cap = StrokeCap.Butt)
                            )
                            startAngle += sweepAngle
                        }
                    }
                    // Tổng tiền ở giữa biểu đồ
                    Text(
                        text = formatMoney.format(totalAmount),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
                // 3 Dấu chấm nhỏ mờ (giống ảnh)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(3) { index ->
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(if (index == 0) Color.White else Color.Gray))
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        // --- PHẦN 2: DANH SÁCH CHI TIẾT KÈM PROGRESS BAR ---
        items(categoryTotals.size) { index ->
            val (category, amount) = categoryTotals[index]
            val percentage = (amount / totalAmount).toFloat()
            val percentageText = String.format("%.2f%%", percentage * 100)
            val color = colors[index % colors.size]

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon danh mục (hình tròn nhỏ)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.size(20.dp).clip(CircleShape).background(color))
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Cột Tên, Phần trăm, Tiền và Thanh kéo
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "$category  $percentageText",
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp
                        )
                        Text(
                            text = formatMoney.format(amount),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = { percentage },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp) // Thanh kéo dày hơn 1 chút cho giống ảnh
                            .clip(RoundedCornerShape(4.dp)),
                        color = color,
                        trackColor = Color(0xFF333333)
                    )
                }
            }
        }

        // Tạo khoảng trống dưới cùng để không bị che bởi thanh Bottom Navigation
        item { Spacer(modifier = Modifier.height(100.dp)) }
    }
}