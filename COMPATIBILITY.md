# Portly Compatibility Matrix

Per strict accuracy reporting rules, this document distinguishes between **directly executed embedded/JVM test suites** (observed passing in this workspace) and **external server/device/enterprise endpoint combinations not executed in this offline environment** (marked `UNTESTED`).

## 1. Protocol Interoperability (Observed Test Runs)

| Protocol / Engine | Mode | Library | Test Harness & Scope | Status |
|---|---|---|---|---|
| **FTP Client** | Passive / Stream / `REST` resume | `commons-net 3.13.0` | Embedded Apache FtpServer round-trip upload/download, offset resume, `530` error mapping (`FtpClientIntegrationTest`) | **PASSED (2/2)** |
| **FTP Server** | Active listener, sandboxed local FS | `ftpserver-core 1.2.1` | Embedded Apache Commons Net client round-trip & path-traversal rejection (`FtpServerIntegrationTest`) | **PASSED (1/1)** |
| **FTPS Client** | Explicit TLS (`PBSZ 0`, `PROT P`), TOFU | `commons-net 3.13.0` | Embedded TLS Apache FtpServer TOFU `CertificateUntrusted` -> trusted pin -> encrypted transfer (`FtpsClientIntegrationTest`) | **PASSED (1/1)** |
| **FTPS Server** | Explicit & Implicit TLS, BouncyCastle PKCS12 | `ftpserver-core 1.2.1` + `bcpkix 1.86` | `FTPSClient` handshake, control/data protection, round-trip (`FtpsServerIntegrationTest`) | **PASSED (1/1)** |
| **SFTP Client** | Password, PEM Key + Passphrase, `known_hosts` | `sshj 0.41.2` | Embedded Apache MINA SSHD upload/download/ranged-read & host-key mismatch enforcement (`SftpClientIntegrationTest`) | **PASSED (2/2)** |
| **HTTP Server** | Range `206`, Auth `401`/`429`, Multipart upload | `ktor-server-cio 3.6.1` | Real CIO loopback server auth, range read, upload & path-traversal `403` (`HttpServerIntegrationTest`) | **PASSED (2/2)** |
| **HTTPS Server** | TLS 1.2/1.3 `SSLServerSocket` -> CIO loopback | `ktor-server-cio 3.6.1` | TLS client handshake (`TrustManager` verified) & HTTPS round-trip (`HttpsServerIntegrationTest`) | **PASSED (1/1)** |
| **WebDAV Client** | `PROPFIND`, `MKCOL`, `MOVE`, `DELETE`, Range, `PUT` | `okhttp 5.5.1` | `MockWebServer` directory listing, Digest challenge cache, streaming PUT & download (`WebDavClientIntegrationTest`) | **PASSED (2/2)** |
| **SMB Client** | SMB 2.02–3.1.1 default, opt-in SMB1 | `jcifs-ng 2.1.10` | Config property checks, share URL formatting, NT status error mapping (`SmbClientConfigTest`) | **PASSED (4/4 config/unit)**; live NAS SMB server **UNTESTED** |
| **Transfer Engine** | 64KB chunk streaming, `.part` resume, retry, SHA-256 | Core Kotlin IO | Pause/resume/cancel/retry, overwrite policies, recursive folder queueing (`TransferEngineTest`) | **PASSED (9/9)** |
| **ZIP Archive** | Streaming ZIP create/list/extract + Zip-Slip block | `java.util.zip` + `PathValidator` | Round-trip + `../../escaped.txt` rejection (`ZipArchiveManagerTest`) | **PASSED (2/2)** |

## 2. Android Platform & Hardware Matrix
- **Compile / Target SDK**: Android API 37 (`compileSdk = 37`, `targetSdk = 37`)
- **Minimum Supported SDK**: Android 8.0 Oreo (`minSdk = 26`)
- **UI Instrumentation / Physical Device Tests**:
  - Android 10–15 Scoped Storage (`MANAGE_EXTERNAL_STORAGE` / SAF `DocumentFile` USB OTG) runtime UX: **UNTESTED** (verified at compile/manifest/API level; no Android device/emulator attached in this environment)
  - `BiometricPrompt` hardware sensor prompt: **UNTESTED**
  - Two-device Wi-Fi LAN NSD (`_smb._tcp.`, `_portly._tcp.`) & QR pairing: **UNTESTED** (token codec/expiry/single-use unit tests passed)

## 3. External Server, NAS, MDM & Endpoint Security Matrix
*(Verified architecture & policy hooks in code/docs; live third-party product validation is `UNTESTED` here.)*
- FileZilla Server / vsftpd / ProFTPD / Pure-FTPd: **UNTESTED**
- OpenSSH (`sshd`) Linux / macOS / Windows OpenSSH: **UNTESTED**
- Nextcloud / ownCloud / Synology WebDAV: **UNTESTED**
- Windows 10/11 SMB Share / Samba 4 / Synology NAS SMB2/3: **UNTESTED**
- Enterprise EMM/MDM consoles (Microsoft Intune, VMware Workspace ONE, Google Endpoint Management, MobileIron) pushing `app_restrictions.xml`: **UNTESTED** (Restriction schema & `EnterprisePolicyManager` parser unit-tested)
- Enterprise EDR/DLP products (Microsoft Defender for Endpoint, CrowdStrike Falcon, SentinelOne, Sophos Endpoint, Trellix, Symantec, Trend Micro, ESET, Cortex XDR): **UNTESTED** (No obfuscation/packers, standard APIs only; see `ENTERPRISE_DEPLOYMENT.md` for IT validation & false-positive triage steps)
