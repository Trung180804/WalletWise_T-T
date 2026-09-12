package com.example.walletwise.testing

import com.example.walletwise.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FirebaseEmulatorSafetyTest {
    @Test
    fun `ordinary debug unit tests keep the emulator bootstrap disabled by default`() {
        assertTrue(BuildConfig.DEBUG)
        assertFalse(BuildConfig.USE_FIREBASE_EMULATOR)
    }

    @Test
    fun `modern Google Play AVD is recognized by ranchu and sdk product properties`() {
        assertTrue(
            FirebaseEmulatorSafety.isAndroidEmulator(
                fingerprint = "google/sdk_gphone64_x86_64/emu64xa:14/release-keys",
                model = "sdk_gphone64_x86_64",
                manufacturer = "Google",
                hardware = "ranchu",
                product = "sdk_gphone64_x86_64",
                device = "emu64xa"
            )
        )
        assertFalse(
            FirebaseEmulatorSafety.isAndroidEmulator(
                fingerprint = "google/panther/panther:14/release-keys",
                model = "Pixel 7",
                manufacturer = "Google",
                hardware = "tensor",
                product = "panther",
                device = "panther"
            )
        )
    }

    @Test
    fun `emulator is disabled unless explicitly requested`() {
        assertNull(
            FirebaseEmulatorSafety.resolve(
                requested = false,
                debuggable = true,
                projectId = "demo-walletwise",
                host = "10.0.2.2",
                firestorePort = 8080,
                authPort = 9099,
                runningOnAndroidEmulator = true,
                hostWasExplicitlyConfigured = false
            )
        )
    }

    @Test
    fun `release and production project ids are rejected even when requested`() {
        assertFails {
            FirebaseEmulatorSafety.resolve(
                requested = true,
                debuggable = false,
                projectId = "demo-walletwise",
                host = "10.0.2.2",
                firestorePort = 8080,
                authPort = 9099,
                runningOnAndroidEmulator = true,
                hostWasExplicitlyConfigured = false
            )
        }
        assertFails {
            FirebaseEmulatorSafety.resolve(
                requested = true,
                debuggable = true,
                projectId = "walletwise-production",
                host = "10.0.2.2",
                firestorePort = 8080,
                authPort = 9099,
                runningOnAndroidEmulator = true,
                hostWasExplicitlyConfigured = false
            )
        }
    }

    @Test
    fun `host policy distinguishes emulator and explicitly configured physical device`() {
        val emulator = FirebaseEmulatorSafety.resolve(
            requested = true,
            debuggable = true,
            projectId = "demo-walletwise",
            host = "10.0.2.2",
            firestorePort = 8080,
            authPort = 9099,
            runningOnAndroidEmulator = true,
            hostWasExplicitlyConfigured = false
        )
        assertEquals("10.0.2.2", emulator?.host)

        assertFails {
            FirebaseEmulatorSafety.resolve(
                requested = true,
                debuggable = true,
                projectId = "demo-walletwise",
                host = "10.0.2.2",
                firestorePort = 8080,
                authPort = 9099,
                runningOnAndroidEmulator = false,
                hostWasExplicitlyConfigured = false
            )
        }
        val physicalTestDevice = FirebaseEmulatorSafety.resolve(
            requested = true,
            debuggable = true,
            projectId = "demo-walletwise",
            host = "192.0.2.10",
            firestorePort = 8080,
            authPort = 9099,
            runningOnAndroidEmulator = false,
            hostWasExplicitlyConfigured = true
        )
        assertEquals("192.0.2.10", physicalTestDevice?.host)
    }

    private fun assertFails(block: () -> Unit) {
        var failed = false
        try {
            block()
        } catch (_: IllegalArgumentException) {
            failed = true
        }
        assertTrue(failed)
    }
}
