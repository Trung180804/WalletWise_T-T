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
final class FirebaseTransactionAdapter: NSObject, CallbackTransactionService {
    private let source: TransactionSnapshotSource
    private let currentSubject: () -> String?
    private var observation: TransactionCallbackCancellation?
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

// Internal entry point allows unit checks to exercise the real native-to-shared mapping.
enum IosTransactionDocumentMapper {
    static func map(documentId: String, data: [String: Any]) -> TransactionDocument? {
        FirebaseTransactionSnapshotSource.map(documentId: documentId, data: data)
    }
}
#endif
