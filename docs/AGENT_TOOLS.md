# Initial agent tools

The first agent tool set is intentionally small:

- `list_files(path?)`: list entries inside the app-private workspace.
- `read_file(path)`: read a UTF-8 text file; model context is capped at 40,000 characters.
- `write_file(path, content)`: write a UTF-8 file up to 10 KiB, only after user approval; larger edits can be made by the user in the editor.
- `apply_patch(files)`: apply exact, unique text replacements to 1–8 existing non-sensitive files (up to 8 edits per file, 24 edits total). Each resulting file is limited to 10 KiB. The app validates every path and result, shows one combined bounded diff, asks once for approval, rechecks file contents before writing, and attempts rollback only when the current file still matches the patch output. It does not create or delete files.
- `run_command(command)`: execute one command from the limited workspace terminal. Mutating commands such as `mkdir` and `touch` require approval and reject sensitive path names or canonical aliases; arbitrary shell commands and shell operators are rejected. A stateful tokenizer validates quotes and rejects multiline input, backslash escapes, and shell-control syntax outside quoted arguments; quoted operator characters are literal data.

The workspace is stored under the app-private `filesDir/orbitforge-workspace` directory. It is not yet an arbitrary user-selected project folder. Paths are canonicalized and constrained to the workspace root to block `..` traversal and symlink escapes.

The agent loop is bounded to eight model rounds and at most eight tool calls per round. The complete tool-call batch is validated before the first tool runs: call IDs must be present and unique, each call must declare `type: function`, names must match the function definitions sent to the provider (the allowlist is derived from those definitions to prevent drift), and arguments must be bounded JSON objects. Malformed or unknown calls fail closed; malformed arguments are never silently replaced with an empty object. Tool calls returned in the final permitted model round are not executed, so the agent does not perform side effects it cannot report back to the model. A provider may accept chat completions but not tool calls; in that case normal chat should still be used and agent mode may return a provider error. Remote MCP and non-streaming agent-provider HTTP responses are limited to 2 MiB before parsing; individual MCP text results are further capped at 20,000 characters.


## Skills

On first agent use, OrbitForge creates two editable starter skills under `workspace/skills/`:
- `android-kotlin/SKILL.md`
- `code-review/SKILL.md`

Up to five `SKILL.md` files of at most 12 KiB each are included as guidance in the agent system context. They are explicitly treated as untrusted content and cannot override application safety rules. Edit them in the Projects file editor.
