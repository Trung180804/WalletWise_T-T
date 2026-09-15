import Foundation
import FirebaseCore
import WalletWiseShared
#if DEBUG
import GTMSessionFetcherCore
#endif

final class AuthBootstrap {
    private static var configured = false
    #if DEBUG
    private(set) static var adapter: FirebaseAuthAdapter?
    #endif

    static var service: CallbackAuthService? {
        #if DEBUG
        return adapter
        #else
        return nil
        #endif
    }

    static func configure() {
        precondition(!configured, "Firebase bootstrap must run once per process")
        configured = true
        let environment = ProcessInfo.processInfo.environment
        let decision = AuthEmulatorPolicy.evaluate(environment)
        #if DEBUG
        if decision == .enabled {
            // Install the deny-by-default transport before Firebase can restore a saved user.
            AuthEmulatorTransport.install()
            // Public demo values, not credentials. The real Google plist is never modified.
            let options = FirebaseOptions(
                googleAppID: "1:1234567890:ios:0123456789abcdef",
                gcmSenderID: "1234567890"
            )
            options.apiKey = "demo-walletwise"
            options.projectID = "demo-walletwise"
            options.bundleID = Bundle.main.bundleIdentifier!
            FirebaseApp.configure(name: "WalletWiseAuthEmulator", options: options)
            let app = FirebaseApp.app(name: "WalletWiseAuthEmulator")!
            let service = FirebaseAuthAdapter(emulatorApp: app)
            adapter = service
            NSLog("[AuthBootstrap] configured once; emulator endpoint=127.0.0.1:9099 project=demo-walletwise")
            return
        }
        #endif
        // Core-only bootstrap retains the existing config, without instantiating Auth/Firestore.
        FirebaseApp.configure()
        NSLog("[AuthBootstrap] configured once; Auth adapter disabled%@", decision == .blocked ? "; emulator guard rejected configuration" : "")
    }
}

#if DEBUG
/** Guards the SDK's fetcher before its URLSession task is resumed. */
enum AuthEmulatorTransport {
    static func install() {
        GTMSessionFetcher.setGlobalTest { fetcher, proceed in
            guard allowed(fetcher.request?.url) else {
                NSLog("[AuthIsolation] FAIL blocked non-emulator network request")
                proceed(nil, nil, URLError(.notConnectedToInternet))
                return
            }
            fetcher.willRedirectBlock = { _, request, redirect in
                guard allowed(request.url) else {
                    NSLog("[AuthIsolation] FAIL blocked non-emulator redirect")
                    redirect(nil)
                    return
                }
                redirect(request)
            }
            // No paths, query/API keys, headers, bodies, tokens or user information.
            NSLog("[AuthTransport] request endpoint=127.0.0.1:9099")
            // The documented all-nil sentinel resumes the REAL SDK request.
            // No response or successful Auth result is supplied by this hook.
            proceed(nil, nil, nil)
        }
    }
    private static func allowed(_ url: URL?) -> Bool {
        url?.scheme == "http" && url?.host == "127.0.0.1" && url?.port == 9099 && url?.user == nil && url?.password == nil
    }
}
#endif
