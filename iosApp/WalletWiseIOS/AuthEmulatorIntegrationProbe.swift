#if DEBUG
import Foundation
import CryptoKit
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
            case "signed-out", "signed-out-b": probe.verifySignedOut(phase)
            case "exercise-user-b": probe.exerciseUserB()
            case "restore-user-b": probe.restoreUserB()
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
        let email = "checkpoint5d2-\(UUID().uuidString.lowercased())@example.invalid"
        let password = UUID().uuidString + UUID().uuidString
        let name = "Emulator " + UUID().uuidString.prefix(8)
        UserDefaults.standard.set(["email": email, "displayName": name], forKey: Self.fixtureKey)
        fill(.register_, email: email, password: password, name: "  " + name + "  ")
        presenter.submit()
        presenter.submit()
        guard check(adapter.registerRequestCount == 1 && presenter.snapshot.auth.isLoading, "register-double-submit-blocked") else { return }
        let duplicate = DuplicateCompletion()
        _ = adapter.register(email: email, password: password, displayName: name, completion: duplicate)
        guard check(duplicate.failure == AuthFailure.requestInProgress && adapter.registerRequestCount == 1, "adapter-global-request-gate") else { return }
        wait("register", until: { self.completedAuthentication }) {
            guard self.check(self.adapter.authenticationSuccessCount == 1, "register-one-success"),
                  self.check(self.adapter.profileUpdateRequestCount == 1 && self.adapter.reloadRequestCount == 1 && self.presenter.snapshot.user?.displayName == name && self.presenter.snapshot.user?.displayLabel == name && !self.presenter.snapshot.registrationIncomplete, "register-profile-update-reload-name"),
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
                          self.check(Set(self.fixture!.keys) == Set(["email", "subject", "displayName"]), "fixture-does-not-persist-password") else { return }
                    self.finish("exercise")
                }
            }
        }
    }

    private func restoreResetLogout() {
        guard let fixture, let email = fixture["email"], let subject = fixture["subject"] else { fail("missing-fixture"); return }
        guard check(presenter.snapshot.isAuthenticated && adapter.currentSubject == subject && presenter.snapshot.user?.uid == subject, "keychain-restored-same-session"),
              check(adapter.loginRequestCount == 0 && adapter.registerRequestCount == 0, "restore-without-login-rpc"),
              check(presenter.snapshot.user?.displayName == fixture["displayName"] && presenter.snapshot.user?.email == email && presenter.snapshot.user?.emailVerified == false, "restored-complete-user-display-name") else { return }
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

    private func verifySignedOut(_ phase: String) {
        guard check(!presenter.snapshot.isAuthenticated && adapter.currentSubject == nil, "logout-not-restored-after-relaunch"),
              check(adapter.loginRequestCount == 0 && adapter.registerRequestCount == 0 && adapter.resetRequestCount == 0, "signed-out-no-auth-rpc"),
              check(fixture == nil, "temporary-fixture-removed") else { return }
        finish(phase)
    }


    private func exerciseUserB() {
        guard check(presenter.snapshot.user == nil && adapter.currentUser == nil, "user-b-starts-signed-out") else { return }
        let email = "checkpoint5d2-b-\(UUID().uuidString.lowercased())@example.invalid"
        let password = UUID().uuidString + UUID().uuidString
        let created = UserCompletion()
        // SDK fixture setup only: a user without a display name. No fake response.
        _ = adapter.register(email: email, password: password, displayName: "", completion: created)
        wait("create-user-b", until: { created.completed && self.presenter.snapshot.user != nil }) {
            guard self.check(created.failure == nil && self.adapter.currentSubject == created.user?.uid, "user-b-sdk-account-created"),
                  self.check(self.presenter.snapshot.user?.displayName == nil && self.presenter.snapshot.user?.displayLabel == email, "user-b-email-fallback") else { return }
            UserDefaults.standard.set(["email": email, "subject": created.user!.uid], forKey: Self.fixtureKey)
            self.presenter.logout()
            guard self.check(self.presenter.snapshot.user == nil && self.adapter.currentUser == nil, "user-b-logout-before-login") else { return }
            self.fill(.login, email: email, password: password)
            self.presenter.submit()
            self.presenter.submit()
            self.wait("login-user-b", until: { self.completedAuthentication }) {
                guard self.check(self.adapter.loginRequestCount == 1 && self.presenter.snapshot.user?.uid == created.user?.uid, "user-b-real-login-double-submit-blocked"),
                      self.check(self.presenter.snapshot.user?.email == email && self.presenter.snapshot.user?.displayName == nil && self.presenter.snapshot.user?.displayLabel == email, "user-b-no-user-a-display-data"),
                      self.check(self.presenter.snapshot.auth.login.password.isEmpty && self.presenter.snapshot.auth.register_.password.isEmpty && self.presenter.snapshot.auth.pendingEvent == nil && !self.presenter.snapshot.registrationIncomplete, "user-b-clears-form-error-password"),
                      self.check(Set(self.fixture!.keys) == Set(["email", "subject"]), "user-b-fixture-no-password") else { return }
                self.finish("exercise-user-b")
            }
        }
    }

    private func restoreUserB() {
        guard let fixture, let email = fixture["email"], let subject = fixture["subject"] else { fail("missing-user-b-fixture"); return }
        guard check(presenter.snapshot.user?.uid == subject && adapter.currentSubject == subject, "user-b-keychain-restore"),
              check(presenter.snapshot.user?.displayName == nil && presenter.snapshot.user?.displayLabel == email, "user-b-restored-email-fallback"),
              check(adapter.loginRequestCount == 0 && adapter.registerRequestCount == 0, "user-b-restore-no-login-rpc") else { return }
        presenter.logout()
        guard check(presenter.snapshot.user == nil && adapter.currentUser == nil && presenter.snapshot.auth.login.password.isEmpty, "user-b-final-logout") else { return }
        UserDefaults.standard.removeObject(forKey: Self.fixtureKey)
        finish("restore-user-b")
    }

    private var fixture: [String: String]? { UserDefaults.standard.dictionary(forKey: Self.fixtureKey) as? [String: String] }
    private var completedAuthentication: Bool {
        presenter.snapshot.isAuthenticated && !presenter.snapshot.auth.isLoading && presenter.snapshot.sessionOperation is AuthOperationStateSuccess
    }

    private func fill(_ route: AuthRoute, email: String, password: String, name: String = "Emulator member") {
        presenter.navigateTo(route: route)
        presenter.onUsernameChanged(value: name)
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

    private func finish(_ phase: String) {
        // CFPreferences may expose an older file to an external process. Export only
        // hashes of demo fixture identifiers for exact Admin comparison, never values.
        if phase == "exercise" || phase == "exercise-user-b", let fixture {
            for key in ["email", "subject", "displayName"] {
                if let value = fixture[key] {
                    let digest = SHA256.hash(data: Data(value.utf8)).map { String(format: "%02x", $0) }.joined()
                    NSLog("[AuthFixture] %@ sha256=%@", key, digest)
                }
            }
        }
        _ = UserDefaults.standard.synchronize()
        NSLog("[AuthIntegration] COMPLETE %@", phase)
    }
}

private final class DuplicateCompletion: NSObject, AuthCompletion {
    private(set) var failure: AuthFailure?
    func complete(user: AuthUser?, failure: AuthFailure?) { self.failure = failure }
}
private final class UserCompletion: NSObject, AuthCompletion {
    private(set) var completed = false
    private(set) var user: AuthUser?
    private(set) var failure: AuthFailure?
    func complete(user: AuthUser?, failure: AuthFailure?) {
        self.user = user
        self.failure = failure
        completed = true
    }
}
#endif
