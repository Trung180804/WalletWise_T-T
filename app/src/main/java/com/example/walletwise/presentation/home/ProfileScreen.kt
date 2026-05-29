package com.example.walletwise.presentation.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.walletwise.R // 👉 Thêm dòng này để nhận diện được ảnh R.drawable.logo
import com.google.firebase.auth.FirebaseAuth
import java.text.DecimalFormat

// ================================================================
// ROUTE ENUM
// ================================================================
enum class ProfileRoute {
    MAIN, EDIT_PROFILE, SETTINGS,
    FONT_SIZE, CURRENCY, DEFAULT_CURRENCY, THEME,
    RECURRING, ADD_RECURRING,
    ABOUT_US, REMINDERS
}

// ================================================================
// ENTRY POINT
// ================================================================
@Composable
fun ProfileScreen(onLogout: () -> Unit, onSubScreenChange: (Boolean) -> Unit) {
    var currentRoute by remember { mutableStateOf(ProfileRoute.MAIN) }

    LaunchedEffect(currentRoute) {
        onSubScreenChange(currentRoute != ProfileRoute.MAIN)
    }

    BackHandler(enabled = currentRoute != ProfileRoute.MAIN) {
        currentRoute = when (currentRoute) {
            ProfileRoute.ADD_RECURRING -> ProfileRoute.RECURRING
            ProfileRoute.FONT_SIZE,
            ProfileRoute.THEME,
            ProfileRoute.RECURRING,
            ProfileRoute.REMINDERS,
            ProfileRoute.DEFAULT_CURRENCY -> ProfileRoute.SETTINGS
            else                          -> ProfileRoute.MAIN
        }
    }

    val isDark = LocalAppTheme.current.value
    val bgColor = if (isDark) Color(0xFF121212) else Color(0xFFF4F6F8)

    Box(modifier = Modifier.fillMaxSize().background(bgColor)) {
        when (currentRoute) {
            ProfileRoute.MAIN         -> MainProfileView(onLogout) { currentRoute = it }
            ProfileRoute.EDIT_PROFILE -> EditProfileView { currentRoute = ProfileRoute.MAIN }
            ProfileRoute.SETTINGS     -> SettingsMainView(
                onNavigate = { currentRoute = it },
                onBack     = { currentRoute = ProfileRoute.MAIN }
            )
            ProfileRoute.FONT_SIZE    -> FontSizeView { currentRoute = ProfileRoute.SETTINGS }
            ProfileRoute.CURRENCY     -> CurrencyConverterView { currentRoute = ProfileRoute.MAIN }
            ProfileRoute.THEME        -> ThemeView { currentRoute = ProfileRoute.SETTINGS }
            ProfileRoute.RECURRING    -> RecurringView(onAdd = { currentRoute = ProfileRoute.ADD_RECURRING }, onBack = { currentRoute = ProfileRoute.SETTINGS })
            ProfileRoute.ADD_RECURRING -> AddRecurringView { currentRoute = ProfileRoute.RECURRING }
            ProfileRoute.ABOUT_US      -> AboutUsView { currentRoute = ProfileRoute.MAIN }
            ProfileRoute.REMINDERS     -> RemindersView { currentRoute = ProfileRoute.SETTINGS }
            ProfileRoute.DEFAULT_CURRENCY -> DefaultCurrencyView { currentRoute = ProfileRoute.SETTINGS }
        }
    }
}

