package com.example.walletwise.presentation.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.walletwise.domain.model.BudgetRule
import com.example.walletwise.domain.model.FinancialMethods
import com.example.walletwise.domain.model.FinancialMethod
import com.example.walletwise.domain.model.FinancialAllocationPlan
import com.example.walletwise.domain.service.BudgetGroupProgress
import com.example.walletwise.domain.service.BudgetProgressStatus
import com.example.walletwise.domain.validation.BudgetValidationError
import com.example.walletwise.presentation.profile.TopHeader
import com.example.walletwise.presentation.profile.formatCurrencyNumber
import com.example.walletwise.shared.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.round
import com.example.walletwise.domain.service.MoneyInput
import com.example.walletwise.domain.service.FinancialCategoryMapping
import com.example.walletwise.domain.service.formatFinancialPercent
import com.example.walletwise.presentation.transaction.GroupedMoneyTransformation
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.heightIn

@Composable
fun SmartBudgetContent(
    state: SmartBudgetUiState,
    onBack: () -> Unit,
    onOpenSetup: () -> Unit,
    onCancelSetup: () -> Unit,
    onAmountChanged: (String) -> Unit,
    onRuleSelected: (BudgetRule) -> Unit,
    onSave: () -> Unit,
    onRefreshInsight: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onResetRatios: () -> Unit,
    onOpenMapping: () -> Unit = {},
    onCloseMapping: () -> Unit = {},
    onCategoryMapped: (String, String) -> Unit = { _, _ -> },
    onRetryMapping: () -> Unit = {}
) {
    val moneyInput = com.example.walletwise.presentation.transaction.rememberMoneyInput(state.inputAmount, onAmountChanged)
    Column(modifier = Modifier.fillMaxSize()) {
        TopHeader(stringResource(Res.string.budget_title), onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(16.dp))
            if (!state.showSetupDialog) MonthSelector(state.monthKey, onPreviousMonth, onNextMonth)
            when {
                state.isLoading -> Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.repositoryError != null && !state.hasPlan -> Text(
                    state.repositoryError.message,
                    color = MaterialTheme.colorScheme.error
                )
                !state.hasPlan -> Column {
                    Text("Thu nhập dự kiến của bạn trong tháng này là bao nhiêu?", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(moneyInput.first, moneyInput.second, label = { Text("Thu nhập dự kiến") },
                        suffix = { Text("đ") }, visualTransformation = GroupedMoneyTransformation,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    FinancialMethodSelection { rule -> onOpenSetup(); onRuleSelected(rule) }
                }
                else -> FinancialPlanResult(
                    state,
                    onOpenSetup,
                    onPreviousMonth,
                    onNextMonth,
                    onResetRatios
                )
            }
            if (state.hasPlan) {
                if (state.mappingLoading) Text("Đang tải phân nhóm danh mục…")
                state.mappingError?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onRetryMapping) { Text("Thử lại phân nhóm") } }
                TextButton(onOpenMapping) { Text("Xem / sửa phân nhóm danh mục") }
            }
            Spacer(Modifier.height(100.dp))
        }
    }

    if (state.showSetupDialog) {
        FinancialSetupDialog(
            state = state,
            onDismiss = onCancelSetup,
            onAmountChanged = onAmountChanged,
            onRuleSelected = onRuleSelected,
            onSave = onSave,
            onPreviousMonth = onPreviousMonth,
            onNextMonth = onNextMonth
        )
    }
    if (state.showMappingDialog) FinancialMappingDialog(state, onCloseMapping, onCategoryMapped)
}

