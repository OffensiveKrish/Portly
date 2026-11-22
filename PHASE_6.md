# PHASE 6 — SFTP Client (SSHJ) & SSH Key Management

## Scope Completed
- Implemented `:protocol:protocol-sftp-client` (`SshjSftpProtocolClient`, `PortlySshHostKeyVerifier`, `SftpClientModule`):
  - Strictly `internal` SSHJ wrapper bound via `@IntoMap @ProtocolClientKey(ProtocolType.SFTP)`.
  - Supports password authentication, PEM private key + passphrase authentication, custom ports, and timeouts.
  - Room-backed `known_hosts` verification (`KnownHostDao`) pinning first-seen SHA-256 host fingerprints and surfacing `ProtocolError.HostKeyMismatch` with expected vs actual fingerprints on key change.
  - Full SFTP operations: `list`, `stat`, `mkdir`, `rename`, recursive `delete`, `chmod`, streaming `openInputStream` with ranged `ReadAheadRemoteFileInputStream(16, offset)`, `openOutputStream`, and optional SSH `exec` checksums.
- Enhanced `ConnectionsScreen` in `:feature:feature-connections` with SFTP private-key file picker / PEM editor + passphrase field and an SSH Known Hosts manager dialog.

## Verification
- Executed `.\gradlew.bat assembleDebug :protocol:protocol-sftp-client:testDebugUnitTest --console=plain`.
- Result: `BUILD SUCCESSFUL` (877 tasks; `SftpClientIntegrationTest` verified password round-trip, ranged resume, RSA private-key auth, and `HostKeyMismatch` against embedded Apache MINA SSHD).
