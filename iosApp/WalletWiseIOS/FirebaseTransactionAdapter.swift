#if DEBUG
import Foundation
import CoreFoundation
import FirebaseCore
import FirebaseFirestore
import WalletWiseShared

/** Injectable observation seam for deterministic SDK boundary tests; real source below uses Firebase. */
protocol TransactionSnapshotSource {
    func observe(userId: String, callback: @escaping ([TransactionDocument]?, Bool, TransactionReadFailure?) -> Void) -> () -> Void
    func legacy(userId: String, callback: @escaping ([TransactionDocument]?, TransactionReadFailure?) -> Void) -> () -> Void
    func categories(userId: String, callback: @escaping ([WalletWiseShared.Category]?, TransactionReadFailure?) -> Void) -> () -> Void
    func mutate(kind: TransactionMutationKind, transaction: WalletWiseShared.Transaction, authorized: @escaping () -> Bool, callback: @escaping (TransactionReadFailure?) -> Void) -> () -> Void
}

extension TransactionSnapshotSource {
    func categories(userId: String, callback: @escaping ([WalletWiseShared.Category]?, TransactionReadFailure?) -> Void) -> () -> Void { callback(nil, .permissionDenied); return {} }
    func mutate(kind: TransactionMutationKind, transaction: WalletWiseShared.Transaction, authorized: @escaping () -> Bool, callback: @escaping (TransactionReadFailure?) -> Void) -> () -> Void { callback(.permissionDenied); return {} }
}

private final class TransactionCallbackCancellation: NSObject, TransactionCancellation {
    private(set) var active = true
    var remove: (() -> Void)?
    func cancel() {
        precondition(Thread.isMainThread)
        guard active else { return }
        active = false
        let cleanup = remove
        remove = nil
        cleanup?()
    }
}

/** One owner per Compose controller. The SDK client may be process scoped, its listeners are not. */
final class FirebaseTransactionAdapter: NSObject, CallbackTransactionService, CallbackTransactionWriteService, CallbackCategoryService {
    private let source: TransactionSnapshotSource
    private let currentSubject: () -> String?
    private var observation: TransactionCallbackCancellation?
    private var categoryObservation: TransactionCallbackCancellation?
    private var write: TransactionCallbackCancellation?
    private(set) var writeRequestCount = 0
    private(set) var confirmedWriteCount = 0
    private(set) var categoryRegistrationCount = 0
    private(set) var categoryRemovalCount = 0
    private(set) var registrationCount = 0
    private(set) var removalCount = 0
    private(set) var snapshotCount = 0
    private(set) var legacyReadCount = 0
    init(source: TransactionSnapshotSource, currentSubject: @escaping () -> String?) {
        self.source = source
        self.currentSubject = currentSubject
    }
    convenience init(firestore: Firestore, auth: FirebaseAuthAdapter) {
        precondition(firestore.app.name == "WalletWiseAuthEmulator" && firestore.app.options.projectID == "demo-walletwise")
        precondition(firestore.settings.host == "127.0.0.1:8080" && !firestore.settings.isSSLEnabled)
        self.init(source: FirebaseTransactionSnapshotSource(firestore), currentSubject: { [weak auth] in auth?.currentSubject })
    }
    func observePrimary(userId: String, observer: TransactionSnapshotObserver) -> TransactionCancellation {
        precondition(Thread.isMainThread)
        observation?.cancel()
        let token = TransactionCallbackCancellation()
        observation = token
        guard !userId.isEmpty, currentSubject() == userId else {
            observer.changed(documents: nil, fromCache: false, failure: .notAuthenticated)
            return token
        }
        registrationCount += 1
        let remove = source.observe(userId: userId) { [weak self, weak token] documents, cached, failure in
            guard let self, let token, token.active, self.currentSubject() == userId else { return }
            self.snapshotCount += 1
            observer.changed(documents: documents, fromCache: cached, failure: failure)
        }
        token.remove = { [weak self] in remove(); self?.removalCount += 1 }
        return token
    }
    func readLegacy(userId: String, completion: TransactionReadCompletion) -> TransactionCancellation {
        precondition(Thread.isMainThread)
        let token = TransactionCallbackCancellation()
        guard currentSubject() == userId else {
            completion.complete(documents: nil, failure: .notAuthenticated)
            return token
        }
        legacyReadCount += 1
        token.remove = source.legacy(userId: userId) { [weak self, weak token] documents, failure in
            guard let self, let token, token.active, self.currentSubject() == userId else { return }
            completion.complete(documents: documents, failure: failure)
        }
        return token
    }
    func observeCategories(userId: String, observer: CategorySnapshotObserver) -> TransactionCancellation {
        precondition(Thread.isMainThread)
        categoryObservation?.cancel()
        let token = TransactionCallbackCancellation()
        categoryObservation = token
        guard !userId.isEmpty, currentSubject() == userId else { observer.changed(categories: nil, failure: .notAuthenticated); return token }
        categoryRegistrationCount += 1
        let remove = source.categories(userId: userId) { [weak self, weak token] categories, failure in
            guard let self, let token, token.active, self.currentSubject() == userId else { return }
            observer.changed(categories: categories, failure: failure)
        }
        token.remove = { [weak self] in remove(); self?.categoryRemovalCount += 1 }
        return token
    }
    func mutate(kind: TransactionMutationKind, transaction: WalletWiseShared.Transaction, completion: TransactionWriteCompletion) -> TransactionCancellation {
        precondition(Thread.isMainThread)
        let token = TransactionCallbackCancellation()
        guard currentSubject() == transaction.userId, !transaction.userId.isEmpty, !transaction.userId.contains("/"),
              !transaction.id.isEmpty, !transaction.id.contains("/"), !transaction.isLegacy else {
            completion.complete(failure: .notAuthenticated); token.cancel(); return token
        }
        guard write?.active != true, kind == .remove || (transaction.amount.isFinite && transaction.amount > 0),
              kind != .add || transaction.imageUrl.isEmpty else { completion.complete(failure: .permissionDenied); token.cancel(); return token }
        write = token
        writeRequestCount += 1
        let authorized = { [weak self, weak token] in token?.active == true && self?.currentSubject() == transaction.userId }
        let remove = source.mutate(kind: kind, transaction: transaction, authorized: authorized) { [weak self, weak token] failure in
            guard let self, let token, token.active, self.currentSubject() == transaction.userId else { return }
            token.cancel()
            if failure == nil { self.confirmedWriteCount += 1 }
            completion.complete(failure: failure)
        }
        if token.active { token.remove = remove } else { remove() }
        return token
    }
}

