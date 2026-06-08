package com.example.walletwise.presentation.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.walletwise.domain.model.User
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon

// Gradient màu tối sang trọng cho Banner
private val BannerGradient = Brush.horizontalGradient(
    colors = listOf(
        Color(0xFF2196F3), // Xanh dương chủ đạo
        Color(0xFF4DD0E1)  // Cyan tươi sáng
    )
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeBannerCarousel(user: User?) {
    val pages = 3

    val pagerState = rememberPagerState(pageCount = { pages })

    // Auto slide mỗi 5 giây
    LaunchedEffect(pagerState) {
        while (true) {
            delay(5000)

            if (pagerState.currentPage == pages - 1) {
                pagerState.scrollToPage(0)
            } else {
                pagerState.animateScrollToPage(
                    pagerState.currentPage + 1
                )
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
        ) { page ->

            AnimatedVisibility(visible = true, enter = fadeIn() + slideInVertically()) {
                when (page) {
                    0 -> GreetingPage(user = user)
                    1 -> StreakDashboardBanner(user = user)
                    2 -> TipPage()
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Indicator (Chấm tròn chuyển trang)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(pages) { index ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .height(8.dp)
                        .width(if (pagerState.currentPage == index) 24.dp else 8.dp)
                        .clip(CircleShape)
                        .background(if (pagerState.currentPage == index) Color(0xFF00C2FF) else Color.LightGray)
                )
            }
        }
    }
}

@Composable
fun GreetingPage(user: User?) {
    val userName = user?.username?.takeIf { it.isNotBlank() } ?: "Người dùng"

    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BannerGradient)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Top
            ) {
                Text(
                    text = "👋 Xin chào",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = userName,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Chúc bạn một ngày quản lý tài chính hiệu quả 💰",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun StreakDashboardBanner(user: User?) {
    // 👉 Đã sửa cảnh báo vàng (Dùng mutableIntStateOf thay vì mutableStateOf)
    var currentStreak by remember { mutableIntStateOf(user?.currentStreak ?: 0) }
    var lastRecordDate by remember { mutableStateOf(user?.lastRecordDate ?: "") }

    LaunchedEffect(Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            db.collection("users").document(uid).addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    currentStreak = snapshot.getLong("currentStreak")?.toInt() ?: 0
                    lastRecordDate = snapshot.getString("lastRecordDate") ?: ""
                }
            }
        }
    }

    val todayStr = LocalDate.now(ZoneId.systemDefault()).toString()
    val isUpdatedToday = lastRecordDate == todayStr

    val randomTip = remember {
        listOf(
            "Ghi chép thêm để không đứt chuỗi!",
            "Kỷ luật tạo nên thành công!",
            "Duy trì thói quen mỗi ngày."
        ).random()
    }

    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BannerGradient)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isUpdatedToday) "Hành trình hôm nay hoàn thành!" else "Duy trì kỷ luật tài chính",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isUpdatedToday) randomTip else "Hôm nay bạn chưa thêm giao dịch. Ghi chép ngay kẻo đứt chuỗi nhé! ⏳",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 2,
                        lineHeight = 20.sp
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .background(
                            color = if (isUpdatedToday) Color(0xFFFF9800).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocalFireDepartment,
                        contentDescription = "Streak Status",
                        // Nếu đã hoàn thành (isUpdatedToday) thì màu cam, chưa thì màu xám
                        tint = if (isUpdatedToday) Color(0xFFFF9800) else Color.Gray,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$currentStreak Ngày",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isUpdatedToday) Color(0xFFFFD54F) else Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun TipPage() {
    val tips = remember {
        listOf(
            "Tiết kiệm hôm nay, an tâm ngày mai 🌟",
            "Chi tiêu thông minh, cuộc sống ổn định 📈",
            "Ghi chép mỗi ngày là chìa khóa thành công 📝",
            "Chi tiêu ít hơn số tiền kiếm được 💵",
            "Đầu tư vào kiến thức luôn sinh lời 📚",
            "Mỗi khoản tiết kiệm nhỏ đều có ý nghĩa 🎯"
        )
    }

    val currentTip = remember { tips.random() }

    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BannerGradient)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "💡 Mẹo tài chính",
                    color = Color(0xFFFFD54F),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = currentTip,
                    color = Color.White,
                    fontSize = 16.sp,
                    lineHeight = 24.sp
                )
            }
        }
    }
}