# Workspace import/export

Projects can import a folder selected through Android's Storage Access Framework and export the app-private workspace back to that selected folder. Persisted folder access is stored as a URI preference.

Transfers are bounded to 200 files, 512 KiB per file, and 5 MiB total. Common build directories, local configuration, keystores, APK/AAB files, and certificate files are skipped. Import does not overwrite existing private workspace files. Export asks for confirmation and may overwrite matching files in the selected destination.
