import Foundation

enum TransactionEmulatorPolicy {
    enum Decision: Equatable { case disabled, blocked, enabled }
    static func evaluate(_ environment: [String: String]) -> Decision {
        guard let flag = environment["WALLETWISE_FIRESTORE_EMULATOR"] else { return .disabled }
        #if DEBUG
        guard flag == "1", AuthEmulatorPolicy.evaluate(environment) == .enabled,
              environment["WALLETWISE_FIRESTORE_PROJECT"] == "demo-walletwise",
              environment["WALLETWISE_FIRESTORE_HOST"] == "127.0.0.1",
              environment["WALLETWISE_FIRESTORE_PORT"] == "8080" else { return .blocked }
        return .enabled
        #else
        return .blocked
        #endif
    }
}
