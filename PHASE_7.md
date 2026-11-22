# PHASE 7 — FTPS Client & Server (TLS 1.2/1.3, Cert Generation & TOFU)

## Scope Completed
- Implemented `:protocol:protocol-ftps-client` (`CommonsFtpsProtocolClient`, `RoomTrustedCertStore`, `FtpsClientModule`):
  - Supports Explicit FTPS (mandatory default) and Implicit FTPS, configurable TLS 1.2/1.3 (`TLSv1.3`, `TLSv1.2`), `PBSZ 0` + `PROT P` encrypted data channels.
  - Validates server certificates against Android system store + user CA store + Room-pinned TOFU fingerprints (`CertificateManager.buildTrustManager`), surfacing `ProtocolError.CertificateUntrusted` with SHA-256 fingerprint for explicit user approval.
- Implemented `:protocol:protocol-ftps-server` (`ApacheFtpsProtocolServer`, `FtpsServerModule`):
  - Generates and persists self-signed PKCS12 certificates via `CertificateManager.generateOrLoadServerKeystore` (BouncyCastle X.509 v3 with SAN IPs).
  - Enforces TLS 1.2/1.3 on both control and passive data channels, `PathValidator` root sandboxing, SAF tree support, per-user permissions, client tracking + kick, and redacted logs.
- Added one-tap TOFU SHA-256 certificate trust approval & reconnect in `RemoteBrowserPane` / `RemoteBrowserViewModel`.

## Verification
- Executed `.\gradlew.bat assembleDebug :protocol:protocol-ftps-client:testDebugUnitTest :protocol:protocol-ftps-server:testDebugUnitTest --console=plain`.
- Result: `BUILD SUCCESSFUL` (894 tasks; `FtpsClientIntegrationTest` and `FtpsServerIntegrationTest` passed).
