import Foundation

@main
struct AuthEmulatorPolicyChecks {
    static func main() {
        let valid = ["WALLETWISE_AUTH_EMULATOR": "1", "WALLETWISE_AUTH_PROJECT": "demo-walletwise",
                     "WALLETWISE_AUTH_HOST": "127.0.0.1", "WALLETWISE_AUTH_PORT": "9099"]
        var checks = 0
        func verify(_ environment: [String: String], _ expected: AuthEmulatorPolicy.Decision) {
            precondition(AuthEmulatorPolicy.evaluate(environment) == expected)
            checks += 1
        }
        verify([:], .disabled)
        #if DEBUG
        verify(valid, .enabled)
        #else
        verify(valid, .blocked)
        #endif
        for (key, wrong) in [("WALLETWISE_AUTH_EMULATOR", "true"), ("WALLETWISE_AUTH_PROJECT", "other-project"),
                             ("WALLETWISE_AUTH_HOST", "localhost"), ("WALLETWISE_AUTH_PORT", "9100")] {
            var environment = valid
            environment[key] = wrong
            verify(environment, .blocked)
        }
        for key in ["WALLETWISE_AUTH_PROJECT", "WALLETWISE_AUTH_HOST", "WALLETWISE_AUTH_PORT"] {
            var environment = valid
            environment.removeValue(forKey: key)
            verify(environment, .blocked)
        }
        print("Auth emulator policy: \(checks) passed, 0 failed, 0 skipped")
    }
}
