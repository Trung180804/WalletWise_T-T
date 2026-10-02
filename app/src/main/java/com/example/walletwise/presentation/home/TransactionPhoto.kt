package com.example.walletwise.presentation.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.imageLoader
import coil.compose.SubcomposeAsyncImage
import com.example.walletwise.presentation.transaction.NoTransactionImage

/** Blank URLs never enter Coil; all loading/error states retain the caller's fixed frame. */
@Composable
fun TransactionPhoto(imageUrl: String?, modifier: Modifier = Modifier, imageLoader: ImageLoader = LocalContext.current.imageLoader) {
    val url = imageUrl?.trim()?.takeIf { it.isNotEmpty() }
    if (url == null) NoTransactionImage(modifier)
    else SubcomposeAsyncImage(model = url, imageLoader = imageLoader, contentDescription = "Ảnh giao dịch",
        modifier = modifier, contentScale = ContentScale.Crop,
        loading = { NoTransactionImage(Modifier.matchParentSize()) },
        error = { NoTransactionImage(Modifier.matchParentSize()) })
}
