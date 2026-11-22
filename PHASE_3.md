# PHASE 3 — Universal Transfer Engine

## Scope Completed
- Implemented `PortlyTransferEngine` in `:transfer:transfer-engine`:
  - Concurrent Room-backed transfer queue (`defaultTransferConcurrency` default 3, plus per-protocol semaphore limits).
  - Strictly 64KB chunked streaming I/O (`ChecksumUtils.STREAM_BUFFER_SIZE`) with zero full-file byte array allocations.
  - `RollingSpeedTracker` calculating live rolling-window throughput (`bytes/sec`) and ETA.
  - Inline streaming SHA-256 checksum computation & protocol verification (`ProtocolError.ChecksumMismatch` on mismatch).
  - Resumable downloads via `.part` temporary files and protocol offset (`openInputStream(path, offset)`) plus resumable uploads.
  - Exponential backoff auto-retry for transient errors (`ProtocolError.isTransient`), plus manual `retry` from `FAILED`/`CANCELLED` back to `QUEUED`.
  - Overwrite conflict policies (`ASK`, `RENAME`, `SKIP`, `OVERWRITE_ALWAYS`) and duplicate detection by size + mtime (±2s).
  - Recursive folder upload/download queuing one entry per file under a shared `groupId`.
  - Room state persistence (`RoomTransferStore`) and history/activity recording, plus `TransferReconcileWorker` (WorkManager) and `NetworkTransferCoordinator` (auto-pause on network loss, auto-resume on reconnect).

## Verification
- Executed `.\gradlew.bat assembleDebug :transfer:transfer-engine:testDebugUnitTest --console=plain`.
- Result: `BUILD SUCCESSFUL` (865 tasks, all 9 tests in `TransferEngineTest` passed with `allWarningsAsErrors = true` on `:transfer:transfer-engine`).
