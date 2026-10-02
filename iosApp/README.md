# WalletWise iOS shell

This directory is the native iOS application boundary. The current checkpoint
contains a SwiftUI shell only; it does not yet link the shared Compose UI or
initialize Firebase.

Required work on macOS:

1. Open `WalletWiseIOS.xcodeproj` with a Kotlin-compatible Xcode version.
2. Select a development team and verify the bundle id
   `com.example.walletwise.ios`.
3. Register that bundle id as an Apple app in the existing WalletWise Firebase
   project and add its `GoogleService-Info.plist` to the app target.
4. Add Firebase Auth and Firestore from
   `https://github.com/firebase/firebase-ios-sdk` using Swift Package Manager.
5. Link `WalletWiseShared.framework` when the shared Compose entry point and
   Swift repository adapters are introduced in a later checkpoint.
6. Build and test on an iOS simulator and a physical device before reporting
   iOS runtime support.

Do not copy Android's `google-services.json` into this application.
