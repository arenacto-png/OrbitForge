# GitHub integration

OrbitForge can list repositories, create a repository initialized with a README, and upload the current app-private workspace as a single Git commit using GitHub's Git Trees/Commits/Refs API. The user must confirm before upload.

A user-provided GitHub token is encrypted with Android Keystore and is never written to diagnostic logs. Use a token with the minimum permissions required for repository listing/creation and contents write access for synchronization. Never paste a token into the chat or issue tracker.

Workspace sync limits uploads to 200 text files and 2 MiB total, skips oversized files, common build directories, known credential files, binary/non-UTF-8 files, and content matching common secret patterns. This is defense-in-depth, not a guarantee; inspect the workspace before publishing. Sync adds/updates files but does not delete remote files that are absent locally.


## Reviewed workspace sync

Before a workspace commit, OrbitForge reads the default branch tree and compares Git blob SHA-1 values for eligible local UTF-8 files. The preview lists only added and modified files; unchanged files are omitted. Modified files receive a bounded content diff. If the remote tree is truncated, a modified remote file is too large or non-text, a secret-like value is detected, or a complete preview cannot fit within the limit, the operation fails closed.

After confirmation, OrbitForge rechecks the remote branch head and each reviewed local file hash before creating a Git tree and commit. The branch ref is updated without force; a concurrent remote update therefore causes the commit to be rejected rather than overwriting the newer branch. Remote files absent from the local workspace are not deleted. Limits are 200 files, 200 KiB per file, 2 MiB total local content, 5,000 remote tree entries, 4 MiB per GitHub API response, and 12,000 characters for the combined diff preview.
