package com.example.walletwise.data.mapper

import com.example.walletwise.domain.model.Reminder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReminderFirestoreCompatibilityTest {
    @Test
    fun pathAndCurrentWireFieldsRemainStable() {
        assertEquals("users/user-1/reminders/reminder-1", FirestoreSchema.reminderDocument("user-1", "reminder-1"))
        val reminder = Reminder(
            id = "reminder-1",
            userId = "user-1",
            title = "Ghi chép",
            frequency = "Hàng tuần",
            startDate = "9 thg 9, 2026",
            time = "20:15",
            note = "Ghi lại chi tiêu",
            isEnabled = false
        )
        assertEquals(
            setOf("id", "userId", "title", "frequency", "startDate", "time", "note", "enabled"),
            FirestoreWireMapper.reminderToMap(reminder).keys
        )
        assertEquals(reminder, FirestoreWireMapper.reminderFromMap("reminder-1", "user-1", FirestoreWireMapper.reminderToMap(reminder)))
    }

    @Test
    fun documentAndCurrentUserIdsAreAuthoritativeForLegacyData() {
        val reminder = FirestoreWireMapper.reminderFromMap(
            "document-id",
            "current-user",
            mapOf("id" to "stale-id", "userId" to "old-user", "title" to "Legacy")
        )
        assertEquals("document-id", reminder.id)
        assertEquals("current-user", reminder.userId)
    }

    @Test
    fun enabledWinsOverLegacyIsEnabledAndNumberValuesAreSafe() {
        val established = FirestoreWireMapper.reminderFromMap(
            "one", "user", mapOf("enabled" to false, "isEnabled" to true)
        )
        val legacyLong = FirestoreWireMapper.reminderFromMap("two", "user", mapOf("isEnabled" to 1L))
        val legacyDouble = FirestoreWireMapper.reminderFromMap("three", "user", mapOf("isEnabled" to 0.0))
        assertFalse(established.isEnabled)
        assertTrue(legacyLong.isEnabled)
        assertFalse(legacyDouble.isEnabled)
    }

    @Test
    fun missingOptionalFieldsUseCompatibleDefaults() {
        val reminder = FirestoreWireMapper.reminderFromMap("id", "user", emptyMap())
        assertEquals("", reminder.title)
        assertEquals("Hàng ngày", reminder.frequency)
        assertEquals("", reminder.startDate)
        assertEquals("20:15", reminder.time)
        assertEquals("", reminder.note)
        assertTrue(reminder.isEnabled)
    }
}
