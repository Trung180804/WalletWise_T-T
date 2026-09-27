#if DEBUG
import Foundation
import FirebaseAuth
import FirebaseFirestore
import WalletWiseShared

private final class SupportCallbackCancellation: NSObject, SupportCancellation {
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

/** Firebase-only chat bridge. Image upload intentionally remains outside this adapter. */
final class FirebaseSupportAdapter: NSObject, CallbackSupportService {
    private let firestore: Firestore
    private let currentSubject: () -> String?
    private let currentEmail: () -> String
    private var observation: SupportCallbackCancellation?
    private var write: SupportCallbackCancellation?
    private(set) var registrationCount = 0
    private(set) var removalCount = 0
    private(set) var snapshotCount = 0
    private(set) var writeRequestCount = 0
    private(set) var confirmedWriteCount = 0

    init(firestore: Firestore, auth: FirebaseAuthAdapter) {
        precondition(firestore.app.name == "WalletWiseAuthEmulator" && firestore.app.options.projectID == "demo-walletwise")
        precondition(firestore.settings.host == "127.0.0.1:8080" && !firestore.settings.isSSLEnabled)
        self.firestore = firestore
        self.currentSubject = { [weak auth] in auth?.currentSubject }
        self.currentEmail = { [weak auth] in auth?.currentUser?.email ?? "" }
    }

    func observeMessages(userId: String, observer: SupportSnapshotObserver) -> SupportCancellation {
        precondition(Thread.isMainThread)
        observation?.cancel()
        let token = SupportCallbackCancellation()
        observation = token
        guard !userId.isEmpty, currentSubject() == userId else {
            observer.supportChanged(documents: nil, fromCache: false, failure: .notAuthenticated)
            return token
        }
        registrationCount += 1
        NSLog("[SupportFirestore] listener endpoint=127.0.0.1:8080")
        let registration = firestore.collection("supportConversations").document(userId)
            .collection("messages").order(by: "createdAt").limit(toLast: 100)
            .addSnapshotListener(includeMetadataChanges: true) { [weak self, weak token] snapshot, error in
                precondition(Thread.isMainThread)
                guard let self, let token, token.active, self.currentSubject() == userId else { return }
                self.snapshotCount += 1
                if let error {
                    observer.supportChanged(documents: nil, fromCache: false, failure: Self.failure(error))
                    return
                }
                guard let snapshot else {
                    observer.supportChanged(documents: nil, fromCache: false, failure: .unknown)
                    return
                }
                let documents = snapshot.documents.compactMap { document -> SupportMessageDocument? in
                    let data = document.data()
                    guard let senderId = data["senderId"] as? String,
                          let senderRole = data["senderRole"] as? String,
                          let content = data["content"] as? String else { return nil }
                    let createdAt = data["createdAt"] as? Timestamp
                    return SupportMessageDocument(
                        documentId: document.documentID,
                        senderId: senderId,
                        senderRole: senderRole,
                        content: content,
                        createdAtMilliseconds: createdAt.map { Int64($0.dateValue().timeIntervalSince1970 * 1000.0) } ?? 0,
                        hasCreatedAt: createdAt != nil,
                        clientRequestId: data["clientRequestId"] as? String ?? document.documentID,
                        pendingWrites: document.metadata.hasPendingWrites,
                        messageType: data["messageType"] as? String ?? "text",
                        imageUrl: data["imageUrl"] as? String,
                        mimeType: data["mimeType"] as? String,
                        fileName: data["fileName"] as? String
                    )
                }
                observer.supportChanged(documents: documents, fromCache: snapshot.metadata.isFromCache, failure: nil)
            }
        token.remove = { [weak self] in registration.remove(); self?.removalCount += 1 }
        return token
    }

