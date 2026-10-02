package com.example.walletwise.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthUserTest {
    @Test fun mapsCompleteUserAndPreservesSdkEmailCase() {
        val user = AuthUser.create("opaque", "  Member@Example.invalid  ", "  Thành viên  ", true)!!
        assertEquals("opaque", user.uid)
        assertEquals("Member@Example.invalid", user.email)
        assertEquals("Thành viên", user.displayName)
        assertTrue(user.emailVerified)
    }

    @Test fun nullableFieldsAreSupportedWithoutInventingData() {
        val user = AuthUser.create("opaque", null, null, false)!!
        assertNull(user.email)
        assertNull(user.displayName)
        assertFalse(user.emailVerified)
        assertEquals("Thành viên", user.displayLabel)
    }

    @Test fun emptyAndWhitespaceUidFailClosed() {
        for (uid in listOf("", " ", "\n\t")) assertNull(AuthUser.create(uid, null, null, false))
    }

    @Test fun blankDisplayNameFallsBackToTrimmedEmail() {
        val user = AuthUser.create("opaque", " member@example.invalid ", " \n ", false)!!
        assertNull(user.displayName)
        assertEquals("member@example.invalid", user.displayLabel)
    }

    @Test fun blankNullableFieldsStayNull() {
        val user = AuthUser.create("opaque", " ", " ", false)!!
        assertNull(user.email)
        assertNull(user.displayName)
        assertEquals("Thành viên", user.displayLabel)
    }

    @Test fun sessionRoundTripPreservesNormalizedUser() {
        val user = AuthUser.create("opaque", " Member@Example.invalid ", " Name ", true)!!
        assertEquals(user, AuthSession.fromUser(user).user)
        assertNull(AuthSession.fromUser(AuthUser.create("opaque", null, null, false)!!).user?.email)
    }

    @Test fun existingAndroidSessionConstructorAndCopyRemainCompatible() {
        val legacy = AuthSession("opaque", "member@example.invalid")
        assertEquals("", legacy.displayName)
        assertFalse(legacy.emailVerified)
        assertEquals("Name", legacy.copy(displayName = "Name").defaultUser().username)
        assertNull(AuthSession("", "").user)
    }

    @Test fun modelStringRepresentationsNeverExposeUserInformation() {
        val user = AuthUser.create("private-subject", "member@example.invalid", "Private name", true)!!
        assertEquals("AuthUser(redacted)", user.toString())
        assertEquals("AuthSession(redacted)", AuthSession.fromUser(user).toString())
    }
}
