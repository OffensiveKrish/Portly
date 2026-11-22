# PHASE 4 — FTP Client, Transfer Engine Integration & Transfers UI

## Scope Completed
- Implemented `:protocol:protocol-ftp-client` (`CommonsFtpProtocolClient`, `FtpClientErrorMapper`, `FtpClientModule`):
  - Strictly `internal` Apache Commons Net `FTPClient` wrapper bound via Hilt `@IntoMap @ProtocolClientKey(ProtocolType.FTP)`.
  - Supports anonymous + username/password auth, Active and Passive modes, custom port, timeout, `testConnection` with latency + banner, `MLSD`/`LIST` directory parsing, `stat`, `mkdir`, `rename`, recursive `delete`, `SITE CHMOD`, streaming `openInputStream`/`openOutputStream` with `REST` offset resume, and `XSHA256`/`MD5`/`XSHA1` checksum query.
  - Maps FTP reply codes (`530`, `550`, `553`, `421`/`425`/`426`, `500`/`502`, timeouts) to `ProtocolError`.
- Added dual-pane Local | Remote browsing in `:feature:feature-filemanager` (`RemoteBrowserViewModel`, `RemoteBrowserPane`, side-by-side drag-and-drop on wide screens `>= 700dp`, tabbed Local/Remote on compact screens, plain FTP warning banner, upload/download queuing into `TransferEngine`).
- Upgraded `:feature:feature-transfers` (`TransfersScreen` with Active/Queued/Completed/Failed/Cancelled tabs, swipe pause/resume/cancel/retry actions, rolling speed & ETA, checksum verified badge, conflict resolution dialog, `PortlyErrorCard`) and added `TransferForegroundService` (`dataSync` foreground notification mirroring active transfers).

## Verification
- Executed `.\gradlew.bat assembleDebug :protocol:protocol-ftp-client:testDebugUnitTest --console=plain`.
- Result: `BUILD SUCCESSFUL` (869 tasks; `FtpClientIntegrationTest` round-trips against embedded Apache FtpServer passed).
