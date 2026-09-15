#if DEBUG
import Foundation
import WalletWiseShared

/** Opt-in, status-only integration checks against the same presenter hosted by Compose. */
final class AuthEmulatorIntegrationProbe {
    private static let fixtureKey = "WalletWiseAuthEmulatorTestFixture"
    private let presenter: ConnectedAuthPresenter
    private let adapter: FirebaseAuthAdapter
    private var failed = false

    private init(presenter: ConnectedAuthPresenter, adapter: FirebaseAuthAdapter) {
        self.presenter = presenter
        self.adapter = adapter
    }

    static func run(phase: String, presenter: ConnectedAuthPresenter) {
        guard let adapter = AuthBootstrap.adapter else { return }
        let probe = AuthEmulatorIntegrationProbe(presenter: presenter, adapter: adapter)
        probe.wait("session-initialization", until: { presenter.snapshot.sessionReady }) {
            switch phase {
            case "exercise": probe.exercise()
            case "restore-reset-logout": probe.restoreResetLogout()
            case "signed-out": probe.verifySignedOut()
            case "cleanup":
                presenter.logout()
                UserDefaults.standard.removeObject(forKey: fixtureKey)
                probe.finish("cleanup")
            default: probe.fail("unknown-phase")
            }
        }
    }

    private func exercise() {
        guard check(!presenter.snapshot.isAuthenticated && adapter.currentSubject == nil, "initially-signed-out") else { return }
        // Only identifiers needed for the cross-process restore/reset check are persisted.
        // Password stays in this controller's asynchronous test work, never in preferences.
        let email = "checkpoint5d1-\(UUID().uuidString.lowercased())@example.invalid"
        let password = UUID().uuidString + UUID().uuidString
        UserDefaults.standard.set(["email": email], forKey: Self.fixtureKey)
        fill(.register_, email: email, password: password)
        presenter.submit()
        presenter.submit()
        guard check(adapter.registerRequestCount == 1 && presenter.snapshot.auth.isLoading, "register-double-submit-blocked") else { return }
        let duplicate = DuplicateCompletion()
        _ = adapter.register(email: email, password: password, completion: duplicate)
        guard check(duplicate.failure == AuthFailure.requestInProgress && adapter.registerRequestCount == 1, "adapter-global-request-gate") else { return }
        wait("register", until: { self.completedAuthentication }) {
            guard self.check(self.adapter.authenticationSuccessCount == 1, "register-one-success"),
                  self.check(self.presenter.snapshot.auth.login.password.isEmpty && self.presenter.snapshot.auth.register_.password.isEmpty && self.presenter.snapshot.auth.pendingEvent == nil, "register-clears-form-no-home") else { return }
            var fixture = self.fixture!
            fixture["subject"] = self.adapter.currentSubject!
            UserDefaults.standard.set(fixture, forKey: Self.fixtureKey)
            self.presenter.logout()
            guard self.check(!self.presenter.snapshot.isAuthenticated && self.adapter.currentSubject == nil, "logout-after-register") else { return }
            self.fill(.login, email: email, password: UUID().uuidString)
            self.presenter.submit()
            self.wait("wrong-password", until: {
                !self.presenter.snapshot.auth.isLoading && self.presenter.snapshot.auth.login.operation is AuthOperationStateRepositoryError
            }) {
                guard self.check(!self.presenter.snapshot.isAuthenticated && self.adapter.currentSubject == nil, "wrong-password-safe-failure"),
                      self.check((self.presenter.snapshot.auth.login.operation as? AuthOperationStateRepositoryError)?.message == "Email hoặc mật khẩu không đúng.", "wrong-password-stable-message") else { return }
                self.presenter.onPasswordChanged(value: password)
                self.presenter.submit()
                self.presenter.submit()
                self.wait("correct-login", until: { self.completedAuthentication }) {
                    guard self.check(self.adapter.loginRequestCount == 2 && self.adapter.authenticationSuccessCount == 2, "login-double-submit-one-success"),
                          self.check(self.adapter.currentSubject == fixture["subject"], "correct-login-same-session"),
                          self.check(Set(self.fixture!.keys) == Set(["email", "subject"]), "fixture-does-not-persist-password") else { return }
                    self.finish("exercise")
                }
            }
        }
    }

