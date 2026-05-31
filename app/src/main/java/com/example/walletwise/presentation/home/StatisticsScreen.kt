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
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.walletwise.domain.model.Transaction
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.max

// --- Bảng màu sáng (Neon) cho Dark Mode ---
val ColorIncome = Color(0xFF00E676)
val ColorExpense = Color(0xFFFF5252)

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

fun formatCompactNumber(value: Float): String {
    return when {
        value >= 1_000_000_000 -> String.format(Locale("vi", "VN"), "%.1f Tỷ", value / 1_000_000_000).replace(",0", "").replace(".0", "")
        value >= 1_000_000 -> String.format(Locale("vi", "VN"), "%.1f Tr", value / 1_000_000).replace(",0", "").replace(".0", "")
        value >= 1_000 -> String.format(Locale("vi", "VN"), "%.0f K", value / 1_000)
        else -> value.toInt().toString()
    }
}

// Class chứa dữ liệu cho biểu đồ
data class StatChartPoint(val label: String, val income: Float, val expense: Float)

@Composable
fun StatisticsScreen(transactions: List<Transaction>, formatMoney: NumberFormat) {
    var mainTab by remember { mutableIntStateOf(0) }

    val txTypes = listOf("Tất cả", "Thu nhập", "Chi tiêu")
    var selectedTxType by remember { mutableIntStateOf(0) }
    var expandedDropdown by remember { mutableStateOf(false) }

    var selectedTimeTab by remember { mutableIntStateOf(1) }
    var timeOffset by remember { mutableIntStateOf(0) }
    var showTimePicker by remember { mutableStateOf(false) }

    LaunchedEffect(selectedTimeTab) { timeOffset = 0 }

    val darkBgColor = Color(0xFF121212)
    val cardColor = Color(0xFF1E1E1E)

    val timeData = remember(selectedTimeTab, timeOffset) {
        val calendar = Calendar.getInstance()
        var start = 0L
        var end = 0L
        when (selectedTimeTab) {
            0 -> {
                calendar.add(Calendar.WEEK_OF_YEAR, timeOffset)
                calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.clear(Calendar.MINUTE); calendar.clear(Calendar.SECOND); calendar.clear(Calendar.MILLISECOND)
                start = calendar.timeInMillis
                calendar.add(Calendar.DAY_OF_YEAR, 6)
                calendar.set(Calendar.HOUR_OF_DAY, 23); calendar.set(Calendar.MINUTE, 59); calendar.set(Calendar.SECOND, 59)
                end = calendar.timeInMillis
            }
            1 -> {
                calendar.add(Calendar.MONTH, timeOffset)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.clear(Calendar.MINUTE); calendar.clear(Calendar.SECOND); calendar.clear(Calendar.MILLISECOND)
                start = calendar.timeInMillis
                calendar.add(Calendar.MONTH, 1)
                calendar.add(Calendar.MILLISECOND, -1)
                end = calendar.timeInMillis
            }
            2 -> {
                calendar.add(Calendar.YEAR, timeOffset)
                calendar.set(Calendar.DAY_OF_YEAR, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.clear(Calendar.MINUTE); calendar.clear(Calendar.SECOND); calendar.clear(Calendar.MILLISECOND)
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

    val timeFilteredTx = transactions.filter { it.timestamp in startTime..endTime }
    val totalIncome = timeFilteredTx.filter { it.type == "Thu" }.sumOf { it.amount }
    val totalExpense = timeFilteredTx.filter { it.type == "Chi" }.sumOf { it.amount }
    val balance = totalIncome - totalExpense

    val currentTransactions = timeFilteredTx.filter {
        when (selectedTxType) {
            1 -> it.type == "Thu"
            2 -> it.type == "Chi"
            else -> true
        }
    }

    val categoryTotals = currentTransactions
        .groupBy { it.category to it.type }
        .map { (key, list) -> Triple(key.first, key.second, list.sumOf { it.amount }) }
        .sortedByDescending { it.third }

    // 👉 ĐÂY LÀ KHỐI LỆNH TẠO DỮ LIỆU ĐỘNG CHO BIỂU ĐỒ TÙY THEO BỘ LỌC
    val dynamicChartData = remember(selectedTimeTab, startTime, endTime, transactions) {
        val data = mutableListOf<StatChartPoint>()
        val cal = Calendar.getInstance().apply { timeInMillis = startTime }

        when (selectedTimeTab) {
            0 -> { // 1 Tuần -> Chia làm 7 cột (T2 -> CN)
                val sdf = SimpleDateFormat("EE", Locale("vi", "VN"))
                for (i in 0..6) {
                    val dayStart = cal.timeInMillis
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                    val dayEnd = cal.timeInMillis - 1

                    val txs = transactions.filter { it.timestamp in dayStart..dayEnd }
                    val inc = txs.filter { it.type == "Thu" }.sumOf { it.amount }.toFloat()
                    val exp = txs.filter { it.type == "Chi" }.sumOf { it.amount }.toFloat()

                    var label = sdf.format(java.util.Date(dayStart)).replace("Thứ ", "T")
                    if (label.lowercase() == "chủ nhật") label = "CN"
                    data.add(StatChartPoint(label, inc, exp))
                }
            }
            1 -> { // 1 Tháng -> Chia làm các Tuần (Tuần 1, Tuần 2...)
                var weekNum = 1
                while (cal.timeInMillis <= endTime) {
                    val segmentStart = cal.timeInMillis
                    cal.add(Calendar.DAY_OF_YEAR, 7)
                    var segmentEnd = cal.timeInMillis - 1
                    if (segmentEnd > endTime) segmentEnd = endTime // Tránh lấn sang tháng sau

                    val txs = transactions.filter { it.timestamp in segmentStart..segmentEnd }
                    val inc = txs.filter { it.type == "Thu" }.sumOf { it.amount }.toFloat()
                    val exp = txs.filter { it.type == "Chi" }.sumOf { it.amount }.toFloat()

                    data.add(StatChartPoint("Tuần $weekNum", inc, exp))
                    weekNum++
                }
            }
            2 -> { // 1 Năm -> Chia làm 12 Tháng (T1 -> T12)
                for (i in 1..12) {
                    val monthStart = cal.timeInMillis
                    cal.add(Calendar.MONTH, 1)
                    val monthEnd = cal.timeInMillis - 1

                    val txs = transactions.filter { it.timestamp in monthStart..monthEnd }
                    val inc = txs.filter { it.type == "Thu" }.sumOf { it.amount }.toFloat()
                    val exp = txs.filter { it.type == "Chi" }.sumOf { it.amount }.toFloat()

                    data.add(StatChartPoint("T$i", inc, exp))
                }
            }
        }
        data
    }

    // Tiêu đề động cho biểu đồ
    val chartTitle = when (selectedTimeTab) {
        0 -> "Thống kê các ngày trong tuần"
        1 -> "Thống kê các tuần trong tháng"
        else -> "Thống kê các tháng trong năm"
    }

    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            containerColor = Color(0xFF2A2A2A),
            title = { Text("Chọn thời gian", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(25) { index ->
                        val offset = -index
                        val label = getTimeLabel(selectedTimeTab, offset)
                        Text(
                            text = label,
                            color = if (offset == timeOffset) Color(0xFFFFD700) else Color.White,
                            modifier = Modifier.fillMaxWidth().clickable { timeOffset = offset; showTimePicker = false }.padding(12.dp)
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showTimePicker = false }) { Text("Đóng", color = Color(0xFFFFD700)) } }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(darkBgColor)) {
        TabRow(selectedTabIndex = mainTab, containerColor = darkBgColor, contentColor = Color(0xFFFFD700), divider = {}) {
            listOf("Tổng quan", "Biểu đồ").forEachIndexed { index, title ->
                Tab(
                    selected = mainTab == index, onClick = { mainTab = index },
                    text = { Text(title, fontWeight = FontWeight.Bold, color = if (mainTab == index) Color(0xFFFFD700) else Color.Gray) }
                )
            }
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(modifier = Modifier.fillMaxWidth().border(1.dp, Color.DarkGray, RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp)), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf("Tuần", "Tháng", "Năm").forEachIndexed { index, title ->
                    Box(
                        modifier = Modifier.weight(1f).background(if (selectedTimeTab == index) Color.White else Color.Transparent).clickable { selectedTimeTab = index }.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) { Text(text = title, color = if (selectedTimeTab == index) Color.Black else Color.White, fontWeight = if (selectedTimeTab == index) FontWeight.Bold else FontWeight.Normal) }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { timeOffset -= 1 }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.KeyboardArrowLeft, contentDescription = null, tint = Color.White) }
                    Row(modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { showTimePicker = true }.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(text = displayLabel, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.Gray)
                    }
                    IconButton(onClick = { timeOffset += 1 }, enabled = timeOffset < 0, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = if (timeOffset < 0) Color.White else Color.Transparent) }
                }
                Box {
                    Surface(color = cardColor, shape = RoundedCornerShape(12.dp), onClick = { expandedDropdown = true }) {
                        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(txTypes[selectedTxType], color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White)
                        }
                    }
                    DropdownMenu(expanded = expandedDropdown, onDismissRequest = { expandedDropdown = false }, modifier = Modifier.background(cardColor)) {
                        txTypes.forEachIndexed { index, title ->
                            DropdownMenuItem(text = { Text(title, color = Color.White) }, onClick = { selectedTxType = index; expandedDropdown = false })
                        }
                    }
                }
            }
        }
        Divider(color = Color(0xFF333333), thickness = 1.dp)

        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            if (mainTab == 0) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = cardColor), shape = RoundedCornerShape(16.dp)) {
                        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("⬇ Thu nhập", color = ColorIncome, fontWeight = FontWeight.Bold)
                                Text("+${formatMoney.format(totalIncome)}", color = ColorIncome, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("⬆ Chi phí", color = ColorExpense, fontWeight = FontWeight.Bold)
                                Text("-${formatMoney.format(totalExpense)}", color = ColorExpense, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Divider(color = Color.DarkGray)
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Số dư", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text(formatMoney.format(balance), color = if (balance >= 0) Color.White else ColorExpense, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Chi tiết theo danh mục", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (categoryTotals.isEmpty()) {
                    item { Text("Chưa có dữ liệu", color = Color.Gray, modifier = Modifier.padding(top = 16.dp)) }
                } else {
                    items(categoryTotals.size) { index ->
                        CategoryProgressItem(categoryTotals[index], totalIncome, totalExpense, formatMoney, index)
                    }
                }
            } else {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(if (selectedTxType == 0) "Tỷ lệ Thu / Chi" else "Chi tiết theo danh mục", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = cardColor)) {
                        DonutChart(categoryTotals, selectedTxType, totalIncome, totalExpense, balance, formatMoney)
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Text(chartTitle, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    Card(modifier = Modifier.fillMaxWidth().height(220.dp), colors = CardDefaults.cardColors(containerColor = cardColor)) {
                        SimpleBarChart(dynamicChartData, selectedTxType)
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Xu hướng (Đường)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    Card(modifier = Modifier.fillMaxWidth().height(220.dp), colors = CardDefaults.cardColors(containerColor = cardColor)) {
                        SimpleLineChart(dynamicChartData, selectedTxType)
                    }
                    Spacer(modifier = Modifier.height(100.dp))
                }
            }
        }
    }
}

