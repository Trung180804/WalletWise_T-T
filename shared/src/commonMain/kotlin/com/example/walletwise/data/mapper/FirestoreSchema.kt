package com.example.walletwise.data.mapper

object FirestoreSchema {
    const val USERS = "users"
    const val TRANSACTIONS = "transactions"
    const val LEGACY_ROOT_TRANSACTIONS = "TRANSACTIONS"
    const val BUDGETS = "budgets"
    const val REMINDERS = "reminders"
    const val RECURRING_TRANSACTIONS = "recurring_transactions"
    const val CATEGORIES = "categories"

    fun userDocument(userId: String): String = "$USERS/$userId"

    fun transactionsCollection(userId: String): String =
        "${userDocument(userId)}/$TRANSACTIONS"

    fun transactionDocument(userId: String, transactionId: String): String =
        "${transactionsCollection(userId)}/$transactionId"

    fun legacyTransactionDocument(transactionId: String): String =
        "$LEGACY_ROOT_TRANSACTIONS/$transactionId"

    fun budgetsCollection(userId: String): String =
        "${userDocument(userId)}/$BUDGETS"

    fun budgetDocument(userId: String, monthYear: String): String =
        "${budgetsCollection(userId)}/$monthYear"

    fun remindersCollection(userId: String): String =
        "${userDocument(userId)}/$REMINDERS"

    fun reminderDocument(userId: String, reminderId: String): String =
        "${remindersCollection(userId)}/$reminderId"

    fun recurringTransactionsCollection(userId: String): String =
        "${userDocument(userId)}/$RECURRING_TRANSACTIONS"

    fun recurringTransactionDocument(userId: String, recurringId: String): String =
        "${recurringTransactionsCollection(userId)}/$recurringId"

    fun categoriesCollection(userId: String): String =
        "${userDocument(userId)}/$CATEGORIES"

    fun categoryDocument(userId: String, categoryId: String): String =
        "${categoriesCollection(userId)}/$categoryId"
}
