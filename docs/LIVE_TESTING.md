# Live test checklist

GitHub Actions runs the JVM unit tests, builds the debug APK, verifies its signature, and writes a SHA-256 checksum. Android Studio sync, installation, and live behavior still require verification outside CI.

## 1. Build and verify

1. Install JDK 17 and Android SDK Platform 35.
2. From the repository root, run `./gradlew test`.
3. Build the debug APK with `./gradlew assembleDebug`.
4. If opening in Android Studio, import the repository root and complete Gradle sync.
5. Locate the APK at `app/build/outputs/apk/debug/app-debug.apk`.
6. From the extracted artifact directory, verify the checksum with `sha256sum -c app-debug.apk.sha256`, then verify the APK certificate with `apksigner verify --verbose --print-certs app-debug.apk`. Install only after both checks succeed.

## 2. Smoke-test the app

- Launch the app; navigate through Agent, Projects, Settings, and Logs.
- In Settings, configure an HTTPS provider base URL, model ID, and your own API key. Save, then press **Проверить подключение**.
- Send a simple chat message and confirm streaming output.
- Enable agent mode and ask it to list files or create a small README. Confirm the write preview and approve/reject behavior.
- Open Projects, create/edit a file, select a folder to import, then export back to the selected folder.
- Open Terminal and try `help`, `pwd`, `ls`, `touch test.txt`, and `cat test.txt`. Confirm mutating commands require approval and shell operators are rejected.
- If using GitHub, save a token with minimum required permissions, list/create a repository, then review the workspace before confirming sync.
- If using MCP, configure a trusted HTTPS endpoint, test discovery, then inspect arguments before approving every external tool call.

## 3. Send diagnostics when something fails

1. Open **Settings → Logs**.
2. Press **Копировать** or **Отправить**.
3. Paste the report into the ChatGPT conversation.
4. Review it first. Redaction is best-effort; never paste API keys, GitHub tokens, MCP tokens, signing credentials, or private project contents.

## What counts as a live-test blocker

- Gradle sync/build failure or failed unit test.
- Crash on startup or navigation.
- API connection, SSE parsing, cancellation, or provider tool-call errors.
- SAF folder import/export failure.
- GitHub token-scope, create-repository, or push failure.
- MCP protocol/session/authentication incompatibility.

Copy the diagnostic report rather than manually retyping the error.