// ================= CÁC COMPONENT GIAO DIỆN CHÚ THÍCH & VẼ BIỂU ĐỒ =================

@Composable
fun ChartLegend(typeFilter: Int) {
    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp, top = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        if (typeFilter == 0 || typeFilter == 1) {
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(ColorIncome))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Thu nhập", color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.width(24.dp))
        }
        if (typeFilter == 0 || typeFilter == 2) {
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(ColorExpense))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Chi phí", color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun CategoryProgressItem(item: Triple<String, String, Double>, totalIncome: Double, totalExpense: Double, formatMoney: NumberFormat, index: Int) {
    val isIncome = item.second == "Thu"
    val sign = if (isIncome) "+" else "-"
    val amountColor = if (isIncome) ColorIncome else ColorExpense
    val typeTotal = if (isIncome) totalIncome else totalExpense
    val percentage = if (typeTotal > 0) (item.third / typeTotal).toFloat() else 0f

    val colors = listOf(Color(0xFFFFD700), Color(0xFF4DD0E1), Color(0xFFF48FB1), Color(0xFF81C784), Color(0xFFBA68C8))
    val barColor = colors[index % colors.size]

    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(barColor.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(barColor))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${item.first}  ${String.format("%.1f%%", percentage * 100)}", color = Color.White, fontSize = 14.sp)
                Text("$sign${formatMoney.format(item.third)}", color = amountColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(progress = { percentage }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)), color = barColor, trackColor = Color(0xFF333333))
        }
    }
}

