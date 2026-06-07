package com.example.walletwise.presentation.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.rememberAsyncImagePainter
import java.io.File

data class CategoryItem(val name: String, val icon: String)

val expenseCategories = listOf(
    CategoryItem("Ăn uống", "🍔"), CategoryItem("Mua sắm", "🛒"),
    CategoryItem("Nhà cửa", "🏠"), CategoryItem("Di chuyển", "🚗"),
    CategoryItem("Y tế", "💊"), CategoryItem("Giải trí", "🎮"),
    CategoryItem("Hóa đơn", "💳"), CategoryItem("Học tập", "📚")
)

val incomeCategories = listOf(
    CategoryItem("Lương", "💰"), CategoryItem("Thưởng", "🎁"),
    CategoryItem("Đầu tư", "📈"), CategoryItem("Kinh doanh", "💼"),
    CategoryItem("Part-time", "🎯"), CategoryItem("Giải thưởng", "🏆"),
    CategoryItem("Quà tặng", "💝"), CategoryItem("Khác", "🔄")
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

    val initialAmount = if (isEditMode) txToEdit!!.amount.toLong().toString() else ""
    var amount by remember { mutableStateOf(initialAmount) }

    var note by remember { mutableStateOf(txToEdit?.note ?: "") }
    var category by remember { mutableStateOf(txToEdit?.category ?: expenseCategories[0].name) }
    var selectedWallet by remember { mutableStateOf(txToEdit?.paymentMethod ?: "Tiền mặt") }

    val mainColor = if (type == "Chi") Color(0xFFFA3B70) else Color(0xFF00C875)
    val bgColor = Color(0xFFF8F9FA)
    val currentCategories = if (type == "Chi") expenseCategories else incomeCategories

    val isLoading by viewModel.isLoading.collectAsState()

    LaunchedEffect(type) {
        if (currentCategories.none { it.name == category }) {
            category = currentCategories[0].name
        }
    }

    val context = LocalContext.current
    var tempImageUri by remember { mutableStateOf<Uri?>(null) }
    var capturedImageUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) { capturedImageUri = tempImageUri }
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

            val authority = "com.example.walletwise.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)

            tempImageUri = uri
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "LỖI CAMERA: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    Scaffold(
        containerColor = Color.White,
        // 👉 THÊM THANH ĐIỀU HƯỚNG Ở ĐÂY
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isEditMode) "Sửa giao dịch" else "Thêm giao dịch",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { handleBack() }) { // Bấm vào là gọi handleBack để dọn dẹp và thoát
                        Icon(Icons.Default.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
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

            // Tab Chọn Chi Tiêu / Thu Nhập
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bgColor, RoundedCornerShape(24.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TabButton(text = "Chi tiêu", isSelected = type == "Chi", activeColor = Color(0xFFFA3B70), modifier = Modifier.weight(1f)) { type = "Chi" }
                TabButton(text = "Thu nhập", isSelected = type == "Thu", activeColor = Color(0xFF00C875), modifier = Modifier.weight(1f)) { type = "Thu" }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Khu vực Camera
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .background(bgColor, RoundedCornerShape(24.dp))
                    .clickable { launchCamera() },
                contentAlignment = Alignment.Center
            ) {
                val imageToShow = capturedImageUri ?: txToEdit?.imageUrl
                if (imageToShow != null) {
                    Image(
                        painter = rememberAsyncImagePainter(imageToShow),
                        contentDescription = "Hóa đơn",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(24.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(color = Color.Gray.copy(alpha = 0.6f), shape = RoundedCornerShape(12.dp)) {
                            Text("AI OCR", color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("📷", fontSize = 40.sp)
                    }
                    Box(modifier = Modifier.align(Alignment.BottomCenter).offset(y = 16.dp).size(40.dp).background(Color.White, CircleShape).border(3.dp, Color(0xFF9C27B0), CircleShape))
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Số tiền & Wallet
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Số tiền", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.DarkGray)
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("0") },
                        trailingIcon = { Text("đ", color = Color.Gray, modifier = Modifier.padding(end = 16.dp)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.LightGray, focusedBorderColor = mainColor)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Box {
                        var expandedWallet by remember { mutableStateOf(false) }
                        val wallets = listOf("Tiền mặt", "Chuyển khoản", "Thẻ tín dụng")

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color.LightGray),
                            modifier = Modifier
                                .height(56.dp)
                                .clickable { expandedWallet = true },
                            color = Color.Transparent
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
                                Text(selectedWallet, fontWeight = FontWeight.Bold)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        }

                        DropdownMenu(
                            expanded = expandedWallet,
                            onDismissRequest = { expandedWallet = false },
                            modifier = Modifier.background(Color.White)
                        ) {
                            wallets.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option, color = Color.Black) },
                                    onClick = {
                                        selectedWallet = option
                                        expandedWallet = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Danh mục
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Danh mục", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.DarkGray)
                Spacer(modifier = Modifier.height(12.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    maxItemsInEachRow = 4
                ) {
                    currentCategories.forEach { cat ->
                        CategoryCard(
                            item = cat,
                            isSelected = category == cat.name,
                            activeColor = mainColor,
                            onClick = { category = cat.name }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Ghi chú
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Ghi chú", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.DarkGray)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    placeholder = { Text("Thêm ghi chú...", color = Color.Gray) },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.LightGray, focusedBorderColor = mainColor)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Nút Lưu
            Button(
                enabled = !isLoading,
                onClick = {
                    val amountValue = amount.toDoubleOrNull() ?: 0.0

                    if (isEditMode) {
                        viewModel.updateTransaction(
                            transaction = txToEdit!!.copy(
                                amount = amountValue,
                                type = type,
                                category = category,
                                note = note,
                                paymentMethod = selectedWallet
                            ),
                            imageUri = capturedImageUri,
                            context = context
                        ) {
                            handleBack()
                        }
                    } else {
                        viewModel.addTransaction(amountValue, type, category, note, selectedWallet, capturedImageUri, context) {
                            handleBack()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(55.dp),
                colors = ButtonDefaults.buttonColors(containerColor = mainColor),
                shape = RoundedCornerShape(16.dp)
            ) {
                val btnText = if (isEditMode) "Cập nhật giao dịch"
                else if (type == "Chi") "Lưu chi tiêu" else "Lưu thu nhập"
                Text(btnText, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun TabButton(text: String, isSelected: Boolean, activeColor: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) activeColor else Color(0xFFA9A9A9))
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun CategoryCard(item: CategoryItem, isSelected: Boolean, activeColor: Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(bottom = 16.dp)
            .width(75.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .background(if (isSelected) Color(0xFFF8F9FA) else Color(0xFFc0c0c0), RoundedCornerShape(16.dp))
                .border(
                    width = if (isSelected) 2.dp else 0.dp,
                    color = if (isSelected) activeColor else Color.Transparent,
                    shape = RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(item.icon, fontSize = 28.sp)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(item.name, fontSize = 11.sp, color = if (isSelected) activeColor else Color.DarkGray, textAlign = TextAlign.Center, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
    }
}