# Security baseline

- API keys are encrypted using AES/GCM with a key stored in Android Keystore.
- Never put API keys, access tokens, or signing credentials in source control.
- Avoid logging authorization headers, raw secrets, or sensitive prompt content.
- Diagnostic redaction is best-effort; users must inspect reports before sharing.
- Treat model output, tool names, paths, and arguments as untrusted input.
- Validate tool arguments against explicit schemas.
- Validate paths before showing approval prompts and restrict file operations to the chosen workspace root; reject traversal and symlink escapes.
- Ask for approval before destructive writes, shell commands, network access, or publishing changes.
- Provide cancellation, timeouts, output-size limits, and a bounded number of agent steps.
- Keep command execution isolated from the app process where feasible. Android-local Linux environments have limitations; do not describe PRoot as a strong security sandbox.
- Never embed a developer-owned secret in the APK.


## Agent tool policy

- The first agent tools are limited to listing files, reading UTF-8 text, and writing UTF-8 files in the app-private workspace.
- Write operations are capped at 10 KB and require explicit confirmation in the UI.
- Workspace paths are canonicalized and checked against the workspace root to block ordinary traversal and symlink escapes.
- No shell/terminal tool is available in this milestone.

- Agent file-read/search/agent-write tools reject common secret files such as `.env`, `local.properties`, keystores, PEM/P12 files, and SSH key paths. User-initiated editor access remains separate.
