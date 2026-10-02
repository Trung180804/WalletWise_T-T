# KMP secret inventory

Checkpoint 1 deliberately does not relocate credentials, so the existing
Android application keeps building without a behavior or configuration change.
No credential value is duplicated in this document.

## Current locations

| Location | Purpose | Current status |
| --- | --- | --- |
| `app/src/main/java/com/example/walletwise/data/repository/TransactionRepositoryImpl.kt` | ImgBB upload API key used by the single Android `ImgBbImageUploader` | Hard-coded in Android source; no longer duplicated in `AuthViewModel` |
| `app/src/main/java/com/example/walletwise/presentation/home/TransactionAIAssistant.kt:9` | Gemini API key | Hard-coded in Android source |
| `app/google-services.json` | Firebase Android client configuration | Tracked; identifies the existing Firebase project |

`google-services.json` contains Firebase client configuration rather than a
server credential. It must remain associated with the current Firebase project;
it must not be copied to the iOS target.

## Safe migration proposal

### Android

1. Add local values to an ignored root `secrets.properties` file and inject
   them through typed Gradle `BuildConfig` fields.
2. Supply the same properties through protected CI environment variables for
   release builds.
3. Keep `app/google-services.json` in its current location during this
   checkpoint so the Firebase project and build behavior do not change.

### iOS

1. Copy `iosApp/Config/Secrets.xcconfig.example` to the ignored
   `Secrets.xcconfig`, then reference values through Xcode build settings.
2. Download `GoogleService-Info.plist` for the iOS bundle id from the same
   Firebase project and keep the local file out of version control unless the
   team explicitly chooses to track Firebase client configuration.
3. Add Firebase Apple SDK through Swift Package Manager only when the Swift
   repository adapters are implemented.

### Exposure risk

Moving a Gemini or ImgBB key from source into an application build setting
prevents accidental source-control disclosure but cannot make a client-side
key secret: a determined user can recover it from the app. The stronger design
is a controlled backend proxy with key restrictions, quotas, and abuse
monitoring. That is a separate architecture decision and no Cloud Function or
scheduler is introduced in this checkpoint.
