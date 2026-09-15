import UIKit
import FirebaseCore
import FirebaseAuth
import FirebaseFirestore

final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        FirebaseApp.configure()
        _ = Auth.auth()
        _ = Firestore.firestore()

        #if DEBUG
        assert(FirebaseApp.app() != nil)
        NSLog("[FirebaseBootstrap] Core/Auth/Firestore initialized; no auth or database operations.")
        #endif

        return true
    }
}
