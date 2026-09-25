package com.example.walletwise.presentation.home

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.asImageBitmap
import com.example.walletwise.domain.service.MoneyInput
import com.example.walletwise.presentation.transaction.GroupedMoneyTransformation
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import coil.compose.AsyncImage
import com.example.walletwise.data.image.TransactionAttachmentState
import com.example.walletwise.presentation.transaction.transactionCategoryChoices
import androidx.compose.runtime.saveable.listSaver
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.rememberAsyncImagePainter
import java.io.File
import com.example.walletwise.domain.model.Category
import com.example.walletwise.domain.model.DefaultExpenseCategories


@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddTransactionScreen(
    viewModel: TransactionViewModel,
    onNavigateBack: () -> Unit
) {
    val session by viewModel.transactionSessionState.collectAsState()
    val sessionUid = session.userId
    val txToEdit = viewModel.transactionToEdit
    val isEditMode = txToEdit != null

    var type by rememberSaveable(sessionUid, txToEdit?.id) { mutableStateOf(txToEdit?.type ?: "Chi") }
    var amount by rememberSaveable(sessionUid, txToEdit?.id) { mutableStateOf(txToEdit?.amount?.toLong()?.toString().orEmpty()) }
    var note by rememberSaveable(sessionUid, txToEdit?.id) { mutableStateOf(txToEdit?.note ?: "") }
    var category by rememberSaveable(sessionUid, txToEdit?.id) { mutableStateOf(txToEdit?.category.orEmpty()) }
    var selectedWallet by rememberSaveable(sessionUid, txToEdit?.id) { mutableStateOf(txToEdit?.paymentMethod ?: "Tiền mặt") }

    val mainColor = if (type == "Chi") Color(0xFFFA3B70) else Color(0xFF00C875)
    val categories by viewModel.categories.collectAsState()
    val choices = transactionCategoryChoices(categories, type, category, txToEdit)
    val currentCategories = choices.firstEight
    LaunchedEffect(type, currentCategories) {
        if (category.isBlank() && !isEditMode) category = currentCategories.firstOrNull()?.name.orEmpty()
    }

    val isLoading by viewModel.isLoading.collectAsState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current // 👉 Quản lý Focus để ẩn bàn phím
    val moneyInput = com.example.walletwise.presentation.transaction.rememberMoneyInput(amount) { amount = it }

    val attachmentDirectory = File(context.cacheDir, "walletwise_transaction_attachment")
    val attachment = rememberSaveable(sessionUid, txToEdit?.id, saver = listSaver(
        save = { state: TransactionAttachmentState -> state.snapshot() },
        restore = { saved -> TransactionAttachmentState(attachmentDirectory).apply { restore(saved) } }
    )) { TransactionAttachmentState(attachmentDirectory, txToEdit?.imageUrl.orEmpty()) }
    DisposableEffect(attachment) { onDispose {
        if ((context as? android.app.Activity)?.isChangingConfigurations != true) attachment.close()
    } }
    var stableDraftId by rememberSaveable(sessionUid, txToEdit?.id) { mutableStateOf(java.util.UUID.randomUUID().toString()) }
    val formUserId = sessionUid
    var captureUserId by rememberSaveable(sessionUid) { mutableStateOf<String?>(null) }
    val dateFormatter = remember { java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(java.time.format.ResolverStyle.STRICT) }
    var dateInput by rememberSaveable(sessionUid, txToEdit?.id) { mutableStateOf(java.time.Instant.ofEpochMilli(txToEdit?.timestamp ?: System.currentTimeMillis()).atZone(java.time.ZoneId.systemDefault()).toLocalDate().format(dateFormatter)) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (captureUserId != null && captureUserId == sessionUid) attachment.cameraResult(success) else attachment.close()
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (captureUserId != null && captureUserId == sessionUid) attachment.pickerResult(uri)
    }
    val handleBack = {
        viewModel.transactionToEdit = null
        attachment.close()
        onNavigateBack()
    }
    BackHandler { handleBack() }
    fun launchCamera() {
        captureUserId = sessionUid
        try { cameraLauncher.launch(attachment.cameraUri(context)) }
        catch (_: Exception) { attachment.launchFailed() }
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
                .imePadding()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            AnimatedSegmentedSlider(currentType = type, onTypeChange = { type = it })
            Spacer(modifier = Modifier.height(32.dp))

            Box(Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                if (attachment.previewModel != null) AsyncImage(attachment.previewModel, "Ảnh đính kèm giao dịch", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                else Text("Ảnh đính kèm giao dịch", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ launchCamera() }, enabled = !isLoading) { Text("Chụp ảnh") }
                OutlinedButton({
                    captureUserId = sessionUid
                    try { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    catch (_: Exception) { attachment.launchFailed() }
                }, enabled = !isLoading) { Text("Thư viện") }
            }
            if (attachment.previewModel != null) TextButton(attachment::remove, enabled = !isLoading) { Text("Xóa ảnh") }
            attachment.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(16.dp))
            // Số tiền & Wallet
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Số tiền", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = moneyInput.first,
                        onValueChange = moneyInput.second,
                        visualTransformation = GroupedMoneyTransformation,
                        modifier = Modifier.weight(1f).testTag("transaction-amount"),
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
                    maxItemsInEachRow = 4
                ) {
                    currentCategories.forEach { cat ->
                        CategoryCard(
                            item = cat,
                            isSelected = category == cat.name,
                            activeColor = mainColor,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                category = cat.name
                                focusManager.clearFocus() // Ẩn phím khi bấm chọn danh mục
                            }
                        )
                    }
                }
                choices.currentOutside?.let { current ->
                    FilterChip(selected = true, onClick = {}, label = { Text("Đang chọn: ${current.name}") })
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

            OutlinedTextField(dateInput, { dateInput = it }, label = { Text("Ngày dd/MM/yyyy") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            // Nút Lưu
            Button(
                enabled = !isLoading,
                onClick = {
                    focusManager.clearFocus()
                    val amountValue = MoneyInput.amount(amount)?.toDouble()
                    if (amountValue == null || amountValue <= 0) {
                        android.widget.Toast.makeText(context, "Số tiền không hợp lệ", android.widget.Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (categories.none { it.type == type && it.name == category } && choices.currentOutside == null) {
                        android.widget.Toast.makeText(context, "Vui lòng chọn danh mục", android.widget.Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val transactionDate = try { com.example.walletwise.data.draft.FormTransactionTime.resolve(
                        java.time.LocalDate.parse(dateInput,dateFormatter),txToEdit?.timestamp,null,
                        System.currentTimeMillis(),java.time.ZoneId.systemDefault()) } catch (_: java.time.DateTimeException) { null }
                    if (transactionDate == null || selectedWallet.isBlank()) {
                        android.widget.Toast.makeText(context, "Vui lòng chọn ngày và phương thức thanh toán", android.widget.Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (isEditMode) {
                        val categoryId = categories.firstOrNull { it.type == type && it.name == category }?.id
                            ?: txToEdit?.takeIf { it.type == type && it.category == category }?.categoryId.orEmpty()
                        viewModel.updateTransaction(requireNotNull(txToEdit).copy(amount = amountValue, type = type, category = category, categoryId = categoryId,
                            note = note, paymentMethod = selectedWallet, timestamp = transactionDate, imageUrl = attachment.existingUrl), attachment.selectedUri, context) { handleBack() }
                    } else {
                        viewModel.addTransaction(amountValue, type, category, note, selectedWallet, attachment.selectedUri, context, draftId = stableDraftId, transactionTimestamp = transactionDate, expectedUserId = formUserId) { handleBack() }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(55.dp),
                colors = ButtonDefaults.buttonColors(containerColor = mainColor),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(if (isEditMode) "Cập nhật giao dịch" else "Xác nhận lưu", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
fun CategoryCard(item: Category, isSelected: Boolean, activeColor: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
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
