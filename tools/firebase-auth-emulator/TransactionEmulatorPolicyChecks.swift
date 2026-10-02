import Foundation

@main struct TransactionEmulatorPolicyChecks {
    static func main() {
        let valid = ["WALLETWISE_AUTH_EMULATOR": "1", "WALLETWISE_AUTH_PROJECT": "demo-walletwise", "WALLETWISE_AUTH_HOST": "127.0.0.1", "WALLETWISE_AUTH_PORT": "9099", "WALLETWISE_FIRESTORE_EMULATOR": "1", "WALLETWISE_FIRESTORE_PROJECT": "demo-walletwise", "WALLETWISE_FIRESTORE_HOST": "127.0.0.1", "WALLETWISE_FIRESTORE_PORT": "8080"]
        var checks = 0
        func verify(_ environment: [String: String], _ expected: TransactionEmulatorPolicy.Decision) {
            precondition(TransactionEmulatorPolicy.evaluate(environment) == expected); checks += 1
        }
        verify([:], .disabled)
        #if DEBUG
        verify(valid, .enabled)
        #else
        verify(valid, .blocked)
        #endif
        for key in valid.keys.sorted() {
            var changed = valid; changed[key] = "invalid"; verify(changed, .blocked)
            changed = valid; changed.removeValue(forKey: key)
            verify(changed, key == "WALLETWISE_FIRESTORE_EMULATOR" ? .disabled : .blocked)
        }
        print("Transaction emulator policy: \(checks) passed, 0 failed, 0 skipped")
    }
}
