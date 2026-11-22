# Phase 14 — Settings, History, Enterprise MDM & Privacy/Audit Transparency

## Implemented
- **Android Enterprise Managed Configurations (`:app`, `:core:core-model`, `:core:core-security`, `:network:network-manager`)**:
  - Created `app/src/main/res/xml/app_restrictions.xml` and registered `android.content.APP_RESTRICTIONS` in `AndroidManifest.xml` declaring managed keys: `disable_plain_ftp`, `disable_smb1_legacy`, `allow_public_server_bind`, `require_biometric_lock`, `disable_servers`, and `diagnostic_logging_enabled`.
  - Implemented `EnterprisePolicy` model and `EnterprisePolicyManager` (`RestrictionsManager` reader + `fromBundle` / `fromMap` parsers).
  - Enforced MDM policies in `PortlyServerManager.startServer` (blocks servers if `disableServers`, blocks plain FTP server if `disablePlainFtp`, locks bind mode to LAN/loopback when `!allowPublicServerBind`) and in `SettingsScreen`.
- **Settings, Retention, Redacted Diagnostics & Battery UX (`:core:core-datastore`, `:core:core-database`, `:feature:feature-settings`)**:
  - Extended `AppSettings` and `PortlySettingsDataStore` with `diagnosticLoggingEnabled` and `logRetentionDays`.
  - Added `pruneOlderThan` DAOs on `TransferHistoryDao` and `ActivityLogDao`.
  - Expanded `SettingsScreen` with Conflict Policy chips (`Ask`, `Rename`, `Skip`, `Overwrite`), Managed Policy banner, one-time dismissible Battery Optimization education card, Diagnostic Logging toggle, retention selector (`7 / 30 / 90 days` + "Prune Now"), "Export Redacted Logs" (`DiagnosticBundleExporter` scrubbing passwords/tokens/PEM keys via `LogRedactor`), and Privacy & Audit Disclosure dialog.
- **History Search, Auto-Retention & Redacted Export (`:feature:feature-history`)**:
  - Updated `HistoryViewModel` / `HistoryScreen` to auto-prune records exceeding `logRetentionDays` on load, filter transfer history & activity logs interactively, and export sanitized JSON history (`CreateDocument`).
- **Tests**:
  - Added `EnterprisePolicyManager` map parsing test in `SecurityUnitTest`.
  - Added `DiagnosticBundleExporterTest` verifying password/Bearer-token/private-key scrubbing in diagnostic JSON output.

## Verification
- Command: `.\gradlew.bat assembleDebug :network:network-manager:testDebugUnitTest :core:core-security:testDebugUnitTest :feature:feature-settings:testDebugUnitTest --console=plain`
- Result: `BUILD SUCCESSFUL`.
