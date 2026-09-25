package com.example.walletwise.domain.repository

import com.example.walletwise.domain.model.ImageUpload

interface ImageUploader {
    suspend fun upload(image: ImageUpload): Result<String>
}
