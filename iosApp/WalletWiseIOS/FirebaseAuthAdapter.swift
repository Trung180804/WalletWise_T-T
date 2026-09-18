#if DEBUG
import Foundation
import FirebaseCore
import FirebaseAuth
import WalletWiseShared

private final class AuthCallbackCancellation: NSObject, AuthCancellation {
    private(set) var active = true
    var completion: AuthCompletion?
    var observer: AuthStateObserver?
    var listener: NSObjectProtocol?
    var listenerGeneration = 0
    var onCancel: (() -> Void)?

    func cancel() {
        precondition(Thread.isMainThread)
        guard active else { return }
        active = false
        completion = nil
        observer = nil
        onCancel?()
        onCancel = nil
    }
}

private final class RegistrationCompletion: NSObject, AuthCompletion {
    let finish: (AuthUser?, AuthFailure?) -> Void
    init(_ finish: @escaping (AuthUser?, AuthFailure?) -> Void) { self.finish = finish }
    func complete(user: AuthUser?, failure: AuthFailure?) { finish(user, failure) }
}

/** Only the validated Debug emulator bootstrap can construct this adapter. */
final class FirebaseAuthAdapter: NSObject, CallbackAuthService {
    private let auth: Auth
    private var observations: [UUID: AuthCallbackCancellation] = [:]
    private var requestInFlight = false
    private(set) var registerRequestCount = 0
    private(set) var profileUpdateRequestCount = 0
    private(set) var reloadRequestCount = 0
    private(set) var loginRequestCount = 0
    private(set) var resetRequestCount = 0
    private(set) var authenticationSuccessCount = 0
    var currentUser: AuthUser? { Self.map(auth.currentUser) }
    var currentSubject: String? { currentUser?.uid }

    init(emulatorApp: FirebaseApp) {
        precondition(Thread.isMainThread)
        precondition(emulatorApp.name == "WalletWiseAuthEmulator" && emulatorApp.options.projectID == "demo-walletwise")
        auth = Auth.auth(app: emulatorApp)
        auth.useEmulator(withHost: "127.0.0.1", port: 9099)
        super.init()
    }

    func observe(observer: AuthStateObserver) -> AuthCancellation {
        precondition(Thread.isMainThread)
        let id = UUID()
        let cancellation = AuthCallbackCancellation()
        cancellation.observer = observer
        observations[id] = cancellation
        installListener(cancellation)
        cancellation.onCancel = { [weak self, auth] in
            if let listener = cancellation.listener { auth.removeStateDidChangeListener(listener) }
            cancellation.listener = nil
            self?.observations.removeValue(forKey: id)
        }
        return cancellation
    }

    private func installListener(_ cancellation: AuthCallbackCancellation) {
        cancellation.listenerGeneration += 1
        let generation = cancellation.listenerGeneration
        cancellation.listener = auth.addStateDidChangeListener { [weak cancellation] _, user in
            guard let cancellation, cancellation.active,
                  cancellation.listenerGeneration == generation else { return }
            cancellation.observer?.changed(user: Self.map(user))
        }
    }

    private func refreshListeners() {
        // The SDK auth-state listener fires on UID changes, not display-name changes.
        // Re-registration delivers its initial SDK current-user snapshot asynchronously.
        for cancellation in observations.values where cancellation.active {
            if let listener = cancellation.listener { auth.removeStateDidChangeListener(listener) }
            installListener(cancellation)
        }
    }

    func login(email: String, password: String, completion: AuthCompletion) -> AuthCancellation {
        begin(completion) { finish in
            self.loginRequestCount += 1
            self.auth.signIn(withEmail: email, password: password) { result, error in
                let user = Self.map(result?.user)
                finish(user, error.map(Self.failure) ?? (user == nil ? .invalidUser : nil))
            }
        }
    }

    func register(email: String, password: String, displayName: String, completion: AuthCompletion) -> AuthCancellation {
        begin(completion) { finish in
            self.registerRequestCount += 1
            let progress = AuthRegistrationProgress(displayName: displayName, completion: RegistrationCompletion(finish))
            self.auth.createUser(withEmail: email, password: password) { result, error in
                guard progress.created(user: Self.map(result?.user), failure: error.map(Self.failure)),
                      let user = result?.user else { return }
                let changes = user.createProfileChangeRequest()
                let normalizedName = displayName.trimmingCharacters(in: .whitespacesAndNewlines)
                changes.displayName = normalizedName.isEmpty ? nil : normalizedName
                self.profileUpdateRequestCount += 1
                changes.commitChanges { error in
                    guard progress.profileUpdated(failure: error.map(Self.failure)) else { return }
                    self.reloadRequestCount += 1
                    user.reload { error in
                        if error == nil { self.refreshListeners() }
                        progress.reloaded(user: Self.map(self.auth.currentUser), failure: error.map(Self.failure))
                    }
                }
            }
        }
    }

    func resetPassword(email: String, completion: AuthCompletion) -> AuthCancellation {
        begin(completion) { finish in
            self.resetRequestCount += 1
            self.auth.sendPasswordReset(withEmail: email) { error in finish(nil, error.map(Self.failure)) }
        }
    }

    func logout() -> AuthFailure? {
        precondition(Thread.isMainThread)
        guard !requestInFlight else { return .requestInProgress }
        // Firebase Apple signOut is synchronous and throwing: only return after completion.
        do { try auth.signOut(); return nil }
        catch { return Self.failure(error) }
    }

    private func begin(_ completion: AuthCompletion,
                       operation: (@escaping (AuthUser?, AuthFailure?) -> Void) -> Void) -> AuthCancellation {
        precondition(Thread.isMainThread)
        let cancellation = AuthCallbackCancellation()
        guard !requestInFlight else {
            completion.complete(user: nil, failure: .requestInProgress)
            return cancellation
        }
        requestInFlight = true
        cancellation.completion = completion
        var finished = false
        operation { [self] user, failure in
            precondition(Thread.isMainThread)
            guard !finished else { return }
            finished = true
            requestInFlight = false
            guard cancellation.active else { return }
            let callback = cancellation.completion
            cancellation.completion = nil
            cancellation.cancel()
            if failure == nil && user != nil { authenticationSuccessCount += 1 }
            callback?.complete(user: user, failure: failure)
        }
        return cancellation
    }

    private static func map(_ user: FirebaseAuth.User?) -> AuthUser? {
        guard let user else { return nil }
        return AuthUser.companion.create(uid: user.uid, email: user.email,
                                         displayName: user.displayName, emailVerified: user.isEmailVerified)
    }

    private static func failure(_ error: Error) -> AuthFailure {
        guard let code = AuthErrorCode(rawValue: (error as NSError).code) else { return .unknown }
        switch code {
        case .invalidCredential, .wrongPassword, .userNotFound, .invalidEmail, .userDisabled: return .invalidCredentials
        case .emailAlreadyInUse: return .emailAlreadyUsed
        case .weakPassword: return .weakPassword
        case .networkError: return .network
        case .tooManyRequests: return .tooManyRequests
        default: return .unknown
        }
    }
}
#endif
