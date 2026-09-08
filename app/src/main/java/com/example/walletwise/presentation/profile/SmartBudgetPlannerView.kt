package com.example.walletwise.presentation.profile

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.walletwise.presentation.home.TransactionViewModel
import java.text.DecimalFormat
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartBudgetPlannerView(
    viewModel: TransactionViewModel,
    onBack: () -> Unit
) {
    val budgetPlan by viewModel.budgetPlan.collectAsState()
    var showSetupDialog by remember { mutableStateOf(false) }
    var aiInsightText by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val df = remember { DecimalFormat("#,##0") }

    LaunchedEffect(Unit) {
        viewModel.fetchBudgetPlan()
    }

    val now = remember { LocalDate.now(ZoneId.systemDefault()) }
    val daysInMonth = now.lengthOfMonth()
    val remainingDays = (daysInMonth - now.dayOfMonth + 1).coerceAtLeast(1)

    Column(modifier = Modifier.fillMaxSize()) {
        TopHeader("Kế hoạch & Phân bổ", onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(16.dp))

            // HEADER CARD: TỔNG NGÂN SÁCH THÁNG
            val plan = budgetPlan
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(3.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Ngân sách Tháng ${now.monthValue}/${now.year}", color = Color.Gray, fontSize = 13.sp)
                            Spacer(Modifier.height(4.dp))
                            if (plan != null) {
                                Text("${df.format(plan.totalBudget)} đ", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            } else {
                                Text("Chưa thiết lập", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                            }
                        }

                        IconButton(
                            onClick = { showSetupDialog = true },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                        ) {
                            Icon(if (plan != null) Icons.Default.Edit else Icons.Default.Add, contentDescription = "Thiết lập", tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    if (plan != null) {
                        Spacer(Modifier.height(12.dp))
                        val ruleName = if (plan.ruleType == "JARS") "Quy tắc 6 Chiếc Lọ (JARS)" else "Quy tắc 50 / 30 / 20"
                        SuggestionChip(
                            onClick = { showSetupDialog = true },
                            label = { Text(ruleName, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    } else {
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { showSetupDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PieChart, null, tint = Color.Black)
                            Spacer(Modifier.width(8.dp))
                            Text("Phân bổ ngân sách ngay", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // SAFE-TO-SPEND (HẠN MỨC AN TOÀN MỖI NGÀY)
            if (plan != null) {
                val totalSpent = plan.needsSpent + plan.wantsSpent + plan.savingsSpent
                val totalRemaining = (plan.totalBudget - totalSpent).coerceAtLeast(0.0)
                val dailySafeLimit = totalRemaining / remainingDays

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFFFFD54F), Color(0xFFFFB300))
                                ),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(20.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Security, null, tint = Color.Black, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Số tiền an toàn hôm nay (Safe-to-Spend)", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "${df.format(dailySafeLimit)} đ / ngày",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                            Text(
                                "Còn dư ${df.format(totalRemaining)} đ cho ${remainingDays} ngày còn lại",
                                color = Color.Black.copy(alpha = 0.8f),
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )

                            Spacer(Modifier.height(12.dp))
                            HorizontalDivider(color = Color.Black.copy(alpha = 0.2f))
                            Spacer(Modifier.height(12.dp))

                            Text("Gợi ý mức tiêu chuẩn theo bữa:", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                MealChip("Sáng", dailySafeLimit * 0.20, df)
                                MealChip("Trưa", dailySafeLimit * 0.35, df)
                                MealChip("Tối", dailySafeLimit * 0.45, df)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // BAR HIỂN THỊ TỶ LỆ PHÂN BỔ 3 NHÓM
                Text(
                    if (plan.ruleType == "JARS") "Phân bổ thông minh (6 Chiếc Lọ JARS)" else "Phân bổ thông minh (Quy tắc 50-30-20)",
                    fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(8.dp))

                val needsWeight = if (plan.ruleType == "JARS") 0.55f else 0.50f
                val wantsWeight = if (plan.ruleType == "JARS") 0.10f else 0.30f
                val savingsWeight = if (plan.ruleType == "JARS") 0.35f else 0.20f

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(CircleShape)
                ) {
                    Box(modifier = Modifier.weight(needsWeight).fillMaxHeight().background(Color(0xFF2196F3)))
                    Box(modifier = Modifier.weight(wantsWeight).fillMaxHeight().background(Color(0xFFE91E63)))
                    Box(modifier = Modifier.weight(savingsWeight).fillMaxHeight().background(Color(0xFF4CAF50)))
                }

                Spacer(Modifier.height(16.dp))

                // CẢNH BÁO THÔNG MINH ĐÈN GIAO THÔNG (PROGRESS BARS)
                Text("Tiến độ ngân sách chi tiết", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(12.dp))

                val isJars = plan.ruleType == "JARS"
                BudgetGroupCard(
                    title = if (isJars) "🔵 Nhu cầu thiết yếu (55%)" else "🔵 Nhu cầu thiết yếu (50%)",
                    description = "Tiền nhà, đi chợ, điện nước, y tế",
                    limit = plan.needsLimit,
                    spent = plan.needsSpent,
                    df = df
                )

                Spacer(Modifier.height(12.dp))

                BudgetGroupCard(
                    title = if (isJars) "🟡 Chi tiêu Hưởng thụ (10%)" else "🟡 Chi tiêu cá nhân (30%)",
                    description = "Ăn ngoài, trà sữa, mua sắm, xem phim",
                    limit = plan.wantsLimit,
                    spent = plan.wantsSpent,
                    df = df
                )

                Spacer(Modifier.height(12.dp))

                BudgetGroupCard(
                    title = if (isJars) "🟢 Tiết kiệm & Đầu tư (35%)" else "🟢 Tiết kiệm & Đầu tư (20%)",
                    description = "Quỹ khẩn cấp, tiết kiệm, đầu tư",
                    limit = plan.savingsLimit,
                    spent = plan.savingsSpent,
                    df = df
                )

                Spacer(Modifier.height(20.dp))

                // SMART AI INSIGHTS
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Cố vấn Tài chính AI", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Spacer(Modifier.height(12.dp))

                        val currentInsight = aiInsightText ?: run {
                            val wantsPercent = if (plan.wantsLimit > 0) (plan.wantsSpent / plan.wantsLimit) * 100 else 0.0
                            if (wantsPercent > 80) {
                                "Cảnh báo: Bạn đã dùng ${wantsPercent.toInt()}% ngân sách Cá nhân! Hãy cân nhắc giảm ăn ngoài trong $remainingDays ngày tới để đảm bảo kế hoạch."
                            } else {
                                "Tuyệt vời! Bạn đang giữ đúng ngân sách. Tiết kiệm thêm ${df.format(dailySafeLimit * 0.2)}đ mỗi ngày sẽ giúp bạn đạt mục tiêu sớm hơn!"
                            }
                        }

                        Text(currentInsight, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp)

                        Spacer(Modifier.height(12.dp))

                        TextButton(
                            onClick = {
                                aiInsightText = "Phân tích tuần qua: Chi tiêu cá nhân đang ở mức ổn định. Khuyên bạn duy trì tự nấu ăn để dư quỹ tiết kiệm."
                                Toast.makeText(context, "Đã cập nhật phân tích AI!", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text("Cập nhật phân tích mới", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(100.dp))
        }
    }

    // DIALOG THIẾT LẬP NGÂN SÁCH
    if (showSetupDialog) {
        var inputAmount by remember { mutableStateOf(budgetPlan?.totalBudget?.toLong()?.toString() ?: "10000000") }
        var selectedRule by remember { mutableStateOf(budgetPlan?.ruleType ?: "50_30_20") }

        val amountVal = inputAmount.toDoubleOrNull() ?: 0.0
        val (needs, wants, savings) = if (selectedRule == "JARS") {
            Triple(amountVal * 0.55, amountVal * 0.10, amountVal * 0.35)
        } else {
            Triple(amountVal * 0.50, amountVal * 0.30, amountVal * 0.20)
        }

        Dialog(onDismissRequest = { showSetupDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Thiết lập ngân sách tháng", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = inputAmount,
                        onValueChange = { if (it.all { c -> c.isDigit() }) inputAmount = it },
                        label = { Text("Tổng ngân sách dự định (VNĐ)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(Modifier.height(16.dp))

                    Text("Chọn quy tắc phân bổ", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selectedRule == "50_30_20",
                            onClick = { selectedRule = "50_30_20" },
                            label = { Text("50/30/20") }
                        )
                        FilterChip(
                            selected = selectedRule == "JARS",
                            onClick = { selectedRule = "JARS" },
                            label = { Text("6 Chiếc Lọ (JARS)") }
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text("Dự kiến chia ra:", fontSize = 12.sp, color = Color.Gray)
                        Spacer(Modifier.height(6.dp))
                        Text("🔵 Thiết yếu: ${df.format(needs)} đ", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text("🟡 Cá nhân: ${df.format(wants)} đ", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text("🟢 Tiết kiệm: ${df.format(savings)} đ", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }

                    Spacer(Modifier.height(24.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(
                            onClick = { showSetupDialog = false },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Hủy", color = Color.Gray)
                        }

                        Button(
                            onClick = {
                                if (amountVal > 0) {
                                    viewModel.saveBudgetPlan(amountVal, selectedRule)
                                    Toast.makeText(context, "Đã lưu kế hoạch ngân sách!", Toast.LENGTH_SHORT).show()
                                    showSetupDialog = false
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Lưu", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MealChip(title: String, amount: Double, df: DecimalFormat) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.1f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text("~${df.format(amount)}đ", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun BudgetGroupCard(
    title: String,
    description: String,
    limit: Double,
    spent: Double,
    df: DecimalFormat
) {
    val progress = if (limit > 0) (spent / limit).coerceIn(0.0, 1.0).toFloat() else 0f
    val remaining = limit - spent

    val statusColor = when {
        spent > limit -> Color(0xFFFA3B70) // Đỏ
        spent >= limit * 0.8 -> Color(0xFFFFB300) // Vàng
        else -> Color(0xFF4CAF50) // Xanh
    }

    val statusText = when {
        spent > limit -> "🔴 Đã vượt ${df.format(spent - limit)} đ!"
        spent >= limit * 0.8 -> "🟡 Cảnh báo: Đã tiêu ${((spent/limit)*100).toInt()}%!"
        else -> "🟢 An toàn: Còn ${df.format(remaining)} đ"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(description, color = Color.Gray, fontSize = 12.sp)
                }
                Text("${df.format(spent)} / ${df.format(limit)} đ", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }

            Spacer(Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = statusColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            Text(statusText, color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}
