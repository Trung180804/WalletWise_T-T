package com.example.walletwise.presentation.profile.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.walletwise.presentation.profile.ThemedDivider
import com.example.walletwise.presentation.profile.TopHeader

@Composable
fun DefaultCurrencyView(onBack: () -> Unit) {
    val currencies = listOf(
        "đồng Việt Nam ( ₫ )" to "VND",
        "Đô la Mĩ ( $ )" to "USD",
        "Euro ( € )" to "EUR",
        "Bảng Anh ( £ )" to "GBP",
        "Nhân dân tệ Trung Quốc ( 元 )" to "CNY",
        "Yen Nhật ( ¥ )" to "JPY",
        "Đô la Canada ( C$ )" to "CAD",
        "đô la Úc ( A$ )" to "AUD",
        "Đôla Hong Kong ( HK$ )" to "HKD",
        "Won Hàn Quốc ( ₩ )" to "KRW",
        "Đô la Singapore ( S$ )" to "SGD",
        "Rupee Ấn Độ ( ₹ )" to "INR"
    )

    Column(modifier = Modifier.fillMaxSize()) {
        TopHeader("Lựa chọn", onBack)

        LazyColumn {
            items(currencies) { (name, code) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onBack() }
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(name, color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp)
                    Text(code, color = MaterialTheme.colorScheme.onBackground, fontSize = 15.sp)
                }
                ThemedDivider()
            }
        }
    }
}
