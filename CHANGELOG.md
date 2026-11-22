# Changelog

All notable changes to the **Portly** project are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.0.0] - 2026-11-22

### Added
- **Complete Material 3 UI/UX Architecture**:
  - Distinctive Portly visual identity with Deep Teal (`#0D9488`), Soft Mint (`#CCFBF1`), and Dark Slate styling.
  - Multi-theme engine with dynamic theme switching: **Light**, **Dark**, **AMOLED** (true pitch-black OLED surfaces), and **Dynamic Color** (Android 12+ Material You).
  - High-contrast, adaptive status and navigation bars matching current theme luminance.
  - Clean edge-to-edge layout across modern Android versions.
- **Embedded Local Server Suite**:
  - High-performance **FTPS (TLS 1.2/1.3)** server on Port 2122.
  - Secure **HTTPS** server with modern web file manager browser UI on Port 8443.
  - Legacy unencrypted **FTP** (Port 2121) and **HTTP** (Port 8080) servers with optional security toggle to hide unencrypted servers.
  - Live client session tracking, rate-limited login attempts (429 cooldown), and force-disconnect controls.
  - Foreground service with notification bar app icon, active ports, client counter, and instant stop action.
  - Network status indicator with Wi-Fi/LAN IP detection and peer pairing via local NSD/mDNS (`_portly._tcp.`).
- **Remote Protocol Clients**:
  - SFTP (SSHv2 with strict host key verification and `known_hosts` persistence).
  - FTPS (explicit TLS with TOFU certificate pinning and X.509 verification).
  - FTP (standard plain mode with unencrypted warning acknowledgment).
  - WebDAV (HTTP/HTTPS chunked streaming with authentication).
  - SMB (SMB 2.02–3.1.1 dialect support via JCIFS-NG).
  - Keystore-backed **AES-256-GCM** credential encryption (`CredentialVault`).
- **File Management & Previews**:
  - Dual-pane browsing for large screens and tablets (`>= 700dp`).
  - SAF document tree, external storage, and internal storage navigation.
  - Zip-Slip-hardened ZIP archive creation and extraction.
  - Sandboxed media viewer (Media3 ExoPlayer for audio/video, Coil 3 for images, PdfRenderer for PDFs, and chunked code/text).
- **Categorized Settings System**:
  - 7 organized categories: Server Configuration & DDNS, File Management, Transfers & Performance, Appearance & Theme, Security & Protocols, Privacy & Audit Diagnostics, and About & Support.
  - Animated sub-screens with system back handling.
- **Automatic In-App Updates**:
  - GitHub Releases API integration for both **Stable** (`vX.Y.Z`) and **Beta** (`vX.Y.Z-beta.N`) channels.
  - Background periodic update checks via Android WorkManager.
  - Update notifications when new releases are detected.
  - Multi-stage security verification before install: package ID matching, downgrade prevention, SHA-256 hash check, and APK signing certificate fingerprint validation.
  - Native installation invocation through Android Package Installer via secure FileProvider.
- **Licensing & CI/CD**:
  - Full **GPL-3.0-or-later** license implementation with third-party attributions.
  - Automated GitHub Actions workflows for continuous integration (`android-ci.yml`), beta releases (`android-beta-release.yml`), and production releases (`android-stable-release.yml`).
