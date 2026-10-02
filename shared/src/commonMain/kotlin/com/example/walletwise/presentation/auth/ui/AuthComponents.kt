package com.example.walletwise.presentation.auth.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.walletwise.presentation.auth.AuthOperationState
import com.example.walletwise.shared.resources.Res
import com.example.walletwise.shared.resources.auth_background_description
import com.example.walletwise.shared.resources.auth_logo_description
import com.example.walletwise.shared.resources.img
import com.example.walletwise.shared.resources.logo
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

internal val AuthGold = Color(0xFFFFD700)

@Composable
fun AuthBackground(content: @Composable ColumnScope.() -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(Res.drawable.img),
            contentDescription = stringResource(Res.string.auth_background_description),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            content = content
        )
    }
}

@Composable
fun AuthHeader(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Image(
            painter = painterResource(Res.drawable.logo),
            contentDescription = stringResource(Res.string.auth_logo_description),
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Fit
        )

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = title,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AuthGold,
            unfocusedBorderColor = Color.Gray,
            focusedLabelColor = AuthGold,
            unfocusedLabelColor = Color.Gray,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedContainerColor = Color.White.copy(alpha = 0.1f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.1f)
        )
    )
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
internal fun AuthSubmitButton(
    isLoading: Boolean,
    text: String,
    boldText: Boolean,
    fontSize: TextUnit = TextUnit.Unspecified,
    onClick: () -> Unit
) {
    if (isLoading) {
        CircularProgressIndicator(color = AuthGold)
    } else {
        Button(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AuthGold)
        ) {
            Text(
                text = text,
                color = Color.Black,
                fontSize = fontSize,
                fontWeight = if (boldText) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
internal fun AuthMessage(
    operation: AuthOperationState,
    mediumWeight: Boolean = false
) {
    val message = when (operation) {
        is AuthOperationState.Success -> operation.message
        is AuthOperationState.ValidationError -> operation.message
        is AuthOperationState.RepositoryError -> operation.message
        AuthOperationState.Idle,
        AuthOperationState.Loading -> null
    } ?: return

    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = message,
        color = if (operation is AuthOperationState.Success) Color.Green else Color.Red,
        fontWeight = if (mediumWeight) FontWeight.Medium else FontWeight.Normal
    )
}
