# Checkpoint 4D settings boundary

Checkpoint 4D shares the Font Size, Theme, and Default Currency screens without
adding persistence or changing app-wide setting behavior.

## Current behavior

- Font Size is local preview state. Save and Back both return to Settings; the
  selected size is discarded when the screen leaves composition and is not
  applied to the rest of the app.
- Theme is applied immediately through the Android-owned `LocalAppTheme` state.
  It is not persisted, so a new app/root-theme instance starts from the current
  system theme as before.
- Default Currency emits the selected ISO code and returns to Settings. Android
  deliberately does not save the code or apply it to converters, transactions,
  formatting, or any other screen.

## Boundary for a future checkpoint

Shared content follows `state + callbacks -> shared Compose content`. Shared
presenters own transient screen state and one-time events. Android wrappers own
navigation and the existing mutable theme CompositionLocal.

A later checkpoint may add a pure settings-store contract and platform adapters
for Android DataStore and iOS UserDefaults, then connect persisted values to the
root theme, typography, currency formatting, and feature presenters. That work
must define defaults, migration, lifecycle, and error behavior before changing
the current screens. No Firebase settings document or Firestore schema change is
part of Checkpoint 4D.
