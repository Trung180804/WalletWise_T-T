package com.example.walletwise.data.repository

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.example.walletwise.domain.model.Transaction
import com.example.walletwise.domain.repository.TransactionRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class TransactionRepositoryImpl : TransactionRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val client = OkHttpClient()

    private val IMGBB_API_KEY = "eba44471019a00974a5ce9624bb366cc"

    override suspend fun addTransaction(transaction: Transaction, localImageUri: Uri?, context: Context): Result<Boolean> {
        return try {
            val userId = auth.currentUser?.uid ?: throw Exception("Chưa đăng nhập")
            val docRef = db.collection("TRANSACTIONS").document()

            var onlineImageUrl = ""

            // Nếu có ảnh, gọi hàm đẩy lên ImgBB
            if (localImageUri != null) {
                onlineImageUrl = uploadImageToImgBB(localImageUri, context)
            }

            // Gắn link online (URL) vào Giao dịch
            val finalTransaction = transaction.copy(
                id = docRef.id,
                userId = userId,
                imageUrl = onlineImageUrl
            )

            // Lưu thông tin văn bản + link ảnh vào Firebase Firestore (Database)
            docRef.set(finalTransaction).await()

            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Hàm phụ trợ: Mã hóa ảnh và Bắn lên server ImgBB
    private suspend fun uploadImageToImgBB(uri: Uri, context: Context): String {
        return withContext(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes() ?: return@withContext ""
                inputStream.close()

                val base64Image = Base64.encodeToString(bytes, Base64.DEFAULT)

                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("key", IMGBB_API_KEY)
                    .addFormDataPart("image", base64Image)
                    .build()

                val request = Request.Builder()
                    .url("https://api.imgbb.com/1/upload")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    if (responseBody != null) {
                        val jsonObject = JSONObject(responseBody)
                        // Bóc tách JSON để lấy đường link URL của bức ảnh
                        return@withContext jsonObject.getJSONObject("data").getString("url")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return@withContext ""
        }
    }

    override suspend fun getTransactions(): Result<List<Transaction>> {
        return try {
            val userId = auth.currentUser?.uid ?: throw Exception("Chưa đăng nhập")
            val snapshot = db.collection("TRANSACTIONS")
                .whereEqualTo("userId", userId)
                .get()
                .await()
            val transactions = snapshot.toObjects(Transaction::class.java)
            Result.success(transactions)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteTransaction(transactionId: String): Result<Boolean> {
        return try {
            db.collection("TRANSACTIONS").document(transactionId).delete().await()
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateTransaction(
        transaction: Transaction,
        localImageUri: Uri?,
        context: Context
    ): Result<Boolean> {
        return try {

            var imageUrl = transaction.imageUrl

            if (localImageUri != null) {
                imageUrl = uploadImageToImgBB(localImageUri, context)
            }

            val updatedTransaction = transaction.copy(
                imageUrl = imageUrl
            )

            db.collection("TRANSACTIONS")
                .document(transaction.id)
                .set(updatedTransaction)
                .await()

            if (localImageUri != null) {
                imageUrl = uploadImageToImgBB(localImageUri, context)

                android.util.Log.d(
                    "UPDATE_TX",
                    "UPLOADED_URLl = $imageUrl"
                )
            }
            Result.success(true)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}