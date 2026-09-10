# Checkpoint 03: Authentication and profile boundary

## Android behavior recorded before refactoring

- Login and registration trimmed their three existing text inputs, used Firebase
  email/password authentication, exposed loading/success/error through
  `AuthState`, and used a 15-second timeout.
- Registration created `users/{uid}` with exactly `id`, `email`, `username`,
  `currentStreak`, and `lastRecordDate`.
- Password reset used Firebase Authentication email reset and displayed its
  success text through the existing `AuthState.error` field.
- The ViewModel installed one Firebase auth listener and replaced its previous
  Firestore profile listener whenever profile loading was repeated.
- A missing profile was created from Firebase email/display name. Existing
  documents, including documents without optional `avatarUrl` or `gender`, were
  read with model defaults.
- Username and gender updates used merge writes. Avatar selection read an
  Android `Uri`, uploaded the bytes to ImgBB, and then merge-wrote `avatarUrl`.
- Password change reauthenticated the email/password user before updating the
  password. Logout cleared the cached profile.

## Boundary after refactoring

- Validation, input models, repository protocols, use cases, state contracts,
  default-profile calculation, and listener coordination live in `commonMain`.
- Firebase Authentication and Cloud Firestore are confined to Android adapter
  classes. Firebase exceptions are mapped to pure repository errors.
- Firebase listeners are exposed as cancellable `Flow` values. Their
  `awaitClose` blocks remove the underlying registrations, while the common
  session controller prevents duplicate collectors and cancels the profile
  collector on logout.
- Android image reading remains in `AndroidAvatarUpdater`; upload uses the same
  `ImgBbImageUploader` instance factory as transaction images. A failed read or
  upload performs no Firestore write, and a failed profile write does not
  replace the cached previous URL.
- The four Compose screens remain unchanged and continue to use
  `AuthViewModel` as a compatibility facade.

No Firebase project, authentication method, UID, collection path, field name,
or profile wire type is changed in this checkpoint. Firebase Apple SDK and the
iOS Swift adapters remain intentionally out of scope.
