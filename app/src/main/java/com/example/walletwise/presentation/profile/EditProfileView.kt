package com.example.walletwise.presentation.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.walletwise.domain.model.User
import com.example.walletwise.presentation.auth.AuthViewModel

@Composable
fun EditProfileView(
    user: User?,
    authViewModel: AuthViewModel = viewModel(),
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val myId = user?.id?.takeIf { it.isNotBlank() } ?: "Chưa có ID"
    val realEmail = user?.email?.takeIf { it.isNotBlank() } ?: "Chưa có Email"
    val fullName = user?.username?.takeIf { it.isNotBlank() } ?: "Người dùng"
    val gender = user?.gender?.takeIf { it.isNotBlank() } ?: "Khác"
    val avatarUrl = user?.avatarUrl ?: ""
    val firstLetter = fullName.firstOrNull { it.isLetter() }?.toString()?.uppercase() ?: "U"

    var showNameDialog by remember { mutableStateOf(false) }
    var showGenderDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var isUpdating by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            Toast.makeText(context, "Đang tải ảnh đại diện lên...", Toast.LENGTH_SHORT).show()
            isUpdating = true
            authViewModel.uploadAndSaveAvatar(uri, context) { success, msg ->
                isUpdating = false
                Toast.makeText(context, msg ?: if (success) "Thành công" else "Thất bại", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopHeader("Hồ sơ", onBack)
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            // Hàng chọn/thay đổi Ảnh đại diện
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { photoPickerLauncher.launch("image/*") }
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Ảnh đại diện", color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp)
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE91E63)),
                    contentAlignment = Alignment.Center
                ) {
                    if (avatarUrl.isNotBlank()) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = "Avatar",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(firstLetter, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            ThemedDivider()

            EditRowItem("ID", myId) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("ID", myId))
                Toast.makeText(context, "Đã sao chép ID", Toast.LENGTH_SHORT).show()
            }

            EditRowItem("Email", realEmail) {
                Toast.makeText(context, "Email không thể thay đổi tại đây", Toast.LENGTH_SHORT).show()
            }

            // Thay 'Biệt danh' thành 'Họ và tên'
            EditRowItem("Họ và tên", fullName) { showNameDialog = true }

            EditRowItem("Giới tính", gender) { showGenderDialog = true }

            EditRowItem("Đổi mật khẩu", "") { showPasswordDialog = true }
        }
    }

    // DIALOG SỬA HỌ VÀ TÊN
    if (showNameDialog) {
        var tempName by remember { mutableStateOf(fullName) }
        Dialog(onDismissRequest = { showNameDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        "Họ và tên",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = tempName,
                        onValueChange = { tempName = it },
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
                            onClick = { showNameDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.width(16.dp))
                        Button(
                            onClick = {
                                if (tempName.isNotBlank()) {
                                    authViewModel.updateUserProfile(username = tempName) { success, msg ->
                                        if (success) {
                                            Toast.makeText(context, "Đã cập nhật Họ và tên!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, msg ?: "Lỗi cập nhật", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                showNameDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Check, null, tint = Color.Black)
                        }
                    }
                }
            }
        }
    }

    // DIALOG CHỌN GIỚI TÍNH
    if (showGenderDialog) {
        Dialog(onDismissRequest = { showGenderDialog = false }) {
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
                        Text("Giới tính", color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.clickable { showGenderDialog = false })
                    }
                    Spacer(Modifier.height(24.dp))
                    listOf("Khác", "Nữ giới", "Nam giới").forEach { opt ->
                        Button(
                            onClick = {
                                authViewModel.updateUserProfile(gender = opt) { _, _ -> }
                                showGenderDialog = false
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .padding(bottom = 8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(opt, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                        }
                    }
                    Text(
                        "Tôi không muốn tiết lộ!",
                        color = Color.Gray,
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .clickable {
                                authViewModel.updateUserProfile(gender = "Bí mật") { _, _ -> }
                                showGenderDialog = false
                            }
                    )
                }
            }
        }
    }

    // DIALOG ĐỔI MẬT KHẨU (KẾT NỐI FIREBASE AUTH)
    if (showPasswordDialog) {
        var oldPass by remember { mutableStateOf("") }
        var newPass by remember { mutableStateOf("") }
        var confirmPass by remember { mutableStateOf("") }
        var isSubmitting by remember { mutableStateOf(false) }

        Dialog(onDismissRequest = { if (!isSubmitting) showPasswordDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Đổi mật khẩu", color = Color(0xFFFA3B70), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = oldPass,
                        onValueChange = { oldPass = it },
                        placeholder = { Text("Mật khẩu hiện tại") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        shape = CircleShape
                    )
                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = newPass,
                        onValueChange = { newPass = it },
                        placeholder = { Text("Mật khẩu mới (tối thiểu 6 ký tự)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        shape = CircleShape
                    )
                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = confirmPass,
                        onValueChange = { confirmPass = it },
                        placeholder = { Text("Nhắc lại mật khẩu mới") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        shape = CircleShape
                    )
                    Spacer(Modifier.height(24.dp))

                    if (isSubmitting) {
                        CircularProgressIndicator(color = Color(0xFFFA3B70))
                    } else {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { showPasswordDialog = false },
                                modifier = Modifier.weight(1f).height(50.dp),
                                shape = CircleShape
                            ) {
                                Text("Hủy bỏ", color = MaterialTheme.colorScheme.onSurface)
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (oldPass.isBlank()) {
                                        Toast.makeText(context, "Vui lòng nhập mật khẩu hiện tại!", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    if (newPass.length < 6) {
                                        Toast.makeText(context, "Mật khẩu mới phải từ 6 ký tự trở lên!", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    if (newPass != confirmPass) {
                                        Toast.makeText(context, "Mật khẩu nhắc lại không trùng khớp!", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }

                                    isSubmitting = true
                                    authViewModel.changePassword(oldPass, newPass) { success, msg ->
                                        isSubmitting = false
                                        Toast.makeText(context, msg ?: if (success) "Thành công!" else "Thất bại", Toast.LENGTH_LONG).show()
                                        if (success) {
                                            showPasswordDialog = false
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f).height(50.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFA3B70)),
                                shape = CircleShape
                            ) {
                                Text("Xác nhận", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
