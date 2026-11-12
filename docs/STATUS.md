# OrbitForge implementation status

## Implemented in source (not yet live-verified)

- Native Compose UI: agent/chat, projects, text editor, limited terminal, settings, diagnostics.
- OpenAI-compatible Chat Completions and Anthropic Messages streaming chat, provider-format selection, and an in-app connection test. The Anthropic adapter translates agent tool schemas, tool-use responses, and tool-result history into the shared strict tool-call validation path.
- Local conversation history and separate encrypted API/GitHub/MCP credentials using Android Keystore.
- Cancelled chat/agent requests replace the pending assistant placeholder with a durable cancellation result; partial chat output is retained with an incomplete-response marker, and the history is persisted.
- Bounded tool-calling agent with file listing, reading, search, writes with approval, and allowlisted terminal commands. Terminal `grep` uses the same sensitive-path/secret-content checks as other agent reads and bounds its result to 100 matches/20,000 characters. Terminal `mkdir`/`touch` also reject sensitive path names and canonical aliases before mutation. Provider tool-call batches are validated completely before execution; malformed JSON, duplicate call IDs, and unknown tools fail closed. The agent is limited to eight model rounds and eight tool calls per round, and does not execute tools returned in its final permitted round.
- Workspace screen searches file names and text contents asynchronously, limits displayed results, filters sensitive paths and secret-like content through the existing search policy, and opens matching files in the editor.
- Agent reads, writes, workspace search, and agent-facing file enumeration check canonical targets as well as supplied paths and hide Git metadata and common credential locations. The `list_files` tool and terminal `ls`/`find` omit common sensitive paths and aliases to sensitive or out-of-workspace targets; `find` also bounds traversal and output.
- The file editor applies lightweight syntax coloring to common code/config formats without changing text or cursor offsets; files over 100,000 characters fall back to plain text.
- Before saving editor changes, the user can inspect a bounded diff and explicitly apply or reject the edit; rejecting restores the original buffer. Agent write approval previews also validate existing canonical targets and secret-like content before showing the diff. The preview is capped to 80 lines and 6,000 characters (plus a truncation marker).
- Agent and user-editor file saves use a same-directory temporary file followed by atomic replacement. This prevents a single target file from being truncated by an interrupted write; it is per-file atomicity, not a multi-file transaction.
- Editable `SKILL.md` starter skills with bounded loading and untrusted-skill policy.
- Remote HTTP MCP initialization, tool discovery, and tool calls; each external call requires confirmation. MCP HTTP requests do not follow automatic redirects (including HTTPS-to-HTTP downgrades); users must configure the final HTTPS endpoint. MCP and non-streaming agent-provider HTTP response bodies are capped at 2 MiB before parsing; returned MCP text is capped at 20,000 characters.
- GitHub repository listing/creation and workspace sync with a reviewed added/modified diff; unchanged files are omitted, the shared sensitive-path policy and secret-like content checks exclude credentials, local hashes and the remote branch head are rechecked before a non-force commit, and remote-only files are never deleted; remote diff retrieval is capped at 40 modified files and 2 MiB.
- Local Git through JGit in the exact app-private workspace root: initialize a `main` branch, inspect staged/unstaged/untracked status, preview a bounded unified diff, and create a user-reviewed local commit. The commit stages only paths included in the reviewed plan, rechecks the preview fingerprint and staged blob hashes, rejects Git pathspec metacharacters before staging, applies the shared sensitive-path policy even for tracked deletions, redacts the full preview if an old/staged diff contains secret-like values, and never pushes to a remote.
- SAF folder picker with bounded import/export, private workspace editor, and persistent folder permissions. Export stages a complete temporary document before replacing an existing destination; it attempts to restore the prior document if promotion fails and retains a recoverable backup if restoration cannot be confirmed.
- Diagnostic report copy/share with API/GitHub/MCP token redaction, quoted JSON credential-field and cookie-header redaction, legacy stored-line re-sanitization, and compact crash traces.
- Provider chat and agent HTTP requests emit local correlation IDs, operation/API-format, HTTP status, duration, and bounded error-class metadata. Correlation IDs are not sent to providers; request URLs, headers, bodies, and prompts are not included in trace events.
- GitHub Actions workflow on main runs JVM unit tests and builds/uploads a debug APK through the checked-in Gradle Wrapper (8.11.1, distribution SHA-256 pinned); setup-gradle v6 validates the wrapper JAR and uses the basic open-source cache provider. CI actions use Node 24-compatible maintained majors (checkout v7, setup-java v6, setup-gradle v6, upload-artifact v7). Android emulator UI smoke tests are not part of the current main workflow.
- Unit tests for provider URL rules, diagnostic redaction, workspace path traversal and symlink escapes, strict agent tool-call parsing and round limits, safe workspace-search parsing, syntax highlighting spans/large-file fallback, bounded diff previews, local Git init/status/diff/commit safety including Git pathspec validation, and strict terminal command parsing.

## Still requires live verification

- GitHub Actions has successfully run JVM unit tests and built the debug APK on main. Gradle sync in Android Studio, emulator UI tests, APK installation on a physical device, and local Git interaction on Android still require separate verification.
- Real API provider compatibility, streaming, cancellation, and tool-call support remain live-verification items. The Anthropic adapter is covered by request/response and SSE unit tests, but has not been tested against a real provider account.
- Provider usage/error diagnostics (actionable bounded HTTP guidance and token-usage accounting) remain pending in the pre-existing diagnostics PR and are not yet part of main; request correlation and timing are now implemented.
- GitHub token scopes, repository creation, and workspace push.
- MCP compatibility with the target server's protocol/authentication variant.
- SAF import/export on the user's Android file provider, especially provider-specific rename/backup behavior during destination replacement.
- Review a copied diagnostic report to confirm it contains no sensitive data.

## Known scope limits

- Terminal is an allowlisted workspace command runner, not a full Linux shell. Its stateful tokenizer handles quoted arguments and rejects unmatched quotes, multiline input, backslash escape syntax, and shell-control operators outside quotes; quoted operator characters remain literal. Termux/remote execution is not yet implemented.
- Local Git status/diff/commit is implemented for the app-private workspace with JGit; partial staging and remote push/pull are not implemented. GitHub synchronization remains a separate REST Git Data API flow with bounded preview and stale-plan checks; it does not delete remote files.
- MCP support is remote HTTPS only; local stdio servers and some OAuth flows are not supported. Automatic redirects are intentionally disabled; configure the final HTTPS URL directly.
- Workspace sync uploads the app-private workspace contents, not this source archive automatically.
- SAF export replacement depends on the selected document provider supporting rename operations; unsupported or failed promotions are skipped rather than falling back to a destructive direct overwrite.
- Basic secret scanning is defense-in-depth, not a guarantee. Inspect files before importing/exporting or pushing them.
