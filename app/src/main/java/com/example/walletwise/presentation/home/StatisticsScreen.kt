package com.example.walletwise.presentation.home

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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

    val bgColor = Color(0xFFF5F7FA) // Nền nhạt
    val cardColor = Color.White      // Thẻ trắng
    val textColor = Color(0xFF2D3436) // Chữ xám đậm
    val primaryBlue = Color(0xFF2196F3) // Xanh chủ đạo
    val grayText = Color(0xFF636E72)

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

    val totalIncome = timeFilteredTx.filter { it.type.trim().equals("Thu", ignoreCase = true) }.sumOf { it.amount }
    val totalExpense = timeFilteredTx.filter { it.type.trim().equals("Chi", ignoreCase = true) }.sumOf { it.amount }
    val netCashFlow = totalIncome - totalExpense // Đổi tên biến từ balance thành netCashFlow để rõ nghĩa

    val currentTransactions = timeFilteredTx.filter {
        when (selectedTxType) {
            1 -> it.type.trim().equals("Thu", ignoreCase = true)
            2 -> it.type.trim().equals("Chi", ignoreCase = true)
            else -> true
        }
    }

    val categoryTotals = currentTransactions
        .groupBy { it.category to it.type }
        .map { (key, list) -> Triple(key.first, key.second, list.sumOf { it.amount }) }
        .sortedByDescending { it.third }

    val dynamicChartData = remember(selectedTimeTab, startTime, endTime, transactions) {
        val data = mutableListOf<StatChartPoint>()
        val cal = Calendar.getInstance().apply { timeInMillis = startTime }

        when (selectedTimeTab) {
            0 -> {
                val sdf = SimpleDateFormat("EE", Locale("vi", "VN"))
                for (i in 0..6) {
                    val dayStart = cal.timeInMillis
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                    val dayEnd = cal.timeInMillis - 1

                    val txs = transactions.filter { it.timestamp in dayStart..dayEnd }
                    val inc = txs.filter { it.type.trim().equals("Thu", ignoreCase = true) }.sumOf { it.amount }.toFloat()
                    val exp = txs.filter { it.type.trim().equals("Chi", ignoreCase = true) }.sumOf { it.amount }.toFloat()

                    var label = sdf.format(java.util.Date(dayStart)).replace("Thứ ", "T")
                    if (label.lowercase() == "chủ nhật") label = "CN"
                    data.add(StatChartPoint(label, inc, exp))
                }
            }
            1 -> {
                var weekNum = 1
                while (cal.timeInMillis <= endTime) {
                    val segmentStart = cal.timeInMillis
                    cal.add(Calendar.DAY_OF_YEAR, 7)
                    var segmentEnd = cal.timeInMillis - 1
                    if (segmentEnd > endTime) segmentEnd = endTime

                    val txs = transactions.filter { it.timestamp in segmentStart..segmentEnd }
                    val inc = txs.filter { it.type.trim().equals("Thu", ignoreCase = true) }.sumOf { it.amount }.toFloat()
                    val exp = txs.filter { it.type.trim().equals("Chi", ignoreCase = true) }.sumOf { it.amount }.toFloat()

                    data.add(StatChartPoint("Tuần $weekNum", inc, exp))
                    weekNum++
                }
            }
            2 -> {
                for (i in 1..12) {
                    val monthStart = cal.timeInMillis
                    cal.add(Calendar.MONTH, 1)
                    val monthEnd = cal.timeInMillis - 1

                    val txs = transactions.filter { it.timestamp in monthStart..monthEnd }
                    val inc = txs.filter { it.type.trim().equals("Thu", ignoreCase = true) }.sumOf { it.amount }.toFloat()
                    val exp = txs.filter { it.type.trim().equals("Chi", ignoreCase = true) }.sumOf { it.amount }.toFloat()

                    data.add(StatChartPoint("T$i", inc, exp))
                }
            }
        }
        data
    }

    val chartTitle = when (selectedTimeTab) {
        0 -> "Thống kê các ngày trong tuần"
        1 -> "Thống kê các tuần trong tháng"
        else -> "Thống kê các tháng trong năm"
    }

    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            containerColor = Color.White,
            title = { Text("Chọn thời gian", color = textColor, fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(25) { index ->
                        val offset = -index
                        val label = getTimeLabel(selectedTimeTab, offset)
                        Text(
                            text = label,
                            color = if (offset == timeOffset) primaryBlue else textColor,
                            modifier = Modifier.fillMaxWidth().clickable { timeOffset = offset; showTimePicker = false }.padding(12.dp)
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showTimePicker = false }) { Text("Đóng", color = primaryBlue) } }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(bgColor)) {
        TabRow(
            selectedTabIndex = mainTab,
            containerColor = Color.White,
            contentColor = primaryBlue,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(tabPositions[mainTab]), color = primaryBlue)
            }
        ) {
            listOf("Tổng quan", "Biểu đồ").forEachIndexed { index, title ->
                Tab(
                    selected = mainTab == index, onClick = { mainTab = index },
                    text = { Text(title, fontWeight = FontWeight.Bold, color = if (mainTab == index) primaryBlue else grayText) }
                )
            }
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(modifier = Modifier.fillMaxWidth().border(1.dp, Color.LightGray, RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp)).background(Color.White), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf("Tuần", "Tháng", "Năm").forEachIndexed { index, title ->
                    Box(
                        modifier = Modifier.weight(1f).background(if (selectedTimeTab == index) primaryBlue else Color.Transparent).clickable { selectedTimeTab = index }.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) { Text(text = title, color = if (selectedTimeTab == index) Color.White else grayText, fontWeight = if (selectedTimeTab == index) FontWeight.Bold else FontWeight.Normal) }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { timeOffset -= 1 }) { Icon(Icons.Default.KeyboardArrowLeft, contentDescription = null, tint = textColor) }
                    Row(modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { showTimePicker = true }.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(text = displayLabel, color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = grayText)
                    }
                    IconButton(onClick = { timeOffset += 1 }, enabled = timeOffset < 0) { Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = if (timeOffset < 0) textColor else Color.LightGray) }
                }
                Box {
                    Surface(color = Color.White, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Color.LightGray), onClick = { expandedDropdown = true }) {
                        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(txTypes[selectedTxType], color = textColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = textColor)
                        }
                    }
                    DropdownMenu(expanded = expandedDropdown, onDismissRequest = { expandedDropdown = false }, modifier = Modifier.background(Color.White)) {
                        txTypes.forEachIndexed { index, title ->
                            DropdownMenuItem(text = { Text(title, color = textColor) }, onClick = { selectedTxType = index; expandedDropdown = false })
                        }
                    }
                }
            }
        }
        Divider(color = Color.LightGray, thickness = 0.5.dp)

        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            if (mainTab == 0) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = cardColor), elevation = CardDefaults.cardElevation(2.dp)) {
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
                            Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color.LightGray)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                // SỬA TẠI ĐÂY: Thay "Số dư" thành "Chênh lệch" để sửa lỗi logic gây hiểu nhầm
                                Text("Chênh lệch", color = textColor, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text(formatMoney.format(netCashFlow), color = if (netCashFlow >= 0) primaryBlue else ColorExpense, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Chi tiết theo danh mục", color = textColor, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }

                if (categoryTotals.isEmpty()) {
                    item { Text("Chưa có dữ liệu", color = textColor, modifier = Modifier.padding(top = 16.dp)) }
                } else {
                    items(categoryTotals.size) { index ->
                        CategoryProgressItem(categoryTotals[index], totalIncome, totalExpense, formatMoney, index, textColor)
                    }
                }
            } else {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(if (selectedTxType == 0) "Tỷ lệ Thu / Chi" else "Chi tiết theo danh mục", color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = cardColor)) {
                        // Truyền netCashFlow xuống thay vì balance
                        DonutChart(categoryTotals, selectedTxType, totalIncome, totalExpense, netCashFlow, formatMoney)
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Text(chartTitle, color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    Card(modifier = Modifier.fillMaxWidth().height(220.dp), colors = CardDefaults.cardColors(containerColor = cardColor)) {
                        SimpleBarChart(dynamicChartData, selectedTxType)
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Xu hướng (Đường)", color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
fun CategoryProgressItem(item: Triple<String, String, Double>, totalIncome: Double, totalExpense: Double, formatMoney: NumberFormat, index: Int, textColor: Color) {
    val isIncome = item.second.trim().equals("Thu", ignoreCase = true)
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
                Text("${item.first}  ${String.format("%.1f%%", percentage * 100)}", color = textColor, fontSize = 14.sp)
                Text("$sign${formatMoney.format(item.third)}", color = amountColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(progress = { percentage }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)), color = barColor, trackColor = Color(0xFFEEEEEE))
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

    // SỬA TẠI ĐÂY: Đổi nhãn tâm vòng tròn từ "Số dư" thành "Chênh lệch"
    val centerLabel = if (typeFilter == 0) "Chênh lệch" else "Tổng cộng"

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
                    val arcColor = if (typeFilter == 0 && item.second.trim().equals("Thu", ignoreCase = true)) {
                        ColorIncome
                    } else if (typeFilter == 0 && item.second.trim().equals("Chi", ignoreCase = true)) {
                        ColorExpense
                    } else {
                        colors[index % colors.size]
                    }
                    drawArc(color = arcColor, startAngle = startAngle, sweepAngle = sweepAngle, useCenter = false, style = Stroke(width = 45f, cap = StrokeCap.Butt))
                    startAngle += sweepAngle
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(centerLabel, color = Color.Gray, fontSize = 12.sp)
                // Đổi màu text thành Đỏ (ColorExpense) nếu chênh lệch âm (Chi > Thu)
                Text(text = formatMoney.format(centerText), color = if (typeFilter == 0 && centerText < 0) ColorExpense else Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
            Text(formatMoney.format(amount), color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                    val gridColor = Color.LightGray
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