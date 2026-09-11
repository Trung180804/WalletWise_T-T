package com.example.walletwise.data.mapper

import com.example.walletwise.domain.model.RECURRING_FREQUENCY_DAILY
import com.example.walletwise.domain.model.RECURRING_FREQUENCY_MONTHLY
import com.example.walletwise.domain.model.RECURRING_FREQUENCY_WEEKLY
import com.example.walletwise.domain.model.RECURRING_FREQUENCY_WIRE_VALUES
import com.example.walletwise.domain.model.RECURRING_FREQUENCY_YEARLY
import com.example.walletwise.domain.model.RECURRING_TIMES_COUNT_WIRE_VALUES
import com.example.walletwise.domain.model.RECURRING_TIMES_UNLIMITED
import com.example.walletwise.domain.model.RecurringTransaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecurringFirestoreCompatibilityTest {
    @Test
    fun schemaPathRemainsNestedUnderTheOwningUser() {
        assertEquals(
            "users/user-1/recurring_transactions/recurring-1",
            FirestoreSchema.recurringTransactionDocument("user-1", "recurring-1")
        )
    }

    @Test
    fun legacyDocumentIdAndOwnerAreAuthoritativeWhenPayloadIdentityIsMissingOrStale() {
        val decoded = FirestoreWireMapper.recurringFromMap(
            documentId = "document-id",
            ownerUserId = "signed-in-user",
            data = mapOf("id" to "stale-id", "userId" to "old-user")
        )

        assertEquals("document-id", decoded.id)
        assertEquals("signed-in-user", decoded.userId)
    }

    @Test
    fun establishedEnabledFieldWinsAndLegacyIsEnabledRemainsReadable() {
        val established = FirestoreWireMapper.recurringFromMap(
            "one",
            "user",
            mapOf("enabled" to false, "isEnabled" to true)
        )
        val transitional = FirestoreWireMapper.recurringFromMap(
            "two",
            "user",
            mapOf("isEnabled" to 1L)
        )

        assertFalse(established.isEnabled)
        assertTrue(transitional.isEnabled)
        assertEquals(false, FirestoreWireMapper.recurringToMap(established)["enabled"])
        assertFalse(FirestoreWireMapper.recurringToMap(established).containsKey("isEnabled"))
    }

    @Test
    fun amountReadsFirestoreLongAndDoubleWithoutChangingWireFields() {
        val fromLong = FirestoreWireMapper.recurringFromMap("one", "user", mapOf("amount" to 42L))
        val fromDouble = FirestoreWireMapper.recurringFromMap("two", "user", mapOf("amount" to 42.5))

        assertEquals(42.0, fromLong.amount)
        assertEquals(42.5, fromDouble.amount)
        assertEquals(EXPECTED_FIELDS, FirestoreWireMapper.recurringToMap(fromLong).keys)
    }

    @Test
    fun allExistingFrequencyAndTimesCountWireValuesRoundTripExactly() {
        assertEquals(
            listOf(
                RECURRING_FREQUENCY_DAILY,
                RECURRING_FREQUENCY_WEEKLY,
                RECURRING_FREQUENCY_MONTHLY,
                RECURRING_FREQUENCY_YEARLY
            ),
            RECURRING_FREQUENCY_WIRE_VALUES
        )
        assertEquals((1..7).map(Int::toString) + RECURRING_TIMES_UNLIMITED, RECURRING_TIMES_COUNT_WIRE_VALUES)

        RECURRING_FREQUENCY_WIRE_VALUES.forEach { frequency ->
            RECURRING_TIMES_COUNT_WIRE_VALUES.forEach { timesCount ->
                val decoded = FirestoreWireMapper.recurringFromMap(
                    "id",
                    "user",
                    mapOf("frequency" to frequency, "timesCount" to timesCount)
                )
                val encoded = FirestoreWireMapper.recurringToMap(decoded)
                assertEquals(frequency, encoded["frequency"])
                assertEquals(timesCount, encoded["timesCount"])
            }
        }
    }

    private companion object {
        val EXPECTED_FIELDS = setOf(
            "id",
            "userId",
            "title",
            "amount",
            "type",
            "category",
            "paymentMethod",
            "frequency",
            "timesCount",
            "startDate",
            "time",
            "note",
            "lastExecutedDate",
            "enabled"
        )
    }
}
