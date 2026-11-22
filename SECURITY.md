# Portly Security Architecture & Verification

## 1. Threat Model & Trust Boundaries
Portly spans local device storage, LAN/WAN protocol clients, embedded LAN servers, and removable/SAF document trees:
- **Path Traversal & Zip-Slip Boundary**:
  - All server requests (FTP, FTPS, HTTP, HTTPS) and ZIP extraction entries pass through [`PathValidator.canonicalizeUnderRoot`](core/core-common/src/main/kotlin/com/portly/core/common/PathValidator.kt), rejecting null bytes, `..` parent escapes, backslash bypasses, and symlink escapes before disk access.
- **Credential & Private Key Storage**:
  - Client passwords, SSH private keys, SSH key passphrases, and server user/keystore credentials are encrypted using [`KeystoreCredentialCipher`](core/core-security/src/main/kotlin/com/portly/core/security/KeystoreCredentialCipher.kt) (**AES-256-GCM** backed by `AndroidKeyStore`, with hardware-backed `EncryptedSharedPreferences` fallback only when Keystore is unavailable) and persisted as ciphertexts in [`CredentialVault`](core/core-database/src/main/kotlin/com/portly/core/database/CredentialVault.kt).
  - Plaintext credentials are **never** stored in Room tables, DataStore preferences, default profile exports, or application logs.
  - Optional encrypted profile portability exports use [`Pbkdf2Crypto`](core/core-security/src/main/kotlin/com/portly/core/security/Pbkdf2Crypto.kt) (**PBKDF2WithHmacSHA256**, **600,000 iterations**, unique 16-byte salt, **AES-256-GCM**).
- **Network & Transport Security**:
  - `network_security_config.xml` sets `cleartextTrafficPermitted="false"` and Manifest sets `android:usesCleartextTraffic="false"` and `android:allowBackup="false"`.
  - Plain FTP and HTTP display explicit unencrypted warnings and are never substituted silently for TLS-enabled protocols.
  - FTPS (client & server), HTTPS server, and WebDAV over HTTPS enforce TLS 1.2/1.3 via `CertificateManager` with TOFU SHA-256 pinning (explicit user confirmation required for untrusted or changed certificates) and user-imported CA support.
  - SFTP enforces strict SSH host key verification (`PortlySshHostKeyVerifier`, backed by Room `known_hosts`) with hard-fail on host key mismatch under `HostKeyMode.STRICT`.
  - Embedded HTTP/HTTPS servers apply a per-client authentication rate limit (5 failed attempts -> 30-second `429 Too Many Requests` cooldown) and `HttpOnly; SameSite=Lax` (`+ Secure` on HTTPS) session cookies.
- **Server Bind & Execution Safety**:
  - Servers default to LAN IPv4 + `127.0.0.1` (`lanOnlyBind = true`) unless user explicitly opts in and MDM policy (`allow_public_server_bind`) permits it.
  - Downloaded files are **never** automatically executed or installed; previews open read-only inside sandboxed viewers or explicit user-triggered `FileProvider` `ACTION_VIEW` intents.
- **Audit Logging & Redaction**:
  - [`LogRedactor`](core/core-security/src/main/kotlin/com/portly/core/security/LogRedactor.kt) strips FTP `PASS` commands, `Authorization` headers, query tokens, PEM private keys, and external filesystem paths before server/diagnostic logs are recorded or exported.

## 2. OWASP MASVS Mapping (Self-Assessment)
| MASVS Category | Implementation in Portly |
|---|---|
| **MASVS-STORAGE** | Keystore AES-256-GCM `CredentialVault`, `allowBackup="false"`, sanitized diagnostic export (`DiagnosticBundleExporter`), no secret logging (`LogRedactor`). |
| **MASVS-CRYPTO** | AES-256-GCM (no ECB/CBC), PBKDF2-HMAC-SHA256 (600k iterations), TLS 1.2/1.3, 64KB streaming SHA-256 file integrity checks (`ChecksumUtils`), BouncyCastle X.509/PKCS12 (`CertificateManager`). |
| **MASVS-AUTH** | Optional `BiometricPrompt` lock gate at launch and server settings changes; brute-force 429 backoff on HTTP/HTTPS server logins; 60s single-use 192-bit peer pairing tokens (`PeerPairingManager`). |
| **MASVS-NETWORK** | `cleartextTrafficPermitted="false"`, SSH `known_hosts` verification, TLS TOFU pinning (`TrustedCertDao`), SMB2.02 minimum default (`SMB1` disabled unless opted in and permitted by MDM). |
| **MASVS-CODE** | Strict path canonicalization (`PathValidator`), Zip-Slip rejection (`ZipArchiveManager`), zero native code/JNI, clean standard R8 shrinking (`proguard-rules.pro`). |
| **MASVS-PRIVACY** | Zero ads, zero analytics SDKs, zero unauthorized network calls, configurable retention pruning (`logRetentionDays`), clear Audit Disclosure UI. |

