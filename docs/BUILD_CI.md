# Build and CI

The `.github/workflows/android.yml` workflow provisions JDK 17, Android SDK Platform 35, and Gradle 8.11.1. It runs `testDebugUnitTest`, builds the debug APK, and uploads `app-debug.apk` as an artifact.

Trigger it by pushing to `main`/`master`, opening a pull request, or using the GitHub Actions workflow-dispatch button.

The current working environment does not have Gradle or the Android SDK installed, so a local Android build cannot be truthfully marked as verified. The first GitHub Actions run will provide the first real build/test result.
