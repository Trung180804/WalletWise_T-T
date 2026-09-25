package com.example.walletwise.presentation.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import com.example.walletwise.shared.resources.Res
import com.example.walletwise.shared.resources.theme_current
import com.example.walletwise.shared.resources.theme_dark
import com.example.walletwise.shared.resources.theme_description
import com.example.walletwise.shared.resources.theme_light
import com.example.walletwise.shared.resources.theme_prompt
import com.example.walletwise.shared.resources.theme_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun ThemeContent(
    isDarkTheme: Boolean,
    onThemeSelected: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopHeader(stringResource(Res.string.theme_title), onBack)

        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                stringResource(Res.string.theme_prompt),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ThemeOptionCard(
                    label = stringResource(Res.string.theme_light),
                    background = Color.White,
                    foreground = Color.Black,
                    isSelected = isThemeOptionSelected(
                        currentIsDarkTheme = isDarkTheme,
                        optionIsDarkTheme = false
                    ),
                    onClick = { dispatchThemeSelection(false, onThemeSelected) }
                )
                ThemeOptionCard(
                    label = stringResource(Res.string.theme_dark),
                    background = Color(0xFF121212),
                    foreground = Color.White,
                    isSelected = isThemeOptionSelected(
                        currentIsDarkTheme = isDarkTheme,
                        optionIsDarkTheme = true
                    ),
                    onClick = { dispatchThemeSelection(true, onThemeSelected) }
                )
            }
            Spacer(Modifier.height(32.dp))
            Text(
                stringResource(Res.string.theme_description),
                color = Color.Gray,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun ThemeOptionCard(
    label: String,
    background: Color,
    foreground: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(110.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                    shape = RoundedCornerShape(16.dp)
                )
                .background(background),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    label,
                    color = foreground,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                if (isSelected) {
                    Spacer(Modifier.height(6.dp))
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (isSelected) {
            Text(
                stringResource(Res.string.theme_current),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

internal fun dispatchThemeSelection(
    isDarkTheme: Boolean,
    onThemeSelected: (Boolean) -> Unit
) {
    onThemeSelected(isDarkTheme)
}

internal fun isThemeOptionSelected(
    currentIsDarkTheme: Boolean,
    optionIsDarkTheme: Boolean
): Boolean = currentIsDarkTheme == optionIsDarkTheme