## 3. Observed Security & Integration Test Evidence
Verified on JVM test runner + Android Gradle Plugin `9.5.0` via:
```powershell
.\gradlew.bat assembleDebug assembleRelease testDebugUnitTest --console=plain
```
**Observed result**: **18 test suites, 46 tests, 0 failures, 0 errors, 0 skipped**:
- `PathValidatorTest` (6 tests): traversal, null bytes, backslash bypass, symlink/root canonical boundary.
- `SecurityUnitTest` (5 tests): PBKDF2 600k round-trip & wrong-passphrase failure, `LogRedactor` credential/PEM scrubbing, X.509 SHA-256 fingerprint, `EnterprisePolicyManager` restriction parsing.
- `ZipArchiveManagerTest` (2 tests): round-trip ZIP create/list/extract + Zip-Slip `../../escaped.txt` rejection.
- `ProfilePortabilityCodecTest` (3 tests): credential-stripped export, PBKDF2-encrypted credential export/import, URL quick-connect parsing.
- `DiagnosticBundleExporterTest` (1 test): password/Bearer/private-key redaction verified in diagnostic JSON output.
- `PeerPairingManagerTest` (2 tests): 192-bit token single-use consumption + 60-second expiry enforcement.
- Protocol integration suites (`FtpClientIntegrationTest`, `FtpServerIntegrationTest`, `FtpsClientIntegrationTest`, `FtpsServerIntegrationTest`, `SftpClientIntegrationTest`, `HttpServerIntegrationTest`, `HttpsServerIntegrationTest`, `WebDavClientIntegrationTest`, `SmbClientConfigTest`, `TransferEngineTest`): see `COMPATIBILITY.md`.

*(Note: Hardware Keystore, BiometricPrompt UI, and on-device SELinux/scoped-storage runtime enforcement require physical device/emulator instrumentation testing — marked UNTESTED in `COMPATIBILITY.md`.)*

## 4. Release Artifact Integrity & Signing Keys

Official Portly production releases are distributed exclusively via [GitHub Releases](https://github.com/upgrdify/portly/releases) and are signed with a permanent cryptographic release key.

### Official Release Signing Identity
- **Algorithm**: RSA 4096-bit (or EC P-256) with SHA-256
- **Subject DN**: `CN=Portly Release, OU=Mobile, O=Portly Project, C=US`
- **Signing Scheme**: Android APK Signature Scheme v2 & v3
- **Expected SHA-256 Certificate Fingerprint**: `D32A8FB29CD4C5968F5C7B9E63FC9ED324EFAA676D62775ADB3AFD92080AEBAE`

### In-App Update Verification Pipeline
Portly's built-in update engine executes a strict multi-tier verification pipeline before any update is handed to the Android Package Installer:
1. **Package ID Matching**: Confirms the archive's package name exactly matches `com.portly.app`.
2. **Anti-Downgrade Check**: Verifies that the archive's `versionCode` is strictly greater than the currently installed `versionCode`.
3. **Cryptographic Checksum**: Computes the SHA-256 hash of the downloaded APK using `ChecksumUtils` and compares it against the signed `SHA256SUMS.txt` / `update-metadata.json` published in the GitHub Release.
4. **Certificate Fingerprint Matching**: Inspects the APK's signing certificate via Android `PackageManager` and verifies that the certificate SHA-256 fingerprint matches the running app's signing certificate. If a mismatch is detected, the installation is blocked immediately with a `Signature mismatch` error.

## 5. Vulnerability Reporting

If you discover a security vulnerability in Portly:
1. Do **not** open a public GitHub issue for sensitive security vulnerabilities.
2. Email the maintainers privately at `security@portly.org` (or via GitHub Private Vulnerability Reporting).
3. Include detailed steps to reproduce, affected versions, and any proof-of-concept material.
4. We acknowledge reports within 48 hours and aim to release patched builds with corresponding source code promptly.
- APK SHA-256: `D32A8FB29CD4C5968F5C7B9E63FC9ED324EFAA676D62775ADB3AFD92080AEBAE`
- Signed with APK Signature Scheme v2 (`apksigner verify --verbose --print-certs` passed with verification test key `CN=Portly Open Source Release Verification`; replace `app/release-test.keystore` via `PORTLY_KEYSTORE_*` env vars for production builds).

## 5. Vulnerability Disclosure Process
- Report suspected vulnerabilities privately to the repository maintainers (`SECURITY.md` contact / private advisory tracker) with reproduction steps, affected protocol/module, and Portly version.
- Do not publicly disclose exploit details prior to coordinated patch verification and release signing.
