# Portly Enterprise Deployment & Security Transparency Guide

This document supports enterprise IT, InfoSec, MDM/EMM administrators, and DLP/EDR reviewers deploying **Portly** (`com.portly.app`).

## 1. Design Principles for Enterprise Endpoint Compatibility
- Uses **only documented Android SDK APIs** (`NsdManager`, `RestrictionsManager`, `BiometricPrompt`, `Storage Access Framework`, `WorkManager`, typed `FOREGROUND_SERVICE_DATA_SYNC`, `AndroidKeyStore`).
- **No packers, custom loaders, DEX reflection tricks, or anti-analysis obfuscation**: Release builds use standard R8 (`isMinifyEnabled = true`, `isShrinkResources = true`) with transparent keep rules (`app/proguard-rules.pro`).
- **Zero hidden listeners or background tunnels**: Every server service binds visibly through a persistent foreground notification displaying active servers and per-server Stop controls.
- **No automatic execution**: Downloaded/extracted files are never auto-launched or silently installed.
- **Zero trackers / zero third-party analytics**: Portly makes zero outbound requests unless explicitly initiated by the user to a configured host.

## 2. Android Permissions Declaration
| Permission | Max SDK / Scope | Documented Operational Purpose |
|---|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` | All | User-initiated file transfer clients (FTP/FTPS/SFTP/WebDAV/SMB), embedded LAN servers, and Wi-Fi-only policy enforcement. |
| `ACCESS_LOCAL_NETWORK` | API 37+ (Android 17) | Runtime permission required by Android 17 to accept or initiate connections on local Wi-Fi/LAN subnets. |
| `CHANGE_WIFI_MULTICAST_STATE` | All | Built-in Android `NsdManager` LAN discovery (`_smb._tcp.`, `_portly._tcp.`). |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC` | API 28+ / 34+ | Keeps user-initiated transfers and active file servers reliable with visible status notifications. |
| `POST_NOTIFICATIONS` | API 33+ | Displays foreground service progress, server active status, and transfer completion notifications. |
| `USE_BIOMETRIC` | All | Optional (or MDM-mandated) app unlock and server configuration gate. |
| `MANAGE_EXTERNAL_STORAGE` | API 30+ (optional) | Optional "All Files Access" when managing arbitrary local directories; SAF `OpenDocumentTree` works without it. |
| `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE` | `maxSdkVersion="32"` / `"29"` | Legacy shared storage compatibility on Android 8–12 / 8–10. |

## 3. Managed Configurations (MDM / EMM via `app_restrictions.xml`)
Portly declares Android Managed Configurations at `app/src/main/res/xml/app_restrictions.xml` (registered via `android.content.APP_RESTRICTIONS` in `AndroidManifest.xml`):

| Restriction Key | Type | Default | Enforced Behavior |
|---|---|---:|---|
| `disable_plain_ftp` | `bool` | `false` | Blocks starting the unencrypted FTP server (`PortlyServerManager`). |
| `disable_smb1_legacy` | `bool` | `false` | Locks SMB1 legacy compatibility OFF in Settings; enforces SMB 2.02–3.1.1 only. |
| `allow_public_server_bind` | `bool` | `true` | When `false`, forces embedded servers strictly to LAN/loopback (`lanOnlyBind = true`). |
| `require_biometric_lock` | `bool` | `false` | Locks biometric/device credential lock ON in Settings when hardware/credential is available. |
| `disable_servers` | `bool` | `false` | Disables starting any embedded FTP/FTPS/HTTP/HTTPS server on the managed device. |
| `diagnostic_logging_enabled` | `bool` | `true` | Sets and locks the enterprise audit/diagnostic logging state. |

## 4. Network Communication Matrix
All server ports are user-configurable and disabled until explicitly started by the user:

| Service / Client | Direction | Transport / Protocol | Default Port | Default Exposure |
|---|---|---|---:|---|
| FTP Client / Passive Data | Outbound | TCP (`FTP`) | `21` + passive | User-initiated host only (plain-FTP warning shown) |
| FTPS Client (Explicit/Implicit) | Outbound | TCP (`TLS 1.2/1.3`) | `21` / `990` + passive | User-initiated host + TOFU SHA-256 pinning |
| SFTP Client | Outbound | TCP (`SSHv2`) | `22` | User-initiated host + `known_hosts` pin |
| WebDAV Client | Outbound | TCP (`HTTP` / `HTTPS`) | `80` / `443` | User-initiated host + Digest/Basic/TOFU TLS |
| SMB Client | Outbound | TCP (`SMB2/3`) | `445` | User-initiated host (LAN/VPN) |
| Embedded FTP Server | Inbound | TCP (`FTP`) | `2121` (+ `50000–50100`) | Disabled by default; LAN/loopback only unless opted in |
| Embedded FTPS Server | Inbound | TCP (`TLS 1.2/1.3`) | `2122` (+ `50000–50100`) | Disabled by default; self-signed BouncyCastle PKCS12 |
| Embedded HTTP Server | Inbound | TCP (`HTTP/1.1`) | `8080` | Disabled by default; session cookie + 429 lockout |
| Embedded HTTPS Server | Inbound | TCP (`TLS 1.2/1.3`) | `8443` | Disabled by default; SSLSocket -> CIO loopback |
| LAN Discovery | LAN Multicast | UDP `5353` (`mDNS` via `NsdManager`) | `5353` | Only while discovery scan or 60s pairing is open |

## 5. Privacy, Audit Logging & DLP Compatibility
- **Recorded Audit Events**: Server start/stop, listening port bind, authentication outcomes (success/failure without secrets), client kicks, transfer completion/failure metadata, and MDM restriction enforcement.
- **Never Recorded**: Passwords, SSH private keys/passphrases, TLS private keys, HTTP session tokens, or file contents (`LogRedactor` enforces sanitization across logs and diagnostic exports).
- **Retention & Export**: Configurable 7 / 30 / 90-day retention pruning (`HistoryViewModel`, `SettingsScreen`) and user-triggered redacted JSON diagnostic bundle export (`DiagnosticBundleExporter`).
- **DLP Compatibility**: File content stays on-device or moves directly between user-configured endpoints over standard protocol streams (never uploaded to intermediate relay servers).

## 6. Reproducible Release Signing & Antivirus False-Positive Process
- Set signing environment variables cleanly in CI before running `assembleRelease`:
  - `PORTLY_KEYSTORE_PATH`, `PORTLY_KEYSTORE_PASSWORD`, `PORTLY_KEY_ALIAS`, `PORTLY_KEY_PASSWORD`
- Verify APK integrity and signing certificate digest before distributing via MDM:
  ```bash
  apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
  ```
- **Observed Reference Verification Build**:
  - Release APK (`app/build/outputs/apk/release/app-release.apk`): `8,224,929` bytes, SHA-256 `D32A8FB29CD4C5968F5C7B9E63FC9ED324EFAA676D62775ADB3AFD92080AEBAE`.
  - If any EDR/AV engine flags an embedded server library (`ftpserver-core`, `ktor-server-cio`, `jcifs-ng`) heuristically as a generic dual-use network tool, submit the signed release APK along with its SHA-256 hash, [`SBOM.md`](SBOM.md), [`sbom.cdx.json`](sbom.cdx.json), and [`DEPENDENCIES.md`](DEPENDENCIES.md) to the vendor's false-positive portal referencing Portly's open-source build provenance.