@Composable
private fun FinancialMethodSelection(onSelect: (BudgetRule) -> Unit) {
    Text("Chọn phương pháp phù hợp", fontSize = 20.sp, fontWeight = FontWeight.Bold)
    Text("Bạn có thể đổi phương pháp bất cứ lúc nào.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(16.dp))
    FinancialMethods.All.forEach { method ->
        FinancialMethodCard(method) { onSelect(method.rule) }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun FinancialMethodCard(method: FinancialMethod, onSelect: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(method.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(4.dp))
            Text(method.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            method.buckets.forEach { Text("${it.percent}%  ${it.name}", fontWeight = FontWeight.Medium) }
            Spacer(Modifier.height(8.dp))
            Text(method.example, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onSelect, modifier = Modifier.fillMaxWidth()) { Text("Sử dụng phương pháp này") }
        }
    }
}

@Composable
private fun FinancialPlanResult(
    state: SmartBudgetUiState,
    onEdit: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onReset: () -> Unit
) {
    Spacer(Modifier.height(8.dp))
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp)) {
            Text(state.allocationPlan.method.name, fontWeight = FontWeight.Bold, fontSize = 19.sp)
            Text("Tổng thu nhập: ${formatBudgetMoney(state.totalBudget)}", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Đã chi trong tháng: ${formatBudgetMoney(state.totalSpent)}")
            Text("Còn lại: ${formatBudgetMoney(state.totalRemaining)}")
        }
    }
    Spacer(Modifier.height(16.dp))
    Text("Phân bổ • ${state.allocationPlan.totalPercent}%", style = MaterialTheme.typography.titleMedium)
    Text("Phần màu: đã dùng • Phần nhạt: còn lại", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    state.liveAllocation.buckets.forEach { usage ->
        val palette = financialBucketPalette(usage.allocation.bucket.key)
        Card(Modifier.fillMaxWidth().padding(vertical = 6.dp), shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = palette.container),
            border = androidx.compose.foundation.BorderStroke(1.dp, palette.accent.copy(alpha = 0.3f))) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(palette.icon, null, tint = palette.accent)
                    Text("${usage.allocation.bucket.name} • ${usage.allocation.bucket.percent}%", fontWeight = FontWeight.Bold)
                }
                Text("Được cấp: ${formatBudgetMoney(usage.allocation.amount.toDouble())}")
                Text("Đã chi: ${formatBudgetMoney(usage.spent.toDouble())}")
                Text("Còn lại: ${formatBudgetMoney(usage.remaining.toDouble())}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("Đã dùng của quỹ: ${formatFinancialPercent(usage.usedPercent)}")
                Text("Còn lại của quỹ: ${formatFinancialPercent(usage.remainingPercent)}", style = MaterialTheme.typography.bodyMedium)
                Text("Còn lại so với tổng thu nhập: ${formatFinancialPercent(usage.remainingIncomePercent(state.allocationPlan.income))}", style = MaterialTheme.typography.bodySmall)
                BudgetUsageTube(usage, fillColor = palette.accent)
                Text(when (usage.status) { BudgetProgressStatus.SAFE -> "Bình thường"; BudgetProgressStatus.WARNING -> "Sắp hết quỹ"; BudgetProgressStatus.EXCEEDED -> "Vượt ngân sách" },
                    color = if (usage.status == BudgetProgressStatus.SAFE) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onEdit, modifier = Modifier.weight(1f)) { Text("Sửa kế hoạch") }
        TextButton(onClick = onReset, modifier = Modifier.weight(1f)) { Text("Đặt lại tỷ lệ") }
    }
    TextButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text("Đổi phương pháp") }
    Text(
        "Các con số là gợi ý quản lý tiền, không phải tư vấn đầu tư bắt buộc.",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun AllocationRows(plan: FinancialAllocationPlan) {
    Text("Phân bổ đề xuất • ${plan.totalPercent}%", fontWeight = FontWeight.Bold, fontSize = 17.sp)
    Spacer(Modifier.height(8.dp))
    plan.allocations.forEach { allocation ->
        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(Modifier.padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(allocation.bucket.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    Text("${allocation.bucket.percent}%", fontWeight = FontWeight.Bold)
                }
                Text(formatBudgetMoney(allocation.amount.toDouble()), fontWeight = FontWeight.Bold)
                LinearProgressIndicator(
                    progress = { allocation.bucket.percent / 100f },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).semantics {
                        contentDescription = "${allocation.bucket.name}: ${allocation.bucket.percent}%"
                    }
                )
                Text(allocation.bucket.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun FinancialSetupDialog(
    state: SmartBudgetUiState,
    onDismiss: () -> Unit,
    onAmountChanged: (String) -> Unit,
    onRuleSelected: (BudgetRule) -> Unit,
    onSave: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    val moneyInput = com.example.walletwise.presentation.transaction.rememberMoneyInput(state.inputAmount, onAmountChanged)
    Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(18.dp).verticalScroll(rememberScrollState())) {
                Text("Lập kế hoạch tháng ${state.monthKey}", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                MonthSelector(state.monthKey, onPreviousMonth, onNextMonth, enabled = !state.isSaving)
                Text("1. Chọn phương pháp  •  2. Nhập thu nhập  •  3. Xem phân bổ", fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        enabled = !state.isSaving,
                        selected = state.selectedRule == BudgetRule.FIFTY_THIRTY_TWENTY,
                        onClick = { onRuleSelected(BudgetRule.FIFTY_THIRTY_TWENTY) },
                        label = { Text("50/30/20") }
                    )
                    FilterChip(
                        enabled = !state.isSaving,
                        selected = state.selectedRule == BudgetRule.JARS,
                        onClick = { onRuleSelected(BudgetRule.JARS) },
                        label = { Text("6 chiếc lọ") }
                    )
                }
                OutlinedTextField(
                    value = moneyInput.first,
                visualTransformation = GroupedMoneyTransformation,
                    onValueChange = moneyInput.second,
                    label = { Text("Thu nhập dự kiến") },
                    singleLine = true,
                    enabled = !state.isSaving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                state.validationError?.let { Text(validationMessage(it), color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.height(12.dp))
                AllocationRows(state.allocationPlan)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !state.isSaving) { Text("Hủy") }
                    Button(onClick = onSave, enabled = !state.isSaving) { Text(if (state.isSaving) "Đang lưu…" else "Lưu kế hoạch") }
                }
            }
        }
    }
}

@Composable
private fun MonthSelector(monthKey: String, onPrevious: () -> Unit, onNext: () -> Unit, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onPrevious, enabled = enabled, modifier = Modifier.semantics { contentDescription = "Tháng trước" }) { Text("‹") }
        Text(monthKey, fontWeight = FontWeight.Bold)
        TextButton(onClick = onNext, enabled = enabled, modifier = Modifier.semantics { contentDescription = "Tháng sau" }) { Text("›") }
    }
}

