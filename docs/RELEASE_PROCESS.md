# Portly — Detailed Release Process & Checklist

This document provides step-by-step procedures, quality gates, and troubleshooting workflows for release maintainers.

---

## 1. Quality Gates Before Release

Before tagging any release:

- [ ] **Clean Checkout & Synced State**: Working directory must be clean with no untracked or modified files.
- [ ] **Unit Tests Passing**: Run `./gradlew testDebugUnitTest` and verify all tests pass.
- [ ] **Android Lint Validation**: Run `./gradlew lintDebug` and verify no fatal errors.
- [ ] **Assemble Release Check**: Run `./gradlew assembleRelease` locally or verify CI passes.
- [ ] **Single Version Definition**: Verify `version.properties` matches the planned release tag.
- [ ] **GPL-3.0 License & Notices**: Ensure all new dependencies are compatible with GPL-3.0-or-later.

---

## 2. Step-by-Step Stable Release Workflow

### Step 1: Centralized Version Increment
Edit `version.properties`:
```properties
VERSION_NAME=2026.11.0
VERSION_CODE=202611000
```

### Step 2: Update CHANGELOG.md
Document all changes under `## [2026.11.0] - YYYY-MM-DD`:
- **Added**: New capabilities and features.
- **Changed**: User experience, defaults, or behavioral adjustments.
- **Fixed**: Bug fixes, crash resolutions, or edge-case handling.
- **Security**: Vulnerability patches or certificate updates.

### Step 3: Commit and Merge to `main`
```bash
git checkout develop
git add version.properties CHANGELOG.md
git commit -m "chore(release): prepare 2026.11.0 release"
git push origin develop

git checkout main
git merge develop
git push origin main
```

### Step 4: Tag and Push
```bash
git tag -a 2026.11.0 -m "Portly Stable Release 2026.11.0"
git push origin 2026.11.0
```

### Step 5: Monitor GitHub Actions
Monitor the execution under `https://github.com/OffensiveKrish/Portly/actions/workflows/android-stable-release.yml`.
Ensure the release produces:
1. `Portly-2026.11.0.apk`
2. `Portly-2026.11.0-source.zip`
3. `Portly-2026.11.0-checksums.txt`
4. `update-metadata.json`

---

## 3. Emergency Hotfix Procedure

When a critical bug is identified in production:

1. Create a hotfix branch from `main`:
   ```bash
   git checkout -b hotfix/2026.11.1 main
   ```
2. Apply the minimal necessary patch.
3. Increment the `PATCH` segment in `version.properties`:
   ```properties
   VERSION_NAME=2026.11.1
   VERSION_CODE=202611001
   ```
4. Update `CHANGELOG.md`.
5. Merge hotfix into both `main` and `develop`:
   ```bash
   git checkout main
   git merge hotfix/2026.11.1
   git push origin main
   
   git checkout develop
   git merge hotfix/2026.11.1
   git push origin develop
   ```
6. Tag the hotfix release:
   ```bash
   git tag -a 2026.11.1 -m "Portly Hotfix Release 2026.11.1"
   git push origin 2026.11.1
   ```

---

## 4. In-App Auto-Update Verification Checklist

After any release is published:

1. Open Portly on an installed device running the prior version.
2. Navigate to **Settings** $\rightarrow$ **About & Support** $\rightarrow$ **Portly Updates & Distribution**.
3. Verify repository is `OffensiveKrish/Portly`.
4. Tap **Check for Updates**.
5. Verify dialog appears:
   - Version name matches the published tag.
   - Release notes are displayed cleanly.
   - Tapping **Update Now** downloads the APK with real-time percentage progress.
   - SHA-256 and APK signing certificate validation passes.
   - Android Package Installer is invoked to complete the installation.
