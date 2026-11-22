# Contributing to Portly

Thank you for your interest in contributing to **Portly**! Portly is an open-source Android application for file management, remote storage access, and embedded server hosting, licensed under the **GNU General Public License v3.0 or later (GPL-3.0-or-later)**.

---

## 1. Branching Strategy & Workflow

We follow a structured Git branching model:

- **`main`**: The stable production branch. Code on `main` is always releasable. Only release tags (`vX.Y.Z`) and pull requests from `develop` or tested `hotfix/*` branches are merged here. Branch protection requires passing CI.
- **`develop`**: The primary integration branch for ongoing development and beta releases (`vX.Y.Z-beta.N`). All feature branches branch off `develop` and merge back into `develop`.
- **`feature/<name>`**: For new features and enhancements. Always branch from `develop`.
- **`hotfix/<name>`**: For urgent production bug fixes. Branch directly from `main`, apply the minimal fix, and merge into both `main` and `develop`.

---

## 2. Pull Request & CI Requirements

Before submitting a Pull Request:

1. **Clean Architecture & Module Isolation**:
   - Portly is organized into 28 Gradle modules. Business logic resides in domain and feature modules; UI components reside in feature modules and `:core:core-ui`.
   - Never introduce circular dependencies or break API boundaries.
2. **Code Quality & Linting**:
   - Ensure clean compilation with Kotlin 2.x and AGP 9.5+.
   - Treat compiler warnings and lint violations seriously (`allWarningsAsErrors` is enforced on critical modules).
3. **Automated Testing**:
   - Run the full test suite locally before pushing:
     ```powershell
     .\gradlew.bat testDebugUnitTest --console=plain
     ```
   - All unit and integration test suites must pass.
4. **CI Checks**:
   - Every push and pull request triggers the `android-ci.yml` GitHub Actions workflow. All checks (Gradle build, Lint, Unit tests, APK generation validation) must pass before merging.

---

## 3. Licensing of Contributions

By submitting code, documentation, or other contributions to Portly, you agree that your contributions are licensed under the **GNU General Public License v3.0 or later (GPL-3.0-or-later)**.
- If introducing any third-party dependencies, ensure their licenses are strictly compatible with GPL-3.0-or-later (e.g., Apache-2.0, MIT, BSD, LGPL-2.1).
- Proprietary or license-incompatible dependencies will be rejected.

---

## 4. Release Process

- **Beta Releases**: Created by tagging a commit on `develop` with `vX.Y.Z-beta.N`. The `android-beta-release.yml` workflow automatically builds, tests, signs, and publishes a GitHub Prerelease.
- **Stable Releases**: Created by merging `develop` into `main` and tagging `main` with `vX.Y.Z`. The `android-stable-release.yml` workflow automatically builds, signs, verifies, and publishes the official release.

For security concerns, please refer to [`SECURITY.md`](SECURITY.md).
