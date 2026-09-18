# iOS Auth Emulator checks (Checkpoints 5D.1 and 5D.2)

These checks use the Firebase Apple SDK 12.11.0 already pinned by the Xcode project.
They never enable production Auth or access Firestore. The existing Android presenter
and repositories are unchanged.

## Bootstrap and safety boundary

Only a Debug binary with **all four** launch environment variables below creates the
Apple Auth adapter:

```text
WALLETWISE_AUTH_EMULATOR=1
WALLETWISE_AUTH_PROJECT=demo-walletwise
WALLETWISE_AUTH_HOST=127.0.0.1
WALLETWISE_AUTH_PORT=9099
```

The named Firebase app `WalletWiseAuthEmulator` uses public demo options, not values
from the ignored real Google plist. Its separate app name isolates its Keychain
session from the default Firebase app. `useEmulator` runs before state observation,
current-user inspection, or outgoing Auth operations.

The Debug transport guard uses GTMSessionFetcher's public global test hook. It rejects
non-emulator requests before their URLSession task resumes, and rejects redirects
outside that endpoint. For allowed requests, the documented **all-nil response**
continues the real network request; it does not supply an Auth result. Audit markers
print only the fixed local endpoint. Keep the Firebase SDK pin: changes to its
transport require rechecking this guard.

Missing flags leave the adapter disconnected. Incorrect flags fail closed. Release
compiles out the adapter, transport hook, and integration probe, and rejects an
emulator flag. Core-only bootstrap still uses the existing valid, ignored
`GoogleService-Info.plist`; it does not instantiate Auth or Firestore in this mode.

The callback boundary exports normalized `AuthUser` data (UID, nullable email/name,
email verification), stable failures, cancellation, and Auth methods, never SDK types.
UID must be nonblank; name/email are trimmed, blank values become null, and SDK email
case is preserved. `AuthSession` retains its existing Android constructor fields and
adds compatible normalized-user mapping. User/session string representations redact
personal data. The Android repository/presenter/Profile flow is unchanged.

Registration waits for SDK create, display-name `commitChanges`, and reload. Shared
`AuthRegistrationProgress` gates the sequence and rejects missing/mismatched reload
data or repeated callbacks. Profile/reload failures explicitly report a created account
with incomplete registration, not success; no account is automatically deleted.
The presenter establishes user state only from its SDK listener, not an operation
completion. Because Firebase 12.11.0 auth-state listeners suppress same-UID metadata
changes, after reload the adapter re-registers each active owner's listener for the
SDK initial current-user snapshot. Listener generations reject previously queued
callbacks. Completion success waits for matching refreshed listener data.

The authenticated Compose shell shows status, display name (or email/member fallback),
and logout, without UID, Home, or Firestore. Passwords remain temporary form/request
data and are cleared on success, route change, logout, and disposal. Registration's
name is saved only to Firebase Auth displayName; no Firestore profile is accessed.

Each call to `walletWiseComposeViewController(service:observer:)` creates a new
`AuthControllerSession` and presenter. SwiftUI's per-controller coordinator retains
that session, not the Firebase bootstrap. Both composition disposal and SwiftUI
`dismantleUIViewController` invoke the idempotent cleanup, including controllers
discarded before composition starts. Cleanup cancels that owner's callbacks/listener,
clears its form, and never logs out the shared Firebase SDK user. The Debug probe
is retained only by its asynchronous work, never in a static presenter/probe field.

Cancellation releases/suppresses presenter callbacks but cannot undo an SDK request
already sent. The native adapter keeps its request gate until that request finishes.
The SDK listener remains the source of truth for actual session changes.

## Run locally

Use Xcode's **default simulator signing**, never `CODE_SIGNING_ALLOWED=NO`.
Do not add Keychain Sharing, access groups, device profiles, or source entitlements.
Discover the available `WalletWise iPhone` UDID with `xcrun simctl list devices available`.

Install Firebase CLI 15.30.1 into a temporary directory, without logging into Firebase:

```sh
CHECK_TMP=$(mktemp -d /private/tmp/walletwise-auth.XXXXXX)
npm install --prefix "$CHECK_TMP/cli" firebase-tools@15.30.1 --no-audit --no-fund
```

In a separate terminal, start only Auth. Run from outside the repository so CLI
debug logs are temporary. `EMULATOR_CONFIG` must point to this directory's `firebase.json`.
The configuration fixes localhost port 9099, disables the UI, and uses single-project mode.

```sh
cd "$CHECK_TMP"
"$CHECK_TMP/cli/node_modules/.bin/firebase" emulators:start --only auth \
  --project demo-walletwise --config "$EMULATOR_CONFIG" > emulator-private.log 2>&1
```

