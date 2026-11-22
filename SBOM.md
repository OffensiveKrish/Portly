# Software Bill of Materials (SBOM)

Portly maintains a machine-readable **CycloneDX 1.5** Software Bill of Materials at [`sbom.cdx.json`](sbom.cdx.json) covering the application component (`com.portly:app:1.0.0`), internal Gradle modules, and direct third-party runtime dependencies declared in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

## Supply-Chain & Vulnerability Management Process
1. **Version Pinning**: All dependency coordinates and versions are centralized in [`gradle/libs.versions.toml`](gradle/libs.versions.toml) without dynamic `+` ranges.
2. **Release Verification**:
   - Release binary (`app/build/outputs/apk/release/app-release.apk`) is built with standard R8 shrinking/minification, APK Signature Scheme v2 verification, and reproducible key/build inputs documented in [`ENTERPRISE_DEPLOYMENT.md`](ENTERPRISE_DEPLOYMENT.md).
3. **Vulnerability Scanning Workflow**:
   - CI or security operators should run CycloneDX/OSV/Trivy against `sbom.cdx.json` prior to enterprise rollout:
     ```bash
     osv-scanner --sbom=sbom.cdx.json
     trivy sbom sbom.cdx.json
     ```
4. **License Compliance**: Every runtime dependency uses Apache-2.0, MIT, or LGPL-2.1-only.
