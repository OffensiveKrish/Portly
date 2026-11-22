# Phase 13 — Android-to-Android Peer Pairing (NSD + QR)

## Implemented
- **`:network:network-manager`**:
  - `PeerPairingManager` generating cryptographically random 192-bit URL-safe pairing tokens with a strict 60-second TTL (`DEFAULT_TTL_MILLIS = 60_000L`), optional SHA-256 certificate fingerprint binding, single-use consumption tracking (`consumeIssuedToken`), `portly://pair?payload=...` URI codec, and `NsdDiscoveryManager` `_portly._tcp.` advertising.
  - Unit tests in `PeerPairingManagerTest` verifying URI encode/decode round-trip, wrong-token rejection, single-use consumption (replay rejected), and 60-second expiry rejection.
- **`:feature:feature-servers`**:
  - Added "Pair Peer" flow in `ServersScreen` / `ServersViewModel` allowing users to generate a 60s QR code & copy/share pairing URI for FTPS (with local server SHA-256 cert fingerprint) or FTP, provision an ephemeral `portly-peer` account in `CredentialVault`, auto-start the selected server, advertise `_portly._tcp.` over NSD, and count down remaining validity seconds in real time.
- **`:feature:feature-connections` & `:app`**:
  - Added deep-link intent filter for `portly://pair` in `AndroidManifest.xml` / `MainActivity.kt`.
  - Added Peer Pairing action and NSD `_portly._tcp.` tab in `ConnectionsScreen` ("Discover LAN Hosts & Peers" + "Pair with Portly Peer"), trusting the sender's SHA-256 certificate in `TrustedCertDao` and saving credentials into `CredentialVault` on user approval before connecting.

## Verification
- Command: `.\gradlew.bat assembleDebug :network:network-manager:testDebugUnitTest --console=plain`
- Result: `BUILD SUCCESSFUL`.
- Note: End-to-end two-device Wi-Fi pairing between physical Android phones was not executed in this headless JVM environment.