@Composable
private fun BudgetHeaderCard(state: SmartBudgetUiState, onOpenSetup: () -> Unit) {
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
                    Text(
                        stringResource(
                            Res.string.budget_month,
                            state.currentDate.month,
                            state.currentDate.year
                        ),
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    when {
                        state.isLoading && !state.hasPlan -> CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 3.dp
                        )
                        state.hasPlan -> Text(
                            "${formatBudgetMoney(state.totalBudget)} đ",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        else -> Text(
                            stringResource(Res.string.budget_not_set),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray
                        )
                    }
                }
                IconButton(
                    onClick = onOpenSetup,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                ) {
                    Icon(
                        if (state.hasPlan) Icons.Default.Edit else Icons.Default.Add,
                        stringResource(Res.string.budget_setup),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (state.hasPlan) {
                Spacer(Modifier.height(12.dp))
                val isJars = state.plan?.ruleType == BudgetRule.JARS.wireValue
                SuggestionChip(
                    onClick = onOpenSetup,
                    label = {
                        Text(
                            stringResource(
                                if (isJars) Res.string.budget_rule_jars_name
                                else Res.string.budget_rule_standard_name
                            ),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            } else {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onOpenSetup,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PieChart, null, tint = Color.Black)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(Res.string.budget_allocate_now),
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun SafeToSpendCard(state: SmartBudgetUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(listOf(Color(0xFFFFD54F), Color(0xFFFFB300))),
                    RoundedCornerShape(20.dp)
                )
                .padding(20.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, null, tint = Color.Black, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(Res.string.budget_safe_today),
                        color = Color.Black,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(Res.string.budget_per_day, formatBudgetMoney(state.dailySafeLimit)),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Text(
                    stringResource(
                        Res.string.budget_remaining_days,
                        formatBudgetMoney(state.totalRemaining),
                        state.remainingDays
                    ),
                    color = Color.Black.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = Color.Black.copy(alpha = 0.2f))
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(Res.string.budget_meal_suggestion),
                    color = Color.Black,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MealChip(Res.string.budget_breakfast, state.breakfastLimit)
                    MealChip(Res.string.budget_lunch, state.lunchLimit)
                    MealChip(Res.string.budget_dinner, state.dinnerLimit)
                }
            }
        }
    }
}

@Composable
private fun MealChip(title: StringResource, amount: Double) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.1f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(title), color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(
                stringResource(Res.string.budget_meal_amount, formatBudgetMoney(amount)),
                color = Color.Black,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun AllocationSection(state: SmartBudgetUiState) {
    val isJars = state.plan?.ruleType == BudgetRule.JARS.wireValue
    Text(
        stringResource(
            if (isJars) Res.string.budget_allocation_jars else Res.string.budget_allocation_standard
        ),
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        color = MaterialTheme.colorScheme.onBackground
    )
    Spacer(Modifier.height(8.dp))
    Row(modifier = Modifier.fillMaxWidth().height(12.dp).clip(CircleShape)) {
        Box(
            Modifier.weight(if (isJars) 0.55f else 0.50f).fillMaxHeight().background(Color(0xFF2196F3))
        )
        Box(
            Modifier.weight(if (isJars) 0.10f else 0.30f).fillMaxHeight().background(Color(0xFFE91E63))
        )
        Box(
            Modifier.weight(if (isJars) 0.35f else 0.20f).fillMaxHeight().background(Color(0xFF4CAF50))
        )
    }
}

@Composable
private fun BudgetGroupCard(
    title: StringResource,
    description: StringResource,
    progress: BudgetGroupProgress
) {
    val statusColor = when (progress.status) {
        BudgetProgressStatus.EXCEEDED -> Color(0xFFFA3B70)
        BudgetProgressStatus.WARNING -> Color(0xFFFFB300)
        BudgetProgressStatus.SAFE -> Color(0xFF4CAF50)
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
                    Text(
                        stringResource(title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(stringResource(description), color = Color.Gray, fontSize = 12.sp)
                }
                Text(
                    stringResource(
                        Res.string.budget_spent_limit,
                        formatBudgetMoney(progress.spent),
                        formatBudgetMoney(progress.limit)
                    ),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { progress.fraction },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = statusColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            val statusText = when (progress.status) {
                BudgetProgressStatus.EXCEEDED -> stringResource(
                    Res.string.budget_status_exceeded,
                    formatBudgetMoney(-progress.remaining)
                )
                BudgetProgressStatus.WARNING -> stringResource(
                    Res.string.budget_status_warning,
                    progress.percentUsed
                )
                BudgetProgressStatus.SAFE -> stringResource(
                    Res.string.budget_status_safe,
                    formatBudgetMoney(progress.remaining)
                )
            }
            Text(statusText, color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun InsightCard(insight: BudgetInsight, onRefresh: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(Res.string.budget_insight_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(12.dp))
            val text = when (insight.kind) {
                BudgetInsightKind.WANTS_WARNING -> stringResource(
                    Res.string.budget_insight_warning,
                    insight.wantsPercent,
                    insight.remainingDays
                )
                BudgetInsightKind.DEFAULT -> stringResource(
                    Res.string.budget_insight_default,
                    formatBudgetMoney(insight.suggestedDailySaving)
                )
                BudgetInsightKind.REFRESHED -> stringResource(Res.string.budget_insight_refreshed)
            }
            Text(
                text,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onRefresh) {
                Text(
                    stringResource(Res.string.budget_refresh_insight),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun BudgetSetupDialog(
    state: SmartBudgetUiState,
    onDismiss: () -> Unit,
    onAmountChanged: (String) -> Unit,
    onRuleSelected: (BudgetRule) -> Unit,
    onSave: () -> Unit
) {
    val moneyInput = com.example.walletwise.presentation.transaction.rememberMoneyInput(state.inputAmount, onAmountChanged)
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    stringResource(Res.string.budget_dialog_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = moneyInput.first,
                visualTransformation = GroupedMoneyTransformation,
                    onValueChange = moneyInput.second,
                    enabled = !state.isSaving,
                    label = { Text(stringResource(Res.string.budget_total_input)) },
                    isError = state.validationError != null,
                    supportingText = state.validationError?.let { error ->
                        { Text(validationMessage(error)) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(Res.string.budget_choose_rule),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.selectedRule == BudgetRule.FIFTY_THIRTY_TWENTY,
                        onClick = { onRuleSelected(BudgetRule.FIFTY_THIRTY_TWENTY) },
                        enabled = !state.isSaving,
                        label = { Text(stringResource(Res.string.budget_rule_standard_chip)) }
                    )
                    FilterChip(
                        selected = state.selectedRule == BudgetRule.JARS,
                        onClick = { onRuleSelected(BudgetRule.JARS) },
                        enabled = !state.isSaving,
                        label = { Text(stringResource(Res.string.budget_rule_jars_chip)) }
                    )
                }
                Spacer(Modifier.height(16.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text(stringResource(Res.string.budget_preview_title), fontSize = 12.sp, color = Color.Gray)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(
                            Res.string.budget_preview_needs,
                            formatBudgetMoney(state.previewAllocation.needs)
                        ),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        stringResource(
                            Res.string.budget_preview_wants,
                            formatBudgetMoney(state.previewAllocation.wants)
                        ),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        stringResource(
                            Res.string.budget_preview_savings,
                            formatBudgetMoney(state.previewAllocation.savings)
                        ),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !state.isSaving,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(Res.string.budget_cancel), color = Color.Gray)
                    }
                    Button(
                        onClick = onSave,
                        enabled = !state.isSaving,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                stringResource(Res.string.budget_save),
                                color = Color.Black,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun validationMessage(error: BudgetValidationError): String = when (error) {
    BudgetValidationError.AMOUNT_REQUIRED -> stringResource(Res.string.budget_error_amount_required)
    BudgetValidationError.AMOUNT_INVALID -> stringResource(Res.string.budget_error_amount_invalid)
    BudgetValidationError.AMOUNT_MUST_BE_POSITIVE -> stringResource(Res.string.budget_error_amount_positive)
    BudgetValidationError.RULE_INVALID,
    BudgetValidationError.USER_ID_REQUIRED,
    BudgetValidationError.MONTH_KEY_INVALID -> stringResource(Res.string.budget_error_amount_invalid)
}

fun formatBudgetMoney(value: Double): String =
    if (!value.isFinite()) "0 ₫" else round(value).toLong().let { amount ->
        val raw = amount.toString()
        (if (amount < 0) "-" else "") + raw.removePrefix("-").reversed().chunked(3).joinToString(".").reversed() + " ₫"
    }

@Composable
private fun FinancialMappingDialog(state: SmartBudgetUiState, onClose: () -> Unit, onMap: (String, String) -> Unit) {
    Dialog(onDismissRequest = onClose) {
        Card(Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Phân nhóm danh mục", style = MaterialTheme.typography.titleLarge)
                Text("Mỗi danh mục đều có quỹ. Bạn có thể đổi quỹ phù hợp với kế hoạch.", style = MaterialTheme.typography.bodyMedium)
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    state.mappingCategories.forEach { category ->
                        var expanded by remember(category) { mutableStateOf(false) }
                        val bucketId = FinancialCategoryMapping.bucket(category, state.allocationPlan.method.rule, state.categoryMappings, state.mappingCategoryIds[category].orEmpty())
                        Text(category, fontWeight = FontWeight.Bold)
                        Box {
                            OutlinedButton({ expanded = true }, enabled = !state.mappingLoading && !state.mappingSaving && state.mappingError == null) {
                                Text(state.allocationPlan.method.buckets.first { it.key == bucketId }.name)
                            }
                            DropdownMenu(expanded, { expanded = false }) {
                                state.allocationPlan.method.buckets.forEach { bucket ->
                                    DropdownMenuItem({ Text(bucket.name) }, { onMap(category, bucket.key); expanded = false })
                                }
                            }
                        }
                    }
                }
                if (state.mappingSaving) Text("Đang lưu phân nhóm…")
                TextButton(onClose, modifier = Modifier.fillMaxWidth()) { Text("Đóng") }
            }
        }
    }
}
