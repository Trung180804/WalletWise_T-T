package com.example.walletwise.domain.model

class ImageUpload(
    val bytes: ByteArray,
    val contentType: String,
    val fileName: String = "image"
)

data class AddTransactionInput(
    val transaction: Transaction,
    val image: ImageUpload? = null
)

data class UpdateTransactionInput(
    val transaction: Transaction,
    val replacementImage: ImageUpload? = null
)
