# PHASE 9 — Connection Manager Polish, Import/Export & Quick Connect

## Scope Completed
- Enhanced `:feature:feature-connections` (`ConnectionsScreen`, `ConnectionsViewModel`, `ProfilePortabilityCodec`):
  - Full Add & Edit connection dialogs supporting protocol-specific fields (FTP passive/active, FTPS Explicit/Implicit, TLS 1.2/1.3 vs TLS 1.3-only, SFTP PEM private keys + passphrases, WebDAV HTTPS toggle, SMB share/domain, custom root path, timeout, and notes).
  - Live `Test Connection` action running `ProtocolClient.testConnection` and reporting round-trip latency (ms), server banner, and root directory or mapped `PortlyErrorCard`.
  - Versioned JSON profile export/import (`ProfileExportBundle` v1) via SAF `CreateDocument` / `OpenDocument`: excludes credentials by default, or optionally encrypts credentials using 600,000-iteration PBKDF2-HMAC-SHA256 + AES-256-GCM (`Pbkdf2Crypto`), never exporting plaintext secrets.
  - Profile search/filter, favorites toggle, SSH Known Hosts manager, and Trusted TLS Certificates manager.
- Added Quick Connect bar (`protocol://user@host:port/path`) and tap-to-connect Recent Profiles on `HomeScreen`.

## Verification
- Executed `.\gradlew.bat assembleDebug :feature:feature-connections:testDebugUnitTest --console=plain`.
- Result: `BUILD SUCCESSFUL` (881 tasks; `ProfilePortabilityCodecTest` verified credential-free export, 600k PBKDF2 encrypted credential round-trip, and quick-connect URL parsing).
