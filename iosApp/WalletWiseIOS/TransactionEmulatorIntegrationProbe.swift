#if DEBUG
import Foundation
import FirebaseFirestore
import WalletWiseShared

/** Opt-in integration of the SAME Auth/Home sessions rendered by Compose. No SDK responses are fabricated. */
final class TransactionEmulatorIntegrationProbe {
    private let auth: ConnectedAuthPresenter
    private let home: TransactionHomeSession
    private let adapter: FirebaseTransactionAdapter
    private var failed = false
    private let environment = ProcessInfo.processInfo.environment
    private init(auth: ConnectedAuthPresenter, home: TransactionHomeSession, adapter: FirebaseTransactionAdapter) {
        self.auth = auth; self.home = home; self.adapter = adapter
    }
    static func run(phase: String, auth: ConnectedAuthPresenter, home: TransactionHomeSession, adapter: FirebaseTransactionAdapter) {
        let probe = TransactionEmulatorIntegrationProbe(auth: auth, home: home, adapter: adapter)
        probe.wait("auth-ready", until: { auth.snapshot.sessionReady }) {
            switch phase {
            case "exercise": probe.units(); if !probe.failed { probe.exercise() }
            case "restore": probe.restore()
            case "cleanup", "prepare":
                home.logout()
                probe.wait("cleanup-listener", until: { adapter.registrationCount == adapter.removalCount }) {
                    guard probe.check(auth.snapshot.user == nil && home.snapshot.list.rows.isEmpty, "cleanup-session-empty") else { return }
                    probe.finish(phase)
                }
            default: probe.fail("unknown-phase")
            }
        }
    }
    private func login(_ key: String) {
        auth.navigateTo(route: .login)
        auth.onEmailChanged(value: environment["WALLETWISE_TEST_" + key + "_EMAIL"]!)
        auth.onPasswordChanged(value: environment["WALLETWISE_TEST_" + key + "_PASSWORD"]!)
        auth.submit()
    }
    private func exercise() {
        home.logout()
        login("A")
        wait("home-a", until: { self.home.snapshot.list.rows.count == 3 }) {
            let state = self.home.snapshot
            guard self.check(self.auth.snapshot.isAuthenticated && state.displayLabel == "Emulator A", "auth-to-home-name"),
                  self.check(Set(state.list.rows.map(\.id)) == Set(["a-income", "a-food", "a-rent"]), "home-only-a"),
                  self.check(state.totalIncome == 1_000_000 && state.totalExpense == 325_000 && state.balance == 675_000, "home-a-totals"),
                  self.check(self.adapter.registrationCount == 1 && self.adapter.legacyReadCount == 0, "primary-content-one-listener-no-legacy") else { return }
            self.ready("home-content")
            self.later {
                self.home.select(transactionId: "a-food")
                guard self.check(self.home.snapshot.selectedTransactionId == "a-food", "card-selects-shared-detail") else { return }
                self.ready("home-detail")
                self.later {
                    self.home.dismissDetail()
                    let before = self.adapter.snapshotCount
                    self.ready("add-a")
                    self.wait("live-update", until: { self.home.snapshot.list.rows.count == 4 }) {
                        self.later {
                            guard self.check(self.home.snapshot.totalExpense == 400_000 && self.home.snapshot.balance == 600_000, "live-update-totals"),
                                  self.check(self.adapter.snapshotCount == before + 1, "live-update-one-snapshot"),
                                  self.check(self.adapter.registrationCount == 1, "same-uid-no-restart") else { return }
                            self.ready("equivalent-a")
                            self.later {
                                guard self.check(self.home.snapshot.list.rows.count == 4 && Set(self.home.snapshot.list.rows.map(\.id)).count == 4, "equivalent-snapshot-no-duplicates") else { return }
                                self.home.logout()
                                guard self.check(self.auth.snapshot.user == nil && self.home.snapshot.userId == nil && self.home.snapshot.list.rows.isEmpty && self.home.snapshot.balance == 0, "logout-a-clears-immediately") else { return }
                                self.wait("a-listener-removed", until: { self.adapter.removalCount == 1 }) {
                                    self.login("B")
                                    self.wait("home-b", until: { self.home.snapshot.list.rows.count == 1 }) {
                                        guard self.check(self.home.snapshot.list.rows[0].id == "b-expense" && self.home.snapshot.totalIncome == 0 && self.home.snapshot.totalExpense == 99_000 && self.home.snapshot.balance == -99_000, "only-b-no-a-data"),
                                              self.check(self.auth.snapshot.user?.displayName == nil && self.home.snapshot.displayLabel == self.environment["WALLETWISE_TEST_B_EMAIL"], "safe-email-fallback"),
                                              self.check(self.home.snapshot.selectedTransactionId == nil && self.adapter.registrationCount - self.adapter.removalCount == 1, "b-clean-detail-one-listener") else { return }
                                        self.ready("empty-b")
                                        self.wait("empty-b", until: { !self.home.snapshot.list.isLoading && self.home.snapshot.list.rows.isEmpty }) {
                                            guard self.check(self.home.snapshot.balance == 0 && self.adapter.legacyReadCount == 1, "empty-primary-one-legacy-read") else { return }
                                            self.ready("home-empty")
                                            self.later {
                                                self.ready("seed-legacy-b")
                                                self.later {
                                                    guard self.check(self.home.snapshot.list.rows.isEmpty && self.adapter.legacyReadCount == 1, "legacy-change-needs-refresh") else { return }
                                                    self.home.retry()
                                                    self.wait("legacy-b", until: { self.home.snapshot.totalIncome == 222_000 }) {
                                                        guard self.check(self.home.snapshot.list.rows.count == 1 && self.home.snapshot.list.rows[0].id == "b-legacy" && self.adapter.legacyReadCount == 2, "legacy-one-shot-no-mix"),
                                                              self.check(self.adapter.registrationCount - self.adapter.removalCount == 1, "retry-one-listener") else { return }
                                                        self.ready("change-legacy-b")
                                                        self.later {
                                                            guard self.check(self.home.snapshot.totalIncome == 222_000 && self.adapter.legacyReadCount == 2, "legacy-no-live-listener") else { return }
                                                            self.home.retry()
                                                            self.wait("legacy-refresh", until: { self.home.snapshot.totalIncome == 333_000 }) {
                                                                guard self.check(self.adapter.legacyReadCount == 3 && self.adapter.registrationCount - self.adapter.removalCount == 1, "legacy-refresh-contract") else { return }
                                                                self.finish("exercise")
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    private func restore() {
        wait("restored-home", until: { self.home.snapshot.totalIncome == 333_000 }) {
            guard self.check(self.auth.snapshot.isAuthenticated && AuthBootstrap.adapter?.loginRequestCount == 0, "auth-restore-no-login-rpc"),
                  self.check(self.home.snapshot.list.rows.count == 1 && self.home.snapshot.list.rows[0].id == "b-legacy", "restored-correct-user-transactions"),
                  self.check(self.adapter.registrationCount == 1 && self.adapter.removalCount == 0 && self.adapter.legacyReadCount == 1, "restore-one-primary-listener") else { return }
            self.finish("restore")
        }
    }
    private func units() {
        let mapped = IosTransactionDocumentMapper.map(documentId: "wire", data: ["amount": NSNumber(value: Int64(42)), "type": "Thu", "timestamp": NSNumber(value: Int64(1500)), "imageUrl": "  "])!
        guard check(mapped.amount == 42 && mapped.timestampMilliseconds == 1500 && mapped.documentId == "wire", "native-map-long-numeric"),
              check(IosTransactionDocumentMapper.map(documentId: "double", data: ["amount": 42.5])!.amount == 42.5 && IosTransactionDocumentMapper.map(documentId: "double", data: ["amount": 42.5, "timestamp": Timestamp(seconds: 2, nanoseconds: 500_000_000)])!.legacyTimestamp?.toEpochMillisecondsOrNull()?.int64Value == 2500, "native-map-double-firebase-timestamp"),
              check(IosTransactionDocumentMapper.map(documentId: "bad", data: ["amount": "invalid", "timestamp": "bad"])!.timestampMilliseconds == 0 && IosTransactionDocumentMapper.map(documentId: "bool", data: ["amount": true, "timestamp": true])!.amount == 0 && IosTransactionDocumentMapper.map(documentId: "bool", data: ["timestamp": true])!.timestampMilliseconds == 0 && IosTransactionDocumentMapper.map(documentId: "", data: [:]) == nil, "native-invalid-document-safe"),
              check(IosTransactionDocumentMapper.map(documentId: "note", data: ["content": "fixture"])!.note == "fixture", "native-legacy-content-compatible") else { return }
        let source = ProbeSnapshotSource()
        var subject: String? = "a"
        let boundary = FirebaseTransactionAdapter(source: source, currentSubject: { subject })
        let receiver = ProbeSnapshotObserver()
        let token = boundary.observePrimary(userId: "a", observer: receiver)
        source.callback?([mapped], false, nil)
        guard check(receiver.documents?.count == 1 && source.registrations == 1, "native-snapshot-callback") else { return }
        source.callback?(nil, false, .network)
        guard check(receiver.failure == .network, "native-error-callback") else { return }
        token.cancel(); token.cancel(); source.callback?([mapped], false, nil)
        guard check(source.removals == 1 && receiver.calls == 2, "native-remove-once-late-callback-dropped") else { return }
        let other = boundary.observePrimary(userId: "a", observer: receiver)
        subject = "b"; source.callback?([mapped], false, nil)
        guard check(receiver.calls == 2, "native-uid-change-drops-old-callback") else { return }
        other.cancel()
        let missing = boundary.observePrimary(userId: "a", observer: receiver)
        guard check(receiver.failure == .notAuthenticated && source.registrations == 2, "native-wrong-user-no-listener") else { return }
        missing.cancel()
    }
    private func ready(_ marker: String) { NSLog("[TransactionIntegration] READY %@", marker) }
    private func later(_ action: @escaping () -> Void) { DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) { if !self.failed { action() } } }
    @discardableResult private func check(_ value: Bool, _ name: String) -> Bool {
        guard value else { fail(name); return false }; NSLog("[TransactionIntegration] PASS %@", name); return true
    }
    private func wait(_ name: String, until condition: @escaping () -> Bool, then continuation: @escaping () -> Void) {
        let deadline = Date().addingTimeInterval(30)
        func poll() {
            guard !failed else { return }
            if condition() { continuation(); return }
            guard Date() < deadline else { fail("timeout-" + name); return }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) { poll() }
        }
        poll()
    }
    private func fail(_ name: String) { failed = true; NSLog("[TransactionIntegration] FAIL %@", name) }
    private func finish(_ phase: String) { NSLog("[TransactionIntegration] COMPLETE %@", phase) }
}
private final class ProbeSnapshotObserver: NSObject, TransactionSnapshotObserver {
    var calls = 0; var documents: [TransactionDocument]?; var failure: TransactionReadFailure?
    func changed(documents: [TransactionDocument]?, fromCache: Bool, failure: TransactionReadFailure?) { calls += 1; self.documents = documents; self.failure = failure }
}
private final class ProbeSnapshotSource: TransactionSnapshotSource {
    var callback: (([TransactionDocument]?, Bool, TransactionReadFailure?) -> Void)?
    var registrations = 0; var removals = 0
    func observe(userId: String, callback: @escaping ([TransactionDocument]?, Bool, TransactionReadFailure?) -> Void) -> () -> Void { registrations += 1; self.callback = callback; return { self.removals += 1 } }
    func legacy(userId: String, callback: @escaping ([TransactionDocument]?, TransactionReadFailure?) -> Void) -> () -> Void { return {} }
}
#endif
