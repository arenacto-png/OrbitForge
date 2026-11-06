# OrbitForge

OrbitForge is an Android-first AI-assisted development workspace built with Kotlin and Jetpack Compose.

## Current functionality

- Native Compose UI for chat/agent, Projects, editor, terminal, settings, and diagnostics.
- OpenAI-compatible Chat Completions and Anthropic Messages streaming, with provider-format selection.
- API, GitHub, and MCP credentials stored separately using Android Keystore.
- Bounded tool-calling agent with workspace listing, reading, search, writes requiring approval, and an allowlisted terminal.
- Workspace editor with syntax highlighting, bounded diff preview, and explicit apply/reject.
- Local Git status/diff/reviewed commit for the app-private workspace; GitHub repository listing and reviewed workspace synchronization are separate flows.
- Remote HTTPS MCP discovery and calls, with confirmation before each external tool call.
- Copy/share diagnostic reports with redaction attempts and local provider request correlation.

## Known limitations

- CI runs JVM unit tests and builds a debug APK; Android Studio sync, emulator UI tests, and installation on a physical device still require live verification.
- The terminal is an allowlisted command runner, not a full shell. Termux and remote execution are not implemented.
- Local Git does not push or pull. GitHub synchronization uses a separate REST Git Data API flow.
- Secret scanning and diagnostic redaction are defense-in-depth, not guarantees; inspect reports and files before sharing or syncing.

## Build

Use JDK 17 and Android SDK Platform 35. The checked-in Gradle Wrapper pins Gradle 8.11.1 and verifies the distribution SHA-256.

Run from the repository root:

```bash
./gradlew test
./gradlew assembleDebug
```

Alternatively, open the project root in Android Studio, sync Gradle, and build the `app` debug variant. Install requested SDK components if prompted.

## Security

Never commit API keys, tokens, signing credentials, or private project data. User-supplied keys are encrypted with Android Keystore. This protects local storage at rest but is not a guarantee against a compromised device. Never embed a developer-owned secret in the APK.
