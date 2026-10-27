# OrbitForge implementation status

## Implemented in source (not yet live-verified)

- Native Compose UI: agent/chat, projects, text editor, limited terminal, settings, diagnostics.
- OpenAI-compatible streaming chat, provider settings, and an in-app connection test.
- Local conversation history and separate encrypted API/GitHub/MCP credentials using Android Keystore.
- Cancelled chat/agent requests replace the pending assistant placeholder with a durable cancellation result; partial chat output is retained with an incomplete-response marker, and the history is persisted.
- Bounded tool-calling agent with file listing, reading, search, writes with approval, and allowlisted terminal commands.
- Workspace screen searches file names and text contents asynchronously, limits displayed results, filters sensitive paths and secret-like content through the existing search policy, and opens matching files in the editor.
- The file editor applies lightweight syntax coloring to common code/config formats without changing text or cursor offsets; files over 100,000 characters fall back to plain text.
- Before saving editor changes, the user can inspect a bounded diff and explicitly apply or reject the edit; rejecting restores the original buffer. The preview is capped to 80 lines and 6,000 characters (plus a truncation marker).
- Editable `SKILL.md` starter skills with bounded loading and untrusted-skill policy.
- Remote HTTP MCP initialization, tool discovery, and tool calls; each external call requires confirmation.
- GitHub repository listing/creation and one-commit workspace sync, with explicit confirmation and basic secret filtering.
- SAF folder picker with bounded import/export, private workspace editor, and persistent folder permissions.
- Diagnostic report copy/share with API/GitHub/MCP token redaction and compact crash traces.
- GitHub Actions workflow for unit tests, debug APK, Android emulator UI smoke tests, and test report artifacts.
- Unit tests for provider URL rules, diagnostic redaction, workspace path traversal, safe parsing of workspace search results, syntax highlighting spans/large-file fallback, and bounded diff previews.

## Still requires live verification

- Android Gradle sync, compile, unit tests, emulator UI tests, and APK installation. The new CI checks are configured but have not yet run against a GitHub repository.
- Real API provider compatibility, streaming, cancellation, and tool-call support.
- GitHub token scopes, repository creation, and workspace push.
- MCP compatibility with the target server's protocol/authentication variant.
- SAF import/export on the user's Android file provider.
- Review a copied diagnostic report to confirm it contains no sensitive data.

## Known scope limits

- Terminal is an allowlisted workspace command runner, not a full Linux shell. Termux/remote execution is not yet implemented.
- Local Git status/diff/commit operations are not implemented; GitHub synchronization uses the REST Git Data API.
- MCP support is remote HTTPS only; local stdio servers and some OAuth flows are not supported.
- Workspace sync uploads the app-private workspace contents, not this source archive automatically.
- Basic secret scanning is defense-in-depth, not a guarantee. Inspect files before importing/exporting or pushing them.
