package com.example.walletwise.presentation.auth.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.walletwise.presentation.auth.AuthUiState
import com.example.walletwise.shared.resources.Res
import com.example.walletwise.shared.resources.back_to_login_link
import com.example.walletwise.shared.resources.email_label
import com.example.walletwise.shared.resources.forgot_password_description
import com.example.walletwise.shared.resources.forgot_password_link
import com.example.walletwise.shared.resources.forgot_password_title
import com.example.walletwise.shared.resources.has_account_prompt
import com.example.walletwise.shared.resources.hide_password
import com.example.walletwise.shared.resources.login_button
import com.example.walletwise.shared.resources.login_link
import com.example.walletwise.shared.resources.login_title
import com.example.walletwise.shared.resources.no_account_prompt
import com.example.walletwise.shared.resources.password_label
import com.example.walletwise.shared.resources.register_button
import com.example.walletwise.shared.resources.register_now_link
import com.example.walletwise.shared.resources.register_title
import com.example.walletwise.shared.resources.reset_password_button
import com.example.walletwise.shared.resources.show_password
import com.example.walletwise.shared.resources.username_label
import org.jetbrains.compose.resources.stringResource

@Composable
fun LoginScreenContent(
    state: AuthUiState,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onSubmit: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToForgotPassword: () -> Unit
) {
    AuthBackground {
        AuthHeader(title = stringResource(Res.string.login_title))

        AuthTextField(
            value = state.login.email,
            onValueChange = onEmailChanged,
            label = stringResource(Res.string.email_label),
            leadingIcon = {
                Icon(Icons.Default.Email, contentDescription = null, tint = Color.Gray)
            }
        )

        AuthTextField(
            value = state.login.password,
            onValueChange = onPasswordChanged,
            label = stringResource(Res.string.password_label),
            leadingIcon = {
                Icon(Icons.Default.Lock, contentDescription = null, tint = Color.Gray)
            },
            trailingIcon = {
                val image = if (state.login.passwordVisible) {
                    Icons.Default.Visibility
                } else {
                    Icons.Default.VisibilityOff
                }
                val description = if (state.login.passwordVisible) {
                    stringResource(Res.string.hide_password)
                } else {
                    stringResource(Res.string.show_password)
                }
                IconButton(onClick = onTogglePasswordVisibility) {
                    Icon(imageVector = image, contentDescription = description, tint = Color.Gray)
                }
            },
            visualTransformation = if (state.login.passwordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            }
        )

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Text(
                text = stringResource(Res.string.forgot_password_link),
                color = AuthGold,
                modifier = Modifier.clickable(onClick = onNavigateToForgotPassword)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        AuthSubmitButton(
            isLoading = state.isLoading,
            text = stringResource(Res.string.login_button),
            boldText = true,
            fontSize = 16.sp,
            onClick = onSubmit
        )

        AuthMessage(state.login.operation)

        Spacer(modifier = Modifier.height(24.dp))
        Row {
            Text(stringResource(Res.string.no_account_prompt), color = Color.White)
            Text(
                text = stringResource(Res.string.register_now_link),
                color = AuthGold,
                modifier = Modifier.clickable(onClick = onNavigateToRegister)
            )
        }
    }
}

@Composable
fun RegisterScreenContent(
    state: AuthUiState,
    onUsernameChanged: (String) -> Unit,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    AuthBackground {
        AuthHeader(title = stringResource(Res.string.register_title))

        AuthTextField(
            value = state.register.username,
            onValueChange = onUsernameChanged,
            label = stringResource(Res.string.username_label),
            leadingIcon = {
                Icon(Icons.Default.AccountCircle, contentDescription = null, tint = Color.Gray)
            }
        )

        AuthTextField(
            value = state.register.email,
            onValueChange = onEmailChanged,
            label = stringResource(Res.string.email_label),
            leadingIcon = {
                Icon(Icons.Default.Email, contentDescription = null, tint = Color.Gray)
            }
        )

        AuthTextField(
            value = state.register.password,
            onValueChange = onPasswordChanged,
            label = stringResource(Res.string.password_label),
            leadingIcon = {
                Icon(Icons.Default.Lock, contentDescription = null, tint = Color.Gray)
            },
            visualTransformation = PasswordVisualTransformation()
        )

        Spacer(modifier = Modifier.height(24.dp))

        AuthSubmitButton(
            isLoading = state.isLoading,
            text = stringResource(Res.string.register_button),
            boldText = false,
            fontSize = 16.sp,
            onClick = onSubmit
        )

        AuthMessage(state.register.operation)

        Spacer(modifier = Modifier.height(24.dp))
        Row {
            Text(stringResource(Res.string.has_account_prompt), color = Color.White)
            Text(
                text = stringResource(Res.string.login_link),
                color = AuthGold,
                modifier = Modifier.clickable(onClick = onNavigateToLogin)
            )
        }
    }
}

@Composable
fun ForgotPasswordScreenContent(
    state: AuthUiState,
    onEmailChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    AuthBackground {
        AuthHeader(title = stringResource(Res.string.forgot_password_title))

        Text(
            text = stringResource(Res.string.forgot_password_description),
            color = Color.LightGray,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        AuthTextField(
            value = state.forgotPassword.email,
            onValueChange = onEmailChanged,
            label = stringResource(Res.string.email_label),
            leadingIcon = {
                Icon(Icons.Default.Email, contentDescription = null, tint = Color.Gray)
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        AuthSubmitButton(
            isLoading = state.isLoading,
            text = stringResource(Res.string.reset_password_button),
            boldText = true,
            onClick = onSubmit
        )

        AuthMessage(state.forgotPassword.operation, mediumWeight = true)

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = stringResource(Res.string.back_to_login_link),
            color = AuthGold,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable(onClick = onNavigateToLogin)
        )
    }
}