@Composable
fun DonutChart(categoryTotals: List<Triple<String, String, Double>>, typeFilter: Int, totalIncome: Double, totalExpense: Double, balance: Double, formatMoney: NumberFormat) {
    val colors = listOf(Color(0xFFFFD700), Color(0xFF4DD0E1), Color(0xFFF48FB1), Color(0xFF81C784), Color(0xFFBA68C8))

    val chartData = if (typeFilter == 0) {
        listOf(Triple("Thu nhập", "Thu", totalIncome), Triple("Chi phí", "Chi", totalExpense)).filter { it.third > 0.0 }
    } else categoryTotals

    val chartTotal = chartData.sumOf { it.third }
    val centerText = if (typeFilter == 0) balance else chartTotal
    val centerLabel = if (typeFilter == 0) "Số dư" else "Tổng cộng"

    if (chartTotal == 0.0) {
        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { Text("Chưa có dữ liệu", color = Color.Gray) }
        return
    }

    Column(modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(160.dp)) {
                var startAngle = -90f
                chartData.forEachIndexed { index, item ->
                    val sweepAngle = ((item.third / chartTotal) * 360f).toFloat()
                    val arcColor = if (typeFilter == 0 && item.second == "Thu") ColorIncome else if (typeFilter == 0 && item.second == "Chi") ColorExpense else colors[index % colors.size]
                    drawArc(color = arcColor, startAngle = startAngle, sweepAngle = sweepAngle, useCenter = false, style = Stroke(width = 45f, cap = StrokeCap.Butt))
                    startAngle += sweepAngle
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(centerLabel, color = Color.Gray, fontSize = 12.sp)
                Text(text = formatMoney.format(centerText), color = if (typeFilter == 0 && centerText < 0) ColorExpense else Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        if (typeFilter == 0) {
            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                LegendItem(ColorIncome, "Thu nhập", totalIncome, formatMoney)
                Spacer(modifier = Modifier.width(24.dp))
                LegendItem(ColorExpense, "Chi phí", totalExpense, formatMoney)
            }
        } else {
            val topCategories = chartData.take(3)
            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                topCategories.forEachIndexed { index, item ->
                    LegendItem(colors[index % colors.size], item.first, item.third, formatMoney)
                    if (index < topCategories.size - 1) Spacer(modifier = Modifier.width(12.dp))
                }
            }
        }
    }
}

