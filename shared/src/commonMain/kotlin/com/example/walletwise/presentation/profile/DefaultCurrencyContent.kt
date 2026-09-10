package com.example.walletwise.presentation.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.walletwise.shared.resources.Res
import com.example.walletwise.shared.resources.default_currency_aud
import com.example.walletwise.shared.resources.default_currency_cad
import com.example.walletwise.shared.resources.default_currency_cny
import com.example.walletwise.shared.resources.default_currency_eur
import com.example.walletwise.shared.resources.default_currency_gbp
import com.example.walletwise.shared.resources.default_currency_hkd
import com.example.walletwise.shared.resources.default_currency_inr
import com.example.walletwise.shared.resources.default_currency_jpy
import com.example.walletwise.shared.resources.default_currency_krw
import com.example.walletwise.shared.resources.default_currency_sgd
import com.example.walletwise.shared.resources.default_currency_title
import com.example.walletwise.shared.resources.default_currency_usd
import com.example.walletwise.shared.resources.default_currency_vnd
import org.jetbrains.compose.resources.stringResource

@Composable
fun DefaultCurrencyContent(
    options: List<CurrencyOption>,
    onCurrencySelected: (String) -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopHeader(stringResource(Res.string.default_currency_title), onBack)

        LazyColumn {
            items(options, key = CurrencyOption::code) { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCurrencySelected(option.code) }
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        currencyOptionName(option.displayName),
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 16.sp
                    )
                    Text(
                        option.code,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 15.sp
                    )
                }
                ThemedDivider()
            }
        }
    }
}

@Composable
private fun currencyOptionName(displayName: CurrencyDisplayName): String = when (displayName) {
    CurrencyDisplayName.VND -> stringResource(Res.string.default_currency_vnd)
    CurrencyDisplayName.USD -> stringResource(Res.string.default_currency_usd)
    CurrencyDisplayName.EUR -> stringResource(Res.string.default_currency_eur)
    CurrencyDisplayName.GBP -> stringResource(Res.string.default_currency_gbp)
    CurrencyDisplayName.CNY -> stringResource(Res.string.default_currency_cny)
    CurrencyDisplayName.JPY -> stringResource(Res.string.default_currency_jpy)
    CurrencyDisplayName.CAD -> stringResource(Res.string.default_currency_cad)
    CurrencyDisplayName.AUD -> stringResource(Res.string.default_currency_aud)
    CurrencyDisplayName.HKD -> stringResource(Res.string.default_currency_hkd)
    CurrencyDisplayName.KRW -> stringResource(Res.string.default_currency_krw)
    CurrencyDisplayName.SGD -> stringResource(Res.string.default_currency_sgd)
    CurrencyDisplayName.INR -> stringResource(Res.string.default_currency_inr)
}
