package com.example.walletwise.presentation.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.rememberAsyncImagePainter
import java.io.File
import java.util.UUID

data class CategoryItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val icon: String = "",
    val type: String = "Chi",
    val isCustom: Boolean = false
)

val expenseCategories = listOf(
    CategoryItem(name = "Ăn uống", icon = "🍔"), CategoryItem(name = "Mua sắm", icon = "🛒"),
    CategoryItem(name = "Nhà cửa", icon = "🏠"), CategoryItem(name = "Di chuyển", icon = "🚗"),
    CategoryItem(name = "Y tế", icon = "💊"), CategoryItem(name = "Giải trí", icon = "🎮"),
    CategoryItem(name = "Hóa đơn", icon = "💳"), CategoryItem(name = "Học tập", icon = "📚")
)

val incomeCategories = listOf(
    CategoryItem(name = "Lương", icon = "💰", type = "Thu"), CategoryItem(name = "Thưởng", icon = "🎁", type = "Thu"),
    CategoryItem(name = "Đầu tư", icon = "📈", type = "Thu"), CategoryItem(name = "Kinh doanh", icon = "💼", type = "Thu"),
    CategoryItem(name = "Part-time", icon = "🎯", type = "Thu"), CategoryItem(name = "Giải thưởng", icon = "🏆", type = "Thu"),
    CategoryItem(name = "Quà tặng", icon = "💝", type = "Thu"), CategoryItem(name = "Khác", icon = "🔄", type = "Thu")
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddTransactionScreen(
    viewModel: TransactionViewModel,
    onNavigateBack: () -> Unit
) {
    val txToEdit = viewModel.transactionToEdit
    val isEditMode = txToEdit != null

    var type by remember { mutableStateOf(txToEdit?.type ?: "Chi") }
    var amount by remember { mutableStateOf(if (isEditMode) txToEdit!!.amount.toLong().toString() else "") }
    var note by remember { mutableStateOf(txToEdit?.note ?: "") }
    var category by remember { mutableStateOf(txToEdit?.category ?: expenseCategories[0].name) }
    var selectedWallet by remember { mutableStateOf(txToEdit?.paymentMethod ?: "Tiền mặt") }

    val mainColor = if (type == "Chi") Color(0xFFFA3B70) else Color(0xFF00C875)
    val categories by viewModel.categories.collectAsState()
    val currentCategories = categories.filter { it.type == type }

    val isLoading by viewModel.isLoading.collectAsState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current // 👉 Quản lý Focus để ẩn bàn phím

    var tempImageUri by remember { mutableStateOf<Uri?>(null) }
    var capturedImageUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) { capturedImageUri = tempImageUri }
    }

    LaunchedEffect(type, currentCategories) {
        if (currentCategories.isNotEmpty() && currentCategories.none { it.name == category }) {
            category = currentCategories[0].name
        }
    }

    val handleBack = {
        viewModel.transactionToEdit = null
        onNavigateBack()
    }

    fun launchCamera() {
        try {
            val file = File(context.cacheDir, "receipt_${System.currentTimeMillis()}.jpg")
            if (file.exists()) file.delete()
            file.createNewFile()
            val uri = FileProvider.getUriForFile(context, "com.example.walletwise.fileprovider", file)
            tempImageUri = uri
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "LỖI CAMERA: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text(if (isEditMode) "Sửa giao dịch" else "Thêm giao dịch", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = { handleBack() }) {
                        Icon(Icons.Default.ArrowBack, "Quay lại", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
            )
        },
        // 👉 Tự động hạ bàn phím khi bấm ra vùng trống
        modifier = Modifier.pointerInput(Unit) {
            detectTapGestures(onTap = { focusManager.clearFocus() })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            AnimatedSegmentedSlider(currentType = type, onTypeChange = { type = it })
            Spacer(modifier = Modifier.height(32.dp))

            // Khu vực Camera
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(24.dp))
                    .clickable { launchCamera() },
                contentAlignment = Alignment.Center
            ) {
                val imageToShow = capturedImageUri ?: txToEdit?.imageUrl
                if (imageToShow != null) {
                    Image(
                        painter = rememberAsyncImagePainter(imageToShow), contentDescription = "Hóa đơn",
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)), contentScale = ContentScale.Crop
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(color = Color.Gray.copy(alpha = 0.6f), shape = RoundedCornerShape(12.dp)) {
                            Text("AI OCR", color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("📷", fontSize = 40.sp)
                    }
                    Box(modifier = Modifier.align(Alignment.BottomCenter).offset(y = 16.dp).size(40.dp).background(MaterialTheme.colorScheme.surface, CircleShape).border(3.dp, Color(0xFF9C27B0), CircleShape))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Số tiền & Wallet
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Số tiền", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { if (it.all { char -> char.isDigit() }) amount = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("0", color = Color.Gray) },
                        trailingIcon = { Text("đ", color = Color.Gray, modifier = Modifier.padding(end = 16.dp)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant, focusedBorderColor = mainColor,
                            focusedTextColor = MaterialTheme.colorScheme.onBackground, unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                        )
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Box {
                        var expandedWallet by remember { mutableStateOf(false) }
                        Surface(
                            shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.height(56.dp).clickable { expandedWallet = true }, color = Color.Transparent
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
                                Text(selectedWallet, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                                Icon(Icons.Default.ArrowDropDown, null, tint = MaterialTheme.colorScheme.onBackground)
                            }
                        }
                        DropdownMenu(expanded = expandedWallet, onDismissRequest = { expandedWallet = false }, modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                            listOf("Tiền mặt", "Chuyển khoản", "Thẻ tín dụng").forEach { option ->
                                DropdownMenuItem(text = { Text(option, color = MaterialTheme.colorScheme.onSurface) }, onClick = { selectedWallet = option; expandedWallet = false })
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Danh mục (Sử dụng FlowRow tự co giãn)
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Danh mục", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                Spacer(modifier = Modifier.height(12.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = 4 // 👉 Ép luôn 4 mục 1 hàng
                ) {
                    currentCategories.forEach { cat ->
                        CategoryCard(
                            item = cat,
                            isSelected = category == cat.name,
                            activeColor = mainColor,
                            // 👉 Dùng weight giúp các ô chia đều không gian thừa, vừa vặn mọi màn hình
                            modifier = Modifier.weight(1f),
                            onClick = {
                                category = cat.name
                                focusManager.clearFocus() // Ẩn phím khi bấm chọn danh mục
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Ghi chú
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Ghi chú", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = note, onValueChange = { note = it }, modifier = Modifier.fillMaxWidth().height(100.dp),
                    placeholder = { Text("Thêm ghi chú...", color = Color.Gray) }, shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant, focusedBorderColor = mainColor,
                        focusedTextColor = MaterialTheme.colorScheme.onBackground, unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Nút Lưu
            Button(
                enabled = !isLoading,
                onClick = {
                    focusManager.clearFocus()
                    val amountValue = amount.toDoubleOrNull()
                    if (amountValue == null || amountValue <= 0) {
                        android.widget.Toast.makeText(context, "Số tiền không hợp lệ", android.widget.Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (isEditMode) {
                        viewModel.updateTransaction(txToEdit!!.copy(amount = amountValue, type = type, category = category, note = note, paymentMethod = selectedWallet), capturedImageUri, context) { handleBack() }
                    } else {
                        viewModel.addTransaction(amountValue, type, category, note, selectedWallet, capturedImageUri, context) { handleBack() }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(55.dp),
                colors = ButtonDefaults.buttonColors(containerColor = mainColor),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(if (isEditMode) "Cập nhật giao dịch" else if (type == "Chi") "Lưu chi tiêu" else "Lưu thu nhập", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun AnimatedSegmentedSlider(currentType: String, onTypeChange: (String) -> Unit) {
    val isIncome = currentType == "Thu"
    val activeColor = if (isIncome) Color(0xFF00C875) else Color(0xFFFA3B70)
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable(interactionSource = interactionSource, indication = null) { onTypeChange(if (isIncome) "Chi" else "Thu") }
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val segmentWidth = maxWidth / 2
            val offset by animateDpAsState(targetValue = if (isIncome) segmentWidth else 0.dp, animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing), label = "sliderAnim")
            Box(modifier = Modifier.offset(x = offset).width(segmentWidth).fillMaxHeight().padding(4.dp).clip(RoundedCornerShape(22.dp)).background(activeColor))
            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight().clickable(interactionSource = interactionSource, indication = null) { onTypeChange("Chi") }, contentAlignment = Alignment.Center) { Text("Chi tiêu", color = if (!isIncome) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
                Box(modifier = Modifier.weight(1f).fillMaxHeight().clickable(interactionSource = interactionSource, indication = null) { onTypeChange("Thu") }, contentAlignment = Alignment.Center) { Text("Thu nhập", color = if (isIncome) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
            }
        }
    }
}

// 👉 Đã thêm modifier truyền từ ngoài vào để fix cứng width
@Composable
fun CategoryCard(item: CategoryItem, isSelected: Boolean, activeColor: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val unselectedBgColor = MaterialTheme.colorScheme.surfaceVariant
    val selectedBgColor = activeColor.copy(alpha = 0.15f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier // Trọng số chia đều màn hình
            .padding(bottom = 16.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(60.dp) // Size icon giữ nguyên vuông vức
                .background(if (isSelected) selectedBgColor else unselectedBgColor, RoundedCornerShape(16.dp))
                .border(width = if (isSelected) 2.dp else 0.dp, color = if (isSelected) activeColor else Color.Transparent, shape = RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(item.icon, fontSize = 28.sp)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = item.name, fontSize = 11.sp, maxLines = 1,
            color = if (isSelected) activeColor else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}