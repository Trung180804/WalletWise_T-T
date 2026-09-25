#if DEBUG
import Foundation
import WalletWiseShared

/** Opt-in tests drive the same shared editor/Home that Compose renders. No fabricated SDK success. */
@MainActor
final class TransactionMutationEmulatorProbe {
    private let auth: ConnectedAuthPresenter
    private let home: TransactionHomeSession
    private let adapter: FirebaseTransactionAdapter
    private let environment = ProcessInfo.processInfo.environment
    private init(auth: ConnectedAuthPresenter, home: TransactionHomeSession, adapter: FirebaseTransactionAdapter) { self.auth = auth; self.home = home; self.adapter = adapter }
    static func run(phase: String, auth: ConnectedAuthPresenter, home: TransactionHomeSession, adapter: FirebaseTransactionAdapter) {
        let probe = TransactionMutationEmulatorProbe(auth: auth, home: home, adapter: adapter)
        Task {
            do {
                try await probe.wait("auth-ready") { auth.snapshot.sessionReady }
                switch phase {
                case "exercise": try probe.units(); try await probe.exercise()
                case "restore": try await probe.restore()
                case "prepare", "cleanup":
                    home.logout()
                    try await probe.wait("cleanup-listeners") { adapter.registrationCount == adapter.removalCount && adapter.categoryRegistrationCount == adapter.categoryRemovalCount }
                    try probe.check(auth.snapshot.user == nil && home.snapshot.list.rows.isEmpty && !home.editor.snapshot.visible, "cleanup-session-and-form-empty")
                default: throw Failure(name: "unknown-phase")
                }
                NSLog("[TransactionIntegration] COMPLETE %@", phase)
            } catch let error as Failure { NSLog("[TransactionIntegration] FAIL %@", error.name) }
              catch { NSLog("[TransactionIntegration] FAIL unexpected-error") }
        }
    }
    private func login(_ key: String) {
        auth.navigateTo(route: .login)
        auth.onEmailChanged(value: environment["WALLETWISE_TEST_" + key + "_EMAIL"]!)
        auth.onPasswordChanged(value: environment["WALLETWISE_TEST_" + key + "_PASSWORD"]!)
        auth.submit()
    }
    private func pause(_ marker: String) async throws {
        NSLog("[TransactionIntegration] READY %@", marker)
        try await Task.sleep(nanoseconds: 2_500_000_000)
    }
    private func exercise() async throws {
        login("A")
        try await wait("home-a") { self.home.snapshot.list.rows.count == 1 && (self.home.editor.categories.state.value as? CategorySessionState)?.isLoading == false }
        try check(adapter.registrationCount == 1 && adapter.categoryRegistrationCount == 1, "one-home-and-category-listener")
        try await pause("home-content")
        home.openAdd()
        home.editor.amountChanged(text: "50.000"); home.editor.categoryChanged(category: "Ăn uống"); home.editor.noteChanged(note: "Created in 5F")
        try check(home.editor.snapshot.form.amount?.int64Value == 50_000 && home.editor.snapshot.dirty, "raw-integer-amount")
        try await pause("add-form")
        home.editor.submit(); home.editor.submit()
        try check(home.editor.snapshot.saving && home.editor.snapshot.success == nil, "add-no-success-before-confirmation")
        try await wait("added") { self.home.editor.snapshot.success != nil && self.home.snapshot.list.rows.count == 2 }
        let createdId = home.snapshot.list.rows.first { $0.note == "Created in 5F" }!.id
        let created = home.transaction(transactionId: createdId)!
        try check(adapter.writeRequestCount == 1 && adapter.confirmedWriteCount == 1 && created.amount == 50_000 && created.imageUrl.isEmpty, "add-one-confirmed-record")
        let retry = MutationReceiver()
        let retryToken = adapter.mutate(kind: .add, transaction: created, completion: retry)
        try await wait("same-id-retry") { retry.calls == 1 }
        try check(retry.failure == nil && home.snapshot.list.rows.filter { $0.id == createdId }.count == 1, "retry-same-id-no-duplicate")
        withExtendedLifetime(retryToken) {}
        let missing = WalletWiseShared.Transaction(id: "never-create-update", userId: created.userId, type: created.type, paymentMethod: created.paymentMethod, amount: created.amount, category: created.category, note: "", timestamp: created.timestamp, imageUrl: "", categoryId: "", isLegacy: false)
        let missingReceiver = MutationReceiver()
        let missingToken = adapter.mutate(kind: .update, transaction: missing, completion: missingReceiver)
        try await wait("missing-update-error") { missingReceiver.calls == 1 }
        try check(missingReceiver.failure != nil && home.transaction(transactionId: missing.id) == nil, "update-missing-document-does-not-create")
        withExtendedLifetime(missingToken) {}
        try await pause("added-result")
        home.select(transactionId: createdId); home.edit(transactionId: createdId)
        home.editor.amountChanged(text: "75.000"); home.editor.noteChanged(note: "Edited in 5F")
        try await pause("edit-form")
        home.editor.submit()
        try await wait("edited") { self.home.editor.snapshot.success != nil && self.home.transaction(transactionId: createdId)?.amount == 75_000 }
        try check(home.transaction(transactionId: createdId)?.note == "Edited in 5F" && home.transaction(transactionId: createdId)?.id == createdId, "edit-keeps-id-changes-fields")
        try await pause("edited-result")
        home.edit(transactionId: "image-fixture"); home.editor.noteChanged(note: "Image preserved in 5F"); home.editor.submit()
        try await wait("image-edit") { self.home.editor.snapshot.success != nil && self.home.transaction(transactionId: "image-fixture")?.note == "Image preserved in 5F" }
        try check(home.transaction(transactionId: "image-fixture")?.imageUrl == "https://example.invalid/existing.png", "edit-keeps-existing-image")
        home.requestDelete(transactionId: createdId)
        try await pause("delete-confirmation")
        home.editor.cancelDelete()
        try check(home.transaction(transactionId: createdId) != nil && home.editor.snapshot.pendingDelete == nil, "cancel-delete-keeps-record")
        home.requestDelete(transactionId: createdId); home.editor.confirmDelete(); home.editor.confirmDelete()
        try await wait("deleted") { self.home.editor.snapshot.success != nil && self.home.transaction(transactionId: createdId) == nil }
        try check(home.snapshot.list.rows.count == 1, "confirmed-delete-removes-once")
        try await pause("deleted-result")
        // Actual SDK request starts under A; logout occurs synchronously before acknowledgement.
        home.openAdd(); home.editor.amountChanged(text: "1000"); home.editor.categoryChanged(category: "Ăn uống"); home.editor.noteChanged(note: "In-flight A")
        let confirmedBefore = adapter.confirmedWriteCount
        home.editor.submit()
        try check(home.editor.snapshot.saving && adapter.confirmedWriteCount == confirmedBefore, "request-inflight-before-logout")
        home.logout()
        try check(home.snapshot.list.rows.isEmpty && !home.editor.snapshot.visible && home.editor.snapshot.form.note.isEmpty, "logout-inflight-clears-form-and-data")
        login("B")
        try await wait("home-b") { self.home.snapshot.list.rows.count == 1 && self.home.snapshot.list.rows[0].id == "b-fixture" }
        try await Task.sleep(nanoseconds: 1_000_000_000)
        try check(home.editor.availableCategories().contains { $0.name == "B category" } && !home.editor.availableCategories().contains { $0.name == "Ăn uống" }, "category-state-isolated-to-b")
        try check(home.editor.snapshot.success == nil && home.editor.snapshot.form.note.isEmpty, "late-a-callback-does-not-publish-to-b")
        let denied = MutationReceiver()
        let deniedToken = adapter.mutate(kind: .update, transaction: created, completion: denied)
        try check(denied.failure == .notAuthenticated && denied.calls == 1, "adapter-rejects-other-user-update")
        let deniedDelete = MutationReceiver()
        let deniedDeleteToken = adapter.mutate(kind: .remove, transaction: created, completion: deniedDelete)
        try check(deniedDelete.failure == .notAuthenticated, "adapter-rejects-other-user-delete")
        withExtendedLifetime((deniedToken, deniedDeleteToken)) {}
        home.openAdd(); home.editor.amountChanged(text: "123000"); home.editor.categoryChanged(category: "B category"); home.editor.noteChanged(note: "Persisted B"); home.editor.submit()
        try await wait("persisted-b") { self.home.editor.snapshot.success != nil && self.home.snapshot.list.rows.count == 2 }
        try check(adapter.registrationCount - adapter.removalCount == 1 && adapter.categoryRegistrationCount - adapter.categoryRemovalCount == 1, "writes-do-not-restart-listeners")
        try await pause("saved-for-relaunch")
    }
    private func restore() async throws {
        try await wait("restored-b") { self.home.snapshot.list.rows.contains { $0.note == "Persisted B" } }
        try check(AuthBootstrap.adapter?.loginRequestCount == 0 && auth.snapshot.isAuthenticated, "restored-auth-no-login-rpc")
        try check(home.snapshot.list.rows.count == 2 && adapter.registrationCount == 1 && adapter.categoryRegistrationCount == 1, "restored-persisted-data-one-listener")
        try await pause("empty-primary-b")
        try await wait("legacy-b") { self.home.snapshot.list.rows.count == 1 && self.home.snapshot.list.rows[0].id == "b-legacy" }
        let legacy = home.transaction(transactionId: "b-legacy")!
        home.select(transactionId: legacy.id)
        try check(legacy.isLegacy && home.snapshot.writableTransactionIds.isEmpty, "legacy-read-only-provenance")
        home.edit(transactionId: legacy.id); home.requestDelete(transactionId: legacy.id)
        try check(!home.editor.snapshot.visible && home.editor.snapshot.pendingDelete == nil, "legacy-edit-delete-disabled")
        try await pause("legacy-detail")
    }
    private func units() throws {
        var uid = "unit-a"
        let source = MutationProbeSource()
        let adapter = FirebaseTransactionAdapter(source: source, currentSubject: { uid })
        let tx = WalletWiseShared.Transaction(id: "unit-doc", userId: uid, type: "Chi", paymentMethod: "Tiền mặt", amount: 50000, category: "Test", note: "", timestamp: 1700000000000, imageUrl: "", categoryId: "cat-unit", isLegacy: false)
        let add = IosTransactionWriteMapper.fields(kind: .add, transaction: tx)
        try check(Set(add.keys) == Set(["id", "userId", "type", "paymentMethod", "amount", "category", "categoryId", "note", "timestamp", "imageUrl"]) && add["amount"] is Double && add["timestamp"] is Int64 && add["imageUrl"] as? String == "" && add["categoryId"] as? String == "cat-unit", "native-add-wire-fields-and-types")
        try check(IosTransactionWriteMapper.documentPath(transaction: tx) == "users/unit-a/transactions/unit-doc", "native-primary-write-path")
        let patch = IosTransactionWriteMapper.fields(kind: .update, transaction: tx)
        try check(patch["id"] == nil && patch["userId"] == nil && patch["imageUrl"] == nil && patch["categoryId"] as? String == "cat-unit" && patch.count == 7, "native-update-patch-preserves-other-fields")
        let receiver = MutationReceiver()
        let token = adapter.mutate(kind: .add, transaction: tx, completion: receiver)
        try check(receiver.calls == 0 && source.requests.count == 1, "native-waits-for-server-callback")
        source.callback?(nil); source.callback?(nil)
        try check(receiver.calls == 1 && adapter.confirmedWriteCount == 1, "native-confirmation-exactly-once")
        token.cancel(); token.cancel()
        try check(source.removals == 1, "native-cancellation-idempotent")
        let retry = adapter.mutate(kind: .add, transaction: tx, completion: receiver)
        try check(source.requests.map { $0.1.id } == ["unit-doc", "unit-doc"], "native-retry-keeps-document-id")
        source.callback?(.network)
        try check(receiver.failure == .network && receiver.calls == 2, "native-error-mapping-callback")
        retry.cancel()
        let late = adapter.mutate(kind: .update, transaction: tx, completion: receiver)
        uid = "unit-b"; late.cancel(); source.callback?(nil)
        try check(receiver.calls == 2, "native-late-cancelled-callback-dropped")
        let wrong = adapter.mutate(kind: .remove, transaction: tx, completion: receiver)
        try check(receiver.failure == .notAuthenticated && source.requests.count == 3, "native-foreign-delete-rejected")
        wrong.cancel()
    }
    private struct Failure: Error { let name: String }
    private func check(_ condition: Bool, _ name: String) throws { guard condition else { throw Failure(name: name) }; NSLog("[TransactionIntegration] PASS %@", name) }
    private func wait(_ name: String, until: @escaping () -> Bool) async throws {
        for _ in 0..<300 { if until() { return }; try await Task.sleep(nanoseconds: 100_000_000) }
        throw Failure(name: name + "-timeout")
    }
}

private final class MutationReceiver: NSObject, TransactionWriteCompletion {
    var calls = 0; var failure: TransactionReadFailure?
    func complete(failure: TransactionReadFailure?) { calls += 1; self.failure = failure }
}
private final class MutationProbeSource: TransactionSnapshotSource {
    var requests: [(TransactionMutationKind, WalletWiseShared.Transaction)] = []; var callback: ((TransactionReadFailure?) -> Void)?; var removals = 0
    func observe(userId: String, callback: @escaping ([TransactionDocument]?, Bool, TransactionReadFailure?) -> Void) -> () -> Void { return {} }
    func legacy(userId: String, callback: @escaping ([TransactionDocument]?, TransactionReadFailure?) -> Void) -> () -> Void { return {} }
    func mutate(kind: TransactionMutationKind, transaction: WalletWiseShared.Transaction, authorized: @escaping () -> Bool, callback: @escaping (TransactionReadFailure?) -> Void) -> () -> Void {
        requests.append((kind, transaction)); self.callback = callback; return { self.removals += 1 }
    }
}
#endif
