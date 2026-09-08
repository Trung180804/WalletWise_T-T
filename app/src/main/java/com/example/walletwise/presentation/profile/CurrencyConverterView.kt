package com.example.walletwise.presentation.profile
// Quy đổi tiền tệ
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.walletwise.ui.theme.LocalAppTheme
import java.text.DecimalFormat

data class CurrencyItem(val name: String, val code: String, val rateToUsd: Double)

@Composable
fun CurrencyConverterView(onBack: () -> Unit) {
    val isDark = LocalAppTheme.current.value
    val df = remember { DecimalFormat("#,##0.##") }

    val allCurrencies = listOf(
        CurrencyItem("Việt Nam đồng", "VND", 25400.0),
        CurrencyItem("Đô la Mỹ",      "USD", 1.0),
        CurrencyItem("Euro",           "EUR", 0.93),
        CurrencyItem("Yên Nhật",       "JPY", 155.0),
        CurrencyItem("Nhân dân tệ",    "CNY", 7.23)
    )

    var row1 by remember { mutableStateOf(allCurrencies[0]) }
    var row2 by remember { mutableStateOf(allCurrencies[2]) }
    var row3 by remember { mutableStateOf(allCurrencies[1]) }

    var activeIndex  by remember { mutableIntStateOf(0) }
    var rawInput     by remember { mutableStateOf("1000") }
    var pendingOp    by remember { mutableStateOf("") }
    var pendingVal   by remember { mutableDoubleStateOf(0.0) }
    var justComputed by remember { mutableStateOf(false) }

    var showDropdownFor by remember { mutableStateOf<Int?>(null) }

    fun displayInput(): String =
        rawInput.toDoubleOrNull()?.let { df.format(it) } ?: rawInput

    fun convertedValue(target: CurrencyItem): String {
        val amount = rawInput.toDoubleOrNull() ?: 0.0
        val active = when (activeIndex) { 0 -> row1; 1 -> row2; else -> row3 }
        if (active.code == target.code) return displayInput()
        val inUsd = amount / active.rateToUsd
        return df.format(inUsd * target.rateToUsd)
    }

    fun switchActive(newIndex: Int) {
        val target = when (newIndex) { 0 -> row1; 1 -> row2; else -> row3 }
        val converted = convertedValue(target)
        rawInput = converted.replace(",", "")
        activeIndex = newIndex
        pendingOp = ""
        pendingVal = 0.0
        justComputed = false
    }

    fun handleKey(key: String) {
        when (key) {
            "C"  -> {
                rawInput = "0"; pendingOp = ""; pendingVal = 0.0; justComputed = false
            }
            "⌫"  -> {
                if (!justComputed)
                    rawInput = if (rawInput.length > 1) rawInput.dropLast(1) else "0"
            }
            "%"  -> {
                val v = rawInput.toDoubleOrNull() ?: 0.0
                rawInput = df.format(v / 100)
                justComputed = true
            }
            "÷", "×", "-", "+" -> {
                pendingVal = rawInput.toDoubleOrNull() ?: 0.0
                pendingOp  = key
                rawInput   = "0"
                justComputed = false
            }
            "="  -> {
                if (pendingOp.isNotEmpty()) {
                    val curr   = rawInput.toDoubleOrNull() ?: 0.0
                    val result = when (pendingOp) {
                        "÷" -> if (curr != 0.0) pendingVal / curr else 0.0
                        "×" -> pendingVal * curr
                        "-" -> pendingVal - curr
                        "+" -> pendingVal + curr
                        else -> curr
                    }
                    rawInput   = df.format(result).replace(",", "")
                    pendingOp  = ""
                    pendingVal = 0.0
                    justComputed = true
                }
            }
            "."  -> {
                if (!rawInput.contains(".")) {
                    rawInput += "."
                    justComputed = false
                }
            }
            "00" -> {
                if (!justComputed)
                    rawInput = if (rawInput == "0") "0" else rawInput + "00"
            }
            else -> {
                rawInput = if (justComputed || rawInput == "0") key else rawInput + key
                justComputed = false
            }
        }
    }

    val headerLabel = if (pendingOp.isNotEmpty()) "${df.format(pendingVal)} $pendingOp ..." else "Ngoại tệ"

    Column(modifier = Modifier.fillMaxSize()) {
        TopHeader(headerLabel, onBack)

        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .weight(1f)
        ) {
            listOf(row1, row2, row3).forEachIndexed { index, curr ->
                CurrencyRow(
                    name            = curr.name,
                    code            = curr.code,
                    value           = convertedValue(curr),
                    isActive        = activeIndex == index,
                    onClick         = { switchActive(index) },
                    onDropdownClick = { showDropdownFor = index }
                )
                if (index < 2) Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "Tỷ giá được cập nhật theo cấu hình nội bộ.",
                color = Color.Gray, fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))

            val keys = listOf(
                "C", "⌫", "%",  "÷",
                "7", "8",  "9",  "×",
                "4", "5",  "6",  "-",
                "1", "2",  "3",  "+",
                "00","0",  ".",  "="
            )
            LazyVerticalGrid(
                columns             = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement   = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                gridItems(keys) { key ->
                    val isOperator = key in listOf("C", "⌫", "%", "÷", "×", "-", "+")
                    val isEquals   = key == "="
                    val bgColor    = when {
                        isEquals   -> Color(0xFFFF7043)
                        isOperator -> if (isDark) Color(0xFF3A2C2C) else Color(0xFFFFE0D6)
                        else       -> MaterialTheme.colorScheme.surfaceVariant
                    }
                    val fgColor = when {
                        isEquals   -> Color.White
                        isOperator -> Color(0xFFFF7043)
                        else       -> MaterialTheme.colorScheme.onSurface
                    }

                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(bgColor)
                            .clickable { handleKey(key) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(key, color = fgColor, fontSize = 26.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }

    if (showDropdownFor != null) {
        Dialog(onDismissRequest = { showDropdownFor = null }) {
            Card(
                shape  = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Chọn tiền tệ", color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    ThemedDivider()
                    Spacer(Modifier.height(8.dp))
                    allCurrencies.forEach { curr ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    when (showDropdownFor) {
                                        0 -> row1 = curr
                                        1 -> row2 = curr
                                        2 -> row3 = curr
                                    }
                                    showDropdownFor = null
                                }
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(curr.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                            Text(curr.code, color = Color.Gray, fontSize = 13.sp)
                        }
                        if (curr != allCurrencies.last()) ThemedDivider()
                    }
                }
            }
        }
    }
}

@Composable
fun CurrencyRow(
    name: String, code: String, value: String,
    isActive: Boolean, onClick: () -> Unit, onDropdownClick: () -> Unit
) {
    val isDark = LocalAppTheme.current.value
    val textC  = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val rowBg  = if (isActive)
        (if (isDark) Color(0xFF2A2500) else Color(0xFFFFF9E0))
    else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(rowBg)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onDropdownClick() }
            ) {
                Text(
                    name, color = textC, fontSize = 16.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                )
                Icon(Icons.Default.ArrowDropDown, null, tint = textC, modifier = Modifier.size(20.dp))
            }
            Text(code, color = Color.Gray, fontSize = 13.sp)
        }
        Text(value, color = textC, fontSize = 22.sp, fontWeight = FontWeight.Medium)
    }
}
