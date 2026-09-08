package com.example.walletwise.presentation.profile.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.walletwise.presentation.home.CategoryItem
import com.example.walletwise.presentation.home.TransactionViewModel

enum class TransactionType(val title: String) {
    EXPENSE("Chi tiêu"),
    INCOME("Thu nhập")
}

data class CategoryModel(
    val id: String,
    val name: String,
    val type: TransactionType,
    val icon: ImageVector,
    val isCustom: Boolean = false
)

@Composable
fun CategoryManagementView(viewModel: TransactionViewModel, onBack: () -> Unit) {
    val categories by viewModel.categories.collectAsState()
    var selectedTab by remember { mutableStateOf("Chi") }
    var showAddScreen by remember { mutableStateOf(false) }

    var editingCategory by remember { mutableStateOf<CategoryItem?>(null) }
    var deletingCategory by remember { mutableStateOf<CategoryItem?>(null) }
    var selectedSwapCategory by remember { mutableStateOf<CategoryItem?>(null) }
    val context = LocalContext.current

    if (showAddScreen) {
        AddCategoryScreen(
            onBack = { showAddScreen = false },
            onSave = { newCategory ->
                viewModel.addCategory(newCategory)
                showAddScreen = false
            }
        )
        return
    }

    if (editingCategory != null) {
        EditCategoryScreen(
            categoryToEdit = editingCategory!!,
            onBack = { editingCategory = null },
            onSave = { updatedCategory ->
                viewModel.updateCategory(updatedCategory)
                editingCategory = null
                Toast.makeText(context, "Đã cập nhật danh mục thành công!", Toast.LENGTH_SHORT).show()
            }
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(26.dp).clickable { onBack() })
            Text("Cài đặt danh mục", color = MaterialTheme.colorScheme.onBackground, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Icon(Icons.Default.Tune, "Sắp xếp", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(26.dp))
        }

        // Tabs Chi/Thu
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
        ) {
            listOf("Chi" to "Chi tiêu", "Thu" to "Thu nhập").forEach { (type, label) ->
                val isSelected = selectedTab == type
                Box(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
                        .clickable {
                            selectedTab = type
                            selectedSwapCategory = null
                        }.padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Gray, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, fontSize = 15.sp)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (selectedSwapCategory != null) {
            Surface(
                color = Color(0xFFFFD54F).copy(alpha = 0.2f),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Đang chọn '${selectedSwapCategory?.name}'. Nhấn 'Đổi' ở danh mục 1-8 phía trên.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { selectedSwapCategory = null }) {
                        Text("Hủy", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        val displayList = categories.filter { it.type == selectedTab }
            .sortedWith(compareBy({ if (it.sortOrder == 0) Int.MAX_VALUE else it.sortOrder }, { it.name }))

        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(displayList) { index, cat ->
                val isTop8 = index < 8
                val isSwapSelected = selectedSwapCategory?.id == cat.id

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 👉 Hiển thị Emoji
                    Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(if (isTop8) Color(0xFFFFF9C4) else MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                        Text(cat.icon, fontSize = 20.sp)
                    }
                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(cat.name, color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (cat.isCustom) {
                                Spacer(Modifier.width(6.dp))
                                Text("(Tùy chỉnh)", color = Color.Gray, fontSize = 11.sp)
                            }
                        }
                        Text(
                            text = if (isTop8) "Mục ${index + 1} ở trang Thêm" else "Chưa hiển thị trang Thêm",
                            color = if (isTop8) Color(0xFF4CAF50) else Color.Gray,
                            fontSize = 11.sp
                        )
                    }

                    // 👉 NÚT ĐỔI VỊ TRÍ / TÙY CHỈNH / XÓA / SỬA
                    if (selectedSwapCategory != null) {
                        if (isTop8) {
                            Button(
                                onClick = {
                                    val cat1 = selectedSwapCategory!!
                                    val cat2 = cat
                                    val order1 = if (cat1.sortOrder != 0) cat1.sortOrder else (displayList.indexOf(cat1) + 1)
                                    val order2 = if (cat2.sortOrder != 0) cat2.sortOrder else (index + 1)

                                    viewModel.swapCategoryPositions(cat1.copy(sortOrder = order2), cat2.copy(sortOrder = order1))
                                    Toast.makeText(context, "Đã đổi vị trí thành công!", Toast.LENGTH_SHORT).show()
                                    selectedSwapCategory = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD54F)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("Đổi", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        } else if (isSwapSelected) {
                            OutlinedButton(
                                onClick = { selectedSwapCategory = null },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("Đang chọn", fontSize = 12.sp)
                            }
                        }
                    } else {
                        if (!isTop8) {
                            Button(
                                onClick = {
                                    selectedSwapCategory = cat
                                    Toast.makeText(context, "Chọn 1 trong 8 danh mục phía trên để đổi vị trí với '${cat.name}'", Toast.LENGTH_LONG).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD54F)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("Tùy chỉnh", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Spacer(Modifier.width(8.dp))
                        }

                        // 👉 Thao tác Xóa / Sửa
                        IconButton(onClick = { deletingCategory = cat }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, "Xóa", tint = Color.Gray, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = { editingCategory = cat }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Edit, "Sửa", tint = Color.Gray, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }

        Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Button(
                onClick = { showAddScreen = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD54F)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, null, tint = Color.Black)
                Spacer(Modifier.width(8.dp))
                Text("Thêm danh mục", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }

    // 👉 HỘP THOẠI XÁC NHẬN XÓA (DELETE CONFIRMATION DIALOG)
    if (deletingCategory != null) {
        AlertDialog(
            onDismissRequest = { deletingCategory = null },
            title = { Text("Xác nhận xóa danh mục", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
            text = { Text("Bạn có chắc chắn muốn xóa danh mục '${deletingCategory?.name}' không? Thao tác này không thể hoàn tác.", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface) },
            confirmButton = {
                Button(
                    onClick = {
                        deletingCategory?.let { viewModel.deleteCategory(it.id) }
                        Toast.makeText(context, "Đã xóa danh mục", Toast.LENGTH_SHORT).show()
                        deletingCategory = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFA3B70))
                ) {
                    Text("Xóa", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingCategory = null }) {
                    Text("Hủy", color = Color.Gray, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddCategoryScreen(onBack: () -> Unit, onSave: (CategoryItem) -> Unit) {
    var categoryName by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("Chi") }
    var selectedIcon by remember { mutableStateOf("🎮") }
    val focusManager = LocalFocusManager.current

    // Data các Emoji
    val iconGroups = mapOf(
        "Giải trí" to listOf("🎮", "🎰", "🎬", "🎵", "🎾", "🎤"),
        "Đồ ăn" to listOf("🍔", "🍟", "🍕", "☕", "🍰", "🍷"),
        "Di chuyển" to listOf("🚗", "🛵", "✈️", "🚌", "⛽"),
        "Khác" to listOf("🛒", "🏠", "🐶", "🎁", "🎓", "💸")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Hủy",
                color = Color.Gray,
                fontSize = 16.sp,
                modifier = Modifier.clickable { onBack() }
            )
            Text(
                text = "Thêm danh mục",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Lưu",
                tint = if (categoryName.isNotBlank()) MaterialTheme.colorScheme.primary else Color.Gray,
                modifier = Modifier.clickable(
                    enabled = categoryName.isNotBlank()
                ) {
                    onSave(
                        CategoryItem(
                            name = categoryName,
                            type = selectedType,
                            icon = selectedIcon,
                            isCustom = true
                        )
                    )
                }
            )
        }

        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Lựa chọn Loại Giao Dịch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("Chi" to "Chi tiêu", "Thu" to "Thu nhập").forEach { (type, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { selectedType = type }
                            .padding(horizontal = 16.dp)
                    ) {
                        RadioButton(
                            selected = (selectedType == type),
                            onClick = { selectedType = type },
                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFFFD54F))
                        )
                        Text(
                            text = label,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Khu vực Nhập Tên Danh Mục
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFD54F)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = selectedIcon, fontSize = 24.sp)
                }
                Spacer(modifier = Modifier.width(16.dp))
                androidx.compose.foundation.text.BasicTextField(
                    value = categoryName,
                    onValueChange = { categoryName = it },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 16.sp
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(16.dp),
                    decorationBox = { innerTextField ->
                        if (categoryName.isEmpty()) {
                            Text(
                                text = "Vui lòng nhập tên danh mục",
                                color = Color.Gray,
                                fontSize = 15.sp
                            )
                        }
                        innerTextField()
                    }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Lưới Chọn Icon
            iconGroups.forEach { (groupName, icons) ->
                Text(
                    text = groupName,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    textAlign = TextAlign.Center
                )
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    maxItemsInEachRow = 4
                ) {
                    icons.forEach { icon ->
                        val isIconSelected = selectedIcon == icon
                        Box(
                            modifier = Modifier
                                .padding(8.dp)
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(if (isIconSelected) Color(0xFFFFD54F) else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    selectedIcon = icon
                                    focusManager.clearFocus()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = icon, fontSize = 24.sp)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditCategoryScreen(categoryToEdit: CategoryItem, onBack: () -> Unit, onSave: (CategoryItem) -> Unit) {
    var categoryName by remember { mutableStateOf(categoryToEdit.name) }
    var selectedType by remember { mutableStateOf(categoryToEdit.type) }
    var selectedIcon by remember { mutableStateOf(categoryToEdit.icon.ifBlank { "🎮" }) }
    val focusManager = LocalFocusManager.current

    // Data các Emoji
    val iconGroups = mapOf(
        "Giải trí" to listOf("🎮", "🎰", "🎬", "🎵", "🎾", "🎤"),
        "Đồ ăn" to listOf("🍔", "🍟", "🍕", "☕", "🍰", "🍷"),
        "Di chuyển" to listOf("🚗", "🛵", "✈️", "🚌", "⛽"),
        "Khác" to listOf("🛒", "🏠", "🐶", "🎁", "🎓", "💸")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Hủy",
                color = Color.Gray,
                fontSize = 16.sp,
                modifier = Modifier.clickable { onBack() }
            )
            Text(
                text = "Sửa danh mục",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Lưu",
                tint = if (categoryName.isNotBlank()) MaterialTheme.colorScheme.primary else Color.Gray,
                modifier = Modifier.clickable(
                    enabled = categoryName.isNotBlank()
                ) {
                    onSave(
                        categoryToEdit.copy(
                            name = categoryName,
                            type = selectedType,
                            icon = selectedIcon
                        )
                    )
                }
            )
        }

        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Lựa chọn Loại Giao Dịch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("Chi" to "Chi tiêu", "Thu" to "Thu nhập").forEach { (type, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { selectedType = type }
                            .padding(horizontal = 16.dp)
                    ) {
                        RadioButton(
                            selected = (selectedType == type),
                            onClick = { selectedType = type },
                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFFFD54F))
                        )
                        Text(
                            text = label,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Khu vực Nhập Tên Danh Mục
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFD54F)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = selectedIcon, fontSize = 24.sp)
                }
                Spacer(modifier = Modifier.width(16.dp))
                androidx.compose.foundation.text.BasicTextField(
                    value = categoryName,
                    onValueChange = { categoryName = it },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 16.sp
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(16.dp),
                    decorationBox = { innerTextField ->
                        if (categoryName.isEmpty()) {
                            Text(
                                text = "Vui lòng nhập tên danh mục",
                                color = Color.Gray,
                                fontSize = 15.sp
                            )
                        }
                        innerTextField()
                    }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Lưới Chọn Icon
            iconGroups.forEach { (groupName, icons) ->
                Text(
                    text = groupName,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    textAlign = TextAlign.Center
                )
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    maxItemsInEachRow = 4
                ) {
                    icons.forEach { icon ->
                        val isIconSelected = selectedIcon == icon
                        Box(
                            modifier = Modifier
                                .padding(8.dp)
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(if (isIconSelected) Color(0xFFFFD54F) else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    selectedIcon = icon
                                    focusManager.clearFocus()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = icon, fontSize = 24.sp)
                        }
                    }
                }
            }
        }
    }
}