// ================================================================
// 1. MÀN HÌNH CHÍNH PROFILE
// ================================================================
// ================================================================
// 1. MÀN HÌNH CHÍNH PROFILE (ĐÃ THÊM XÁC NHẬN ĐĂNG XUẤT)
// ================================================================
@Composable
fun MainProfileView(
    onLogout: () -> Unit,
    onNavigate: (ProfileRoute) -> Unit
) {
    val user  = FirebaseAuth.getInstance().currentUser
    val email = user?.email ?: "Chưa đăng nhập"
    val isDark = LocalAppTheme.current.value
    val textC  = if (isDark) Color.White else Color.Black

    // Biến trạng thái để ẩn/hiện hộp thoại xác nhận đăng xuất
    var showLogoutDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))

        // Avatar
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(Color(0xFFE91E63)),
            contentAlignment = Alignment.Center
        ) {
            Text("T", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(16.dp))
        Text("Trung Đinh", color = textC, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(email, color = Color.Gray, fontSize = 14.sp)
        Spacer(Modifier.height(32.dp))

        // Menu items
        MenuRowItem(Icons.Default.Person,  "Hồ sơ")                    { onNavigate(ProfileRoute.EDIT_PROFILE) }
        MenuRowItem(Icons.Default.Refresh, "Quy đổi mệnh giá tiền")   { onNavigate(ProfileRoute.CURRENCY) }
        MenuRowItem(Icons.Default.Settings,"Cài đặt")                  { onNavigate(ProfileRoute.SETTINGS) }
        MenuRowItem(Icons.Default.Share,   "Giới thiệu cho bạn bè")   { /* share intent */ }
        MenuRowItem(Icons.Default.Info,    "Về chúng tôi")             { onNavigate(ProfileRoute.ABOUT_US) }

        Spacer(Modifier.height(32.dp))

        // Nút đăng xuất (bấm vào sẽ hiện Dialog thay vì đăng xuất luôn)
        Button(
            onClick  = { showLogoutDialog = true },
            modifier = Modifier.fillMaxWidth().height(55.dp),
            colors   = ButtonDefaults.buttonColors(
                containerColor = if (isDark) Color(0xFF333333) else Color(0xFFE0E0E0)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Đăng xuất", color = Color(0xFFFA3B70), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(100.dp))
    }

    // ── HỘP THOẠI XÁC NHẬN ĐĂNG XUẤT ──────────────────────────────
    if (showLogoutDialog) {
        Dialog(onDismissRequest = { showLogoutDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) Color(0xFF1E1E1E) else Color.White
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Đăng xuất", color = Color(0xFFFA3B70), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))

                    Text(
                        "Bạn có chắc chắn muốn đăng xuất khỏi ứng dụng không?",
                        color = textC,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(24.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { showLogoutDialog = false },
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = CircleShape
                        ) {
                            Text("Hủy bỏ", color = textC, fontWeight = FontWeight.Bold)
                        }

                        Spacer(Modifier.width(12.dp))

                        Button(
                            onClick = {
                                showLogoutDialog = false
                                onLogout() // Gọi hàm đăng xuất thật sự ở đây
                            },
                            modifier = Modifier.weight(1f).height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFA3B70)),
                            shape = CircleShape
                        ) {
                            Text("Đăng xuất", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ================================================================
// 2. QUY ĐỔI NGOẠI TỆ — Calculator có xử lý operator đầy đủ
// ================================================================
data class CurrencyItem(val name: String, val code: String, val rateToUsd: Double)

@Composable
fun CurrencyConverterView(onBack: () -> Unit) {
    val isDark = LocalAppTheme.current.value
    val textC  = if (isDark) Color.White else Color.Black
    val keyBg  = if (isDark) Color(0xFF2C2C2C) else Color(0xFFE0E0E0)
    val df     = remember { DecimalFormat("#,##0.##") }

    val allCurrencies = listOf(
        CurrencyItem("Việt Nam đồng", "VND", 25400.0),
        CurrencyItem("Đô la Mỹ",      "USD", 1.0),
        CurrencyItem("Euro",           "EUR", 0.93),
        CurrencyItem("Yên Nhật",       "JPY", 155.0),
        CurrencyItem("Nhân dân tệ",    "CNY", 7.23)
    )

    var row1 by remember { mutableStateOf(allCurrencies[0]) }
    var row2 by remember { mutableStateOf(allCurrencies[2]) }
    var row3 by remember { mutableStateOf(allCurrencies[1]) }

    var activeIndex  by remember { mutableIntStateOf(0) }
    var rawInput     by remember { mutableStateOf("1000") }
    var pendingOp    by remember { mutableStateOf("") }
    var pendingVal   by remember { mutableStateOf(0.0) }
    var justComputed by remember { mutableStateOf(false) }

    var showDropdownFor by remember { mutableStateOf<Int?>(null) }

    fun displayInput(): String =
        rawInput.toDoubleOrNull()?.let { df.format(it) } ?: rawInput

    fun convertedValue(target: CurrencyItem): String {
        val amount = rawInput.toDoubleOrNull() ?: 0.0
        val active = when (activeIndex) { 0 -> row1; 1 -> row2; else -> row3 }
        if (active.code == target.code) return displayInput()
        val inUsd = amount / active.rateToUsd
        return df.format(inUsd * target.rateToUsd)
    }

    fun switchActive(newIndex: Int) {
        val target = when (newIndex) { 0 -> row1; 1 -> row2; else -> row3 }
        val converted = convertedValue(target)
        rawInput = converted.replace(",", "")
        activeIndex = newIndex
        pendingOp = ""
        pendingVal = 0.0
        justComputed = false
    }

    fun handleKey(key: String) {
        when (key) {
            "C"  -> {
                rawInput = "0"; pendingOp = ""; pendingVal = 0.0; justComputed = false
            }
            "⌫"  -> {
                if (!justComputed)
                    rawInput = if (rawInput.length > 1) rawInput.dropLast(1) else "0"
            }
            "%"  -> {
                val v = rawInput.toDoubleOrNull() ?: 0.0
                rawInput = df.format(v / 100)
                justComputed = true
            }
            "÷", "×", "-", "+" -> {
                pendingVal = rawInput.toDoubleOrNull() ?: 0.0
                pendingOp  = key
                rawInput   = "0"
                justComputed = false
            }
            "="  -> {
                if (pendingOp.isNotEmpty()) {
                    val curr   = rawInput.toDoubleOrNull() ?: 0.0
                    val result = when (pendingOp) {
                        "÷" -> if (curr != 0.0) pendingVal / curr else 0.0
                        "×" -> pendingVal * curr
                        "-" -> pendingVal - curr
                        "+" -> pendingVal + curr
                        else -> curr
                    }
                    rawInput   = df.format(result).replace(",", "")
                    pendingOp  = ""
                    pendingVal = 0.0
                    justComputed = true
                }
            }
            "."  -> {
                if (!rawInput.contains(".")) {
                    rawInput += "."
                    justComputed = false
                }
            }
            "00" -> {
                if (!justComputed)
                    rawInput = if (rawInput == "0") "0" else rawInput + "00"
            }
            else -> {
                rawInput = if (justComputed || rawInput == "0") key else rawInput + key
                justComputed = false
            }
        }
    }

    val headerLabel = if (pendingOp.isNotEmpty()) "${df.format(pendingVal)} $pendingOp ..." else "Ngoại tệ"

    Column(modifier = Modifier.fillMaxSize()) {
        TopHeader(headerLabel, onBack)

        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .weight(1f)
        ) {
            listOf(row1, row2, row3).forEachIndexed { index, curr ->
                CurrencyRow(
                    name            = curr.name,
                    code            = curr.code,
                    value           = convertedValue(curr),
                    isActive        = activeIndex == index,
                    onClick         = { switchActive(index) },
                    onDropdownClick = { showDropdownFor = index }
                )
                if (index < 2) Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "Tỷ giá được cập nhật theo cấu hình nội bộ.",
                color = Color.Gray, fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))

            val keys = listOf(
                "C", "⌫", "%",  "÷",
                "7", "8",  "9",  "×",
                "4", "5",  "6",  "-",
                "1", "2",  "3",  "+",
                "00","0",  ".",  "="
            )
            LazyVerticalGrid(
                columns             = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement   = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                gridItems(keys) { key ->
                    val isOperator = key in listOf("C", "⌫", "%", "÷", "×", "-", "+")
                    val isEquals   = key == "="
                    val bgColor    = when {
                        isEquals   -> Color(0xFFFF7043)
                        isOperator -> if (isDark) Color(0xFF3A2C2C) else Color(0xFFFFE0D6)
                        else       -> keyBg
                    }
                    val fgColor = when {
                        isEquals   -> Color.White
                        isOperator -> Color(0xFFFF7043)
                        else       -> textC
                    }

                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(bgColor)
                            .clickable { handleKey(key) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(key, color = fgColor, fontSize = 26.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }

    if (showDropdownFor != null) {
        Dialog(onDismissRequest = { showDropdownFor = null }) {
            Card(
                shape  = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) Color(0xFF1E1E1E) else Color.White
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Chọn tiền tệ", color = textC,
                        fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    allCurrencies.forEach { curr ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    when (showDropdownFor) {
                                        0 -> row1 = curr
                                        1 -> row2 = curr
                                        2 -> row3 = curr
                                    }
                                    showDropdownFor = null
                                }
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(curr.name, color = textC, fontSize = 15.sp)
                            Text(curr.code, color = Color.Gray, fontSize = 13.sp)
                        }
                        if (curr != allCurrencies.last()) HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
fun CurrencyRow(
    name: String, code: String, value: String,
    isActive: Boolean, onClick: () -> Unit, onDropdownClick: () -> Unit
) {
    val isDark = LocalAppTheme.current.value
    val textC  = if (isActive) Color(0xFFFFD700) else if (isDark) Color.White else Color.Black
    val rowBg  = if (isActive)
        (if (isDark) Color(0xFF2A2500) else Color(0xFFFFF9E0))
    else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(rowBg)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onDropdownClick() }
            ) {
                Text(
                    name, color = textC, fontSize = 16.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                )
                Icon(Icons.Default.ArrowDropDown, null, tint = textC, modifier = Modifier.size(20.dp))
            }
            Text(code, color = Color.Gray, fontSize = 13.sp)
        }
        Text(value, color = textC, fontSize = 22.sp, fontWeight = FontWeight.Medium)
    }
}

// ================================================================
// 3. LỜI NHẮC NHỞ
// ================================================================
@Composable
fun RemindersView(onBack: () -> Unit) {
    val textC = if (LocalAppTheme.current.value) Color.White else Color.Black
    var title    by remember { mutableStateOf("") }
    var note     by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text("Hủy", color = textC, fontSize = 16.sp, modifier = Modifier.clickable { onBack() })
            Text("Thêm lời nhắc", color = textC, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Icon(
                Icons.Default.Check, "Lưu", tint = if (title.isNotBlank()) Color(0xFFFFD700) else Color.Gray,
                modifier = Modifier.clickable {
                    if (title.isNotBlank()) { onBack() }
                }
            )
        }
        ThemedDivider()

        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(16.dp))
            FormInputBlock("Tên mục nhắc nhở",   title, { title = it }, "Vd: Trả tiền nhà")
            FormInputBlock("Ghi chú",             note,  { note  = it }, "Vd: Tiền nhà tháng 5")
            FormStaticBlock("Tần suất nhắc nhở",  "Hàng ngày",         isDropdown = true)
            FormStaticBlock("Ngày bắt đầu nhắc",  "28 thg 5, 2026",    isDropdown = true)
            FormStaticBlock("Thời gian",           "20:15",             isDropdown = true)
            Spacer(Modifier.height(100.dp))
        }
    }
}

// ================================================================
// 4. THÊM GIAO DỊCH ĐỊNH KỲ
// ================================================================
@Composable
fun AddRecurringView(onBack: () -> Unit) {
    val textC   = if (LocalAppTheme.current.value) Color.White else Color.Black
    var title   by remember { mutableStateOf("") }
    var amount  by remember { mutableStateOf("") }
    var note    by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text("Hủy", color = textC, fontSize = 16.sp, modifier = Modifier.clickable { onBack() })
            Text("Thêm định kỳ", color = textC, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Icon(
                Icons.Default.Check, "Lưu", tint = if (title.isNotBlank()) Color(0xFFFFD700) else Color.Gray,
                modifier = Modifier.clickable { if (title.isNotBlank()) onBack() }
            )
        }
        ThemedDivider()

        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(16.dp))
            FormInputBlock("Tên giao dịch",      title,  { title  = it }, "Vui lòng nhập tên thanh toán")
            FormStaticBlock("Loại",               "Chi tiêu",              isDropdown = true)
            FormStaticBlock("Danh mục",           "Chọn danh mục",         isDropdown = true)
            FormStaticBlock("Tần suất",           "Hàng tháng",            isDropdown = true)
            FormStaticBlock("Ngày bắt đầu",       "28 thg 5, 2026",        isDropdown = true)
            Text(
                "Dữ liệu chỉ được thêm tự động vào ngày thiết lập.",
                color = Color.Gray, fontSize = 12.sp
            )
            Spacer(Modifier.height(16.dp))
            FormInputBlock("Số tiền (mỗi lần)",  amount, { amount = it }, "0 đ")
            FormInputBlock("Ghi chú",             note,   { note   = it }, "Nhập ghi chú...")
            Spacer(Modifier.height(100.dp))
        }
    }
}

// ================================================================
// 5. CÀI ĐẶT CHÍNH
// ================================================================
@Composable
fun SettingsMainView(onNavigate: (ProfileRoute) -> Unit, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopHeader("Cài đặt", onBack)
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(8.dp))
            SettingsRowItem(Icons.Default.Edit,          "Cỡ chữ")             { onNavigate(ProfileRoute.FONT_SIZE) }

            // Đã đổi Icon chuẩn để tránh lỗi AttachMoney
            SettingsRowItem(Icons.AutoMirrored.Filled.List, "Cài đặt danh mục")  { /* TODO */ }
            SettingsRowItem(Icons.Default.ShoppingCart, "Tiền tệ mặc định") { onNavigate(ProfileRoute.DEFAULT_CURRENCY) }

            SettingsRowItem(Icons.Default.Notifications, "Lời nhắc nhở")       { onNavigate(ProfileRoute.REMINDERS) }
            SettingsRowItem(Icons.Default.Refresh,       "Giao dịch định kỳ") { onNavigate(ProfileRoute.RECURRING) }
            ThemedDivider()
            SettingsRowItem(Icons.Default.Star,          "Chủ đề Sáng / Tối") { onNavigate(ProfileRoute.THEME) }
            Spacer(Modifier.height(40.dp))
        }
    }
}

