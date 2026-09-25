package com.example.walletwise.testing

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.util.Log
import com.example.walletwise.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Debug-only replacement for FirebaseInitProvider.
 *
 * Android creates providers before broadcast receivers. Consequently a debug
 * APK built with USE_FIREBASE_EMULATOR=true re-establishes the default Firebase
 * app and both emulator endpoints after process death and after a real reboot.
 * If any safety invariant is violated initialization fails closed rather than
 * falling through to the google-services production configuration.
 */
class DebugFirebaseInitProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        val appContext = checkNotNull(context).applicationContext
        DebugFirebaseBootstrap.initialize(appContext)
        return false
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}

internal object DebugFirebaseBootstrap {
    const val DEMO_PROJECT_ID = "demo-walletwise"
    const val EMULATOR_HOST = "10.0.2.2"
    const val AUTH_PORT = 9099
    const val FIRESTORE_PORT = 8080
    const val LOG_TAG = "FIREBASE_DEBUG_BOOTSTRAP"

    @Volatile
    private var initializedEndpoint: FirebaseEmulatorEndpoint? = null

    fun initialize(context: android.content.Context): FirebaseEmulatorEndpoint? = synchronized(this) {
        initializedEndpoint?.let { return it }
        if (!BuildConfig.USE_FIREBASE_EMULATOR) {
            FirebaseApp.initializeApp(context)
            Log.i(LOG_TAG, "Firebase emulator disabled; normal debug initialization")
            return null
        }

        val endpoint = FirebaseEmulatorSafety.resolve(
            requested = true,
            debuggable = BuildConfig.DEBUG,
            projectId = DEMO_PROJECT_ID,
            host = EMULATOR_HOST,
            firestorePort = FIRESTORE_PORT,
            authPort = AUTH_PORT,
            runningOnAndroidEmulator = FirebaseEmulatorSafety.isAndroidEmulator(
                fingerprint = Build.FINGERPRINT,
                model = Build.MODEL,
                manufacturer = Build.MANUFACTURER,
                hardware = Build.HARDWARE,
                product = Build.PRODUCT,
                device = Build.DEVICE
            ),
            hostWasExplicitlyConfigured = false
        ) ?: error("Explicit Firebase Emulator request did not resolve an endpoint")

        val existing = FirebaseApp.getApps(context).firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
        val app = existing ?: FirebaseApp.initializeApp(context, demoOptions())
        checkNotNull(app) { "Unable to initialize the debug Firebase app" }
        check(app.options.projectId == DEMO_PROJECT_ID) {
            "Refusing Firebase Emulator bootstrap for project ${app.options.projectId}"
        }

        FirebaseAuth.getInstance(app).useEmulator(endpoint.host, endpoint.authPort)
        FirebaseFirestore.getInstance(app).useEmulator(endpoint.host, endpoint.firestorePort)
        initializedEndpoint = endpoint
        Log.i(
            LOG_TAG,
            "Default Firebase project=${endpoint.projectId} " +
                "Auth=${endpoint.host}:${endpoint.authPort} " +
                "Firestore=${endpoint.host}:${endpoint.firestorePort}"
        )
        endpoint
    }

    fun endpoint(): FirebaseEmulatorEndpoint? = initializedEndpoint

    private fun demoOptions(): FirebaseOptions = FirebaseOptions.Builder()
        .setProjectId(DEMO_PROJECT_ID)
        .setApplicationId("1:1234567890:android:checkpoint04j")
        .setApiKey("fake-checkpoint-04j-key")
        .build()
}
