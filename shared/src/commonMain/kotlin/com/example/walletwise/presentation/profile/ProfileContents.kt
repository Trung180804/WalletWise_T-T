package com.example.walletwise.presentation.profile

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.walletwise.shared.resources.Res
import com.example.walletwise.shared.resources.email_label
import com.example.walletwise.shared.resources.profile_avatar
import com.example.walletwise.shared.resources.profile_cancel
import com.example.walletwise.shared.resources.profile_change_password
import com.example.walletwise.shared.resources.profile_confirm
import com.example.walletwise.shared.resources.profile_confirm_password
import com.example.walletwise.shared.resources.profile_current_password
import com.example.walletwise.shared.resources.profile_full_name
import com.example.walletwise.shared.resources.profile_gender
import com.example.walletwise.shared.resources.profile_gender_female
import com.example.walletwise.shared.resources.profile_gender_male
import com.example.walletwise.shared.resources.profile_gender_other
import com.example.walletwise.shared.resources.profile_gender_secret
import com.example.walletwise.shared.resources.profile_gender_secret_action
import com.example.walletwise.shared.resources.profile_id
import com.example.walletwise.shared.resources.profile_login
import com.example.walletwise.shared.resources.profile_logout
import com.example.walletwise.shared.resources.profile_logout_question
import com.example.walletwise.shared.resources.profile_member
import com.example.walletwise.shared.resources.profile_menu_about
import com.example.walletwise.shared.resources.profile_menu_budget
import com.example.walletwise.shared.resources.profile_menu_currency
import com.example.walletwise.shared.resources.profile_menu_customer_care
import com.example.walletwise.shared.resources.profile_menu_settings
import com.example.walletwise.shared.resources.profile_missing_email
import com.example.walletwise.shared.resources.profile_missing_id
import com.example.walletwise.shared.resources.profile_new_password
import com.example.walletwise.shared.resources.profile_not_logged_in
import com.example.walletwise.shared.resources.profile_title
import com.example.walletwise.shared.resources.profile_user
import org.jetbrains.compose.resources.stringResource

typealias ProfileAvatarContent = @Composable (
    avatarUrl: String,
    fallbackInitial: String,
    fallbackFontSize: Int
) -> Unit

@Composable
fun ProfileMainContent(
    state: ProfileUiState,
    onNavigate: (ProfileDestination) -> Unit,
    onLogoutRequest: () -> Unit,
    onCancelLogout: () -> Unit,
    onConfirmLogout: () -> Unit,
    avatarContent: ProfileAvatarContent? = null
) {
    when (val loadState = state.loadState) {
        ProfileLoadState.Loading -> ProfileStatusContent(isLoading = true)
        is ProfileLoadState.Error -> ProfileStatusContent(message = loadState.message)
        ProfileLoadState.Data -> ProfileMainData(
            state = state,
            onNavigate = onNavigate,
            onLogoutRequest = onLogoutRequest,
            avatarContent = avatarContent
        )
    }

    if (state.isLogoutDialogVisible && state.isLoggedIn) {
        LogoutDialog(
            onDismiss = onCancelLogout,
            onConfirm = onConfirmLogout
        )
    }
}

@Composable
private fun ProfileMainData(
    state: ProfileUiState,
    onNavigate: (ProfileDestination) -> Unit,
    onLogoutRequest: () -> Unit,
    avatarContent: ProfileAvatarContent?
) {
    val email = if (state.isLoggedIn) {
        state.email
    } else {
        stringResource(Res.string.profile_not_logged_in)
    }
    val displayName = if (state.isLoggedIn) {
        state.username.ifBlank { stringResource(Res.string.profile_member) }
    } else {
        stringResource(Res.string.profile_user)
    }
    val initial = email.firstOrNull()?.uppercase() ?: "N"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))

        ProfileAvatar(
            avatarUrl = state.avatarUrl,
            fallbackInitial = initial,
            size = 90,
            fontSize = 40,
            avatarContent = avatarContent
        )

        Spacer(Modifier.height(16.dp))
        Text(
            text = displayName,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Text(email, color = Color.Gray, fontSize = 14.sp)
        Spacer(Modifier.height(32.dp))

        MenuRowItem(
            Icons.Default.Person,
            stringResource(Res.string.profile_title)
        ) { onNavigate(ProfileDestination.EDIT_PROFILE) }
        MenuRowItem(
            Icons.Default.PieChart,
            stringResource(Res.string.profile_menu_budget)
        ) { onNavigate(ProfileDestination.SMART_BUDGET) }
        MenuRowItem(
            Icons.Default.Refresh,
            stringResource(Res.string.profile_menu_currency)
        ) { onNavigate(ProfileDestination.CURRENCY) }
        MenuRowItem(
            Icons.Default.SupportAgent,
            stringResource(Res.string.profile_menu_customer_care)
        ) { onNavigate(ProfileDestination.CUSTOMER_CARE) }
        MenuRowItem(
            Icons.Default.Settings,
            stringResource(Res.string.profile_menu_settings)
        ) { onNavigate(ProfileDestination.SETTINGS) }
        MenuRowItem(
            Icons.Default.Info,
            stringResource(Res.string.profile_menu_about)
        ) { onNavigate(ProfileDestination.ABOUT_US) }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onLogoutRequest,
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = if (state.isLoggedIn) {
                    stringResource(Res.string.profile_logout)
                } else {
                    stringResource(Res.string.profile_login)
                },
                color = if (state.isLoggedIn) Color(0xFFFA3B70) else Color(0xFF00C875),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(100.dp))
    }
}

