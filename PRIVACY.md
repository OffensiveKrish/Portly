# Privacy Policy for Portly

**Last Updated:** November 2026

Portly is an open-source, offline-first Android application designed to put users in full control of their files, server hosting, and remote storage connections.

## 1. Zero Telemetry & Zero Analytics
Portly does **not**:
- Include any analytics frameworks (no Google Firebase Analytics, no Mixpanel, no Flurry, etc.).
- Include any advertising frameworks or ad trackers.
- Collect, transmit, or sell personal information, device identifiers, IP addresses, or usage patterns to third-party servers.

## 2. Local Network & Direct Communication
- **Client Connections**: When you connect to an FTP, FTPS, SFTP, WebDAV, or SMB server, Portly communicates directly and exclusively with the destination host you configure. No traffic passes through Portly intermediary servers or proxies.
- **Server Hosting**: When you host an FTP, FTPS, HTTP, or HTTPS server on your Android device, connections are received directly on your local Wi-Fi / LAN network (or loopback). The application does not relay server traffic through external cloud servers.
- **Peer Pairing**: Peer pairing uses local Network Service Discovery (`NSD` / mDNS) and local direct Wi-Fi/LAN transport. Pairing tokens are ephemeral (60-second validity) and generated locally.

## 3. Credential Security & Local Storage
- All saved server credentials (passwords, usernames, SSH private keys, passphrases) are encrypted using **AES-256-GCM** backed by the **AndroidKeyStore** hardware module and stored locally on your device in a private SQLite/Room database.
- Portly sets `android:allowBackup="false"` in its Android Manifest to prevent credentials and private state from leaking through unencrypted cloud backups.
- Plaintext passwords and cryptographic private keys are scrubbed by `LogRedactor` before any operational or server logs are created.

## 4. Updates & GitHub Releases
- Portly checks for updates by querying the official **GitHub Releases API** (`https://api.github.com/repos/{owner}/{repo}/releases`).
- When checking for updates, Portly transmits only the standard HTTP request headers required to fetch release metadata. No personal data, device IDs, or telemetry are transmitted.
- Automatic update checks and notifications can be disabled at any time in **Settings > About Portly > Updates**.

## 5. Diagnostic Logging & Auditing
- Diagnostic logging is strictly opt-in and controlled by the user in **Settings > Privacy & Audit Diagnostics**.
- Operational logs are stored solely within the app's internal sandbox.
- When exporting diagnostic logs, sensitive tokens, passwords, and private keys are automatically redacted.
- Log retention periods (7, 30, or 90 days) automatically prune older log entries.

## 6. Open Source Verification
Because Portly is licensed under the **GNU General Public License v3.0 or later (GPL-3.0-or-later)**, you can independently review, verify, and build the source code yourself.

For security concerns or privacy questions, please open an issue or consult [`SECURITY.md`](SECURITY.md).