// ================================================================
// 6. CỠ CHỮ
// ================================================================
@Composable
fun FontSizeView(onBack: () -> Unit) {
    val isDark = LocalAppTheme.current.value
    val textC  = if (isDark) Color.White else Color.Black
    var fontSize by remember { mutableFloatStateOf(16f) }
    val presets = listOf("Nhỏ" to 13f, "Vừa" to 16f, "Lớn" to 20f, "Rất lớn" to 24f)

    Column(modifier = Modifier.fillMaxSize()) {
        TopHeader("Cỡ chữ", onBack)

        Column(
            modifier = Modifier
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape  = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) Color(0xFF1E1E1E) else Color(0xFFFFFFFF)
                ),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "Xem trước văn bản",
                        color = Color.Gray, fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        "WalletWise giúp bạn quản lý\nchi tiêu thông minh hơn mỗi ngày.",
                        color = textC,
                        fontSize = fontSize.sp,
                        lineHeight = (fontSize * 1.5f).sp
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
            Text("Cỡ chữ: ${fontSize.toInt()}sp", color = textC, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Slider(
                value          = fontSize,
                onValueChange  = { fontSize = it },
                valueRange     = 12f..26f,
                steps          = 6,
                colors         = SliderDefaults.colors(thumbColor = Color(0xFFFFD700), activeTrackColor = Color(0xFFFFD700)),
                modifier = Modifier.fillMaxWidth()
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("12sp", color = Color.Gray, fontSize = 12.sp)
                Text("26sp", color = Color.Gray, fontSize = 12.sp)
            }

            Spacer(Modifier.height(32.dp))
            Text("Preset nhanh", color = textC, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                presets.forEach { (label, size) ->
                    val isSelected = fontSize == size
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) Color(0xFFFFD700) else if (isDark) Color(0xFF2C2C2C) else Color(0xFFE0E0E0))
                            .clickable { fontSize = size }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, color = if (isSelected) Color.Black else textC, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.Center)
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700)), shape = RoundedCornerShape(14.dp)) {
                Text("Lưu cài đặt", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ================================================================
// 7. GIAO DỊCH ĐỊNH KỲ
// ================================================================
data class RecurringItem(
    val title     : String,
    val amount    : Long,
    val frequency : String,
    val category  : String,
    val iconColor : Color = Color(0xFF4CAF50)
)

@Composable
fun RecurringView(onAdd: () -> Unit, onBack: () -> Unit) {
    val items = remember {
        listOf(
            RecurringItem("Tiền thuê nhà", 3_500_000, "Hàng tháng", "Hóa đơn",   Color(0xFF2196F3)),
            RecurringItem("Netflix",        260_000,   "Hàng tháng", "Giải trí",  Color(0xFFE91E63)),
            RecurringItem("Gym",            300_000,   "Hàng tháng", "Sức khỏe",  Color(0xFF4CAF50)),
            RecurringItem("Điện thoại",     199_000,   "Hàng tháng", "Hóa đơn",   Color(0xFFFF9800)),
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopHeader("Giao dịch định kỳ", onBack)

        LazyColumn(
            modifier            = Modifier.weight(1f),
            contentPadding      = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items) { item -> RecurringItemCard(item) }
        }

        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700)), shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Default.Add, null, tint = Color.Black)
                Spacer(Modifier.width(8.dp))
                Text("Thêm giao dịch định kỳ", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun RecurringItemCard(item: RecurringItem) {
    val isDark = LocalAppTheme.current.value
    val textC  = if (isDark) Color.White else Color.Black
    val cardBg = if (isDark) Color(0xFF1E1E1E) else Color.White
    val df     = remember { DecimalFormat("#,##0") }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(item.iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Refresh, null, tint = item.iconColor, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title,     color = textC,         fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(item.frequency, color = Color.Gray,   fontSize = 12.sp)
                Text(item.category,  color = item.iconColor, fontSize = 11.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("-${df.format(item.amount)} đ", color = Color(0xFFFA3B70), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ================================================================
// 8. CHỦ ĐỀ SÁNG / TỐI
// ================================================================
@Composable
fun ThemeView(onBack: () -> Unit) {
    val appTheme = LocalAppTheme.current
    val isDark   = appTheme.value
    val textC    = if (isDark) Color.White else Color.Black

    Column(modifier = Modifier.fillMaxSize()) {
        TopHeader("Chủ đề", onBack)

        Column(modifier = Modifier.padding(24.dp)) {
            Text("Chọn giao diện hiển thị", color = textC, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ThemeOptionCard("SÁNG", Color.White, Color.Black, !isDark) { appTheme.value = false }
                ThemeOptionCard("TỐI", Color(0xFF121212), Color.White, isDark) { appTheme.value = true }
            }
            Spacer(Modifier.height(32.dp))
            Text("Giao diện sẽ được áp dụng ngay lập tức.", color = Color.Gray, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun ThemeOptionCard(label: String, bg: Color, fg: Color, isSelected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) {
        Box(
            modifier = Modifier.size(110.dp).clip(RoundedCornerShape(16.dp)).border(width = if (isSelected) 3.dp else 1.dp, color = if (isSelected) Color(0xFFFFD700) else Color.Gray, shape = RoundedCornerShape(16.dp)).background(bg),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, color = fg, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (isSelected) {
                    Spacer(Modifier.height(6.dp))
                    Icon(Icons.Default.Check, null, tint = Color(0xFFFFD700), modifier = Modifier.size(20.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (isSelected) Text("Đang dùng", color = Color(0xFFFFD700), fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

// ================================================================
// 9. QUY ĐỔI TIỀN TỆ MẶC ĐỊNH
// ================================================================
@Composable
fun DefaultCurrencyView(onBack: () -> Unit) {
    val textC = if (LocalAppTheme.current.value) Color.White else Color.Black

    val currencies = listOf(
        "đồng Việt Nam ( ₫ )" to "VND",
        "Đô la Mĩ ( $ )" to "USD",
        "Euro ( € )" to "EUR",
        "Bảng Anh ( £ )" to "GBP",
        "Nhân dân tệ Trung Quốc ( 元 )" to "CNY",
        "Yen Nhật ( ¥ )" to "JPY",
        "Đô la Canada ( C$ )" to "CAD",
        "đô la Úc ( A$ )" to "AUD",
        "Đôla Hong Kong ( HK$ )" to "HKD",
        "Won Hàn Quốc ( ₩ )" to "KRW",
        "Đô la Singapore ( S$ )" to "SGD",
        "Rupee Ấn Độ ( ₹ )" to "INR"
    )

    Column(modifier = Modifier.fillMaxSize()) {
        TopHeader("Lựa chọn", onBack)

        LazyColumn {
            items(currencies) { (name, code) ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onBack() }.padding(horizontal = 16.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(name, color = textC, fontSize = 16.sp)
                    Text(code, color = textC, fontSize = 15.sp)
                }
                ThemedDivider()
            }
        }
    }
}

// ================================================================
// 10. SỬA HỒ SƠ
// ================================================================
@Composable
fun EditProfileView(onBack: () -> Unit) {
    val context = LocalContext.current
    val myId = "113220"

    var showNameDialog by remember { mutableStateOf(false) }
    var showGenderDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }

    var nickname by remember { mutableStateOf("Trung Đinh") }
    var gender by remember { mutableStateOf("Khác") }

    val isDark = LocalAppTheme.current.value
    val textC = if (isDark) Color.White else Color.Black
    val surfaceC = if (isDark) Color(0xFF1E1E1E) else Color.White

    Column(modifier = Modifier.fillMaxSize()) {
        TopHeader("Hồ sơ", onBack)
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Ảnh đại diện", color = textC, fontSize = 16.sp)
                Box(modifier = Modifier.size(50.dp).clip(CircleShape).background(Color(0xFFE91E63)), contentAlignment = Alignment.Center) { Text("T", color = Color.White, fontSize = 24.sp) }
            }
            ThemedDivider()
            EditRowItem("ID", myId) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("ID", myId))
                Toast.makeText(context, "Đã sao chép ID", Toast.LENGTH_SHORT).show()
            }
            EditRowItem("Biệt danh", nickname) { showNameDialog = true }
            EditRowItem("Giới tính", gender) { showGenderDialog = true }
            EditRowItem("Đổi mật khẩu", "") { showPasswordDialog = true }
        }
    }

    // Các popup dialog (ẩn khi chưa dùng)
    if (showNameDialog) {
        var tempName by remember { mutableStateOf(nickname) }
        Dialog(onDismissRequest = { showNameDialog = false }) {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = surfaceC)) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Biệt danh", color = textC, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(value = tempName, onValueChange = { tempName = it }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textC, unfocusedTextColor = textC))
                    Spacer(Modifier.height(24.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Button(onClick = { showNameDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700)), shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) { Icon(Icons.Default.Close, null, tint = Color.Black) }
                        Spacer(Modifier.width(16.dp))
                        Button(onClick = { nickname = tempName; showNameDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700)), shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) { Icon(Icons.Default.Check, null, tint = Color.Black) }
                    }
                }
            }
        }
    }

    if (showGenderDialog) {
        Dialog(onDismissRequest = { showGenderDialog = false }) {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = surfaceC)) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Giới tính", color = textC, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Icon(Icons.Default.Close, null, tint = textC, modifier = Modifier.clickable { showGenderDialog = false })
                    }
                    Spacer(Modifier.height(24.dp))
                    listOf("Khác", "Nữ giới", "Nam giới").forEach { opt ->
                        Button(onClick = { gender = opt; showGenderDialog = false }, modifier = Modifier.fillMaxWidth().height(50.dp).padding(bottom = 8.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700)), shape = RoundedCornerShape(12.dp)) { Text(opt, color = Color.Black) }
                    }
                    Text("Tôi không muốn tiết lộ!", color = textC, modifier = Modifier.padding(top = 16.dp).clickable { gender = "Bí mật"; showGenderDialog = false })
                }
            }
        }
    }

    if (showPasswordDialog) {
        var oldPass by remember { mutableStateOf("") }
        var newPass by remember { mutableStateOf("") }
        var confirmPass by remember { mutableStateOf("") }
        Dialog(onDismissRequest = { showPasswordDialog = false }) {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = surfaceC)) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Đổi mật khẩu", color = Color(0xFFFA3B70), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(value = oldPass, onValueChange = { oldPass = it }, placeholder = { Text("Mật khẩu hiện tại") }, modifier = Modifier.fillMaxWidth(), shape = CircleShape)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = newPass, onValueChange = { newPass = it }, placeholder = { Text("Mật khẩu mới") }, modifier = Modifier.fillMaxWidth(), shape = CircleShape)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = confirmPass, onValueChange = { confirmPass = it }, placeholder = { Text("Nhắc lại mật khẩu mới") }, modifier = Modifier.fillMaxWidth(), shape = CircleShape)
                    Spacer(Modifier.height(24.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { showPasswordDialog = false }, modifier = Modifier.weight(1f).height(50.dp), shape = CircleShape) { Text("Hủy bỏ", color = textC) }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { showPasswordDialog = false }, modifier = Modifier.weight(1f).height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFA3B70)), shape = CircleShape) { Text("Xác nhận", color = Color.White) }
                    }
                }
            }
        }
    }
}

