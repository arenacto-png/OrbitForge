# GitHub integration

OrbitForge can list repositories, create a repository initialized with a README, and upload the current app-private workspace as a single Git commit using GitHub's Git Trees/Commits/Refs API. The user must confirm before upload.

A user-provided GitHub token is encrypted with Android Keystore and is never written to diagnostic logs. Use a token with the minimum permissions required for repository listing/creation and contents write access for synchronization. Never paste a token into the chat or issue tracker.

Workspace sync limits uploads to 200 text files and 2 MiB total, skips oversized files, common build directories, known credential files, binary/non-UTF-8 files, and content matching common secret patterns. This is defense-in-depth, not a guarantee; inspect the workspace before publishing. Sync adds/updates files but does not delete remote files that are absent locally.
