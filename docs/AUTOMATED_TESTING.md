# Automated Android testing

## What CI checks

The GitHub Actions workflow runs on pushes, pull requests, or manually from the Actions tab:

1. Unit tests (`testDebugUnitTest`).
2. Debug APK build (`assembleDebug`).
3. Instrumented Compose UI smoke tests on a Pixel 2 Android emulator (API 35).
4. Uploads the APK and test reports even if a test fails, when those files exist.

## Current UI smoke coverage

- App startup and primary navigation.
- Projects screen and limited terminal entry/return navigation.
- Settings validation for an insecure `http://` provider URL.
- Opening diagnostics and copying the diagnostic report.

These are smoke tests, not exhaustive functional or security verification. Network-dependent API, GitHub, MCP, and SAF behavior still need dedicated fake-server tests and a manual run on a real device/provider.

## Unit test coverage

- Workspace search result parsing handles nested paths, colons in filenames and matched text, unknown paths, and malformed line numbers.
- Filename search excludes sensitive paths such as `.env`, SSH keys, and local credential files.
- Syntax highlighting preserves source text, colors common tokens, and falls back to plain text for files larger than 100,000 characters.

## Run locally

With JDK 17, Android SDK 35, Gradle 8.11.1 and an Android emulator/device configured:

```bash
gradle --no-daemon testDebugUnitTest
gradle --no-daemon assembleDebug
gradle --no-daemon connectedDebugAndroidTest
```

## Where to find results

In GitHub: **Actions → Android CI → selected run → Artifacts**. Download `OrbitForge-debug-apk` for the APK and `OrbitForge-test-reports` for test output. If the workflow fails, open the failed step and attach its error log or the report artifact to the ChatGPT conversation.

## Limits

The CI emulator is a clean virtual device. It cannot prove behavior on every phone, Android vendor build, file picker, API provider, GitHub permission setup, or MCP server. After CI passes, install the APK on the target phone and use **Settings → Logs and diagnostics → Copy/Share** to report device-specific issues.
