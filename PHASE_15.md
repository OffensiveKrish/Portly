# Phase 15 — Security Hardening, Signed Release Build & Final Deliverables

## Implemented
- **R8 & Release Signing (`:app`)**:
  - Updated `app/proguard-rules.pro` with standard R8 keep rules for protocol engines (`Apache FtpServer`, `SSHJ`, `jcifs-ng`, `Ktor`, `OkHttp`, `BouncyCastle`, `kotlinx.serialization`) without any packers, string encryption, or anti-analysis obfuscation.
  - Added `signingConfigs.release` in `app/build.gradle.kts` reading `PORTLY_KEYSTORE_PATH` / `PORTLY_KEYSTORE_PASSWORD` / `PORTLY_KEY_ALIAS` / `PORTLY_KEY_PASSWORD` environment variables with fallback to `app/release-test.keystore` (generated with JDK `keytool` for reproducible verification; must be replaced with an organization HSM/Play signing key before production publishing).
  - Removed the default `androidx.work.WorkManagerInitializer` entry from `app/src/main/AndroidManifest.xml` (`tools:node="remove"`), resolving the `RemoveWorkManagerInitializer` vital lint error so `PortlyApplication`'s `Configuration.Provider` initializes `HiltWorkerFactory` on demand cleanly.
- **Documentation & Compliance Deliverables**:
  - `DEPENDENCIES.md`
  - `SBOM.md` and `sbom.cdx.json` (CycloneDX 1.5)
  - `SECURITY.md`
  - `COMPATIBILITY.md`
  - `ENTERPRISE_DEPLOYMENT.md`
  - `README.md`

## Observed Build, Signing & Verification Output
- **Command**: `.\gradlew.bat assembleDebug assembleRelease testDebugUnitTest --console=plain`
  - **Result**: `BUILD SUCCESSFUL` (`lintVitalRelease`, `minifyReleaseWithR8`, `packageRelease`, `packageDebug`, and all module unit/integration tests succeeded).
- **Test Summary**: **18 suites, 46 tests, 0 failures, 0 errors, 0 skipped**.
- **APK Outputs**:
  - `app/build/outputs/apk/release/app-release.apk`: `8,224,929` bytes, SHA-256 `D32A8FB29CD4C5968F5C7B9E63FC9ED324EFAA676D62775ADB3AFD92080AEBAE`.
  - `app/build/outputs/apk/debug/app-debug.apk`: `35,981,946` bytes, SHA-256 `B96841E93416616463AA3E09F47BC5A303FB38BF3E4F4DA46B084C12585B130B`.
- **Signature Verification (`apksigner verify --verbose --print-certs`)**:
  - Verified with APK Signature Scheme v2 (`true`), 1 signer (`CN=Portly Open Source Release Verification, OU=Engineering, O=Portly, L=Local, ST=NA, C=US`), signer certificate SHA-256 `09637ab91c8c71a5bfccd4e3e7f6a552991b9f14153d84f68a67e08f22d01d1a`.
