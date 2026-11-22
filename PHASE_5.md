# PHASE 5 — FTP Server, Server Dashboard & Foreground Service

## Scope Completed
- Implemented `:protocol:protocol-ftp-server`:
  - `ApacheFtpProtocolServer` wrapping Apache FtpServer (`internal` to the module, exposed only via `ProtocolServer` bound in `FtpServerModule`).
  - `PortlyFtpFileSystemFactory` supporting both local `File` roots (hardened with `PathValidator.canonicalizeUnderRoot`) and SAF `DocumentFile` trees, enforcing global `readOnly` and per-user `READ`/`WRITE`/`DELETE`/`MKDIR` permissions.
  - `PortlyFtpUserManager` supporting anonymous and password-authenticated accounts with constant-time password verification and per-IP connection limits.
  - Real-time connected clients tracking via `DefaultFtplet`, forced session disconnect (`kickClient`), and redacted server logs via `LogRedactor`.
- Implemented `PortlyServerManager` and `QrCodeGenerator` (ZXing) in `:network:network-manager`.
- Implemented `ServerForegroundService` (`dataSync` foreground service) with persistent notification showing running server(s), port(s), client count, tap-to-open Servers screen, and per-server Stop actions.
- Implemented full `ServersScreen` in `:feature:feature-servers` (independent server start/stop switches, SAF root picker, port/connection/auth/permission configuration gated by optional BiometricPrompt, URL copy/share/QR code dialog, live Clients panel with Kick button, and redacted Logs tab).

## Verification
- Executed `.\gradlew.bat assembleDebug :protocol:protocol-ftp-server:testDebugUnitTest --console=plain`.
- Result: `BUILD SUCCESSFUL` (873 tasks; `FtpServerIntegrationTest` verified auth, permissions, path-traversal blocking, and log redaction).