Do not print raw CLI logs: the Emulator itself can print a reset link and test email.
Redact those values before sharing logs. No real email is sent by the Auth Emulator.

Build the app using the existing Direct Integration phase:

```sh
xcodebuild -project iosApp/WalletWiseIOS.xcodeproj -scheme WalletWiseIOS \
  -configuration Debug -destination "platform=iOS Simulator,id=$SIMULATOR_UDID" \
  -derivedDataPath "$CHECK_TMP/DerivedData" build > "$CHECK_TMP/build.log" 2>&1
python3 tools/firebase-auth-emulator/run_simulator_checks.py \
  --simulator "$SIMULATOR_UDID" \
  --app "$CHECK_TMP/DerivedData/Build/Products/Debug-iphonesimulator/WalletWiseIOS.app" \
  --artifacts "$CHECK_TMP/integration"
```

The opt-in Debug probe drives the **same presenter rendered by Compose**, rather than
automated screen taps. It registers a random `example.invalid` account, blocks duplicate
submits at both presenter and adapter boundaries, logs out, rejects a wrong password,
and logs in correctly. A second process restores the same Keychain identity **without
login**, logs out, and submits reset. A third process confirms the signed-out session.
Only status markers are emitted. The temporary fixture in simulator app preferences
contains only test email, the identity to compare, and expected name for user A, never
a password. It is removed after reset/logout. The runner checks exactly one account
and its exact display name after A registers, plus one password-reset
OOB code through localhost admin APIs, then deletes every demo account. It refuses
admin redirects. The public `owner` sentinel is Emulator-only, not a credential.
Then a second SDK-created account without a display name is used for real login,
email fallback, isolation from user A, another Keychain restore, and logout/relaunch.
All demo accounts (two after the user-B checks) are deleted in the runner's finally.
No password is persisted between processes.

The probe emits only SHA-256 fingerprints of the temporary fixture's email, identity,
and expected display name. The runner compares those fingerprints against Emulator
Admin API metadata, without printing full values. It does not read the app's raw
CFPreferences plist, whose on-disk snapshot may lag behind the active process.

Profile-update failure is covered deterministically by the shared sequence/presenter
unit tests used by the adapter. Runtime fault injection is not claimed as PASS unless
a separate Emulator run proves it; do not alter production code or loosen the guard
to manufacture a failure.

Stop the CLI with Ctrl-C after testing: accounts are deleted by the runner, but OOB
codes are in-memory and disappear when the Emulator exits. Verify **no** listener
remains on 9099, 4400, or 4500. Never erase the simulator or delete build caches.

```sh
lsof -nP -iTCP:9099 -iTCP:4400 -iTCP:4500 -sTCP:LISTEN
```

With the Emulator stopped, use the runner's `--guard-mode debug` for disabled/invalid
flag checks, or `--guard-mode release` for a normal Release smoke launch with **no**
emulator flags or credentials. It captures the Login screenshot for inspection and
checks absence of localhost or production Auth endpoints. Policy checks compiled with and without `-D DEBUG`
using `AuthEmulatorPolicy.swift` and `AuthEmulatorPolicyChecks.swift` also prove a valid
emulator flag is rejected in a Release policy.

## Scoped Release linker memory

The current 2 GiB Gradle heap builds Android/Debug but ran out of heap while linking
the optimized iOS framework. Keep repository `gradle.properties` unchanged. On a host
with enough available RAM (the checkpoint host has 16 GiB), run the linker separately
with a temporary 4 GiB heap and no persistent daemon:

```sh
./gradlew -Dorg.gradle.jvmargs="-Xmx4g -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8" \
  --no-daemon :shared:linkReleaseFrameworkIosSimulatorArm64 --stacktrace
```

Then build Xcode Release using default simulator signing. Do not run heavy builds in
parallel while linking, exceed half of physical RAM, or change dependencies/caches/
linker flags/deployment target to work around a memory failure. If the temporary
heap still fails, collect diagnostics and stop rather than raising it repeatedly.

Run shared iOS and Android host tests with `--rerun-tasks` for fresh XML evidence;
report XML tests/failures/skipped instead of interpreting `UP-TO-DATE` as a new test run.

Primary references:

- [Connect an app to the Auth Emulator](https://firebase.google.com/docs/emulator-suite/connect_auth)
- [Install/configure the Emulator Suite](https://firebase.google.com/docs/emulator-suite/install_and_configure)
- [GTMSessionFetcher public test-hook contract (5.3.1)](https://github.com/google/gtm-session-fetcher/blob/v5.3.1/Sources/Core/Public/GTMSessionFetcher/GTMSessionFetcher.h)
