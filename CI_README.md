# Mobile GitHub Actions CI Setup

## Workflows Included
1. **Android Build & Tests (Ubuntu):**
   - Runs `:shared:testAndroidHostTest`
   - Runs `:app:testDebugUnitTest`
   - Compiles `:shared:compileAndroidMain`
   - Builds Debug APK (`:app:assembleDebug`)
   - Performs AGP Lint analysis (`:app:lintDebug`)

2. **Windows Gradle Wrapper Smoke Test (Windows):**
   - Verifies `gradlew.bat` execution when `Env:CLASSPATH` is absent/unset.
   - Ensures cross-platform Windows wrapper compatibility without requiring `$env:CLASSPATH = "."`.

## Deferred Features
- **iOS CI (`iosSimulatorArm64Test`):** Deferred to a dedicated follow-up PR to avoid macOS runner queue delays and heavy Xcode setup in initial Mobile CI.
