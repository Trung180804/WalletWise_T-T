package com.example.walletwise.presentation.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.walletwise.data.image.ContentResolverImageReader
import com.example.walletwise.presentation.auth.AuthViewModel
import com.example.walletwise.shared.resources.Res
import com.example.walletwise.shared.resources.profile_avatar_description
import com.example.walletwise.shared.resources.profile_copied_id
import com.example.walletwise.shared.resources.profile_email_read_only
import com.example.walletwise.shared.resources.profile_loading_avatar
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
fun EditProfileView(
    state: ProfileUiState,
    authViewModel: AuthViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val imageReader = remember { ContentResolverImageReader() }
    val loadingMessage = stringResource(Res.string.profile_loading_avatar)
    val copiedMessage = stringResource(Res.string.profile_copied_id)
    val emailReadOnlyMessage = stringResource(Res.string.profile_email_read_only)

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) {
            authViewModel.onProfileAvatarPickerCancelled()
        } else {
            Toast.makeText(context, loadingMessage, Toast.LENGTH_SHORT).show()
            scope.launch {
                imageReader.read(uri, context).fold(
                    onSuccess = authViewModel::uploadProfileAvatar,
                    onFailure = { error ->
                        authViewModel.onProfileAvatarReadFailed(error.message.orEmpty())
                    }
                )
            }
        }
    }

    EditProfileContent(
        state = state,
        onBack = onBack,
        onPickAvatar = { photoPickerLauncher.launch("image/*") },
        onCopyUserId = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("ID", state.editUserId))
            Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
        },
        onEmailClick = {
            Toast.makeText(context, emailReadOnlyMessage, Toast.LENGTH_SHORT).show()
        },
        onOpenNameEditor = authViewModel::openProfileNameEditor,
        onNameChanged = authViewModel::onProfileNameChanged,
        onCancelNameEditor = authViewModel::cancelProfileNameEditor,
        onSubmitName = authViewModel::submitProfileName,
        onOpenGenderSelector = authViewModel::openProfileGenderSelector,
        onCancelGenderSelector = authViewModel::cancelProfileGenderSelector,
        onSelectGender = authViewModel::selectProfileGender,
        onOpenPasswordEditor = authViewModel::openProfilePasswordEditor,
        onCurrentPasswordChanged = authViewModel::onCurrentProfilePasswordChanged,
        onNewPasswordChanged = authViewModel::onNewProfilePasswordChanged,
        onPasswordConfirmationChanged = authViewModel::onProfilePasswordConfirmationChanged,
        onCancelPasswordEditor = authViewModel::cancelProfilePasswordEditor,
        onSubmitPasswordChange = authViewModel::submitProfilePasswordChange,
        avatarContent = { url, initial, fontSize ->
            AndroidProfileAvatar(url, initial, fontSize)
        }
    )
}

@Composable
internal fun AndroidProfileAvatar(
    avatarUrl: String,
    fallbackInitial: String,
    fallbackFontSize: Int
) {
    var hasLoadError by remember(avatarUrl) { mutableStateOf(false) }
    val description = stringResource(Res.string.profile_avatar_description)

    if (hasLoadError) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                fallbackInitial,
                color = Color.White,
                fontSize = fallbackFontSize.sp,
                fontWeight = FontWeight.Bold
            )
        }
    } else {
        AsyncImage(
            model = avatarUrl,
            contentDescription = description,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            onLoading = { hasLoadError = false },
            onError = { hasLoadError = true }
        )
    }
}
