package com.example.walletwise.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.walletwise.data.repository.AuthRepositoryImpl
import com.example.walletwise.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    private val repository: AuthRepository = AuthRepositoryImpl()
) : ViewModel() {

    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    fun register(email: String, pass: String, username: String) {
        if (email.isBlank() || pass.isBlank() || username.isBlank()) {
            _state.value = AuthState(error = "Vui lòng nhập đầy đủ thông tin!")
            return
        }
        viewModelScope.launch {
            _state.value = AuthState(isLoading = true)
            repository.register(email, pass, username)
                .onSuccess { _state.value = AuthState(isSuccess = true) }
                .onFailure { _state.value = AuthState(error = it.localizedMessage ?: "Đăng ký thất bại") }
        }
    }

    fun login(email: String, pass: String) {
        if (email.isBlank() || pass.isBlank()) {
            _state.value = AuthState(error = "Email và mật khẩu không được để trống!")
            return
        }
        viewModelScope.launch {
            _state.value = AuthState(isLoading = true)
            repository.login(email, pass)
                .onSuccess { _state.value = AuthState(isSuccess = true) }
                .onFailure { _state.value = AuthState(error = it.localizedMessage ?: "Đăng nhập thất bại") }
        }
    }

    fun resetPassword(email: String) {
        if (email.isBlank()) {
            _state.value = AuthState(error = "Vui lòng nhập Email để khôi phục!")
            return
        }
        viewModelScope.launch {
            _state.value = AuthState(isLoading = true)
            repository.resetPassword(email)
                .onSuccess { _state.value = AuthState(isSuccess = true, error = "Đã gửi liên kết đặt lại mật khẩu!") }
                .onFailure { _state.value = AuthState(error = it.localizedMessage ?: "Không thể gửi email khôi phục") }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}