# Phase 12 — ZIP Archive Manager

## Implemented
- **`:feature:feature-archive`**:
  - `ZipArchiveManager` providing 64KB chunked streaming ZIP creation (`ZipOutputStream`), archive entry listing (`ZipFile` / `ZipInputStream`), and selective or full extraction.
  - Strict **Zip-Slip protection** via `PathValidator.canonicalizeUnderRoot(destDir, rawName)` prior to extracting any entry, throwing `SecurityException` on traversal (`../`, `..\\`, null bytes, or root-escape paths).
  - Supports opening local `.zip` files and `content://` URIs via DocumentFile/SAF `OpenDocument`.
  - `ArchiveViewModel` and `ArchiveScreen` supporting search filter, multi-select entry checkboxes, Extract All / Extract Selected dialogs, and Create ZIP workflow with live progress indicator.
- **Navigation & Integration**:
  - Updated `FileManagerScreen` selection bar with "Open in Archive Manager" for `.zip` files and "Compress to ZIP" for selected files/folders.
  - Updated `MainActivity` route `archive?path={path}&sources={sources}` to pass archive path and compression source paths into `ArchiveScreen`.
- **Tests (`ZipArchiveManagerTest`)**:
  - Verified full round-trip create -> list -> extract all -> selective extract for files, nested folders, and empty folders.
  - Verified malicious `../../escaped.txt` ZIP entry triggers `SecurityException` and writes zero files outside extraction sandbox.

## Verification
- Command: `.\gradlew.bat assembleDebug :feature:feature-archive:testDebugUnitTest --console=plain`
- Result: `BUILD SUCCESSFUL`.
