# PHASE 1 — Foundation

## Scope Completed
- Created the full 28-module Gradle Kotlin DSL project structure with `libs.versions.toml` version catalog (`AGP 9.5.0`, `Kotlin 2.4.21`, `Hilt 2.60.1`, `Compose BOM 2026.09.00`, `Room 2.8.5`, `DataStore 1.3.0`).
- Implemented all `core/*` modules with `allWarningsAsErrors = true`:
  - `core-model`: Shared domain models (`ConnectionProfile`, `ProtocolType`, `ServerType`, `ServerConfig`, `TransferSpec`, `TransferState`, `ProtocolError`, `AppSettings`, `LocalFile`, `RemoteFile`) and strict protocol/transfer interfaces (`ProtocolClient`, `ProtocolServer`, `TransferEngine`).
  - `core-common`: `AppResult`, `DispatchersModule`, `PathValidator` (canonical path-traversal guard), `Formatters`, `ChecksumUtils` (64KB streaming digest), and Hilt `@MapKey` multibindings (`ProtocolClientKey`, `ProtocolServerKey`).
  - `core-security`: `KeystoreCredentialCipher` (AES-256-GCM with per-record IV + `EncryptedSharedPreferences` fallback), `Pbkdf2Crypto` (600,000 iterations), `CertificateManager` (BouncyCastle X.509 generation, SHA-256 fingerprints, TOFU + user CA TrustManager), `RootDetector`, `BiometricAuthHelper`, and `LogRedactor`.
  - `core-database`: `PortlyDatabase`, Room entities & DAOs, domain mappers, and `CredentialVault`.
  - `core-datastore`: `PortlySettingsDataStore` backing appearance, transfer, and security preferences.
  - `core-ui`: Material 3 theme (Light, Dark, AMOLED pure black, dynamic color on API 31+) and reusable accessible components (`PortlyErrorCard`, `PlainFtpWarningBanner`, `SkeletonList`, `PortlyConfirmDialog`, `BreadcrumbBar`, `EmptyStateView`, `ProtocolBadge`).
- Implemented `:network:network-manager` (`NetworkMonitor`) and Room-backed `:transfer:transfer-engine` skeleton.
- Implemented `:app` shell (`PortlyApplication`, `MainActivity` with `NavigationSuiteScaffold`, biometric lock gate, and navigation across Home, Files, Connections, Servers, Transfers, History, Settings).

## Verification
- Executed `.\gradlew.bat assembleDebug :core:core-common:testDebugUnitTest :core:core-security:testDebugUnitTest --console=plain`.
- Result: `BUILD SUCCESSFUL` (873 actionable tasks, zero compiler warnings on `core/*` and `transfer-engine`, unit tests for `PathValidator`, `ChecksumUtils`, `Pbkdf2Crypto`, `LogRedactor`, and `CertificateManager` passed).
