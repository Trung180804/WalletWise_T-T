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
        require(projectId.startsWith("demo-") && projectId.length > "demo-".length) {
            "Only a demo-* Firebase project is allowed"
        }
        require(host.isNotBlank()) { "Firebase Emulator host is required" }
        require(firestorePort in 1..65_535 && authPort in 1..65_535) {
            "Firebase Emulator ports are invalid"
        }
        require(runningOnAndroidEmulator || hostWasExplicitlyConfigured) {
            "A physical device requires an explicitly configured development-host IP"
        }
        if (runningOnAndroidEmulator && !hostWasExplicitlyConfigured) {
            require(host == "10.0.2.2") { "Android Emulator must use 10.0.2.2 by default" }
        }
        return FirebaseEmulatorEndpoint(projectId, host, firestorePort, authPort)
    }
}
