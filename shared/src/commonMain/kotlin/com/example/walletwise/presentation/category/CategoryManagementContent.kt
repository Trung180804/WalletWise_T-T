package com.example.walletwise.presentation.category

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.walletwise.domain.model.CATEGORY_FALLBACK_ICON
import com.example.walletwise.domain.model.Category
import com.example.walletwise.domain.model.DefaultCategories
import com.example.walletwise.shared.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val CategoryGold = Color(0xFFFFD54F)
private val SelectableCategoryIcons: Set<String> = (
    DefaultCategories.map { it.icon } + listOf(
        "🎮", "🎰", "🎬", "🎵", "🎾", "🎤",
        "🍔", "🍟", "🍕", "☕", "🍰", "🍷",
        "🚗", "🛵", "✈️", "🚌", "⛽",
        "🛒", "🏠", "🐶", "🎁", "🎓", "💸"
    )
).toSet()

fun categoryIconForDisplay(iconKey: String): String =
    iconKey.takeIf { it in SelectableCategoryIcons } ?: CATEGORY_FALLBACK_ICON

@Composable
fun CategoryManagementContent(
    state: CategoryUiState,
    onBack: () -> Unit,
    onTypeSelected: (String) -> Unit,
    onOpenAdd: () -> Unit,
    onOpenEdit: (Category) -> Unit,
    onCancelEditor: () -> Unit,
    onNameChanged: (String) -> Unit,
    onFormTypeSelected: (String) -> Unit,
    onIconSelected: (String) -> Unit,
    onSubmit: () -> Unit,
    onRequestDelete: (Category) -> Unit,
    onCancelDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onSelectSwapCategory: (Category) -> Unit,
    onCancelSwap: () -> Unit,
    onSwapWith: (Category) -> Unit
) {
    when (state.editorMode) {
        CategoryEditorMode.ADD, CategoryEditorMode.EDIT -> CategoryEditorContent(
            state = state,
            onCancel = onCancelEditor,
            onNameChanged = onNameChanged,
            onTypeSelected = onFormTypeSelected,
            onIconSelected = onIconSelected,
            onSubmit = onSubmit
        )
        CategoryEditorMode.NONE -> CategoryListContent(
            state = state,
            onBack = onBack,
            onTypeSelected = onTypeSelected,
            onOpenAdd = onOpenAdd,
            onOpenEdit = onOpenEdit,
            onRequestDelete = onRequestDelete,
            onSelectSwapCategory = onSelectSwapCategory,
            onCancelSwap = onCancelSwap,
            onSwapWith = onSwapWith
        )
    }

    state.deletingCategory?.let { category ->
        AlertDialog(
            onDismissRequest = onCancelDelete,
            title = {
                Text(
                    stringResource(Res.string.category_delete_title),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    stringResource(Res.string.category_delete_question, category.name),
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                Button(
                    onClick = onConfirmDelete,
                    enabled = !state.isDeleting,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFA3B70))
                ) {
                    if (state.isDeleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            stringResource(Res.string.category_delete),
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelDelete, enabled = !state.isDeleting) {
                    Text(
                        stringResource(Res.string.category_cancel),
                        color = Color.Gray,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        )
    }
}

@Composable
private fun CategoryListContent(
    state: CategoryUiState,
    onBack: () -> Unit,
    onTypeSelected: (String) -> Unit,
    onOpenAdd: () -> Unit,
    onOpenEdit: (Category) -> Unit,
    onRequestDelete: (Category) -> Unit,
    onSelectSwapCategory: (Category) -> Unit,
    onCancelSwap: () -> Unit,
    onSwapWith: (Category) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                stringResource(Res.string.category_back),
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(26.dp).clickable(onClick = onBack)
            )
            Text(
                stringResource(Res.string.category_title),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )
            Icon(
                Icons.Default.Tune,
                stringResource(Res.string.category_sort),
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(26.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
        ) {
            CategoryTypeTabs.forEach { tab ->
                val selected = state.selectedType == tab.type
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
                        .clickable { onTypeSelected(tab.type) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(tab.label),
                        color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Gray,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 15.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        state.selectedSwapCategory?.let { selected ->
            Surface(
                color = CategoryGold.copy(alpha = 0.2f),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        stringResource(Res.string.category_swap_banner, selected.name),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onCancelSwap, enabled = !state.isSwapping) {
                        Text(
                            stringResource(Res.string.category_cancel),
                            color = Color.Red,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        Box(modifier = Modifier.weight(1f)) {
            when {
                state.isLoading && state.categories.isEmpty() -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = CategoryGold
                )
                state.isEmpty -> Text(
                    stringResource(Res.string.category_empty),
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.Gray
                )
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(
                        items = state.displayedCategories,
                        key = { _, category -> category.id }
                    ) { index, category ->
                        CategoryRow(
                            category = category,
                            index = index,
                            state = state,
                            onOpenEdit = onOpenEdit,
                            onRequestDelete = onRequestDelete,
                            onSelectSwapCategory = onSelectSwapCategory,
                            onCancelSwap = onCancelSwap,
                            onSwapWith = onSwapWith
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Button(
                onClick = onOpenAdd,
                enabled = !state.isSubmitting && !state.isDeleting && !state.isSwapping,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CategoryGold),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, null, tint = Color.Black)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(Res.string.category_add),
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
private fun CategoryRow(
    category: Category,
    index: Int,
    state: CategoryUiState,
    onOpenEdit: (Category) -> Unit,
    onRequestDelete: (Category) -> Unit,
    onSelectSwapCategory: (Category) -> Unit,
    onCancelSwap: () -> Unit,
    onSwapWith: (Category) -> Unit
) {
    val isTopEight = index < 8
    val isSwapSelected = state.selectedSwapCategory?.id == category.id
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (isTopEight) Color(0xFFFFF9C4) else MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(categoryIconForDisplay(category.icon), fontSize = 20.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    category.name,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (category.isCustom) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(Res.string.category_custom_label),
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
            }
            Text(
                if (isTopEight) {
                    stringResource(Res.string.category_top_position, index + 1)
                } else {
                    stringResource(Res.string.category_not_on_add)
                },
                color = if (isTopEight) Color(0xFF4CAF50) else Color.Gray,
                fontSize = 11.sp
            )
        }

        if (state.selectedSwapCategory != null) {
            if (isTopEight) {
                Button(
                    onClick = { onSwapWith(category) },
                    enabled = !state.isSwapping && !isSwapSelected,
                    colors = ButtonDefaults.buttonColors(containerColor = CategoryGold),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text(
                        stringResource(Res.string.category_swap),
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            } else if (isSwapSelected) {
                OutlinedButton(
                    onClick = onCancelSwap,
                    enabled = !state.isSwapping,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text(stringResource(Res.string.category_selected), fontSize = 12.sp)
                }
            }
        } else {
            if (!isTopEight) {
                Button(
                    onClick = { onSelectSwapCategory(category) },
                    colors = ButtonDefaults.buttonColors(containerColor = CategoryGold),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text(
                        stringResource(Res.string.category_customize),
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
            IconButton(onClick = { onRequestDelete(category) }, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Delete,
                    stringResource(Res.string.category_delete),
                    tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = { onOpenEdit(category) }, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Edit,
                    stringResource(Res.string.category_edit),
                    tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryEditorContent(
    state: CategoryUiState,
    onCancel: () -> Unit,
    onNameChanged: (String) -> Unit,
    onTypeSelected: (String) -> Unit,
    onIconSelected: (String) -> Unit,
    onSubmit: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                stringResource(Res.string.category_cancel),
                color = Color.Gray,
                fontSize = 16.sp,
                modifier = Modifier.clickable(enabled = !state.isSubmitting, onClick = onCancel)
            )
            Text(
                stringResource(
                    if (state.editorMode == CategoryEditorMode.ADD) {
                        Res.string.category_add_title
                    } else {
                        Res.string.category_edit_title
                    }
                ),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            if (state.isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    Icons.Default.Check,
                    stringResource(Res.string.category_save),
                    tint = if (state.formName.isNotBlank()) MaterialTheme.colorScheme.primary else Color.Gray,
                    modifier = Modifier.clickable(enabled = state.formName.isNotBlank(), onClick = onSubmit)
                )
            }
        }

        Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            Row(
                modifier = Modifier.fillMaxWidth().selectableGroup(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CategoryTypeTabs.forEach { tab ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onTypeSelected(tab.type) }.padding(horizontal = 16.dp)
                    ) {
                        RadioButton(
                            selected = state.formType == tab.type,
                            onClick = { onTypeSelected(tab.type) },
                            colors = RadioButtonDefaults.colors(selectedColor = CategoryGold)
                        )
                        Text(stringResource(tab.label), color = MaterialTheme.colorScheme.onBackground)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier.size(50.dp).clip(CircleShape).background(CategoryGold),
                    contentAlignment = Alignment.Center
                ) {
                    Text(categoryIconForDisplay(state.formIcon), fontSize = 24.sp)
                }
                Spacer(Modifier.width(16.dp))
                androidx.compose.foundation.text.BasicTextField(
                    value = state.formName,
                    onValueChange = onNameChanged,
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 16.sp
                    ),
                    enabled = !state.isSubmitting,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(16.dp),
                    decorationBox = { innerTextField ->
                        if (state.formName.isEmpty()) {
                            Text(
                                stringResource(Res.string.category_name_placeholder),
                                color = Color.Gray,
                                fontSize = 15.sp
                            )
                        }
                        innerTextField()
                    }
                )
            }
            Spacer(Modifier.height(32.dp))
            CategoryIconGroups.forEach { group ->
                Text(
                    stringResource(group.label),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    textAlign = TextAlign.Center
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    maxItemsInEachRow = 4
                ) {
                    group.icons.forEach { icon ->
                        Box(
                            modifier = Modifier
                                .padding(8.dp)
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(
                                    if (state.formIcon == icon) CategoryGold
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable(enabled = !state.isSubmitting) {
                                    onIconSelected(icon)
                                    focusManager.clearFocus()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(icon, fontSize = 24.sp)
                        }
                    }
                }
            }
        }
    }
}

private data class CategoryTypeTab(val type: String, val label: StringResource)
private val CategoryTypeTabs = listOf(
    CategoryTypeTab("Chi", Res.string.category_expense),
    CategoryTypeTab("Thu", Res.string.category_income)
)

private data class CategoryIconGroup(val label: StringResource, val icons: List<String>)
private val CategoryIconGroups = listOf(
    CategoryIconGroup(Res.string.category_icon_entertainment, listOf("🎮", "🎰", "🎬", "🎵", "🎾", "🎤")),
    CategoryIconGroup(Res.string.category_icon_food, listOf("🍔", "🍟", "🍕", "☕", "🍰", "🍷")),
    CategoryIconGroup(Res.string.category_icon_transport, listOf("🚗", "🛵", "✈️", "🚌", "⛽")),
    CategoryIconGroup(Res.string.category_icon_other, listOf("🛒", "🏠", "🐶", "🎁", "🎓", "💸"))
)