// ================================================================
// 11. VỀ CHÚNG TÔI
// ================================================================
@Composable
fun AboutUsView(onBack: () -> Unit) {
    val isDark = LocalAppTheme.current.value
    val textC  = if (isDark) Color.White else Color.Black
    val cardBg = if (isDark) Color(0xFF1E1E1E) else Color.White

    Column(modifier = Modifier.fillMaxSize()) {
        TopHeader("Về chúng tôi", onBack)

        Column(
            modifier = Modifier
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            // Logo / Icon app đã dùng hàm Image chuẩn
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF2C2C2C)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo),
                    contentDescription = "logo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            }

            Spacer(Modifier.height(16.dp))
            Text("WalletWise", color = textC,     fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("Phiên bản 1.0.0",  color = Color.Gray, fontSize = 13.sp)
            Spacer(Modifier.height(32.dp))

            // Thông tin nhóm
            Card(
                modifier  = Modifier.fillMaxWidth(),
                shape     = RoundedCornerShape(14.dp),
                colors    = CardDefaults.cardColors(containerColor = cardBg),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    AboutInfoRow("📚", "Đồ án",   "Lập trình Mobile")
                    AboutInfoRow("👨‍💻", "Thực hiện","Đinh Văn Trung & Cà Văn Tuấn")
                    AboutInfoRow("🎓", "Trường",  "ĐH Công nghiệp Hà Nội")
                    AboutInfoRow("📅", "Năm",     "2025 – 2026")
                }
            }

            Spacer(Modifier.height(24.dp))

            Card(
                modifier  = Modifier.fillMaxWidth(),
                shape     = RoundedCornerShape(14.dp),
                colors    = CardDefaults.cardColors(containerColor = cardBg),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Mô tả ứng dụng", color = textC, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "WalletWise là ứng dụng quản lý chi tiêu cá nhân " +
                                "thông minh, giúp bạn theo dõi thu nhập, chi tiêu " +
                                "và đạt được mục tiêu tài chính của mình.",
                        color = Color.Gray, fontSize = 14.sp, lineHeight = 22.sp
                    )
                }
            }

            Spacer(Modifier.height(60.dp))
        }
    }
}

