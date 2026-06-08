package com.example.walletwise.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.walletwise.data.repository.AuthRepositoryImpl
import com.example.walletwise.domain.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    private val repository: AuthRepository = AuthRepositoryImpl()
) : ViewModel() {

    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private val _currentUser = MutableStateFlow<com.example.walletwise.domain.model.User?>(null)
    val currentUser: StateFlow<com.example.walletwise.domain.model.User?> = _currentUser.asStateFlow()
    init {
        loadUserProfile()
    }

    fun register(email: String, pass: String, username: String) {
        if (email.isBlank() || pass.isBlank() || username.isBlank()) {
            _state.value = AuthState(error = "Vui lòng nhập đầy đủ thông tin!")
            return
        }
        viewModelScope.launch {
            _state.value = AuthState(isLoading = true)
            repository.register(email, pass, username)
                .onSuccess {
                    loadUserProfile()
                    _state.value = AuthState(isSuccess = true)
                }
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
                .onSuccess {
                    loadUserProfile()
                    _state.value = AuthState(isSuccess = true)
                }
                .onFailure { _state.value = AuthState(error = it.localizedMessage ?: "Đăng nhập thất bại") }
        }
    }

    fun logout() {
        _currentUser.value = null
        _state.value = AuthState() // Reset trạng thái
    }

    // =======================================================
    // LOGIC FORGOT PASSWORD (QUÊN MẬT KHẨU BẰNG OTP)
    // =======================================================

    // Bước 1: Gửi mã OTP
    fun sendOtpEmail(email: String) {
        if (email.isBlank()) {
            _state.value = AuthState(error = "Vui lòng nhập Email để khôi phục!")
            return
        }
        viewModelScope.launch {
            _state.value = AuthState(isLoading = true)

            // Sinh mã ngẫu nhiên 6 chữ số
            val otp = (100000..999999).random().toString()

            // Lưu mã này vào Firebase Firestore (Collection: OTPs)
            FirebaseFirestore.getInstance().collection("OTPs").document(email)
                .set(mapOf("otp" to otp, "timestamp" to System.currentTimeMillis()))
                .addOnSuccessListener {
                    // MẸO: Hiện thẳng mã OTP ra thông báo để bạn dễ dàng test Đồ án
                    _state.value = AuthState(isSuccess = true, error = "Đã gửi mã! (Mã Test: $otp)")
                }
                .addOnFailureListener {
                    _state.value = AuthState(error = "Lỗi hệ thống: Không thể tạo mã OTP")
                }
        }
    }

    // Bước 2: Xác minh mã OTP
    fun verifyOtp(email: String, otp: String, onSuccess: () -> Unit) {
        if (otp.isBlank()) {
            _state.value = AuthState(error = "Vui lòng nhập mã OTP!")
            return
        }
        viewModelScope.launch {
            _state.value = AuthState(isLoading = true)

            // Lấy mã OTP từ Firestore xuống để so sánh
            FirebaseFirestore.getInstance().collection("OTPs").document(email).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        val savedOtp = doc.getString("otp")
                        if (savedOtp == otp) {
                            // Nếu mã khớp -> Báo thành công và gọi hàm điều hướng
                            _state.value = AuthState(isSuccess = true, error = "Xác thực thành công!")
                            onSuccess()
                        } else {
                            _state.value = AuthState(error = "Mã OTP không chính xác!")
                        }
                    } else {
                        _state.value = AuthState(error = "Không tìm thấy mã OTP cho email này!")
                    }
                }
                .addOnFailureListener {
                    _state.value = AuthState(error = "Lỗi kiểm tra mã OTP")
                }
        }
    }

    // =======================================================

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun loadUserProfile() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .addSnapshotListener { snapshot, error ->

                if (error != null) return@addSnapshotListener

                if (snapshot != null && snapshot.exists()) {
                    _currentUser.value =
                        snapshot.toObject(com.example.walletwise.domain.model.User::class.java)
                }
            }
    }
}