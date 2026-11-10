# Build and CI

The `.github/workflows/android.yml` workflow runs on pushes to `main`, pull requests targeting `main`, and manual dispatch. It uses JDK 17 and the repository's Gradle Wrapper (Gradle 8.11.1), runs JVM unit tests with `./gradlew test`, builds the debug APK with `./gradlew assembleDebug`, and uploads the APK as the `orbitforge-debug-apk` artifact.

The Wrapper distribution URL is restricted to the official Gradle service and its SHA-256 is pinned in `gradle/wrapper/gradle-wrapper.properties`. The wrapper JAR is sourced from the official Gradle 8.11.1 tag; `gradle/actions/setup-gradle@v6` validates wrapper JAR checksums automatically before the build. The workflow uses Node 24-compatible maintained action majors: checkout v7, setup-java v6, setup-gradle v6, and upload-artifact v7. Gradle caching is configured with the open-source `basic` provider.

The current workflow does not run Android instrumentation or emulator UI tests. A successful CI run verifies the configured JVM tests and debug APK build, not Android Studio sync, installation, or behavior on a physical device.

For a local build, install JDK 17 and Android SDK Platform 35, then run `./gradlew test` and `./gradlew assembleDebug`. Do not mark Android Studio sync or on-device tests as complete unless they were actually run.
