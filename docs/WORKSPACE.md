# Workspace import/export

Projects can import a folder selected through Android's Storage Access Framework and export the app-private workspace back to that selected folder. Persisted folder access is stored as a URI preference.

Transfers are bounded to 200 files, 512 KiB per file, and 5 MiB total. Common build directories, local configuration, keystores, APK/AAB files, and certificate files are skipped. Import does not overwrite existing private workspace files. Export asks for confirmation and may overwrite matching files in the selected destination.


## Editor change review

The editor keeps the original buffer while you make changes. Selecting **Проверить изменения** opens a bounded preview (up to 80 lines and 6,000 characters, plus a truncation marker). **Применить** saves the reviewed buffer, **Отклонить** restores the original buffer, and **Вернуться** closes the preview without saving or discarding edits. This is a review aid, not a full unified diff; always inspect the target path before applying.
