# Security baseline

- API keys are encrypted using AES/GCM with a key stored in Android Keystore.
- Never put API keys, access tokens, or signing credentials in source control.
- Avoid logging authorization headers, raw secrets, or sensitive prompt content.
- Provider request traces use a locally generated correlation ID plus operation, API format, phase, status, duration, and exception class only. Do not send trace IDs as provider headers or log request URLs, headers, bodies, or prompts.
- Diagnostic redaction is best-effort; users must inspect reports before sharing.
- Treat model output, tool names, paths, and arguments as untrusted input.
- Validate tool arguments against explicit schemas.
- Validate paths before showing approval prompts and restrict file operations to the chosen workspace root; reject traversal and symlink escapes. Agent read/write/search policies must check the canonical target as well as the user-supplied path so in-root symlink aliases cannot bypass sensitive-file exclusions.
- Ask for approval before destructive writes, shell commands, network access, or publishing changes.
- App-private workspace saves write UTF-8 content to a same-directory temporary file, sync it, and replace the target with an atomic move. If the filesystem cannot provide an atomic move, the save fails closed instead of falling back to a direct truncating write. This protects individual files, not a multi-file operation.
- Provide cancellation, timeouts, output-size limits, and a bounded number of agent steps. Non-streaming agent-provider and remote MCP HTTP response bodies are capped at 2 MiB before JSON/SSE parsing.
- Validate the entire provider tool-call batch before executing any call. Require the OpenAI-compatible `type: function` discriminator; derive the accepted tool-name allowlist from the exact function definitions sent to the provider so newly added tools cannot be accidentally rejected or silently omitted; reject malformed arrays/entries, duplicate call IDs, unknown tool names, and malformed or non-object JSON arguments; never substitute empty arguments after a parse failure.
- Limit the agent to eight model rounds and eight tool calls per round. Do not execute tool calls returned in the final permitted round, because there is no remaining model round to process their results.
- Keep command execution isolated from the app process where feasible. Android-local Linux environments have limitations; do not describe PRoot as a strong security sandbox.
- Never embed a developer-owned secret in the APK.
- Provider API format is an explicit non-secret preference. OpenAI-compatible requests use the Bearer authorization header; Anthropic Messages requests use `x-api-key` and the fixed `anthropic-version` header. Neither adapter places credentials in the URL or request body.


## Agent tool policy

- The first agent tools are limited to listing files, reading UTF-8 text, and writing UTF-8 files in the app-private workspace.
- Write operations are capped at 10 KB and require explicit confirmation in the UI.
- Workspace paths are canonicalized and checked against the workspace root to block ordinary traversal and symlink escapes.
- The workspace terminal uses a stateful tokenizer rather than a system shell. It rejects unmatched quotes, multiline input, unsupported backslash escapes, and shell-control operators outside quoted arguments; quoted operator characters are treated as data.

- Agent file-read/search/agent-write tools reject common secret files such as `.env`, `local.properties`, keystores, PEM/P12 files, and SSH key paths. User-initiated editor access remains separate. Terminal `grep` must use the agent-safe reader (not the editor's unrestricted reader), rejects blank needles, and caps output at 100 matches/20,000 characters so it cannot bypass the read policy or flood model context.


## GitHub workspace commit safety

Workspace synchronization is a two-phase operation: a read-only comparison produces a bounded diff, then the user explicitly confirms the reviewed plan. The app rechecks the remote branch head and local file contents immediately before committing. The branch ref update is non-force, and remote files not represented locally are preserved. Symbolic links, unsupported remote Git object modes, secret-like content, oversized files, binary files, truncated remote trees, and incomplete diff previews are rejected or skipped before commit.


Remote diff retrieval is additionally limited to 40 modified remote files and 2 MiB of combined old-file content so a large repository cannot make the preview retain an unbounded amount of data.

## Local Git commit safety

Local Git opens only the `.git` directory directly inside OrbitForge's app-private workspace; it never discovers or modifies a parent repository and has no remote push/pull path. Before a commit, the user reviews a bounded diff covering staged, unstaged, and untracked text changes. The commit stages only paths included in the reviewed plan (partial staging is not supported), validates relative paths and symlink boundaries, rejects Git pathspec metacharacters (`*`, `?`, brackets, and `:`) before staging, rejects sensitive filenames (including tracked deletions), secret-like current content, binary/non-UTF-8 files, conflicts, and oversized changes, then rechecks the reviewed snapshot and staged blob hashes. If an old/staged diff contains secret-like values, the whole preview is replaced with a redacted summary. Limits are 200 changed paths, 200 KiB per file, 2 MiB total working content, 30 diff files, and 200 KiB of diff output. Secret scanning is defense-in-depth, not a guarantee; the user should inspect the preview.


## Gradle Wrapper integrity

The checked-in Wrapper JAR is sourced from the official Gradle 8.11.1 tag and has SHA-256 `2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046`. The downloaded Gradle 8.11.1 binary distribution is pinned with `distributionSha256Sum`. CI uses `gradle/actions/setup-gradle@v4`, which validates Wrapper JAR checksums automatically. Review and verify both Wrapper files when upgrading Gradle.
