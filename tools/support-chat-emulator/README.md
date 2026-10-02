# Support chat Emulator harness

Use only demo-walletwise, Auth 127.0.0.1:9099, Firestore 127.0.0.1:8080, and local fixture server 127.0.0.1:8787. Android uses 10.0.2.2. No CLI login, deploy, service account or production key.

Install the pinned SDK with npm install. Run checkpoint.ps1 -Action StartFirebase, then -Action Suite. The suite requires empty Auth Emulator data and cleans all documents/accounts in this isolated demo project. Do not connect to a shared fixture database.

Staff custom claims are assigned through local Auth Emulator accounts:update, Authorization: Bearer owner, customAttributes={"supportAgent":true}. The staff client refreshes its ID token. Bearer owner is used only for Emulator administration and cleanup, never client Firestore operations. These administrative endpoints are trusted infrastructure and must stay on loopback.

StartStaff creates fresh fixtures and responds to real messages from Android. The mobile app never fabricates staff replies. The local fixture endpoint keeps synthetic credentials in memory; it is not Web Admin authentication. FinishStaff cleans fixtures. StopFirebase stops only its owned process tree and verifies closed test ports. StopAndroid verifies the AVD identity before clearing test app data and stopping it.

Gradle action accepts exactly one task. Host/unit tests use --rerun-tasks. Normal checks keep the flag false; runtime builds explicitly use -Emulator. No BuildAll action exists.

Schema:
supportConversations/{uid}: userId, userEmail, status, createdAt, updatedAt, lastMessagePreview, lastSenderRole.
status: open, waiting_staff, waiting_user, closed.
messages/{messageId}: id, senderId, senderRole=user|staff, content, createdAt, clientRequestId; optional legacy status=sent.
clientRequestId equals immutable message ID; retry reads existing data and preserves timestamp/conversation metadata.
Mobile also reads legacy agent as a staff reply.
Customer writes waiting_staff/user metadata; staff replies write waiting_user/staff. Staff alone controls open/closed. Privilege comes only from token.supportAgent, never document role fields.

network-guard.cjs must be preloaded before Node SDK/CLI. It rejects non-loopback traffic and reports endpoint counts. Do not log credentials, JWTs, full emails/UIDs or sensitive content.

Official references:
https://firebase.google.com/docs/emulator-suite/connect_auth
https://firebase.google.com/docs/emulator-suite/connect_firestore
https://firebase.google.com/docs/firestore/security/test-rules-emulator

Before Web Admin: implement real server-controlled staff claims, admin authentication/session handling and a separate approved production Rules review/deploy. Presence requires a real presence implementation.
