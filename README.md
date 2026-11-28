<div align="center">
  <img src="core/core-ui/src/main/res/drawable/ic_portly_logo.png" alt="Portly Logo" width="112" height="112" />
  <h1>Portly</h1>
  <p><strong>Connect. Transfer. Manage. Anywhere.</strong></p>
  <p>
    <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPLv3-blue.svg" alt="License: GPL v3" /></a>
    <a href="https://github.com/OffensiveKrish/Portly/actions/workflows/android-ci.yml"><img src="https://github.com/OffensiveKrish/Portly/actions/workflows/android-ci.yml/badge.svg" alt="Android CI" /></a>
    <a href="https://developer.android.com"><img src="https://img.shields.io/badge/Android-8.0%2B%20(API%2026)-green.svg" alt="Min Android SDK" /></a>
  </p>
</div>

**Portly** is a modern, enterprise-grade, privacy-respecting Android application for local server hosting, remote protocol access, full-featured file management, and secure file transfers.

Designed with a clean Material Design 3 interface featuring a distinctive Deep Teal identity, Portly supports **Light**, **Dark**, and **AMOLED** (true pitch-black) themes with dynamic Material You accent theming.

---

## 🌟 Core Features

### 1. Embedded Local Server Suite
Host high-performance servers directly from your Android device:
- **FTPS Server** (Port 2122) — TLS 1.2/1.3 encrypted file server.
- **HTTPS Server** (Port 8443) — Modern browser-based web file manager with secure upload/download and streaming.
- **FTP Server** (Port 2121) & **HTTP Server** (Port 8080) — Legacy servers with a one-tap setting toggle to hide unencrypted protocols.
- **Client Monitoring & Control** — View active client IPs, active sessions, data served, and kick connections with one tap.
- **Foreground Service** — Persistent notification bar indicator showing live server status, client count, and instant stop actions.

### 2. Multi-Protocol Remote Storage Client
Connect securely to remote storage across all major network protocols:
- **SFTP** (SSHv2 with strict host key verification and `known_hosts` persistence).
- **FTPS** (Explicit TLS with TOFU certificate pinning and X.509 validation).
- **FTP** (Standard plain mode with unencrypted transmission warning).
- **WebDAV** (HTTP/HTTPS chunked streaming).
- **SMB** (SMB 2.02–3.1.1 dialect support via JCIFS-NG).
- **Keystore Security** — All passwords, private keys, and passphrases encrypted using hardware-backed **AES-256-GCM** via `AndroidKeyStore`.

### 3. Professional File Manager & Archive Tools
- **Unified Storage Navigation** — Browse internal storage, SD cards, and USB / SAF document trees.
- **Dual-Pane Mode** — Side-by-side local and remote panes with drag-and-drop on tablets and foldables (`>= 700dp`).
- **Rich File Previews** — Built-in offline viewers for images (Coil 3), video and audio (Media3 ExoPlayer), PDFs (`PdfRenderer`), CSV spreadsheets, and code/text files.
- **Secure Archive Handling** — Create and extract `.zip` files with strict Zip-Slip path traversal protection.
- **Peer-to-Peer Pairing** — Instant device-to-device pairing via Network Service Discovery (`NSD` / mDNS) and 60-second single-use QR tokens.

### 4. Automatic In-App Updates
- Built-in updater supporting both **Stable** and **Beta** release channels directly from GitHub Releases.
- Background update checks via Android WorkManager with local update notifications.
- Multi-tier release verification: Package ID validation, downgrade protection, SHA-256 checksum matching, and cryptographic APK signing certificate fingerprint validation before launching the Android Package Installer.

---

## 📲 Download & Installation

Portly is distributed independently through **GitHub Releases** as signed, standalone Android APK packages. No Google Play or third-party store account is required.

### System Requirements
- **Minimum OS**: Android 8.0 (Oreo / API Level 26)
- **Target OS**: Android 15 / 16 / 17 (API Level 35+)
- **Architecture**: Universal APK (ARM64-v8a, ARMv7a, x86_64)

