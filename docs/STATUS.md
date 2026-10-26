# OrbitForge implementation status

## Implemented in source (not yet live-verified)

- Native Compose UI: agent/chat, projects, text editor, limited terminal, settings, diagnostics.
- OpenAI-compatible streaming chat, provider settings, and an in-app connection test.
- Local conversation history and separate encrypted API/GitHub/MCP credentials using Android Keystore.
- Bounded tool-calling agent with file listing, reading, search, writes with approval, and allowlisted terminal commands.
- Editable `SKILL.md` starter skills with bounded loading and untrusted-skill policy.
- Remote HTTP MCP initialization, tool discovery, and tool calls; each external call requires confirmation.
- GitHub repository listing/creation and one-commit workspace sync, with explicit confirmation and basic secret filtering.
- SAF folder picker with bounded import/export, private workspace editor, and persistent folder permissions.
- Diagnostic report copy/share with API/GitHub/MCP token redaction and compact crash traces.
- GitHub Actions uses Gradle 8.11.1 via the Gradle setup action, runs JVM unit tests, and builds/uploads the debug APK before emulator testing. The Android emulator UI smoke-test job is configured; previous attempts failed before boot because the hosted runner had about 6.9 GB free while the emulator required about 7.3 GB. The workflow now removes unrelated preinstalled toolchains and checks for at least 9 GiB free space before starting the emulator; rerun validation is pending.
- Unit tests for provider URL rules, diagnostic redaction, and workspace path traversal.

## Still requires live verification

- Emulator UI smoke tests must complete successfully after the runner disk-space cleanup change.
- APK installation and interaction on the user's physical Android device remain unverified.
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
