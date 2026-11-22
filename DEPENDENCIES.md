# Portly Dependency & License Inventory

All dependencies are declared in [`gradle/libs.versions.toml`](gradle/libs.versions.toml) and verified against the project's licensing rule (**Apache-2.0**, **MIT**, **BSD**, or **LGPL-2.1-only with dynamic/AAR-linked Android compliance**). Notice that final stripped APK contribution is smaller than raw library artifact sizes because R8 (`isMinifyEnabled = true`, `isShrinkResources = true`) shrinks the full application to `8,224,929` bytes (`app-release.apk`).

| Coordinate | Version | Role | License | Observed Primary Artifact Size (bytes) |
|---|---:|---|---|---:|
| `org.apache.ftpserver:ftpserver-core` | `1.2.1` | Embedded FTP / FTPS server engine | Apache-2.0 | `319,560` |
| `commons-net:commons-net` | `3.13.0` | FTP / FTPS client protocol | Apache-2.0 | `337,311` |
| `com.hierynomus:sshj` | `0.41.2` | SFTP / SSH2 client + host key verification | Apache-2.0 | `599,969` |
| `io.ktor:ktor-server-core` (`ktor-server-core-jvm`) | `3.6.1` | Embedded HTTP/HTTPS server routing | Apache-2.0 | `1,021,169` |
| `io.ktor:ktor-server-cio` (`ktor-server-cio-jvm`) | `3.6.1` | Asynchronous CIO HTTP engine | Apache-2.0 | `119,056` |
| `com.squareup.okhttp3:okhttp` (`okhttp-android`) | `5.5.1` | WebDAV over HTTP/HTTPS transport | Apache-2.0 | `949,648` |
| `eu.agno3.jcifs:jcifs-ng` | `2.1.10` | SMB 2.02–3.1.1 client library | LGPL-2.1-only | `822,626` |
| `org.bouncycastle:bcprov-jdk18on` | `1.86` | Cryptography & X.509 support | MIT (Bouncy Castle License) | `7,224,011` |
| `org.bouncycastle:bcpkix-jdk18on` | `1.86` | PKCS12 / X.509 certificate builder | MIT (Bouncy Castle License) | `1,341,621` |
| `com.google.zxing:core` | `3.5.4` | Offline QR code matrix generation | Apache-2.0 | `610,364` |
| `io.coil-kt.coil3:coil-compose` (`coil-compose-android`) | `3.6.3` | Image preview loading | Apache-2.0 | `14,002` |
| `androidx.media3:media3-exoplayer` | `1.11.1` | Local video/audio preview playback | Apache-2.0 | `1,703,377` |
| `org.slf4j:slf4j-jdk14` | `2.0.17` | SLF4J logger bridge for JVM/Android protocol libs | MIT | `10,574` |
| `androidx.*` (Compose BOM `2026.09.00`, Room `2.8.5`, DataStore `1.3.0`, WorkManager `2.12.0`, Biometric `1.1.0`, Security Crypto `1.1.0`, DocumentFile `1.1.0`, Navigation `2.10.2`, Hilt `2.60.1`) | Managed in `libs.versions.toml` | UI, DI, persistence, background services | Apache-2.0 | Managed in AAR cache; minified into APK |
| `org.jetbrains.kotlinx:kotlinx-coroutines-*` | `1.11.0` | Coroutines & Flow | Apache-2.0 | Managed in JAR cache |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | `1.12.0` | JSON portability & pairing codecs | Apache-2.0 | Managed in JAR cache |
| **Test-only:** `org.apache.sshd:sshd-core`, `sshd-sftp` | `2.20.1` | Embedded SFTP server for integration tests | Apache-2.0 | Test classpath only (not packaged) |
| **Test-only:** `com.squareup.okhttp3:mockwebserver` | `5.5.1` | Local WebDAV mock server for tests | Apache-2.0 | Test classpath only (not packaged) |
| **Test-only:** `junit:junit` | `4.13.2` | JUnit test runner | EPL-1.0 | Test classpath only (not packaged) |

## Notes on Optional / Excluded Libraries
- **Sardine**: Deliberately omitted in favor of lighter WebDAV-over-OkHttp XML parsing (`OkHttpWebDavProtocolClient`), avoiding Apache HttpClient bloat.
- **zt-zip / native compression**: Deliberately omitted; standard JDK `java.util.zip` (`ZipOutputStream`, `ZipInputStream`, `ZipFile`) handles streaming 64KB ZIP creation, listing, and Zip-Slip-hardened extraction with zero extra dependencies.
- **Analytics / Ads / Telemetry**: None present anywhere in dependency graph.
