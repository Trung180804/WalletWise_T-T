package com.example.walletwise.testing

internal data class FirebaseEmulatorEndpoint(
    val projectId: String,
    val host: String,
    val firestorePort: Int,
    val authPort: Int
)

/** Debug-only guard used by the Checkpoint 4I instrumentation harness. */
internal object FirebaseEmulatorSafety {
    fun isAndroidEmulator(
        fingerprint: String,
        model: String,
        manufacturer: String,
        hardware: String,
        product: String,
        device: String
    ): Boolean {
        val normalizedHardware = hardware.lowercase()
        return fingerprint.startsWith("generic", ignoreCase = true) ||
            fingerprint.contains("emulator", ignoreCase = true) ||
            model.contains("emulator", ignoreCase = true) ||
            manufacturer.contains("genymotion", ignoreCase = true) ||
            normalizedHardware == "goldfish" ||
            normalizedHardware == "ranchu" ||
            product.startsWith("sdk_", ignoreCase = true) ||
            device.startsWith("emu", ignoreCase = true)
    }

    fun resolve(
        requested: Boolean,
        debuggable: Boolean,
        projectId: String,
        host: String,
        firestorePort: Int,
        authPort: Int,
        runningOnAndroidEmulator: Boolean,
        hostWasExplicitlyConfigured: Boolean
    ): FirebaseEmulatorEndpoint? {
        if (!requested) return null
        require(debuggable) { "Firebase Emulator is forbidden outside a debuggable build" }
        require(projectId == "demo-walletwise") {
            "Only the isolated demo-walletwise project is allowed"
        }
        require(host.isNotBlank()) { "Firebase Emulator host is required" }
        require(firestorePort == 8080 && authPort == 9099) {
            "Firebase Emulator ports are invalid"
        }
        require(runningOnAndroidEmulator || hostWasExplicitlyConfigured) {
            "A physical device requires an explicitly configured development-host IP"
        }
        if (runningOnAndroidEmulator && !hostWasExplicitlyConfigured) {
            require(host == "10.0.2.2") { "Android Emulator must use 10.0.2.2 by default" }
        }
        require(host == "localhost" || host.matches(Regex("^(127\\.0\\.0\\.1|10\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}|192\\.168\\.[0-9]{1,3}\\.[0-9]{1,3}|192\\.0\\.2\\.[0-9]{1,3}|172\\.(1[6-9]|2[0-9]|3[01])\\.[0-9]{1,3}\\.[0-9]{1,3})$"))) {
            "Firebase Emulator requires a loopback or development-network host"
        }
        return FirebaseEmulatorEndpoint(projectId, host, firestorePort, authPort)
    }
}
