package com.example.walletwise.presentation.profile
// Về chúng tôi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.walletwise.R

@Composable
fun AboutUsView(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopHeader("Về chúng tôi", onBack)

        Column(
            modifier = Modifier
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

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
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(Modifier.height(16.dp))
            Text("WalletWise", color = MaterialTheme.colorScheme.onBackground, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("Phiên bản 1.0.0",  color = Color.Gray, fontSize = 13.sp)
            Spacer(Modifier.height(32.dp))

            Card(
                modifier  = Modifier.fillMaxWidth(),
                shape     = RoundedCornerShape(14.dp),
                colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    AboutInfoRow("📚", "Môn học",   "Đồ án tốt nghiệp")
                    AboutInfoRow("👨‍💻", "Thực hiện","Đinh Văn Trung - 2022606128")
                    AboutInfoRow("🎓", "Trường",  "ĐH Công nghiệp Hà Nội")
                    AboutInfoRow("📅", "Năm",     "2026")
                }
            }

            Spacer(Modifier.height(24.dp))

            Card(
                modifier  = Modifier.fillMaxWidth(),
                shape     = RoundedCornerShape(14.dp),
                colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Mô tả ứng dụng", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold)
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
        Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