@Composable
fun AboutInfoRow(emoji: String, label: String, value: String) {
    val isDark = LocalAppTheme.current.value
    val textC  = if (isDark) Color.White else Color.Black
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 18.sp)
            Spacer(Modifier.width(10.dp))
            Text(label, color = Color.Gray, fontSize = 14.sp)
        }
        Text(value, color = textC, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

// ================================================================
// COMPONENTS DÙNG CHUNG
// ================================================================
@Composable
fun TopHeader(title: String, onBack: () -> Unit) {
    val textC = if (LocalAppTheme.current.value) Color.White else Color.Black
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack, "Quay lại",
            tint = textC,
            modifier = Modifier
                .size(26.dp)
                .clickable { onBack() }
        )
        Text(
            title, color = textC,
            fontSize   = 19.sp,
            fontWeight = FontWeight.Bold,
            modifier   = Modifier.weight(1f),
            textAlign  = TextAlign.Center
        )
        Spacer(Modifier.size(26.dp))
    }
    ThemedDivider()
}

@Composable
fun ThemedDivider() {
    HorizontalDivider(
        color = if (LocalAppTheme.current.value) Color(0xFF2C2C2C) else Color(0xFFE0E0E0)
    )
}

@Composable
fun MenuRowItem(icon: ImageVector, title: String, onClick: () -> Unit) {
    val textC = if (LocalAppTheme.current.value) Color.White else Color.Black
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color.Gray, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(title, color = textC, fontSize = 16.sp)
        Spacer(Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.Gray)
    }
    ThemedDivider()
}

