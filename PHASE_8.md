# PHASE 8 — HTTP & HTTPS Servers (Ktor CIO + TLS + Web UI)

## Scope Completed
- Implemented `:protocol:protocol-http-server` (`KtorHttpProtocolServer`, `HttpWebUiHtml`, `HttpServerModule`):
  - Ktor CIO server bound to LAN by default (`lanOnlyBind`), serving an offline-capable responsive mobile Web UI (`browse`, `upload`, `download`, `mkdir`, `rename`, `delete`, `search`).
  - Streaming multipart uploads via `ByteReadChannel.copyTo` and `PartialContent` HTTP `Range` (`206 Partial Content`) downloads for large files.
  - Path traversal prevention (`PathValidator.canonicalizeUnderRoot`) returning `403 Forbidden`, per-user permissions, global read-only toggle, session cookies (`HttpOnly; SameSite=Lax`, 30m TTL), and 5-failure / 30s login rate-limiting (`429 Too Many Requests`).
- Implemented `:protocol:protocol-https-server` (`KtorHttpsProtocolServer`, `HttpsServerModule`):
  - Ktor CIO engine on ephemeral loopback `127.0.0.1:0` fronted by a TLS 1.2/1.3 `SSLServerSocket` using `CertificateManager` PKCS12 self-signed certificates (SHA-256 fingerprint, renewable via `renewCertificate()`).
  - Enforces `HttpOnly; Secure; SameSite=Lax` cookies, `Range` streaming, traversal blocks, rate-limiting, client kick, and redacted logs.

## Verification
- Executed `.\gradlew.bat assembleDebug :protocol:protocol-http-server:testDebugUnitTest :protocol:protocol-https-server:testDebugUnitTest --console=plain`.
- Result: `BUILD SUCCESSFUL` (902 tasks; `HttpServerIntegrationTest` and `HttpsServerIntegrationTest` verified login rate-limiting, cookie flags, TLS, traversal rejection, Range 206, and streaming multipart upload).
