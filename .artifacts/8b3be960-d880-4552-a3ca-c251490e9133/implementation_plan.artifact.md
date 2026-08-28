# Fix 3 (and more) errors in ProfileScreen.kt and AddTransactionScreen.kt

The user reported 3 errors across `ProfileScreen.kt` and `AddTransactionScreen.kt`. Based on the analysis and build results, I found 4 compilation errors and some significant warnings.

## Proposed Changes

### [Component] UI Screens

#### [MODIFY] [ProfileScreen.kt](file:///Users/macos/WalletWise_T-T/app/src/main/java/com/example/walletwise/presentation/home/ProfileScreen.kt)
- Add missing imports for `LocalFocusManager`, `pointerInput`, and `detectTapGestures`.
- Use `Icons.AutoMirrored.Filled.ArrowBack` instead of the deprecated `Icons.Default.ArrowBack` where applicable.

#### [MODIFY] [AddTransactionScreen.kt](file:///Users/macos/WalletWise_T-T/app/src/main/java/com/example/walletwise/presentation/home/AddTransactionScreen.kt)
- Fix the `Smart cast to 'Uri' is impossible` error in `launchCamera` by using a local variable for the `Uri`.
- Use `Icons.AutoMirrored.Filled.ArrowBack` instead of the deprecated `Icons.Default.ArrowBack`.
- Remove unnecessary `!!` non-null assertion as suggested by the analyzer.

## Verification Plan

### Automated Tests
- Run `gradle_build("app:assembleDebug")` to ensure the project compiles successfully.

### Manual Verification
- N/A (Build verification should be sufficient for these compilation errors).
