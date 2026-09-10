package com.example.walletwise.presentation.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.walletwise.shared.resources.Res
import com.example.walletwise.shared.resources.settings_category
import com.example.walletwise.shared.resources.settings_default_currency
import com.example.walletwise.shared.resources.settings_font_size
import com.example.walletwise.shared.resources.settings_recurring
import com.example.walletwise.shared.resources.settings_reminders
import com.example.walletwise.shared.resources.settings_theme
import com.example.walletwise.shared.resources.settings_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

enum class SettingsDestination {
    FONT_SIZE,
    CATEGORY_MANAGEMENT,
    DEFAULT_CURRENCY,
    REMINDERS,
    RECURRING,
    THEME
}

val SettingsMenuDestinationOrder: List<SettingsDestination> = listOf(
    SettingsDestination.FONT_SIZE,
    SettingsDestination.CATEGORY_MANAGEMENT,
    SettingsDestination.DEFAULT_CURRENCY,
    SettingsDestination.REMINDERS,
    SettingsDestination.RECURRING,
    SettingsDestination.THEME
)

@Composable
fun SettingsMainContent(
    onNavigate: (SettingsDestination) -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopHeader(stringResource(Res.string.settings_title), onBack)
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(8.dp))
            SettingsMenuDestinationOrder.forEach { destination ->
                val presentation = destination.presentation()
                SettingsRowItem(
                    presentation.icon,
                    stringResource(presentation.title)
                ) { onNavigate(destination) }

                if (destination == SettingsDestination.RECURRING) {
                    ThemedDivider()
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

private data class SettingsItemPresentation(
    val icon: ImageVector,
    val title: StringResource
)

private fun SettingsDestination.presentation(): SettingsItemPresentation = when (this) {
    SettingsDestination.FONT_SIZE -> SettingsItemPresentation(
        Icons.Default.Edit,
        Res.string.settings_font_size
    )
    SettingsDestination.CATEGORY_MANAGEMENT -> SettingsItemPresentation(
        Icons.AutoMirrored.Filled.List,
        Res.string.settings_category
    )
    SettingsDestination.DEFAULT_CURRENCY -> SettingsItemPresentation(
        Icons.Default.ShoppingCart,
        Res.string.settings_default_currency
    )
    SettingsDestination.REMINDERS -> SettingsItemPresentation(
        Icons.Default.Notifications,
        Res.string.settings_reminders
    )
    SettingsDestination.RECURRING -> SettingsItemPresentation(
        Icons.Default.Refresh,
        Res.string.settings_recurring
    )
    SettingsDestination.THEME -> SettingsItemPresentation(
        Icons.Default.Star,
        Res.string.settings_theme
    )
}
