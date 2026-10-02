package com.example.walletwise.domain.repository

import com.example.walletwise.domain.model.Transaction

enum class TransactionMutationKind { ADD, UPDATE, REMOVE }

interface TransactionWriteCompletion {
    /** Null failure means the server has confirmed the write, never just a local queued write. */
    fun complete(failure: TransactionReadFailure?)
}

/** Same UI-thread/cancellation boundary as observation. IDs and ownership are fixed before submission. */
interface CallbackTransactionWriteService {
    fun mutate(kind: TransactionMutationKind, transaction: Transaction, completion: TransactionWriteCompletion): TransactionCancellation
}

class TransactionWriteException(val reason: TransactionReadFailure) : IllegalStateException(
    when (reason) {
        TransactionReadFailure.NOT_AUTHENTICATED -> "Phiên đăng nhập đã thay đổi. Vui lòng đăng nhập lại."
        TransactionReadFailure.PERMISSION_DENIED -> "Không thể thay đổi giao dịch này."
        TransactionReadFailure.NETWORK -> "Không thể lưu giao dịch. Vui lòng thử lại."
        TransactionReadFailure.UNKNOWN -> "Không thể hoàn tất giao dịch. Vui lòng thử lại."
    }
)
