# OrbitForge

OrbitForge is a new Android-first AI development workspace. Version `0.3.0` is an early prototype with a native Compose UI, streaming OpenAI-compatible Chat Completions client, local chat history, encrypted API-key storage, an app-private workspace, and copy/share diagnostics.

## Current functionality
- Kotlin + Jetpack Compose UI: Agent, Projects, Settings
- Configurable HTTPS base URL and model ID for OpenAI-compatible Chat Completions APIs
- API key encrypted with AES/GCM using Android Keystore
- Streaming server-sent events (SSE) responses, timeout/connect errors, cancellation from the UI
- Local bounded chat history (up to 100 messages)
- App-private workspace folder and sample README creation
- Persistent diagnostic log with redaction attempts, copy-to-clipboard and Android share sheet

## Known limitations
- Not yet a full autonomous coding agent: model tool-calling, editor, terminal, Skills, MCP and Git are not implemented.
- The provider implementation targets OpenAI-compatible Chat Completions SSE; provider-specific formats may need adapters.
- Diagnostic redaction is best-effort; inspect reports before sharing.
- Gradle wrapper files are not included. Open in Android Studio with JDK 17 and Android SDK Platform 35.
- This project has not been built or tested on a real device in this environment.

## Build
Open the project root in Android Studio, sync Gradle, and build the `app` debug variant. Install requested SDK components if prompted.

## Security
Never commit API keys, tokens, signing credentials, or private project data. The app stores a user-supplied key encrypted with Android Keystore. This protects local storage at rest but is not a guarantee against a compromised device. Never embed a developer-owned secret in the APK.