@Composable
fun EditProfileContent(
    state: ProfileUiState,
    onBack: () -> Unit,
    onPickAvatar: () -> Unit,
    onCopyUserId: () -> Unit,
    onEmailClick: () -> Unit,
    onOpenNameEditor: () -> Unit,
    onNameChanged: (String) -> Unit,
    onCancelNameEditor: () -> Unit,
    onSubmitName: () -> Unit,
    onOpenGenderSelector: () -> Unit,
    onCancelGenderSelector: () -> Unit,
    onSelectGender: (String) -> Unit,
    onOpenPasswordEditor: () -> Unit,
    onCurrentPasswordChanged: (String) -> Unit,
    onNewPasswordChanged: (String) -> Unit,
    onPasswordConfirmationChanged: (String) -> Unit,
    onCancelPasswordEditor: () -> Unit,
    onSubmitPasswordChange: () -> Unit,
    avatarContent: ProfileAvatarContent? = null
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopHeader(stringResource(Res.string.profile_title), onBack)
        when (val loadState = state.loadState) {
            ProfileLoadState.Loading -> ProfileStatusContent(isLoading = true)
            is ProfileLoadState.Error -> ProfileStatusContent(message = loadState.message)
            ProfileLoadState.Data -> EditProfileData(
                state = state,
                onPickAvatar = onPickAvatar,
                onCopyUserId = onCopyUserId,
                onEmailClick = onEmailClick,
                onOpenNameEditor = onOpenNameEditor,
                onOpenGenderSelector = onOpenGenderSelector,
                onOpenPasswordEditor = onOpenPasswordEditor,
                avatarContent = avatarContent
            )
        }
    }

    state.nameDraft?.let { draft ->
        NameDialog(
            value = draft,
            isSubmitting = state.isUpdatingProfile,
            onValueChanged = onNameChanged,
            onDismiss = onCancelNameEditor,
            onSubmit = onSubmitName
        )
    }

    if (state.isGenderDialogVisible) {
        GenderDialog(
            isSubmitting = state.isUpdatingProfile,
            onDismiss = onCancelGenderSelector,
            onSelect = onSelectGender
        )
    }

    state.passwordEditor?.let { editor ->
        PasswordDialog(
            state = editor,
            onCurrentPasswordChanged = onCurrentPasswordChanged,
            onNewPasswordChanged = onNewPasswordChanged,
            onConfirmationChanged = onPasswordConfirmationChanged,
            onDismiss = onCancelPasswordEditor,
            onSubmit = onSubmitPasswordChange
        )
    }
}

@Composable
private fun EditProfileData(
    state: ProfileUiState,
    onPickAvatar: () -> Unit,
    onCopyUserId: () -> Unit,
    onEmailClick: () -> Unit,
    onOpenNameEditor: () -> Unit,
    onOpenGenderSelector: () -> Unit,
    onOpenPasswordEditor: () -> Unit,
    avatarContent: ProfileAvatarContent?
) {
    val userId = state.userId.ifBlank { stringResource(Res.string.profile_missing_id) }
    val email = state.email.ifBlank { stringResource(Res.string.profile_missing_email) }
    val fullName = state.username.ifBlank { stringResource(Res.string.profile_user) }
    val gender = state.gender.ifBlank { stringResource(Res.string.profile_gender_other) }
    val firstLetter = fullName.firstOrNull { it.isLetter() }
        ?.toString()
        ?.uppercase()
        ?: "U"

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !state.isUploadingAvatar, onClick = onPickAvatar)
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(Res.string.profile_avatar),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 16.sp
            )
            if (state.isUploadingAvatar) {
                Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            } else {
                ProfileAvatar(
                    avatarUrl = state.avatarUrl,
                    fallbackInitial = firstLetter,
                    size = 56,
                    fontSize = 26,
                    avatarContent = avatarContent
                )
            }
        }
        ThemedDivider()

        EditRowItem(stringResource(Res.string.profile_id), userId, onCopyUserId)
        EditRowItem(stringResource(Res.string.email_label), email, onEmailClick)
        EditRowItem(stringResource(Res.string.profile_full_name), fullName, onOpenNameEditor)
        EditRowItem(stringResource(Res.string.profile_gender), gender, onOpenGenderSelector)
        EditRowItem(
            stringResource(Res.string.profile_change_password),
            "",
            onOpenPasswordEditor
        )
    }
}

