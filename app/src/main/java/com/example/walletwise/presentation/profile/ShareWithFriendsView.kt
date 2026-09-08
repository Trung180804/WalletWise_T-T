package com.example.walletwise.presentation.profile
// giới thiệu cho bạn bè
import android.content.Context
import android.content.Intent

fun shareAppWithFriends(context: Context) {
    val sendIntent: Intent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(
            Intent.EXTRA_TEXT,
            "Tải ngay ứng dụng WalletWise - Quản lý tài chính cá nhân thông minh!"
        )
        type = "text/plain"
    }
    val shareIntent = Intent.createChooser(sendIntent, "Giới thiệu WalletWise cho bạn bè")
    context.startActivity(shareIntent)
}
