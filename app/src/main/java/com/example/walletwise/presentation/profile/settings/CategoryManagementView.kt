package com.example.walletwise.presentation.profile.settings

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.walletwise.presentation.category.CategoryManagementContent
import com.example.walletwise.presentation.category.CategoryMessage
import com.example.walletwise.presentation.category.CategoryUiEvent
import com.example.walletwise.presentation.home.TransactionViewModel

/** Android route/lifecycle/platform-feedback boundary for the shared Category UI. */
@Composable
fun CategoryManagementView(
    viewModel: TransactionViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val presenter = remember(viewModel, scope) { viewModel.createCategoryPresenter(scope) }
    val state by presenter.state.collectAsStateWithLifecycle()

    DisposableEffect(presenter) {
        onDispose { presenter.close() }
    }

    val event = state.pendingEvent
    LaunchedEffect(event?.id) {
        val envelope = event ?: return@LaunchedEffect
        when (val value = envelope.event) {
            CategoryUiEvent.NavigateBack -> onBack()
            is CategoryUiEvent.Message -> {
                categoryToastText(value.kind, value.argument)?.let { message ->
                    val duration = if (value.kind == CategoryMessage.SELECT_SWAP_TARGET) {
                        Toast.LENGTH_LONG
                    } else {
                        Toast.LENGTH_SHORT
                    }
                    Toast.makeText(context, message, duration).show()
                }
            }
        }
        presenter.consumeEvent(envelope.id)
    }

    CategoryManagementContent(
        state = state,
        onBack = presenter::onBack,
        onTypeSelected = presenter::onTypeSelected,
        onOpenAdd = presenter::onOpenAdd,
        onOpenEdit = presenter::onOpenEdit,
        onCancelEditor = presenter::onCancelEditor,
        onNameChanged = presenter::onNameChanged,
        onFormTypeSelected = presenter::onFormTypeSelected,
        onIconSelected = presenter::onIconSelected,
        onSubmit = presenter::onSubmit,
        onRequestDelete = presenter::onRequestDelete,
        onCancelDelete = presenter::onCancelDelete,
        onConfirmDelete = presenter::onConfirmDelete,
        onSelectSwapCategory = presenter::onSelectSwapCategory,
        onCancelSwap = presenter::onCancelSwap,
        onSwapWith = presenter::onSwapWith
    )
}

private fun categoryToastText(kind: CategoryMessage, argument: String?): String? = when (kind) {
    CategoryMessage.ADDED -> null
    CategoryMessage.UPDATED -> "Đã cập nhật danh mục thành công!"
    CategoryMessage.DELETED -> "Đã xóa danh mục"
    CategoryMessage.SWAPPED -> "Đã đổi vị trí thành công!"
    CategoryMessage.SELECT_SWAP_TARGET ->
        "Chọn 1 trong 8 danh mục phía trên để đổi vị trí với '${argument.orEmpty()}'"
    CategoryMessage.ERROR -> argument ?: "Không thể cập nhật danh mục"
}