    private func restoreResetLogout() {
        guard let fixture, let email = fixture["email"], let subject = fixture["subject"] else { fail("missing-fixture"); return }
        guard check(presenter.snapshot.isAuthenticated && adapter.currentSubject == subject && presenter.snapshot.identity?.subject == subject, "keychain-restored-same-session"),
              check(adapter.loginRequestCount == 0 && adapter.registerRequestCount == 0, "restore-without-login-rpc") else { return }
        presenter.logout()
        guard check(!presenter.snapshot.isAuthenticated && adapter.currentSubject == nil && presenter.snapshot.auth.login.password.isEmpty, "logout-clears-sdk-and-shared") else { return }
        fill(.forgotPassword, email: email, password: "")
        presenter.submit()
        presenter.submit()
        guard check(adapter.resetRequestCount == 1, "reset-double-submit-blocked") else { return }
        wait("reset-acceptance", until: {
            !self.presenter.snapshot.auth.isLoading && self.presenter.snapshot.auth.forgotPassword.operation is AuthOperationStateSuccess
        }) {
            guard self.check(!self.presenter.snapshot.isAuthenticated && self.adapter.currentSubject == nil, "reset-accepted-without-login"),
                  self.check(self.adapter.resetRequestCount == 1, "reset-one-accepted-request") else { return }
            self.presenter.navigateTo(route: .login)
            guard self.check(self.presenter.snapshot.auth.forgotPassword.email.isEmpty && self.presenter.snapshot.auth.login.password.isEmpty, "route-clears-reset-form") else { return }
            UserDefaults.standard.removeObject(forKey: Self.fixtureKey)
            self.finish("restore-reset-logout")
        }
    }

    private func verifySignedOut() {
        guard check(!presenter.snapshot.isAuthenticated && adapter.currentSubject == nil, "logout-not-restored-after-relaunch"),
              check(adapter.loginRequestCount == 0 && adapter.registerRequestCount == 0 && adapter.resetRequestCount == 0, "signed-out-no-auth-rpc"),
              check(fixture == nil, "temporary-fixture-removed") else { return }
        finish("signed-out")
    }

    private var fixture: [String: String]? { UserDefaults.standard.dictionary(forKey: Self.fixtureKey) as? [String: String] }
    private var completedAuthentication: Bool {
        presenter.snapshot.isAuthenticated && !presenter.snapshot.auth.isLoading && presenter.snapshot.sessionOperation is AuthOperationStateSuccess
    }

    private func fill(_ route: AuthRoute, email: String, password: String) {
        presenter.navigateTo(route: route)
        presenter.onUsernameChanged(value: "Emulator member")
        presenter.onEmailChanged(value: email)
        presenter.onPasswordChanged(value: password)
    }

    @discardableResult private func check(_ condition: Bool, _ name: String) -> Bool {
        guard condition else { fail(name); return false }
        NSLog("[AuthIntegration] PASS %@", name)
        return true
    }

    private func wait(_ name: String, until condition: @escaping () -> Bool, then continuation: @escaping () -> Void) {
        let deadline = Date().addingTimeInterval(20)
        func poll() {
            guard !failed else { return }
            if condition() { continuation(); return }
            guard Date() < deadline else { fail("timeout-" + name); return }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) { poll() }
        }
        poll()
    }

    private func fail(_ name: String) {
        failed = true
        NSLog("[AuthIntegration] FAIL %@", name)
    }

    private func finish(_ phase: String) { NSLog("[AuthIntegration] COMPLETE %@", phase) }
}

private final class DuplicateCompletion: NSObject, AuthCompletion {
    private(set) var failure: AuthFailure?
    func complete(identity: AuthIdentity?, failure: AuthFailure?) { self.failure = failure }
}
#endif
