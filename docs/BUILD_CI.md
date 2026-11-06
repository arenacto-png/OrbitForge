# Build and CI

The `.github/workflows/android.yml` workflow runs on pushes to `main`, pull requests targeting `main`, and manual dispatch. It uses JDK 17 and Gradle 8.11.1, runs JVM unit tests with `gradle test`, builds the debug APK with `gradle assembleDebug`, and uploads the APK plus checksum as the `orbitforge-debug-apk` artifact.

After building the debug APK, CI runs `scripts/verify-apk.sh` to verify its Android APK signature with `apksigner` and write a SHA-256 checksum next to the APK; both files are uploaded as the `orbitforge-debug-apk` artifact.

The current workflow does not run Android instrumentation or emulator UI tests. A successful CI run verifies the configured JVM tests, debug APK build, signature, and checksum, not installation or behavior on a physical device.

There is no Gradle wrapper in the repository yet; adding one and verifying Android Studio sync/build remain roadmap items. Do not mark local builds or on-device tests as complete unless they were actually run.