@Composable
fun LegendItem(color: Color, label: String, amount: Double, formatMoney: NumberFormat) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            Text(label, color = Color.LightGray, fontSize = 11.sp)
            Text(formatMoney.format(amount), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SimpleBarChart(data: List<StatChartPoint>, typeFilter: Int) {
    val maxVal = (data.maxOfOrNull { max(it.income, it.expense) }?.takeIf { it > 0f } ?: 1f) * 1.1f

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        ChartLegend(typeFilter)

        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(
                modifier = Modifier.fillMaxHeight().padding(end = 12.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                Text(formatCompactNumber(maxVal), color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                Text(formatCompactNumber(maxVal / 2f), color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                Text("0", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            }

            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Canvas(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    val gridColor = Color.DarkGray.copy(alpha = 0.5f)
                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)

                    drawLine(color = gridColor, start = Offset(0f, 0f), end = Offset(size.width, 0f), pathEffect = dashEffect)
                    drawLine(color = gridColor, start = Offset(0f, size.height / 2), end = Offset(size.width, size.height / 2), pathEffect = dashEffect)
                    drawLine(color = gridColor, start = Offset(0f, size.height), end = Offset(size.width, size.height), pathEffect = dashEffect)

                    val sectionWidth = size.width / data.size
                    val minBarHeight = 2.dp.toPx()

                    data.forEachIndexed { index, item ->
                        val centerX = (index * sectionWidth) + (sectionWidth / 2)

                        if (typeFilter == 0) {
                            val barW = 8.dp.toPx()
                            val space = 2.dp.toPx()
                            val totalW = (barW * 2) + space
                            val startX = centerX - (totalW / 2)

                            val hIncRaw = (item.income / maxVal) * size.height
                            val hExpRaw = (item.expense / maxVal) * size.height

                            val hInc = if (item.income > 0f) hIncRaw.coerceAtLeast(minBarHeight) else 0f
                            val hExp = if (item.expense > 0f) hExpRaw.coerceAtLeast(minBarHeight) else 0f
                            val corner = CornerRadius(4f, 4f)

                            if (hInc > 0f) drawRoundRect(ColorIncome, topLeft = Offset(startX, size.height - hInc), size = Size(barW, hInc), cornerRadius = corner)
                            if (hExp > 0f) drawRoundRect(ColorExpense, topLeft = Offset(startX + barW + space, size.height - hExp), size = Size(barW, hExp), cornerRadius = corner)
                        } else {
                            val barW = 16.dp.toPx()
                            val startX = centerX - (barW / 2)
                            val value = if (typeFilter == 1) item.income else item.expense
                            val color = if (typeFilter == 1) ColorIncome else ColorExpense

                            val hRaw = (value / maxVal) * size.height
                            val h = if (value > 0f) hRaw.coerceAtLeast(minBarHeight) else 0f

                            if (h > 0f) drawRoundRect(color, topLeft = Offset(startX, size.height - h), size = Size(barW, h), cornerRadius = CornerRadius(8f, 8f))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    data.forEach { point ->
                        Text(text = point.label, color = Color.Gray, fontSize = 10.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
fun SimpleLineChart(data: List<StatChartPoint>, typeFilter: Int) {
    val maxVal = (data.maxOfOrNull { max(it.income, it.expense) }?.takeIf { it > 0f } ?: 1f) * 1.1f

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        ChartLegend(typeFilter)

        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(
                modifier = Modifier.fillMaxHeight().padding(end = 12.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                Text(formatCompactNumber(maxVal), color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                Text(formatCompactNumber(maxVal / 2f), color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                Text("0", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            }

            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Canvas(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    val gridColor = Color.DarkGray.copy(alpha = 0.5f)
                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)

                    drawLine(color = gridColor, start = Offset(0f, 0f), end = Offset(size.width, 0f), pathEffect = dashEffect)
                    drawLine(color = gridColor, start = Offset(0f, size.height / 2), end = Offset(size.width, size.height / 2), pathEffect = dashEffect)
                    drawLine(color = gridColor, start = Offset(0f, size.height), end = Offset(size.width, size.height), pathEffect = dashEffect)

                    val spacing = size.width / (data.size - 1).coerceAtLeast(1)
                    val pathInc = Path()
                    val pathExp = Path()

                    val yPadding = 6.dp.toPx()
                    val drawHeight = size.height - (yPadding * 2)

                    data.forEachIndexed { index, item ->
                        val x = index * spacing
                        val yInc = yPadding + drawHeight - ((item.income / maxVal) * drawHeight)
                        val yExp = yPadding + drawHeight - ((item.expense / maxVal) * drawHeight)

                        if (index == 0) { pathInc.moveTo(x, yInc); pathExp.moveTo(x, yExp) }
                        else { pathInc.lineTo(x, yInc); pathExp.lineTo(x, yExp) }
                    }

                    val strokeInc = if (typeFilter == 0) 4.dp.toPx() else 2.dp.toPx()
                    val strokeExp = 2.dp.toPx()
                    val radiusInc = if (typeFilter == 0) 5.dp.toPx() else 4.dp.toPx()
                    val radiusExp = 3.dp.toPx()

                    if (typeFilter == 0 || typeFilter == 1) {
                        drawPath(path = pathInc, color = ColorIncome, style = Stroke(width = strokeInc, cap = StrokeCap.Round))
                        data.forEachIndexed { index, item ->
                            val x = index * spacing
                            val yInc = yPadding + drawHeight - ((item.income / maxVal) * drawHeight)
                            drawCircle(color = ColorIncome, radius = radiusInc, center = Offset(x, yInc))
                        }
                    }
                    if (typeFilter == 0 || typeFilter == 2) {
                        drawPath(path = pathExp, color = ColorExpense, style = Stroke(width = strokeExp, cap = StrokeCap.Round))
                        data.forEachIndexed { index, item ->
                            val x = index * spacing
                            val yExp = yPadding + drawHeight - ((item.expense / maxVal) * drawHeight)
                            drawCircle(color = ColorExpense, radius = radiusExp, center = Offset(x, yExp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    data.forEach { point ->
                        Text(text = point.label, color = Color.Gray, fontSize = 10.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}