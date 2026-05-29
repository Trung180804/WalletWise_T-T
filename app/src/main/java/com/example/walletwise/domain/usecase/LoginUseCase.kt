package com.example.walletwise.domain.usecase

import com.example.walletwise.domain.repository.AuthRepository

class LoginUseCase(private val repository: AuthRepository) {

    // Từ khóa "operator fun invoke" cho phép bạn gọi thẳng class này như một hàm
    suspend operator fun invoke(email: String, pass: String): Result<Boolean> {

        // 1. Kiểm tra logic nghiệp vụ (Validate)
        if (email.isBlank() || pass.isBlank()) {
            return Result.failure(Exception("Email hoặc mật khẩu không được để trống!"))
        }

        // 2. Nếu hợp lệ, gọi xuống Repository để Firebase làm việc
        return repository.login(email, pass)
    }
}