@Composable
private fun ProfileAvatar(
    avatarUrl: String,
    fallbackInitial: String,
    size: Int,
    fontSize: Int,
    avatarContent: ProfileAvatarContent?
) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Color(0xFFE91E63)),
        contentAlignment = Alignment.Center
    ) {
        if (avatarUrl.isNotBlank() && avatarContent != null) {
            avatarContent(avatarUrl, fallbackInitial, fontSize)
        } else {
            AvatarInitial(fallbackInitial, fontSize)
        }
    }
}

@Composable
private fun AvatarInitial(initial: String, fontSize: Int) {
    Text(
        initial,
        color = Color.White,
        fontSize = fontSize.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun ProfileStatusContent(
    isLoading: Boolean = false,
    message: String? = null
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (isLoading) {
            CircularProgressIndicator()
        } else {
            Text(
                text = message.orEmpty(),
                color = Color(0xFFFA3B70),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp)
            )
        }
    }
}

@Composable
private fun LogoutDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    stringResource(Res.string.profile_logout),
                    color = Color(0xFFFA3B70),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(Res.string.profile_logout_question),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text(
                            stringResource(Res.string.profile_cancel),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFA3B70)),
                        shape = CircleShape
                    ) {
                        Text(
                            stringResource(Res.string.profile_logout),
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NameDialog(
    value: String,
    isSubmitting: Boolean,
    onValueChanged: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    stringResource(Res.string.profile_full_name),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChanged,
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = onDismiss,
                        enabled = !isSubmitting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Button(
                        onClick = onSubmit,
                        enabled = !isSubmitting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        } else {
                            Icon(Icons.Default.Check, null, tint = Color.Black)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GenderDialog(
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val secretGender = stringResource(Res.string.profile_gender_secret)
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(Res.string.profile_gender),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        Icons.Default.Close,
                        null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.clickable(enabled = !isSubmitting, onClick = onDismiss)
                    )
                }
                Spacer(Modifier.height(24.dp))
                listOf(
                    stringResource(Res.string.profile_gender_other),
                    stringResource(Res.string.profile_gender_female),
                    stringResource(Res.string.profile_gender_male)
                ).forEach { option ->
                    Button(
                        onClick = { onSelect(option) },
                        enabled = !isSubmitting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .padding(bottom = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            option,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Text(
                    stringResource(Res.string.profile_gender_secret_action),
                    color = Color.Gray,
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .clickable(enabled = !isSubmitting) {
                            onSelect(secretGender)
                        }
                )
            }
        }
    }
}

@Composable
private fun PasswordDialog(
    state: ProfilePasswordEditorState,
    onCurrentPasswordChanged: (String) -> Unit,
    onNewPasswordChanged: (String) -> Unit,
    onConfirmationChanged: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    stringResource(Res.string.profile_change_password),
                    color = Color(0xFFFA3B70),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(16.dp))

                PasswordField(
                    value = state.currentPassword,
                    placeholder = stringResource(Res.string.profile_current_password),
                    enabled = !state.isSubmitting,
                    onValueChanged = onCurrentPasswordChanged
                )
                Spacer(Modifier.height(8.dp))
                PasswordField(
                    value = state.newPassword,
                    placeholder = stringResource(Res.string.profile_new_password),
                    enabled = !state.isSubmitting,
                    onValueChanged = onNewPasswordChanged
                )
                Spacer(Modifier.height(8.dp))
                PasswordField(
                    value = state.confirmation,
                    placeholder = stringResource(Res.string.profile_confirm_password),
                    enabled = !state.isSubmitting,
                    onValueChanged = onConfirmationChanged
                )
                Spacer(Modifier.height(24.dp))

                if (state.isSubmitting) {
                    CircularProgressIndicator(color = Color(0xFFFA3B70))
                } else {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = CircleShape
                        ) {
                            Text(
                                stringResource(Res.string.profile_cancel),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = onSubmit,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFA3B70)
                            ),
                            shape = CircleShape
                        ) {
                            Text(
                                stringResource(Res.string.profile_confirm),
                                color = Color.White,
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
private fun PasswordField(
    value: String,
    placeholder: String,
    enabled: Boolean,
    onValueChanged: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChanged,
        placeholder = { Text(placeholder) },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        shape = CircleShape
    )
}
