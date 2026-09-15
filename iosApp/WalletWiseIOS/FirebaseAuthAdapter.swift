#if DEBUG
import Foundation
import FirebaseCore
import FirebaseAuth
import WalletWiseShared

private final class AuthCallbackCancellation: NSObject, AuthCancellation {
    private(set) var active = true
    var completion: AuthCompletion?
    var onCancel: (() -> Void)?

    func cancel() {
        precondition(Thread.isMainThread)
        guard active else { return }
        active = false
        completion = nil
        onCancel?()
        onCancel = nil
    }
}

/** Only the validated Debug emulator bootstrap can construct this adapter. */
final class FirebaseAuthAdapter: NSObject, CallbackAuthService {
    private let auth: Auth
    private var requestInFlight = false
    private(set) var registerRequestCount = 0
    private(set) var loginRequestCount = 0
    private(set) var resetRequestCount = 0
    private(set) var authenticationSuccessCount = 0
    var currentSubject: String? { auth.currentUser?.uid }

    init(emulatorApp: FirebaseApp) {
        precondition(Thread.isMainThread)
        precondition(emulatorApp.name == "WalletWiseAuthEmulator" && emulatorApp.options.projectID == "demo-walletwise")
        auth = Auth.auth(app: emulatorApp)
        // This synchronous SDK call precedes observation/currentUser access/outgoing operations.
        auth.useEmulator(withHost: "127.0.0.1", port: 9099)
        super.init()
    }

    func observe(observer: AuthStateObserver) -> AuthCancellation {
        precondition(Thread.isMainThread)
        let cancellation = AuthCallbackCancellation()
        let listener = auth.addStateDidChangeListener { _, user in
            guard cancellation.active else { return }
            observer.changed(identity: user.map { AuthIdentity(subject: $0.uid) })
        }
        cancellation.onCancel = { [auth] in auth.removeStateDidChangeListener(listener) }
        return cancellation
    }

    func login(email: String, password: String, completion: AuthCompletion) -> AuthCancellation {
        begin(completion) { finish in
            self.loginRequestCount += 1
            self.auth.signIn(withEmail: email, password: password) { result, error in
                finish(result?.user, error)
            }
        }
    }

    func register(email: String, password: String, completion: AuthCompletion) -> AuthCancellation {
        begin(completion) { finish in
            self.registerRequestCount += 1
            // Auth only: no profile write or Firestore operation.
            self.auth.createUser(withEmail: email, password: password) { result, error in
                finish(result?.user, error)
            }
        }
    }

    func resetPassword(email: String, completion: AuthCompletion) -> AuthCancellation {
        begin(completion) { finish in
            self.resetRequestCount += 1
            self.auth.sendPasswordReset(withEmail: email) { error in finish(nil, error) }
        }
    }

    func logout() -> AuthFailure? {
        precondition(Thread.isMainThread)
        guard !requestInFlight else { return .requestInProgress }
        do { try auth.signOut(); return nil }
        catch { return Self.failure(error) }
    }

    private func begin(_ completion: AuthCompletion,
                       operation: (@escaping (FirebaseAuth.User?, Error?) -> Void) -> Void) -> AuthCancellation {
        precondition(Thread.isMainThread)
        let cancellation = AuthCallbackCancellation()
        guard !requestInFlight else {
            completion.complete(identity: nil, failure: .requestInProgress)
            return cancellation
        }
        requestInFlight = true
        cancellation.completion = completion
        operation { [self] user, error in
            precondition(Thread.isMainThread)
            requestInFlight = false
            // An SDK request is not cancellable. Keep the global gate until it finishes,
            // but release/suppress a cancelled presenter's callback.
            guard cancellation.active else { return }
            let callback = cancellation.completion
            cancellation.completion = nil
            cancellation.cancel()
            if error == nil && user != nil { authenticationSuccessCount += 1 }
            callback?.complete(identity: user.map { AuthIdentity(subject: $0.uid) },
                               failure: error.map(Self.failure))
        }
        return cancellation
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
