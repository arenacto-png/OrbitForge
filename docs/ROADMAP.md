# Roadmap

## Milestone 1 — Android shell and provider
- [x] Kotlin/Compose app shell and navigation
- [x] Provider settings and encrypted key storage
- [x] Streaming OpenAI-compatible chat
- [x] Local bounded chat history
- [x] App-private workspace and safe user-selected folder import/export
- [x] Diagnostic log, redaction, uncaught-crash traces, copy/share report
- [x] Agent mode with a bounded function-calling loop
- [x] Workspace list/read/search/write tools with user approval for writes
- [x] Basic text editor and in-app file creation
- [x] `SKILL.md` loader with starter skills
- [x] Basic provider and diagnostic redaction tests
- [ ] Verify Gradle sync/build in Android Studio
- [x] GitHub Actions CI workflow for unit tests and debug APK artifact
- [ ] Add Gradle wrapper and verify build in Android Studio

## Milestone 2 — Agent foundation
- [x] Unit tests for workspace path traversal and absolute-path rejection
- [ ] Add tests for symlink escapes, tool-call parsing and agent step limits
- [x] Bounded diff preview before agent writes; approve/reject supported
- [ ] Support structured multi-file patch application
- [ ] Add conversation/task cancellation and recovery tests
- [ ] Add provider-specific adapters and clearer usage/error diagnostics

## Milestone 3 — Developer workspace
- [x] Basic project file list and text editor
- [ ] Syntax highlighting, diff preview and apply/reject
- [ ] Search within workspace
- [x] GitHub token storage, repository listing, and repository creation
- [x] Push workspace files as a confirmed commit through GitHub Git Data API
- [ ] Git status/diff/commit support

## Milestone 4 — Runtime and extensibility
- [x] Limited app-private workspace terminal with command allowlist
- [ ] Optional Termux/remote runtime adapter
- [x] Skills (`SKILL.md`) loader with editable starter skills and size limits
- [x] Single remote MCP endpoint with tool discovery/calls and approval before every external tool call
- [ ] Optional remote executor for heavy builds
- [x] CI workflow for unit tests and debug APK artifact
- [ ] Release signing guidance and APK verification


See [`LIVE_TESTING.md`](LIVE_TESTING.md) for the first-build and on-device verification procedure.
