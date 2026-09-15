import SwiftUI
import WalletWiseShared

@main
struct WalletWiseIOSApp: App {
    init() {
        _ = PlatformDefaults_iosKt.currentEpochMilliseconds()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
