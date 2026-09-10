package com.example.walletwise.presentation.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.walletwise.shared.resources.Res
import com.example.walletwise.shared.resources.about_app_description
import com.example.walletwise.shared.resources.about_app_description_title
import com.example.walletwise.shared.resources.about_author
import com.example.walletwise.shared.resources.about_author_value
import com.example.walletwise.shared.resources.about_logo_description
import com.example.walletwise.shared.resources.about_school
import com.example.walletwise.shared.resources.about_school_value
import com.example.walletwise.shared.resources.about_subject
import com.example.walletwise.shared.resources.about_subject_value
import com.example.walletwise.shared.resources.about_title
import com.example.walletwise.shared.resources.about_version
import com.example.walletwise.shared.resources.about_year
import com.example.walletwise.shared.resources.about_year_value
import com.example.walletwise.shared.resources.app_name
import com.example.walletwise.shared.resources.customer_care_email
import com.example.walletwise.shared.resources.customer_care_faq
import com.example.walletwise.shared.resources.customer_care_feedback
import com.example.walletwise.shared.resources.customer_care_feedback_placeholder
import com.example.walletwise.shared.resources.customer_care_help_question
import com.example.walletwise.shared.resources.customer_care_hotline
import com.example.walletwise.shared.resources.customer_care_submit
import com.example.walletwise.shared.resources.customer_care_title
import com.example.walletwise.shared.resources.logo
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun AboutContent(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopHeader(stringResource(Res.string.about_title), onBack)

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
                    painter = painterResource(Res.drawable.logo),
                    contentDescription = stringResource(Res.string.about_logo_description),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(Res.string.app_name),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(Res.string.about_version),
                color = Color.Gray,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(32.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    AboutInfoRow(
                        "📚",
                        stringResource(Res.string.about_subject),
                        stringResource(Res.string.about_subject_value)
                    )
                    AboutInfoRow(
                        "👨‍💻",
                        stringResource(Res.string.about_author),
                        stringResource(Res.string.about_author_value)
                    )
                    AboutInfoRow(
                        "🎓",
                        stringResource(Res.string.about_school),
                        stringResource(Res.string.about_school_value)
                    )
                    AboutInfoRow(
                        "📅",
                        stringResource(Res.string.about_year),
                        stringResource(Res.string.about_year_value)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        stringResource(Res.string.about_app_description_title),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(Res.string.about_app_description),
                        color = Color.Gray,
                        fontSize = 14.sp,
                        lineHeight = 22.sp
                    )
                }
            }

            Spacer(Modifier.height(60.dp))
        }
    }
}

@Composable
private fun AboutInfoRow(emoji: String, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 18.sp)
            Spacer(Modifier.width(10.dp))
            Text(label, color = Color.Gray, fontSize = 14.sp)
        }
        Text(
            value,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun CustomerCareContent(
    onBack: () -> Unit,
    onCallHotline: () -> Unit,
    onEmailSupport: () -> Unit,
    onOpenFaq: () -> Unit,
    onFeedbackChanged: (String) -> Unit,
    onSubmitFeedback: () -> Unit,
    feedback: String = ""
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopHeader(stringResource(Res.string.customer_care_title), onBack)
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(Res.string.customer_care_help_question),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(24.dp))

            SettingsRowItem(
                Icons.Default.Call,
                stringResource(Res.string.customer_care_hotline),
                onCallHotline
            )
            SettingsRowItem(
                Icons.Default.Email,
                stringResource(Res.string.customer_care_email),
                onEmailSupport
            )
            SettingsRowItem(
                Icons.Default.QuestionAnswer,
                stringResource(Res.string.customer_care_faq),
                onOpenFaq
            )

            Spacer(Modifier.height(32.dp))
            FormInputBlock(
                stringResource(Res.string.customer_care_feedback),
                feedback,
                onFeedbackChanged,
                stringResource(Res.string.customer_care_feedback_placeholder)
            )
            Button(
                onClick = onSubmitFeedback,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    stringResource(Res.string.customer_care_submit),
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
