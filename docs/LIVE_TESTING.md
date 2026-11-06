# Live test checklist

GitHub Actions has built the debug APK, but installation and live behavior have not been verified in this environment. Use this checklist after downloading the artifact.

## 1. Get a debug APK

1. Unzip `OrbitForge-starter.zip` on a PC.
2. Create a new empty GitHub repository named `OrbitForge` (or another name).
3. Upload the contents of the `OrbitForge/` folder, not the ZIP as one file. Do not upload API keys, tokens, signing files, or local configuration.
4. Open **Actions → Android CI** and run the workflow, or push to `main`/`master`.
5. Confirm the workflow's unit tests, `assembleDebug`, signature verification, and checksum steps pass.
6. Download and extract the `OrbitForge-debug-apk` artifact. In the folder containing the APK, verify the checksum with `sha256sum -c app-debug.apk.sha256` (or calculate SHA-256 yourself), then verify the APK certificate with `apksigner verify --verbose --print-certs app-debug.apk`.
7. Install `app-debug.apk` on the Android device only after verification.

## 2. Smoke-test the app

- Launch the app; navigate through Agent, Projects, Settings, and Logs.
- In Settings, configure an HTTPS OpenAI-compatible API base URL, model ID, and your own API key. Save, then press **Проверить подключение**.
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
