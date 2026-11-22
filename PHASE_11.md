# Phase 11 — SMB Client & LAN Share Discovery

## Implemented
- **`:protocol:protocol-smb-client`**:
  - `JcifsSmbProtocolClient` implementing `ProtocolClient` via `jcifs-ng` (`CIFSContext`, `SmbFile`, `SmbRandomAccessFile`) bound to `ProtocolType.SMB` via Hilt (`SmbClientModule`).
  - Defaults to SMB 2.02 minimum (`jcifs.smb.client.minVersion = SMB202`) up to SMB 3.1.1 (`SMB311`); legacy SMB1 is disabled unless explicitly enabled per profile (`allowSmb1Legacy`), with domain/workgroup and share root URL building (`smb://host[:port]/share/...`).
  - 64KB streaming download/upload and offset resume via `SmbRandomAccessFile.seek(offset)`.
  - `mapSmbError` translating `SmbAuthException` and `NT_STATUS_LOGON_FAILURE` to `ProtocolError.AuthenticationFailed`, `NT_STATUS_ACCESS_DENIED` to `ProtocolError.PermissionDenied`, and `NT_STATUS_OBJECT_NAME_NOT_FOUND` / `NT_STATUS_OBJECT_PATH_NOT_FOUND` / `NT_STATUS_BAD_NETWORK_NAME` to `ProtocolError.PathNotFound`.
  - Unit tests in `SmbClientConfigTest` verifying SMB2+ defaults, opt-in SMB1 dialect config, URL generation with share names/custom ports, and NT status error mapping.
- **`:network:network-manager`**:
  - `NsdDiscoveryManager` wrapping Android built-in `NsdManager` (`_smb._tcp.` and `_portly._tcp.`) with API 34+ `ServiceInfoCallback` and pre-34 `resolveService` compatibility.
- **`:feature:feature-connections`**:
  - Wired LAN SMB discovery modal (`ConnectionsScreen`) letting users scan the local subnet via NSD and prefill a new SMB connection profile with discovered host/port.

## Verification
- Command: `.\gradlew.bat assembleDebug :protocol:protocol-smb-client:testDebugUnitTest --console=plain`
- Result: `BUILD SUCCESSFUL` (`:protocol:protocol-smb-client:testDebugUnitTest` executed and passed; `assembleDebug` passed).
- Note: Live SMB2/3 handshake against an external NAS/Windows/Samba host on LAN was not run in this headless JVM environment (logged as untested outside mocked/config tests).
