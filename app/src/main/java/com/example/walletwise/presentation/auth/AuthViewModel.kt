package com.example.walletwise.presentation.auth

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.walletwise.data.repository.AuthRepositoryImpl
import com.example.walletwise.domain.repository.AuthRepository
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class AuthViewModel(
    private val repository: AuthRepository = AuthRepositoryImpl()
) : ViewModel() {

    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private val _currentUser = MutableStateFlow<com.example.walletwise.domain.model.User?>(null)
    val currentUser: StateFlow<com.example.walletwise.domain.model.User?> = _currentUser.asStateFlow()

    private var profileListener: ListenerRegistration? = null
    private var authStateListener: FirebaseAuth.AuthStateListener? = null

    fun register(email: String, pass: String, username: String) {
        val cleanEmail = email.trim()
        val cleanPass = pass.trim()
        val cleanName = username.trim()
        if (cleanEmail.isBlank() || cleanPass.isBlank() || cleanName.isBlank()) {
            _state.value = AuthState(error = "Vui lòng nhập đầy đủ thông tin!")
            return
        }
        viewModelScope.launch {
            _state.value = AuthState(isLoading = true)
            try {
                kotlinx.coroutines.withTimeout(15000) {
                    repository.register(cleanEmail, cleanPass, cleanName)
                        .onSuccess {
                            loadUserProfile()
                            _state.value = AuthState(isSuccess = true)
                        }
                        .onFailure { e ->
                            _state.value = AuthState(error = e.localizedMessage ?: "Đăng ký thất bại!")
                        }
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                _state.value = AuthState(error = "Thời gian phản hồi quá lâu. Vui lòng kiểm tra lại mạng!")
            } catch (e: Exception) {
                _state.value = AuthState(error = e.localizedMessage ?: "Đăng ký thất bại!")
            }
        }
    }

    fun login(email: String, pass: String) {
        val cleanEmail = email.trim()
        val cleanPass = pass.trim()
        if (cleanEmail.isBlank() || cleanPass.isBlank()) {
            _state.value = AuthState(error = "Email và mật khẩu không được để trống!")
            return
        }
        viewModelScope.launch {
            _state.value = AuthState(isLoading = true)
            try {
                kotlinx.coroutines.withTimeout(15000) {
                    repository.login(cleanEmail, cleanPass)
                        .onSuccess {
                            loadUserProfile()
                            _state.value = AuthState(isSuccess = true)
                        }
                        .onFailure { e ->
                            _state.value = AuthState(error = e.localizedMessage ?: "Đăng nhập thất bại!")
                        }
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                _state.value = AuthState(error = "Mạng yếu hoặc phản hồi từ Firebase quá lâu. Vui lòng thử lại!")
            } catch (e: Exception) {
                _state.value = AuthState(error = e.localizedMessage ?: "Đăng nhập thất bại!")
            }
        }
    }

    fun logout() {
        FirebaseAuth.getInstance().signOut()

        _currentUser.value = null
        _state.value = AuthState()
    }

    // =======================================================
    // LOGIC FORGOT PASSWORD (QUÊN MẬT KHẨU BẰNG OTP)
    // =======================================================

//    // Bước 1: Gửi mã OTP
//    fun sendOtpEmail(email: String) {
//        if (email.isBlank()) {
//            _state.value = AuthState(error = "Vui lòng nhập Email để khôi phục!")
//            return
//        }
//        viewModelScope.launch {
//            _state.value = AuthState(isLoading = true)
//
//            // Sinh mã ngẫu nhiên 6 chữ số
//            val otp = (100000..999999).random().toString()
//
//            // Lưu mã này vào Firebase Firestore (Collection: OTPs)
//            FirebaseFirestore.getInstance().collection("OTPs").document(email)
//                .set(mapOf("otp" to otp, "timestamp" to System.currentTimeMillis()))
//                .addOnSuccessListener {
//                    // MẸO: Hiện thẳng mã OTP ra thông báo để bạn dễ dàng test Đồ án
//                    _state.value = AuthState(isSuccess = true, error = "Đã gửi mã! (Mã Test: $otp)")
//                }
//                .addOnFailureListener {
//                    _state.value = AuthState(error = "Lỗi hệ thống: Không thể tạo mã OTP")
//                }
//        }
//    }
//
//    // Bước 2: Xác minh mã OTP
//    fun verifyOtp(email: String, otp: String, onSuccess: () -> Unit) {
//        if (otp.isBlank()) {
//            _state.value = AuthState(error = "Vui lòng nhập mã OTP!")
//            return
//        }
//        viewModelScope.launch {
//            _state.value = AuthState(isLoading = true)
//
//            // Lấy mã OTP từ Firestore xuống để so sánh
//            FirebaseFirestore.getInstance().collection("OTPs").document(email).get()
//                .addOnSuccessListener { doc ->
//                    if (doc.exists()) {
//                        val savedOtp = doc.getString("otp")
//                        if (savedOtp == otp) {
//                            // Nếu mã khớp -> Báo thành công và gọi hàm điều hướng
//                            _state.value = AuthState(isSuccess = true, error = "Xác thực thành công!")
//                            onSuccess()
//                        } else {
//                            _state.value = AuthState(error = "Mã OTP không chính xác!")
//                        }
//                    } else {
//                        _state.value = AuthState(error = "Không tìm thấy mã OTP cho email này!")
//                    }
//                }
//                .addOnFailureListener {
//                    _state.value = AuthState(error = "Lỗi kiểm tra mã OTP")
//                }
//        }
//    }

    // =======================================================

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun resetState() {
        _state.value = AuthState()
    }

    fun updateUserProfile(username: String? = null, gender: String? = null, avatarUrl: String? = null, onResult: ((Boolean, String?) -> Unit)? = null) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            onResult?.invoke(false, "Chưa đăng nhập")
            return
        }
        val updates = mutableMapOf<String, Any>()
        username?.let { updates["username"] = it }
        gender?.let { updates["gender"] = it }
        avatarUrl?.let { updates["avatarUrl"] = it }

        if (updates.isEmpty()) return

        FirebaseFirestore.getInstance().collection("users").document(uid)
            .set(updates, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                loadUserProfile()
                onResult?.invoke(true, "Cập nhật thành công!")
            }
            .addOnFailureListener { e ->
                onResult?.invoke(false, e.localizedMessage ?: "Cập nhật thất bại!")
            }
    }

    fun changePassword(oldPass: String, newPass: String, onResult: (Boolean, String?) -> Unit) {
        val user = FirebaseAuth.getInstance().currentUser
        val email = user?.email
        if (user == null || email.isNullOrBlank()) {
            onResult(false, "Chưa đăng nhập")
            return
        }
        if (oldPass.isBlank()) {
            onResult(false, "Vui lòng nhập mật khẩu hiện tại!")
            return
        }
        if (newPass.length < 6) {
            onResult(false, "Mật khẩu mới phải có ít nhất 6 ký tự!")
            return
        }

        val credential = EmailAuthProvider.getCredential(email, oldPass)
        user.reauthenticate(credential)
            .addOnSuccessListener {
                user.updatePassword(newPass)
                    .addOnSuccessListener {
                        onResult(true, "Đổi mật khẩu thành công!")
                    }
                    .addOnFailureListener { e ->
                        onResult(false, e.localizedMessage ?: "Không thể đổi mật khẩu!")
                    }
            }
            .addOnFailureListener {
                onResult(false, "Mật khẩu hiện tại không chính xác!")
            }
    }

    fun uploadAndSaveAvatar(uri: Uri, context: Context, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            _state.value = AuthState(isLoading = true)
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes() ?: throw Exception("Không thể đọc file ảnh")
                inputStream.close()

                val base64Image = Base64.encodeToString(bytes, Base64.DEFAULT)

                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("key", "eba44471019a00974a5ce9624bb366cc")
                    .addFormDataPart("image", base64Image)
                    .build()

                val request = Request.Builder()
                    .url("https://api.imgbb.com/1/upload")
                    .post(requestBody)
                    .build()

                val client = OkHttpClient()
                val response = withContext(Dispatchers.IO) {
                    client.newCall(request).execute()
                }

                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    if (responseBody != null) {
                        val jsonObject = JSONObject(responseBody)
                        val imageUrl = jsonObject.getJSONObject("data").getString("url")
                        updateUserProfile(avatarUrl = imageUrl) { success, msg ->
                            _state.value = AuthState()
                            onResult(success, msg ?: "Đã đổi ảnh đại diện thành công!")
                        }
                        return@launch
                    }
                }
                _state.value = AuthState()
                onResult(false, "Không thể tải ảnh đại diện lên máy chủ!")
            } catch (e: Exception) {
                _state.value = AuthState()
                onResult(false, e.localizedMessage ?: "Lỗi tải ảnh")
            }
        }
    }

    fun loadUserProfile() {
        val firebaseUser = FirebaseAuth.getInstance().currentUser
        if (firebaseUser == null) {
            _currentUser.value = null
            return
        }
        val uid = firebaseUser.uid

        // Set immediate fallback user so UI isn't null while Firestore loads
        if (_currentUser.value == null) {
            val email = firebaseUser.email ?: ""
            val username = firebaseUser.displayName?.takeIf { it.isNotBlank() }
                ?: email.substringBefore("@").takeIf { it.isNotBlank() }
                ?: "Thành viên"
            _currentUser.value = com.example.walletwise.domain.model.User(
                id = uid,
                email = email,
                username = username
            )
        }

        profileListener?.remove()
        profileListener = FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .addSnapshotListener { snapshot, error ->

                if (error != null) {
                    Log.e("FIRESTORE_ERROR", "Unable to listen to user profile", error)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val userObj = runCatching {
                        snapshot.toObject(com.example.walletwise.domain.model.User::class.java)
                    }.onFailure {
                        Log.e("FIRESTORE_ERROR", "Ignoring invalid user profile", it)
                    }.getOrNull()
                    if (userObj != null) {
                        _currentUser.value = userObj
                    } else {
                        val email = runCatching { snapshot.getString("email") }.getOrNull()
                            ?: firebaseUser.email
                            ?: ""
                        val username = runCatching { snapshot.getString("username") }.getOrNull()
                            ?: firebaseUser.displayName
                            ?: email.substringBefore("@")
                        val streak = runCatching { snapshot.getLong("currentStreak") }
                            .getOrNull()
                            ?.toInt()
                            ?: 0
                        val lastDate = runCatching { snapshot.getString("lastRecordDate") }
                            .getOrNull()
                            ?: ""
                        _currentUser.value = com.example.walletwise.domain.model.User(
                            id = uid,
                            email = email,
                            username = username,
                            currentStreak = streak,
                            lastRecordDate = lastDate
                        )
                    }
                } else {
                    // Document does not exist in Firestore -> Create default document for user
                    val email = firebaseUser.email ?: ""
                    val username = firebaseUser.displayName?.takeIf { it.isNotBlank() }
                        ?: email.substringBefore("@").takeIf { it.isNotBlank() }
                        ?: "Thành viên"
                    val defaultUserMap = mapOf(
                        "id" to uid,
                        "email" to email,
                        "username" to username,
                        "currentStreak" to 0,
                        "lastRecordDate" to ""
                    )
                    FirebaseFirestore.getInstance().collection("users").document(uid).set(defaultUserMap)
                    _currentUser.value = com.example.walletwise.domain.model.User(
                        id = uid,
                        email = email,
                        username = username
                    )
                }
            }
    }

    fun sendPasswordReset(email: String) {
        if (email.isBlank()) {
            _state.value = AuthState(
                error = "Vui lòng nhập Email!"
            )
            return
        }

        _state.value = AuthState(isLoading = true)

        FirebaseAuth.getInstance()
            .sendPasswordResetEmail(email)
            .addOnSuccessListener {
                _state.value = AuthState(
                    isSuccess = true,
                    error = "Đã gửi email khôi phục mật khẩu. Vui lòng kiểm tra hộp thư."
                )
            }
            .addOnFailureListener { e ->
                _state.value = AuthState(
                    error = e.localizedMessage ?: "Không thể gửi email khôi phục"
                )
            }
    }
    private val _isLoggedIn = MutableStateFlow(
        FirebaseAuth.getInstance().currentUser != null
    )

    val isLoggedIn = _isLoggedIn.asStateFlow()

    init {
        loadUserProfile()

        authStateListener = FirebaseAuth.AuthStateListener { auth ->

            val loggedIn = auth.currentUser != null

            _isLoggedIn.value = loggedIn

            if (!loggedIn) {
                profileListener?.remove()
                profileListener = null
                _currentUser.value = null
            } else {
                loadUserProfile()
            }
        }
        FirebaseAuth.getInstance().addAuthStateListener(authStateListener!!)
    }

    override fun onCleared() {
        profileListener?.remove()
        authStateListener?.let(FirebaseAuth.getInstance()::removeAuthStateListener)
        super.onCleared()
    }
}
