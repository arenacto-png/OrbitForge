# Workspace import/export

Projects can import a folder selected through Android's Storage Access Framework and export the app-private workspace back to that selected folder. Persisted folder access is stored as a URI preference.

Transfers are bounded to 200 files, 512 KiB per file, and 5 MiB total. Common build directories, local configuration, keystores, APK/AAB files, and certificate files are skipped. Import does not overwrite existing private workspace files. Export asks for confirmation and may overwrite matching files in the selected destination.


## Editor change review

The editor keeps the original buffer while you make changes. Selecting **Проверить изменения** opens a bounded preview (up to 80 lines and 6,000 characters, plus a truncation marker). **Применить** saves the reviewed buffer and **Отклонить** restores the original buffer. Dismissing the dialog without choosing either action returns to the editor without saving or discarding edits. This is a review aid, not a full unified diff; always inspect the target path before applying.


## Local Git

The Projects screen can initialize a local Git repository in the app-private workspace, show staged/unstaged/untracked status, and prepare a bounded diff before creating a local commit. The first branch is `main`; the user supplies a commit message and author name/email. A commit stages only paths shown in the reviewed diff and does not push to GitHub. Partial staging and remote push/pull are not supported. Sensitive paths (including tracked deletions), secret-like current content, symlinks, binary/non-UTF-8 files, conflicts, and oversized diffs are blocked. When an older staged/base version contains secret-like text, the full diff preview is replaced by a redacted summary; inspect the preview before confirming.
