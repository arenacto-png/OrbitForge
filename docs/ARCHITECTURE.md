# Architecture

## Implemented structure

- `MainActivity`: Compose navigation and screens for Agent, Projects, Editor, Terminal, Settings, and Diagnostics.
- `AppStorage`: non-secret preferences, bounded chat history, and selected external-folder URI.
- `security/SecretStore`: AES/GCM-encrypted user credentials with Android Keystore keys (API, GitHub, MCP).
- `ai/OpenAiCompatibleClient`: streaming Chat Completions over SSE.
- `agent/AgentEngine`: bounded tool-calling loop with cancellation, status updates, and explicit approval callbacks.
- `agent/WorkspaceTools` + `WorkspacePathPolicy`: app-private workspace operations and canonical-path boundaries.
- `agent/WorkspaceTerminal`: allowlisted commands only; no arbitrary shell.
- `agent/SkillManager`: loads editable `SKILL.md` guidance as untrusted reference material.
- `agent/WorkspaceTransfer`: bounded import/export through Android Storage Access Framework.
- `github/GitHubRepositoryClient`: list/create repositories and confirmed workspace sync as one Git Data API commit.
- `mcp/McpClient`: one configured remote HTTP MCP endpoint; every tool call requires user approval.
- `diagnostics/DiagnosticLog`: bounded persistent logs, redaction, and crash traces.

## Principles

1. Model output and tool arguments are untrusted input.
2. Tool permissions are explicit and workspace-scoped.
3. Agent writes are capped at 10 KB and require confirmation.
4. Terminal commands come from an allowlist and do not invoke a system shell.
5. External MCP calls and GitHub publishing require explicit user action.
6. API credentials are encrypted at rest and must never be committed or logged.
7. Long-running network calls support cancellation and timeouts where practical.

## Future decomposition

The folders currently organize code within the `app` module. Separate Gradle modules for domain, data, runtime, and feature screens can be extracted when the codebase and test suite justify that complexity. Local Git status/diff, syntax-aware editing, remote execution, and full MCP transport support remain future work.
