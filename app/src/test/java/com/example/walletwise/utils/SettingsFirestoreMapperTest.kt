package com.example.walletwise.utils

import com.example.walletwise.domain.model.RecurringTransaction
import com.example.walletwise.domain.model.Reminder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsFirestoreMapperTest {

    @Test
    fun `legacy enabled field is decoded and document id is authoritative`() {
        val recurring = SettingsFirestoreMapper.recurringFromMap(
            documentId = "firestore-document-id",
            ownerUserId = "current-user",
            data = mapOf(
                "id" to "stale-model-id",
                "userId" to "another-user",
                "title" to "Tiền nhà",
                "amount" to 2_000_000L,
                "enabled" to false
            )
        )

        assertEquals("firestore-document-id", recurring.id)
        assertEquals("current-user", recurring.userId)
        assertEquals(2_000_000.0, recurring.amount, 0.0)
        assertFalse(recurring.isEnabled)
    }

    @Test
    fun `established enabled value wins when a broken document contains both keys`() {
        val reminder = SettingsFirestoreMapper.reminderFromMap(
            documentId = "reminder-1",
            ownerUserId = "user-1",
            data = mapOf("enabled" to false, "isEnabled" to true)
        )

        assertFalse(reminder.isEnabled)
    }

    @Test
    fun `writes use one enabled key as the source of truth`() {
        val disabledRecurring = RecurringTransaction(id = "one", isEnabled = false)
        val enabledReminder = Reminder(id = "two", isEnabled = true)

        val recurringMap = SettingsFirestoreMapper.recurringToMap(disabledRecurring)
        val reminderMap = SettingsFirestoreMapper.reminderToMap(enabledReminder)

        assertEquals(false, recurringMap["enabled"])
        assertEquals(true, reminderMap["enabled"])
        assertFalse(recurringMap.containsKey("isEnabled"))
        assertFalse(reminderMap.containsKey("isEnabled"))
    }

    @Test
    fun `missing enabled fields default to enabled for existing records`() {
        val recurring = SettingsFirestoreMapper.recurringFromMap("id", "user", emptyMap())
        val reminder = SettingsFirestoreMapper.reminderFromMap("id", "user", emptyMap())

        assertTrue(recurring.isEnabled)
        assertTrue(reminder.isEnabled)
    }
}