### How to Install
1. Open the [**Releases**](https://github.com/OffensiveKrish/Portly/releases) page on your Android device.
2. Under the latest **Stable Release** (or **Beta Release** if testing), download `Portly-vX.Y.Z-universal.apk`.
3. Tap the downloaded APK file to install.
   - If prompted by Android, enable **"Allow from this source"** for your browser or file manager to permit installing unknown apps.
4. Launch **Portly** from your app drawer.

---

## 🔒 Security & Verification

To verify that your downloaded APK has not been modified or tampered with:

### 1. Check SHA-256 Checksum
Compare the SHA-256 hash of your downloaded APK against the hash published in `SHA256SUMS.txt` on the GitHub Release page:

```bash
# On Linux / macOS
sha256sum Portly-v1.0.0-universal.apk

# On Windows PowerShell
Get-FileHash -Algorithm SHA256 .\Portly-v1.0.0-universal.apk
```

### 2. Verify APK Signing Certificate
Inspect the cryptographic signing certificate using Android SDK's `apksigner`:

```bash
apksigner verify --print-certs Portly-v1.0.0-universal.apk
```

Ensure the certificate SHA-256 fingerprint matches the official Portly release signing key published in [`SECURITY.md`](SECURITY.md).

---

## 🔄 Updating Portly

- **Automatic Checks**: By default, Portly checks for updates on startup and periodically in the background (once daily via WorkManager).
- **Manual Check**: Navigate to **Settings > About Portly > Updates** and tap **Check for Updates**.
- **Release Channel**: Switch between **Stable** and **Beta** channels anytime in **Settings > About Portly > Updates**.
- **Verification**: The built-in updater automatically verifies the APK package ID, ensures no version downgrade occurs, checks the SHA-256 hash, and verifies that the APK signing certificate matches your currently installed app before initiating installation.

---

## 🏗️ Architecture & Building From Source

Portly is built with modern Android best practices using 28 modular Gradle subprojects:

- **`:app`** — Main application container, navigation scaffolding, biometric authentication gate, foreground services, and WorkManager jobs.
- **`:core:core-model`** — Pure domain models, protocol interfaces, and enterprise policy definitions.
- **`:core:core-common`** — Core utilities, `PathValidator`, 64KB `ChecksumUtils`, formatters, and automatic update infrastructure.
- **`:core:core-security`** — `KeystoreCredentialCipher` (AES-256-GCM), `Pbkdf2Crypto` (600k iterations), `CertificateManager` (X.509/PKCS12), `LogRedactor`.
- **`:core:core-database`** — Room Database, `CredentialVault`, connection profiles, and transfer history.
- **`:core:core-datastore`** — DataStore preferences (`PortlySettingsDataStore`).
- **`:core:core-ui`** — Material 3 theme engine (Light, Dark, AMOLED, Dynamic Material You) and shared UI components.
- **`:transfer:transfer-engine`** — High-performance 64KB chunked queue with rolling ETA, `.part` resume, SHA-256 hashing, and conflict policies.
- **`:network:network-manager`** — Network state monitor, ZXing QR generation, and NSD mDNS peer discovery.
- **`:protocol:*`** — Isolated clients (FTP, FTPS, SFTP, WebDAV, SMB) and servers (FTP, FTPS, HTTP, HTTPS).
- **`:feature:*`** — Feature modules for Home, Files, Connections, Servers, Transfers, History, Settings, Preview, and Archive.

### Building
Prerequisites: **JDK 17** and **Android SDK** (`compileSdk = 37`, `minSdk = 26`).

```powershell
# Build debug APK and run all unit tests
.\gradlew.bat assembleDebug testDebugUnitTest --console=plain

# Build release APK
.\gradlew.bat assembleRelease --console=plain
```

---

## 📄 License & Attribution

Portly is licensed under the **GNU General Public License v3.0 or later (GPL-3.0-or-later)**.
See [`LICENSE`](LICENSE) for the full text.

Third-party libraries used in Portly remain under their respective permissive open-source licenses (Apache-2.0, MIT, LGPL-2.1). Full attributions and notices are available in [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).

Portly trademarks, logo, and brand assets are protected; see [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md#1-portly-branding--trademark-notice).
