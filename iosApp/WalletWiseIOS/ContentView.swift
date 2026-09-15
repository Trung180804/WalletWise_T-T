import SwiftUI
import UIKit
import WalletWiseShared

struct ContentView: View {
    var body: some View {
        ComposeAuthView()
            .ignoresSafeArea()
    }
}

private struct ComposeAuthView: UIViewControllerRepresentable {
    final class Coordinator: NSObject, AuthControllerObserver {
        private var session: AuthControllerSession?

        func created(session: AuthControllerSession) {
            precondition(self.session == nil)
            self.session = session
            #if DEBUG
            if let phase = ProcessInfo.processInfo.environment["WALLETWISE_AUTH_TEST_PHASE"] {
                // The probe is retained only by its asynchronous work, not by bootstrap.
                DispatchQueue.main.async {
                    AuthEmulatorIntegrationProbe.run(phase: phase, presenter: session.presenter)
                }
            }
            #endif
        }

        func dispose() {
            session?.dispose()
            session = nil
        }
    }

    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIViewController(context: Context) -> UIViewController {
        WalletWiseComposeViewControllerKt.walletWiseComposeViewController(
            service: AuthBootstrap.service, observer: context.coordinator
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}

    static func dismantleUIViewController(_ uiViewController: UIViewController, coordinator: Coordinator) {
        // Also handles a controller discarded before its first Compose composition.
        coordinator.dispose()
    }
}

#Preview {
    ContentView()
}