@Composable
fun EditRowItem(title: String, value: String, onClick: () -> Unit) {
    val textC = if (LocalAppTheme.current.value) Color.White else Color.Black
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(title, color = textC, fontSize = 16.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, color = Color.Gray, fontSize = 15.sp)
            Spacer(Modifier.width(6.dp))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.Gray)
        }
    }
    ThemedDivider()
}

@Composable
fun SettingsRowItem(icon: ImageVector, title: String, onClick: () -> Unit) {
    val textC = if (LocalAppTheme.current.value) Color.White else Color.Black
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .height(68.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color(0xFFFFD700), modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(title, color = textC, fontSize = 16.sp)
        Spacer(Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.Gray)
    }
    ThemedDivider()
}

@Composable
fun FormInputBlock(
    title        : String,
    value        : String,
    onValueChange: (String) -> Unit,
    placeholder  : String
) {
    val isDark = LocalAppTheme.current.value
    val textC  = if (isDark) Color.White else Color.Black
    val bg     = if (isDark) Color(0xFF1E1E1E) else Color(0xFFEEEEEE)

    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier
                .width(4.dp)
                .height(16.dp)
                .background(Color(0xFFFFD700)))
            Spacer(Modifier.width(8.dp))
            Text(title, color = textC, fontSize = 14.sp)
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(bg)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (value.isEmpty()) Text(placeholder, color = Color.Gray, fontSize = 14.sp)
            BasicTextField(
                value         = value,
                onValueChange = onValueChange,
                textStyle     = TextStyle(color = textC, fontSize = 14.sp),
                modifier      = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun FormStaticBlock(title: String, value: String, isDropdown: Boolean = false) {
    val isDark = LocalAppTheme.current.value
    val textC  = if (isDark) Color.White else Color.Black
    val bg     = if (isDark) Color(0xFF1E1E1E) else Color(0xFFEEEEEE)

    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier
                .width(4.dp)
                .height(16.dp)
                .background(Color(0xFFFFD700)))
            Spacer(Modifier.width(8.dp))
            Text(title, color = textC, fontSize = 14.sp)
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(bg)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(value, color = textC, fontSize = 14.sp)
            if (isDropdown) Icon(Icons.Default.ArrowDropDown, null, tint = textC)
        }
    }
}