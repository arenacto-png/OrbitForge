# Provider API formats

OrbitForge currently supports two wire formats behind the same chat and agent interfaces.

## OpenAI-compatible Chat Completions

- Default for existing installations; older preferences without an API-format value continue to use this format.
- The base URL is normalized to `/v1/chat/completions` (or an already-complete path).
- Requests use `Authorization: Bearer <key>`.
- Chat and agent messages use the OpenAI Chat Completions schema.

## Anthropic Messages

- Select **Anthropic Messages** in Settings → Provider API format.
- A host-only base URL such as `https://api.anthropic.com` resolves to `/v1/messages`; a `/v1` base resolves to `/v1/messages` as well.
- Requests use `x-api-key` and `anthropic-version: 2023-06-01`; the API key is never inserted into the URL or JSON body.
- Streaming text deltas are read from `content_block_delta` events.
- Agent tool schemas are translated from the app's internal OpenAI function definitions to Anthropic `tools/input_schema`. Anthropic `tool_use` response blocks are normalized into the app's existing strict tool-call parser; tool results are sent as `tool_result` content blocks in a user message.
- The adapter requests up to 4,096 output tokens for Anthropic requests because the Messages API requires `max_tokens`.

## Request diagnostics

Each chat or agent HTTP request receives a locally generated correlation ID. Diagnostic events record the operation, API format, phase, HTTP status when available, elapsed time, and exception class. The ID is included in HTTP failure messages so a user can match the error with the diagnostic report. It is not sent to the provider.

Trace events deliberately omit request URLs, headers, request/response bodies, and prompts. Provider HTTP errors now use bounded, redacted guidance; token usage is recorded only when the provider returns parseable counters. Request tracing does not itself imply either behavior.

## HTTP errors and usage

Common HTTP statuses map to actionable guidance. Error-body reads are bounded (64 KiB for streaming chat and 2 MiB for non-streaming agent requests); only selected request errors may include a redacted detail excerpt capped at 250 characters. Network guidance distinguishes timeouts, DNS failures, TLS failures, and connection failures without exposing raw exception messages.

OpenAI-compatible usage counters are recorded when included in a non-streaming response or streaming usage event. The parser also recognizes Anthropic input/output counter names and merges partial streaming counters when those events are present. Missing, malformed, negative, or out-of-range telemetry is ignored rather than failing a request.

## Scope and limitations

- This is an API-format adapter, not a provider SDK. It supports text chat and standard JSON-object tool use.
- Vision/image blocks, extended-thinking controls, provider-specific structured output, and beta headers are not exposed by the current UI.
- The API key remains in the encrypted credential store. Format selection, base URL, and model ID are non-secret preferences.
- Unit tests validate request construction, role/tool-history conversion, response normalization, and streaming event parsing. A real provider account, Android Studio sync, emulator UI, and physical-device behavior still require live verification.
