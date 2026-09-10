package com.example.walletwise.presentation.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.walletwise.shared.resources.Res
import com.example.walletwise.shared.resources.currency_cny
import com.example.walletwise.shared.resources.currency_disclaimer
import com.example.walletwise.shared.resources.currency_eur
import com.example.walletwise.shared.resources.currency_jpy
import com.example.walletwise.shared.resources.currency_picker_title
import com.example.walletwise.shared.resources.currency_title
import com.example.walletwise.shared.resources.currency_usd
import com.example.walletwise.shared.resources.currency_vnd
import org.jetbrains.compose.resources.stringResource

@Composable
fun CurrencyConverterContent(
    state: CurrencyConverterUiState,
    isDark: Boolean,
    onBack: () -> Unit,
    onCurrencyRowSelected: (Int) -> Unit,
    onOpenCurrencyPicker: (Int) -> Unit,
    onDismissCurrencyPicker: () -> Unit,
    onCurrencySelected: (CurrencyDefinition) -> Unit,
    onKeyPressed: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopHeader(state.headerLabel(stringResource(Res.string.currency_title)), onBack)

        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .weight(1f)
        ) {
            state.rows.forEachIndexed { index, currency ->
                CurrencyRow(
                    name = currencyDisplayName(currency.code),
                    code = currency.code,
                    value = state.displayValue(index),
                    isActive = state.activeIndex == index,
                    isDark = isDark,
                    onClick = { onCurrencyRowSelected(index) },
                    onDropdownClick = { onOpenCurrencyPicker(index) }
                )
                if (index < state.rows.lastIndex) Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(Res.string.currency_disclaimer),
                color = Color.Gray,
                fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(CurrencyKeypadKeys) { key ->
                    val isOperator = key in listOf("C", "⌫", "%", "÷", "×", "-", "+")
                    val isEquals = key == "="
                    val backgroundColor = when {
                        isEquals -> Color(0xFFFF7043)
                        isOperator -> if (isDark) Color(0xFF3A2C2C) else Color(0xFFFFE0D6)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                    val foregroundColor = when {
                        isEquals -> Color.White
                        isOperator -> Color(0xFFFF7043)
                        else -> MaterialTheme.colorScheme.onSurface
                    }

                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(backgroundColor)
                            .clickable { onKeyPressed(key) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            key,
                            color = foregroundColor,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }

    if (state.dropdownRowIndex != null) {
        Dialog(onDismissRequest = onDismissCurrencyPicker) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        stringResource(Res.string.currency_picker_title),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    ThemedDivider()
                    Spacer(Modifier.height(8.dp))
                    SupportedCurrencies.forEach { currency ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onCurrencySelected(currency) }
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                currencyDisplayName(currency.code),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 15.sp
                            )
                            Text(currency.code, color = Color.Gray, fontSize = 13.sp)
                        }
                        if (currency != SupportedCurrencies.last()) ThemedDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun CurrencyRow(
    name: String,
    code: String,
    value: String,
    isActive: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
    onDropdownClick: () -> Unit
) {
    val textColor = if (isActive) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurface
    val rowBackground = if (isActive) {
        if (isDark) Color(0xFF2A2500) else Color(0xFFFFF9E0)
    } else {
        Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(rowBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(onClick = onDropdownClick)
            ) {
                Text(
                    name,
                    color = textColor,
                    fontSize = 16.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                )
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(code, color = Color.Gray, fontSize = 13.sp)
        }
        Text(value, color = textColor, fontSize = 22.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun currencyDisplayName(code: String): String = when (code) {
    "VND" -> stringResource(Res.string.currency_vnd)
    "USD" -> stringResource(Res.string.currency_usd)
    "EUR" -> stringResource(Res.string.currency_eur)
    "JPY" -> stringResource(Res.string.currency_jpy)
    "CNY" -> stringResource(Res.string.currency_cny)
    else -> code
}