private final class FirebaseTransactionSnapshotSource: TransactionSnapshotSource {
    private let firestore: Firestore
    init(_ firestore: Firestore) { self.firestore = firestore }
    func observe(userId: String, callback: @escaping ([TransactionDocument]?, Bool, TransactionReadFailure?) -> Void) -> () -> Void {
        // Firestore uses gRPC, not the Auth URLSession hook. Its immutable emulator settings are checked before this call.
        NSLog("[FirestoreTransport] listener endpoint=127.0.0.1:8080")
        let registration = firestore.collection("users").document(userId).collection("transactions")
            .addSnapshotListener(includeMetadataChanges: true) { snapshot, error in
                precondition(Thread.isMainThread)
                if let error { callback(nil, false, Self.failure(error)); return }
                guard let snapshot else { callback(nil, false, .unknown); return }
                // Home never optimistically removes/adds records before server acknowledgement.
                guard !snapshot.metadata.hasPendingWrites else { return }
                NSLog("[FirestoreTransport] snapshot endpoint=127.0.0.1:8080")
                callback(snapshot.documents.compactMap { Self.map(documentId: $0.documentID, data: $0.data()) }, snapshot.metadata.isFromCache, nil)
            }
        return { registration.remove() }
    }
    func legacy(userId: String, callback: @escaping ([TransactionDocument]?, TransactionReadFailure?) -> Void) -> () -> Void {
        var active = true
        NSLog("[FirestoreTransport] legacy-read endpoint=127.0.0.1:8080")
        firestore.collection("TRANSACTIONS").whereField("userId", isEqualTo: userId).getDocuments(source: .server) { snapshot, error in
            precondition(Thread.isMainThread)
            guard active else { return }
            if let error { callback(nil, Self.failure(error)); return }
            guard let snapshot else { callback(nil, .unknown); return }
            callback(snapshot.documents.compactMap { Self.map(documentId: $0.documentID, data: $0.data()) }, nil)
        }
        return { active = false }
    }
    func categories(userId: String, callback: @escaping ([WalletWiseShared.Category]?, TransactionReadFailure?) -> Void) -> () -> Void {
        NSLog("[FirestoreTransport] category-listener endpoint=127.0.0.1:8080")
        let listener = firestore.collection("users").document(userId).collection("categories").addSnapshotListener(includeMetadataChanges: true) { snapshot, error in
            precondition(Thread.isMainThread)
            if let error { callback(nil, Self.failure(error)); return }
            guard let snapshot else { callback(nil, .unknown); return }
            if snapshot.metadata.isFromCache && snapshot.documents.isEmpty { return }
            let categories: [WalletWiseShared.Category] = snapshot.documents.compactMap { document in
                let data = document.data()
                guard let name = data["name"] as? String, !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return nil }
                return WalletWiseShared.Category(id: document.documentID, name: name, icon: data["icon"] as? String ?? "", type: data["type"] as? String ?? "Chi", isCustom: data["isCustom"] as? Bool ?? false, sortOrder: (data["sortOrder"] as? NSNumber)?.int32Value ?? 0)
            }
            callback(categories, nil)
        }
        return { listener.remove() }
    }
    func mutate(kind: TransactionMutationKind, transaction: WalletWiseShared.Transaction, authorized: @escaping () -> Bool, callback: @escaping (TransactionReadFailure?) -> Void) -> () -> Void {
        var active = true
        let document = firestore.document(IosTransactionWriteMapper.documentPath(transaction: transaction))
        let complete: (Error?) -> Void = { error in
            precondition(Thread.isMainThread)
            guard active else { return }
            callback(error.map(Self.failure))
        }
        guard authorized() else { callback(.notAuthenticated); return {} }
        NSLog("[FirestoreTransport] mutation endpoint=127.0.0.1:8080")
        switch kind {
        case .add: document.setData(IosTransactionWriteMapper.fields(kind: kind, transaction: transaction), completion: complete)
        case .update: document.updateData(IosTransactionWriteMapper.fields(kind: kind, transaction: transaction), completion: complete)
        case .remove:
            document.getDocument(source: .server) { snapshot, error in
                guard active, authorized() else { return }
                if let error { complete(error); return }
                guard let snapshot, snapshot.exists,
                      (snapshot.data()?["userId"] as? String ?? transaction.userId) == transaction.userId else { callback(.permissionDenied); return }
                document.delete(completion: complete)
            }
        default: callback(.permissionDenied)
        }
        return { active = false }
    }
    static func map(documentId: String, data: [String: Any]) -> TransactionDocument? {
        guard !documentId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return nil }
        let number = numeric(data["timestamp"])
        let numericTimestamp = number?.doubleValue ?? 0
        let milliseconds = numericTimestamp.isFinite && numericTimestamp >= Double(Int64.min) && numericTimestamp < Double(Int64.max) ? Int64(numericTimestamp) : 0
        let timestamp = data["timestamp"] as? Timestamp
        let amount = numeric(data["amount"])?.doubleValue ?? 0
        return TransactionDocument(
            documentId: documentId, type: data["type"] as? String ?? "", amount: amount.isFinite ? amount : 0,
            category: data["category"] as? String ?? "", paymentMethod: data["paymentMethod"] as? String ?? "Tiền mặt",
            note: data["note"] as? String ?? data["content"] as? String ?? "", timestampMilliseconds: milliseconds,
            legacyTimestamp: timestamp.map { FirestoreTimestampValue(seconds: $0.seconds, nanoseconds: $0.nanoseconds) },
            imageUrl: data["imageUrl"] as? String ?? ""
        )
    }
    private static func numeric(_ value: Any?) -> NSNumber? {
        guard let number = value as? NSNumber, CFGetTypeID(number) != CFBooleanGetTypeID() else { return nil }
        return number
    }
    private static func failure(_ error: Error) -> TransactionReadFailure {
        switch (error as NSError).code {
        case FirestoreErrorCode.permissionDenied.rawValue: return .permissionDenied
        case FirestoreErrorCode.unauthenticated.rawValue: return .notAuthenticated
        case FirestoreErrorCode.unavailable.rawValue, FirestoreErrorCode.deadlineExceeded.rawValue: return .network
        default: return .unknown
        }
    }
}

enum IosTransactionWriteMapper {
    static func documentPath(transaction: WalletWiseShared.Transaction) -> String { "users/" + transaction.userId + "/transactions/" + transaction.id }
    static func fields(kind: TransactionMutationKind, transaction: WalletWiseShared.Transaction) -> [String: Any] {
        var data: [String: Any] = ["type": transaction.type, "amount": transaction.amount, "category": transaction.category,
            "paymentMethod": transaction.paymentMethod, "note": transaction.note, "timestamp": transaction.timestamp]
        if kind == .add { data["id"] = transaction.id; data["userId"] = transaction.userId; data["imageUrl"] = "" }
        return data
    }
}

// Internal entry point allows unit checks to exercise the real native-to-shared mapping.
enum IosTransactionDocumentMapper {
    static func map(documentId: String, data: [String: Any]) -> TransactionDocument? {
        FirebaseTransactionSnapshotSource.map(documentId: documentId, data: data)
    }
}
#endif