    func send(session: AuthSession, message: SupportMessage, completion: SupportWriteCompletion) -> SupportCancellation {
        precondition(Thread.isMainThread)
        let token = SupportCallbackCancellation()
        guard currentSubject() == session.userId,
              message.senderId == session.userId,
              message.senderRole == .user,
              !message.id.isEmpty, !message.id.contains("/"),
              message.clientRequestId == message.id,
              write?.active != true else {
            completion.supportCompleted(failure: .notAuthenticated)
            token.cancel()
            return token
        }
        write = token
        writeRequestCount += 1
        let parent = firestore.collection("supportConversations").document(session.userId)
        let document = parent.collection("messages").document(message.id)
        firestore.runTransaction({ [weak self, weak token] transaction, errorPointer -> Any? in
            guard let self, let token, token.active, self.currentSubject() == session.userId else {
                errorPointer?.pointee = Self.localError("Support session changed")
                return nil
            }
            do {
                let existing = try transaction.getDocument(document)
                if existing.exists {
                    guard existing.get("senderId") as? String == session.userId,
                          existing.get("senderRole") as? String == "user",
                          existing.get("clientRequestId") as? String == message.clientRequestId else {
                        errorPointer?.pointee = Self.localError("Idempotency key collision")
                        return nil
                    }
                    return nil
                }
                let parentSnapshot = try transaction.getDocument(parent)
                if parentSnapshot.exists, parentSnapshot.get("status") as? String == "closed" {
                    errorPointer?.pointee = Self.localError("Conversation is closed")
                    return nil
                }
                var parentFields: [String: Any] = [
                    "userId": session.userId,
                    "userEmail": self.currentEmail().trimmingCharacters(in: .whitespacesAndNewlines),
                    "status": "waiting_staff",
                    "updatedAt": FieldValue.serverTimestamp(),
                    "lastMessagePreview": message.messageType == .image ? "[Hình ảnh]" : String(message.content.trimmingCharacters(in: .whitespacesAndNewlines).prefix(160)),
                    "lastSenderRole": "user"
                ]
                if !parentSnapshot.exists { parentFields["createdAt"] = FieldValue.serverTimestamp() }
                transaction.setData(parentFields, forDocument: parent, merge: true)
                var messageFields: [String: Any] = [
                    "id": message.id,
                    "senderId": session.userId,
                    "senderRole": "user",
                    "content": message.content.trimmingCharacters(in: .whitespacesAndNewlines),
                    "createdAt": FieldValue.serverTimestamp(),
                    "clientRequestId": message.clientRequestId,
                    "status": "sent",
                    "messageType": message.messageType == .image ? "image" : "text"
                ]
                if message.messageType == .image {
                    guard let imageUrl = message.imageUrl, let mimeType = message.mimeType, let fileName = message.fileName else {
                        errorPointer?.pointee = Self.localError("Image fields missing")
                        return nil
                    }
                    messageFields["imageUrl"] = imageUrl
                    messageFields["mimeType"] = mimeType
                    messageFields["fileName"] = fileName
                }
                transaction.setData(messageFields, forDocument: document)
                return nil
            } catch let error as NSError {
                errorPointer?.pointee = error
                return nil
            }
        }) { [weak self, weak token] _, error in
            precondition(Thread.isMainThread)
            guard let self, let token, token.active, self.currentSubject() == session.userId else { return }
            token.cancel()
            if error == nil { self.confirmedWriteCount += 1 }
            completion.supportCompleted(failure: error.map(Self.failure))
        }
        return token
    }

    private static func localError(_ message: String) -> NSError {
        NSError(domain: "WalletWiseSupport", code: 1, userInfo: [NSLocalizedDescriptionKey: message])
    }

    private static func failure(_ error: Error) -> SupportFailure {
        switch (error as NSError).code {
        case FirestoreErrorCode.permissionDenied.rawValue: return .permissionDenied
        case FirestoreErrorCode.unauthenticated.rawValue: return .notAuthenticated
        case FirestoreErrorCode.unavailable.rawValue, FirestoreErrorCode.deadlineExceeded.rawValue: return .network
        default: return .unknown
        }
    }
}
#endif
