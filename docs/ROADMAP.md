# Roadmap

## Milestone 1 — Android shell and provider
- [x] Kotlin/Compose app shell and navigation
- [x] Provider settings and encrypted key storage
- [x] Streaming OpenAI-compatible chat
- [x] Local bounded chat history
- [x] App-private workspace and safe user-selected folder import/export
- [x] Stage SAF export replacements before promotion and preserve prior destination data on failed writes
- [x] Diagnostic log, redaction, uncaught-crash traces, copy/share report
- [x] Agent mode with a bounded function-calling loop
- [x] Workspace list/read/search/write tools with user approval for writes
- [x] Basic text editor and in-app file creation
- [x] `SKILL.md` loader with starter skills
- [x] Basic provider and diagnostic redaction tests
- [ ] Verify Gradle sync/build in Android Studio
- [x] GitHub Actions CI workflow for unit tests and debug APK artifact
- [x] Add Gradle Wrapper 8.11.1 with pinned distribution SHA-256 and use it in CI
- [x] Keep GitHub Actions on maintained Node 24-compatible action majors

## Milestone 2 — Agent foundation
- [x] Unit tests for workspace path traversal and absolute-path rejection
- [x] Add tests for symlink escapes, strict tool-call parsing and agent step limits
- [x] Block sensitive-file access through in-workspace symlink aliases for agent reads, writes, and search
- [x] Filter agent-facing file listings and terminal traversal for sensitive paths and canonical symlink aliases
- [x] Validate existing file content before exposing agent write-approval diffs
- [x] Bounded diff preview before agent writes; approve/reject supported
- [ ] Support structured multi-file patch application
- [x] Add conversation/task cancellation and recovery tests (persist cancelled result and preserve partial chat output)
- [x] Add provider-specific request/response adapters for OpenAI-compatible Chat Completions and Anthropic Messages (streaming chat and agent tool calls)
- [x] Add local provider request correlation IDs and duration/status events without logging prompts or credentials
- [ ] Add clearer provider usage/error diagnostics (bounded error details and token-usage accounting)

## Milestone 3 — Developer workspace
- [x] Basic project file list and text editor
- [x] Basic syntax highlighting for common source/config formats with a large-file plain-text fallback
- [x] Editor diff preview with explicit apply/reject and bounded output
- [x] Atomic per-file replacement for app-private agent/editor saves (same-directory temp file; fail closed if atomic move is unavailable)
- [x] Search within workspace (file names and text contents; sensitive content filtered; results open in editor)
- [x] GitHub token storage, repository listing, and repository creation
- [x] Push workspace files as a confirmed commit through GitHub Git Data API
- [x] Local Git status/diff/commit support (app-private root, bounded reviewed diff, commit only reviewed paths; no remote push)
- [x] Reject Git pathspec metacharacters in reviewed local commit paths before staging
- [x] GitHub workspace sync preview with added/modified/unchanged classification and stale-plan checks (remote GitHub sync; not local Git)
- [x] Apply the shared sensitive-path policy to local Git and GitHub workspace sync

## Milestone 4 — Runtime and extensibility
- [x] Limited app-private workspace terminal with command allowlist
- [x] Stateful terminal argument parser with quote validation and shell-control rejection tests
- [x] Enforce agent-safe file-read policy and bounded output for terminal `grep`
- [ ] Optional Termux/remote runtime adapter
- [x] Skills (`SKILL.md`) loader with editable starter skills and size limits
- [x] Single remote MCP endpoint with tool discovery/calls, approval before every external tool call, and shared 2 MiB HTTP response bounds for MCP and non-streaming agent-provider calls
- [ ] Optional remote executor for heavy builds
- [x] CI workflow for unit tests and debug APK artifact
- [x] Release signing guidance and CI verification of debug APK signature and SHA-256 (release keys remain manual and private)


See [`LIVE_TESTING.md`](LIVE_TESTING.md) for the first-build and on-device verification procedure.
