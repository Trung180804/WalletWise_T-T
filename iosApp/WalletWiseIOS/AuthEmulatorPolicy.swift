import Foundation

enum AuthEmulatorPolicy {
    enum Decision: Equatable { case disabled, blocked, enabled }

    static func evaluate(_ environment: [String: String]) -> Decision {
        #if DEBUG
        guard let flag = environment["WALLETWISE_AUTH_EMULATOR"] else { return .disabled }
        guard flag == "1",
              environment["WALLETWISE_AUTH_PROJECT"] == "demo-walletwise",
              environment["WALLETWISE_AUTH_HOST"] == "127.0.0.1",
              environment["WALLETWISE_AUTH_PORT"] == "9099" else { return .blocked }
        return .enabled
        #else
        // A Release binary can never enable an emulator or this experimental Auth adapter.
        return environment["WALLETWISE_AUTH_EMULATOR"] == nil ? .disabled : .blocked
        #endif
    }
}
