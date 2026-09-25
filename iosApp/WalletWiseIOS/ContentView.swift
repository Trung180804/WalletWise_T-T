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
    final class Coordinator: NSObject, AuthControllerObserver, TransactionHomeObserver {
        private var session: AuthControllerSession?
        private var home: TransactionHomeSession?
        let transactions = AuthBootstrap.transactionService

        func createdHome(session: TransactionHomeSession) {
            home = session
            #if DEBUG
            if let phase = ProcessInfo.processInfo.environment["WALLETWISE_TRANSACTION_WRITE_TEST_PHASE"], let auth = self.session, let adapter = transactions as? FirebaseTransactionAdapter {
                DispatchQueue.main.async { TransactionMutationEmulatorProbe.run(phase: phase, auth: auth.presenter, home: session, adapter: adapter) }
            }
            if let phase = ProcessInfo.processInfo.environment["WALLETWISE_TRANSACTION_TEST_PHASE"], let auth = self.session, let adapter = transactions as? FirebaseTransactionAdapter {
                DispatchQueue.main.async { TransactionEmulatorIntegrationProbe.run(phase: phase, auth: auth.presenter, home: session, adapter: adapter) }
            }
            #endif
        }

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
            home?.dispose()
            home = nil
            session?.dispose()
            session = nil
        }
    }

    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIViewController(context: Context) -> UIViewController {
        WalletWiseComposeViewControllerKt.walletWiseComposeViewController(
            service: AuthBootstrap.service, observer: context.coordinator,
            transactionService: context.coordinator.transactions, homeObserver: context.coordinator,
            transactionWriter: context.coordinator.transactions as? CallbackTransactionWriteService,
            categoryService: context.coordinator.transactions as? CallbackCategoryService
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
