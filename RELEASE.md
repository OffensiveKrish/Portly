# Portly — Official Release Management & Publishing Guide

This document establishes the official release process, versioning standard, artifact packaging, and security requirements for **Portly**.

---

## 1. Official Versioning Standard: Calendar Versioning (CalVer)

Portly adheres to strict **Calendar Versioning (CalVer)**:

$$\text{Format: } \mathbf{YYYY.MM.PATCH}$$

### Rules
1. **`YYYY`**: Four-digit release year (e.g., `2026`).
2. **`MM`**: Two-digit release month with leading zero (e.g., `11` for November, `01` for January).
3. **`PATCH`**: Sequential integer starting at `0` for the first release of any month, incremented for bug fixes and subsequent releases within that same month (e.g., `2026.11.0`, `2026.11.1`).
4. **Pre-release Identifiers**:
   - Alpha: `2026.11.0-alpha.1`
   - Beta: `2026.11.0-beta.1`
   - Release Candidate: `2026.11.0-rc.1`
5. **No Version Reuse**: Never reuse, re-tag, or silently overwrite an existing release or Git tag.
6. **Tag Consistency**: The Git tag (e.g. `2026.11.0`) and the application `versionName` displayed in Settings and About screens must match.

---

## 2. Centralized Android Version Management

All release versions are managed in **one single file** at the repository root:

📁 **`version.properties`**
```properties
VERSION_NAME=2026.11.0
VERSION_CODE=202611000
```

### Android `versionCode` Calculation Rule
Android requires a 32-bit positive, monotonically increasing integer `versionCode`:
$$\text{versionCode} = (\text{YYYY} \times 100{,}000) + (\text{MM} \times 1{,}000) + \text{PATCH}$$

* `2026.11.0` $\rightarrow$ `202611000` (Strictly greater than initial `v1.0.0` build `1`)
* `2026.11.1` $\rightarrow$ `202611001`
* `2026.12.0` $\rightarrow$ `202612000`
* `2027.01.0` $\rightarrow$ `202701000`

`app/build.gradle.kts` dynamically reads `version.properties` at build time. No manual edits across multiple build files are required.

---

## 3. Standard Release Assets

Every production GitHub Release must include:

| Asset Filename | Description | Compliance & Purpose |
|---|---|---|
| `Portly-YYYY.MM.PATCH.apk` | Signed Production Universal Android APK | Primary installer for end-users |
| `Portly-YYYY.MM.PATCH-universal.apk` | Compatible Universal APK alias | For backward-compatible direct links |
| `Portly-YYYY.MM.PATCH-source.zip` | Exact corresponding source code tree | Strict **GPL-3.0-or-later** source distribution compliance |
| `Portly-YYYY.MM.PATCH-checksums.txt` | SHA-256 digests for all published assets | Cryptographic authenticity & tamper verification |
| `SHA256SUMS.txt` | Standard Unix checksums file | Automation & script verification |
| `update-metadata.json` | JSON release metadata | Consumed by Portly's In-App Auto-Updater |
| `CHANGELOG.md` | Full changelog document | User-facing release audit & feature tracking |

---

## 4. Release Procedures

### A. Preparing a Release
1. Ensure all changes are committed and tested on the `develop` branch.
2. Update `version.properties` with the new CalVer `VERSION_NAME` and calculated `VERSION_CODE`.
3. Update `CHANGELOG.md` under a new section `## [YYYY.MM.PATCH] - YYYY-MM-DD`.
4. Merge `develop` into `main`:
   ```bash
   git checkout main
   git merge develop
   git push origin main
   ```

### B. Triggering the Release Automation
There are two sanctioned methods to publish a release:

#### Method 1: Push a CalVer Tag (Recommended)
```bash
git tag 2026.11.0
git push origin 2026.11.0
```

#### Method 2: GitHub Actions Workflow Dispatch
1. Navigate to **Actions** $\rightarrow$ **Android Stable Release** in the GitHub repository.
2. Click **Run workflow**.
3. Enter the CalVer version (e.g. `2026.11.0`).

The workflow will:
1. Validate CalVer version syntax.
2. Run test suites and lint checks.
3. Build and cryptographically sign the production APK.
4. Generate the corresponding source zip archive (`git archive`).
5. Compute SHA-256 checksums and extract certificate fingerprints.
6. Generate standardized release notes.
7. Publish the GitHub Release with all assets attached.

---

## 5. Security & Cryptographic Integrity

1. **Production Signing Secrets**: Stored exclusively in GitHub repository protected secrets:
   - `ANDROID_KEYSTORE_BASE64`
   - `ANDROID_KEYSTORE_PASSWORD`
   - `ANDROID_KEY_ALIAS`
   - `ANDROID_KEY_PASSWORD`
2. **Never Commit Secrets**: Keystores and private signing keys must never be committed to Git.
3. **Downgrade Protection**: The in-app updater and Android Package Installer automatically reject downgrades or tampered packages failing signature/checksum checks.
4. **Official GitHub Repository**: Automatic updates exclusively point to `OffensiveKrish/Portly`.
