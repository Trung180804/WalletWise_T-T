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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.walletwise.domain.model.BudgetRule
import com.example.walletwise.domain.service.BudgetGroupProgress
import com.example.walletwise.domain.service.BudgetProgressStatus
import com.example.walletwise.domain.validation.BudgetValidationError
import com.example.walletwise.presentation.profile.TopHeader
import com.example.walletwise.presentation.profile.formatCurrencyNumber
import com.example.walletwise.shared.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.round

@Composable
fun SmartBudgetContent(
    state: SmartBudgetUiState,
    onBack: () -> Unit,
    onOpenSetup: () -> Unit,
    onCancelSetup: () -> Unit,
    onAmountChanged: (String) -> Unit,
    onRuleSelected: (BudgetRule) -> Unit,
    onSave: () -> Unit,
    onRefreshInsight: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopHeader(stringResource(Res.string.budget_title), onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(16.dp))
            BudgetHeaderCard(state, onOpenSetup)
            if (state.hasPlan) {
                Spacer(Modifier.height(16.dp))
                SafeToSpendCard(state)
                Spacer(Modifier.height(20.dp))
                AllocationSection(state)
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(Res.string.budget_progress_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(12.dp))
                val isJars = state.plan?.ruleType == BudgetRule.JARS.wireValue
                BudgetGroupCard(
                    title = if (isJars) Res.string.budget_needs_jars else Res.string.budget_needs_standard,
                    description = Res.string.budget_needs_description,
                    progress = state.needs
                )
                Spacer(Modifier.height(12.dp))
                BudgetGroupCard(
                    title = if (isJars) Res.string.budget_wants_jars else Res.string.budget_wants_standard,
                    description = Res.string.budget_wants_description,
                    progress = state.wants
                )
                Spacer(Modifier.height(12.dp))
                BudgetGroupCard(
                    title = if (isJars) Res.string.budget_savings_jars else Res.string.budget_savings_standard,
                    description = Res.string.budget_savings_description,
                    progress = state.savings
                )
                Spacer(Modifier.height(20.dp))
                InsightCard(state.insight, onRefreshInsight)
            }
            Spacer(Modifier.height(100.dp))
        }
    }

    if (state.showSetupDialog) {
        BudgetSetupDialog(
            state = state,
            onDismiss = onCancelSetup,
            onAmountChanged = onAmountChanged,
            onRuleSelected = onRuleSelected,
            onSave = onSave
        )
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
                    value = state.inputAmount,
                    onValueChange = onAmountChanged,
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
    formatCurrencyNumber(if (value.isFinite()) round(value) else 0.0)
