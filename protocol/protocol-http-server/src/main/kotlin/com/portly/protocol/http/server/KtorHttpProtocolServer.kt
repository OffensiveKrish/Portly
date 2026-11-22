package com.portly.protocol.http.server

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.portly.core.common.ChecksumUtils
import com.portly.core.common.Formatters
import com.portly.core.common.PathValidator
import com.portly.core.common.ProtocolException
import com.portly.core.database.CredentialVault
import com.portly.core.model.ConnectedClientInfo
import com.portly.core.model.LogLevel
import com.portly.core.model.ProtocolError
import com.portly.core.model.ProtocolServer
import com.portly.core.model.ServerConfig
import com.portly.core.model.ServerLogEntry
import com.portly.core.model.ServerPermission
import com.portly.core.model.ServerRuntimeState
import com.portly.core.model.ServerStatus
import com.portly.core.model.ServerType
import com.portly.core.security.LogRedactor
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.autohead.AutoHeadResponse
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.partialcontent.PartialContent
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.receiveParameters
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.jvm.javaio.copyTo
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@Serializable
internal data class WebFileEntryDto(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val sizeFormatted: String,
    val modifiedMillis: Long,
    val modifiedFormatted: String,
)

@Serializable
internal data class WebListResponse(
    val path: String,
    val entries: List<WebFileEntryDto>,
)

@Serializable
internal data class MkdirRequest(val parentPath: String, val name: String)

@Serializable
internal data class RenameRequest(val path: String, val newName: String)

@Serializable
internal data class DeleteRequest(val path: String)

@Serializable
internal data class BatchDeleteRequest(val paths: List<String>)

@Serializable
internal data class BatchDeleteResponse(val successCount: Int, val failedCount: Int)

@Serializable
internal data class HttpDeviceInfo(
    val deviceName: String,
    val androidVersion: String,
    val serverVersion: String,
    val totalStorageBytes: Long,
    val freeStorageBytes: Long,
    val usedStorageBytes: Long,
    val totalFormatted: String,
    val freeFormatted: String,
    val usedFormatted: String,
    val usedPercent: Int,
    val hasStorageAccess: Boolean = true,
)

private data class SessionRecord(
    val token: String,
    val username: String,
    val clientIp: String,
    val expiresAtMillis: Long,
)

private data class LoginRateLimit(
    val failures: Int = 0,
    val lockedUntilMillis: Long = 0L,
)

internal class KtorHttpProtocolServer(
    private val context: Context?,
    private val credentialVault: CredentialVault?,
    private val passwordResolver: (suspend (ServerConfig) -> Map<String, String>)? = null,
) : ProtocolServer {

    override val serverType: ServerType = ServerType.HTTP

    private val _state = MutableStateFlow(ServerRuntimeState(serverType = ServerType.HTTP))
    override val state: StateFlow<ServerRuntimeState> = _state.asStateFlow()

    private val _clients = MutableStateFlow<List<ConnectedClientInfo>>(emptyList())
    override val clients: StateFlow<List<ConnectedClientInfo>> = _clients.asStateFlow()

    private val _logs = MutableStateFlow<List<ServerLogEntry>>(emptyList())
    override val logs: StateFlow<List<ServerLogEntry>> = _logs.asStateFlow()

    private var embedded: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private var currentConfig: ServerConfig? = null
    private var localAddresses: List<String> = listOf("127.0.0.1")
    private val sessions = ConcurrentHashMap<String, SessionRecord>()
    private val kickedIps = ConcurrentHashMap.newKeySet<String>()
    private val loginFailuresByIp = ConcurrentHashMap<String, LoginRateLimit>()
    private val activeClients = ConcurrentHashMap<String, ConnectedClientInfo>()
    private val totalBytesServed = AtomicLong(0L)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun start(config: ServerConfig): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            stopInternal(userStopped = false)
            _state.value = ServerRuntimeState(
                serverType = ServerType.HTTP,
                status = ServerStatus.STARTING,
                port = config.port,
            )

            val passwords = resolvePasswords(config)
            val bindHost = "0.0.0.0"

            val server = embeddedServer(CIO, configure = {
                connector {
                    host = bindHost
                    port = config.port
                }
            }) {
                install(AutoHeadResponse)
                install(PartialContent)

                routing {
                    val handleLoginGet: suspend (ApplicationCall) -> Unit = { c ->
                        val ip = c.request.origin.remoteAddress
                        val lockRemaining = remainingLockoutSeconds(ip)
                        c.respondText(
                            HttpWebUiHtml.loginPage(lockedOutSeconds = lockRemaining),
                            ContentType.Text.Html,
                        )
                    }

                    val handleLoginPost: suspend (ApplicationCall) -> Unit = { c ->
                        val ip = c.request.origin.remoteAddress
                        val lockRemaining = remainingLockoutSeconds(ip)
                        if (lockRemaining > 0L) {
                            c.response.header(HttpHeaders.RetryAfter, lockRemaining.toString())
                            c.respondText(
                                HttpWebUiHtml.loginPage(lockedOutSeconds = lockRemaining),
                                ContentType.Text.Html,
                                HttpStatusCode.TooManyRequests,
                            )
                        } else {
                            val params = c.receiveParameters()
                            val user = params["username"]?.trim().orEmpty()
                            val pass = params["password"].orEmpty()
                            if (authenticateUser(config, passwords, user, pass)) {
                                loginFailuresByIp.remove(ip)
                                kickedIps.remove(ip)
                                val token = newSessionToken()
                                sessions[token] = SessionRecord(
                                    token = token,
                                    username = user,
                                    clientIp = ip,
                                    expiresAtMillis = System.currentTimeMillis() + SESSION_TTL_MS,
                                )
                                c.response.header(
                                    HttpHeaders.SetCookie,
                                    "$COOKIE_NAME=$token; Path=/; HttpOnly; SameSite=Lax; Max-Age=${SESSION_TTL_MS / 1000}",
                                )
                                recordClientActivity(ip, user, "LOGIN", null, 0L)
                                appendLog(LogLevel.INFO, "Web login succeeded for $user from $ip", config)
                                c.respondRedirect("/")
                            } else {
                                val prev = loginFailuresByIp[ip] ?: LoginRateLimit()
                                val nextFailures = prev.failures + 1
                                val lockedUntil = if (nextFailures >= 5) {
                                    System.currentTimeMillis() + 30_000L
                                } else {
                                    0L
                                }
                                loginFailuresByIp[ip] = LoginRateLimit(
                                    failures = if (nextFailures >= 5) 0 else nextFailures,
                                    lockedUntilMillis = lockedUntil,
                                )
                                appendLog(LogLevel.WARN, "Failed web login attempt for $user from $ip", config)
                                if (lockedUntil > 0L) {
                                    c.response.header(HttpHeaders.RetryAfter, "30")
                                    c.respondText(
                                        HttpWebUiHtml.loginPage(lockedOutSeconds = 30),
                                        ContentType.Text.Html,
                                        HttpStatusCode.TooManyRequests,
                                    )
                                } else {
                                    c.respondText(
                                        HttpWebUiHtml.loginPage(errorMessage = "Invalid username or password."),
                                        ContentType.Text.Html,
                                        HttpStatusCode.Unauthorized,
                                    )
                                }
                            }
                        }
                    }

                    get("/login") { handleLoginGet(call) }
                    get("/Web/Account/Login.htm") { handleLoginGet(call) }
                    post("/login") { handleLoginPost(call) }
                    post("/Web/Account/Login.htm") { handleLoginPost(call) }
                    get("/Web/Shared/forms.css") {
                        call.respondText(HttpWebUiHtml.FORMS_CSS, ContentType.parse("text/css"))
                    }
                    get("/Web/Shared/images/gs-atom-logo.png") {
                        call.respondBytes(HttpWebUiHtml.LOGO_PNG_BYTES, ContentType.parse("image/png"))
                    }

                    post("/logout") {
                        call.request.cookies[COOKIE_NAME]?.let { sessions.remove(it) }
                        call.response.header(
                            HttpHeaders.SetCookie,
                            "$COOKIE_NAME=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0",
                        )
                        call.respondRedirect("/login")
                    }

                    get("/logout") {
                        call.request.cookies[COOKIE_NAME]?.let { sessions.remove(it) }
                        call.response.header(
                            HttpHeaders.SetCookie,
                            "$COOKIE_NAME=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0",
                        )
                        call.respondRedirect("/login")
                    }

                    get("/assets/logo.png") {
                        call.respondBytes(HttpWebUiHtml.LOGO_PNG_BYTES, ContentType.parse("image/png"))
                    }

                    get("/favicon.ico") {
                        call.respondBytes(HttpWebUiHtml.LOGO_PNG_BYTES, ContentType.parse("image/png"))
                    }

                    get("/api/device-info") {
                        val user = authorizeOrChallenge(call, config, passwords, redirectToLogin = false) ?: return@get
                        call.respondText(
                            json.encodeToString(getDeviceInfo()),
                            ContentType.Application.Json,
                        )
                    }

                    post("/api/batch-delete") {
                        val user = authorizeOrChallenge(call, config, passwords, redirectToLogin = false) ?: return@post
                        if (config.readOnly || !hasPermission(config, user, ServerPermission.DELETE)) {
                            call.respond(HttpStatusCode.Forbidden, "Delete permission denied")
                            return@post
                        }
                        val req = json.decodeFromString<BatchDeleteRequest>(call.receiveText())
                        var success = 0
                        var failed = 0
                        for (p in req.paths) {
                            if (deleteItem(config, p)) success++ else failed++
                        }
                        appendLog(LogLevel.INFO, "$user batch deleted $success item(s) ($failed failed)", config)
                        call.respondText(
                            json.encodeToString(BatchDeleteResponse(success, failed)),
                            ContentType.Application.Json,
                        )
                    }

                    get("/api/zip-download") {
                        val user = authorizeOrChallenge(call, config, passwords, redirectToLogin = true) ?: return@get
                        if (!hasPermission(config, user, ServerPermission.READ)) {
                            call.respond(HttpStatusCode.Forbidden, "Read permission denied")
                            return@get
                        }
                        val pathsParam = call.request.queryParameters["paths"].orEmpty()
                        val paths = pathsParam.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        if (paths.isEmpty()) {
                            call.respond(HttpStatusCode.BadRequest, "No paths provided")
                            return@get
                        }
                        serveZipDownload(call, config, user, paths)
                    }

                    get("/preview") {
                        val user = authorizeOrChallenge(call, config, passwords, redirectToLogin = true) ?: return@get
                        if (!hasPermission(config, user, ServerPermission.READ)) {
                            call.respond(HttpStatusCode.Forbidden, "Read permission denied")
                            return@get
                        }
                        val reqPath = call.request.queryParameters["path"] ?: ""
                        serveFilePreview(call, config, user, reqPath)
                    }

                    get("/") {
                        val user = authorizeOrChallenge(call, config, passwords, redirectToLogin = true) ?: return@get
                        recordClientActivity(call.request.origin.remoteAddress, user, "BROWSE", "/", 0L)
                        call.respondText(
                            HttpWebUiHtml.fileBrowserPage("/", config.readOnly, config.requireAuth, user),
                            ContentType.Text.Html,
                        )
                    }

                    get("/api/list") {
                        val user = authorizeOrChallenge(call, config, passwords, redirectToLogin = false) ?: return@get
                        if (!hasPermission(config, user, ServerPermission.READ)) {
                            call.respond(HttpStatusCode.Forbidden, "Read permission denied")
                            return@get
                        }
                        val reqPath = call.request.queryParameters["path"] ?: "/"
                        val entries = listDirectory(config, reqPath)
                        if (entries == null) {
                            call.respond(HttpStatusCode.Forbidden, "Invalid or unsafe path")
                            return@get
                        }
                        recordClientActivity(call.request.origin.remoteAddress, user, "LIST", reqPath, 0L)
                        call.respondText(
                            json.encodeToString(WebListResponse(PathValidator.normalizeRemotePath(reqPath), entries)),
                            ContentType.Application.Json,
                        )
                    }

                    get("/api/search") {
                        val user = authorizeOrChallenge(call, config, passwords, redirectToLogin = false) ?: return@get
                        if (!hasPermission(config, user, ServerPermission.READ)) {
                            call.respond(HttpStatusCode.Forbidden, "Read permission denied")
                            return@get
                        }
                        val reqPath = call.request.queryParameters["path"] ?: "/"
                        val query = call.request.queryParameters["q"]?.trim().orEmpty()
                        val entries = searchDirectory(config, reqPath, query)
                        if (entries == null) {
                            call.respond(HttpStatusCode.Forbidden, "Invalid or unsafe path")
                            return@get
                        }
                        call.respondText(
                            json.encodeToString(WebListResponse(PathValidator.normalizeRemotePath(reqPath), entries)),
                            ContentType.Application.Json,
                        )
                    }

                    get("/download") {
                        val user = authorizeOrChallenge(call, config, passwords, redirectToLogin = true) ?: return@get
                        if (!hasPermission(config, user, ServerPermission.READ)) {
                            call.respond(HttpStatusCode.Forbidden, "Read permission denied")
                            return@get
                        }
                        val reqPath = call.request.queryParameters["path"] ?: ""
                        serveFileDownload(call, config, user, reqPath)
                    }

                    get("/api/download") {
                        val user = authorizeOrChallenge(call, config, passwords, redirectToLogin = true) ?: return@get
                        if (!hasPermission(config, user, ServerPermission.READ)) {
                            call.respond(HttpStatusCode.Forbidden, "Read permission denied")
                            return@get
                        }
                        val reqPath = call.request.queryParameters["path"] ?: ""
                        serveFileDownload(call, config, user, reqPath)
                    }

                    post("/api/upload") {
                        val user = authorizeOrChallenge(call, config, passwords, redirectToLogin = false) ?: return@post
                        if (config.readOnly || !hasPermission(config, user, ServerPermission.WRITE)) {
                            call.respond(HttpStatusCode.Forbidden, "Write permission denied")
                            return@post
                        }
                        val dirPath = call.request.queryParameters["path"] ?: "/"
                        handleUpload(call, config, user, dirPath)
                    }

                    post("/api/mkdir") {
                        val user = authorizeOrChallenge(call, config, passwords, redirectToLogin = false) ?: return@post
                        if (config.readOnly || !hasPermission(config, user, ServerPermission.MKDIR)) {
                            call.respond(HttpStatusCode.Forbidden, "Mkdir permission denied")
                            return@post
                        }
                        val body = json.decodeFromString<MkdirRequest>(call.receiveText())
                        val ok = createFolder(config, body.parentPath, body.name)
                        if (ok) {
                            appendLog(LogLevel.INFO, "$user created folder ${body.name}", config)
                            call.respond(HttpStatusCode.OK, "Created")
                        } else {
                            call.respond(HttpStatusCode.Forbidden, "Could not create folder")
                        }
                    }

                    post("/api/rename") {
                        val user = authorizeOrChallenge(call, config, passwords, redirectToLogin = false) ?: return@post
                        if (config.readOnly || !hasPermission(config, user, ServerPermission.WRITE)) {
                            call.respond(HttpStatusCode.Forbidden, "Write permission denied")
                            return@post
                        }
                        val body = json.decodeFromString<RenameRequest>(call.receiveText())
                        if (renameItem(config, body.path, body.newName)) {
                            call.respond(HttpStatusCode.OK, "Renamed")
                        } else {
                            call.respond(HttpStatusCode.Forbidden, "Rename rejected")
                        }
                    }

                    post("/api/delete") {
                        val user = authorizeOrChallenge(call, config, passwords, redirectToLogin = false) ?: return@post
                        if (config.readOnly || !hasPermission(config, user, ServerPermission.DELETE)) {
                            call.respond(HttpStatusCode.Forbidden, "Delete permission denied")
                            return@post
                        }
                        val body = json.decodeFromString<DeleteRequest>(call.receiveText())
                        if (deleteItem(config, body.path)) {
                            appendLog(LogLevel.INFO, "$user deleted ${body.path}", config)
                            call.respond(HttpStatusCode.OK, "Deleted")
                        } else {
                            call.respond(HttpStatusCode.Forbidden, "Delete rejected")
                        }
                    }
                }
            }

            server.start(wait = false)
            val resolvedPort = server.engine.resolvedConnectors().firstOrNull()?.port ?: config.port
            embedded = server
            currentConfig = config.copy(port = resolvedPort)
            totalBytesServed.set(0L)

            appendLog(LogLevel.INFO, "HTTP server started on $bindHost:$resolvedPort", config)
            _state.value = ServerRuntimeState(
                serverType = ServerType.HTTP,
                status = ServerStatus.RUNNING,
                port = resolvedPort,
                activeUrls = buildUrls(resolvedPort),
                activeClientCount = 0,
                totalBytesServed = 0L,
                startedAtMillis = System.currentTimeMillis(),
                userStoppedExplicitly = false,
            )
            Unit
        }.onFailure { err ->
            appendLog(LogLevel.ERROR, "Failed to start HTTP server: ${err.message}", config)
            _state.value = ServerRuntimeState(
                serverType = ServerType.HTTP,
                status = ServerStatus.ERROR,
                port = config.port,
                lastError = ProtocolError.ProtocolFailure(
                    userMessage = "Could not start HTTP server on port ${config.port}.",
                    nextStep = "Try another port (e.g. 8080+).",
                    technicalDetail = err.message,
                ),
            )
        }
    }

    override suspend fun stop() = withContext(Dispatchers.IO) {
        stopInternal(userStopped = true)
    }

    private fun stopInternal(userStopped: Boolean) {
        val srv = embedded
        embedded = null
        sessions.clear()
        activeClients.clear()
        _clients.value = emptyList()
        if (srv != null) {
            runCatching { srv.stop(300L, 1_000L) }
            appendLog(LogLevel.INFO, "HTTP server stopped", currentConfig)
        }
        _state.update {
            it.copy(
                status = ServerStatus.STOPPED,
                activeUrls = emptyList(),
                activeClientCount = 0,
                userStoppedExplicitly = userStopped,
            )
        }
    }

    override suspend fun kickClient(clientId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = activeClients.remove(clientId)
                ?: throw ProtocolException(ProtocolError.PathNotFound("Client not active"))
            kickedIps.add(client.remoteAddress)
            sessions.entries.removeIf { it.value.clientIp == client.remoteAddress }
            _clients.value = activeClients.values.toList()
            _state.update { it.copy(activeClientCount = activeClients.size) }
            appendLog(LogLevel.WARN, "Kicked HTTP client ${client.remoteAddress}", currentConfig)
            Unit
        }
    }

    override fun updateLocalAddresses(addresses: List<String>) {
        localAddresses = addresses.ifEmpty { listOf("127.0.0.1") }
        _state.update { current ->
            if (current.status == ServerStatus.RUNNING) {
                current.copy(activeUrls = buildUrls(current.port))
            } else {
                current
            }
        }
    }

    private suspend fun authorizeOrChallenge(
        call: ApplicationCall,
        config: ServerConfig,
        passwords: Map<String, String>,
        redirectToLogin: Boolean,
    ): String? {
        val ip = call.request.origin.remoteAddress
        if (config.lanOnlyBind && !config.allowPublicBindOptIn && !isLanOrLoopback(ip)) {
            call.respond(HttpStatusCode.Forbidden, "LAN-only access enforced")
            return null
        }
        if (ip in kickedIps) {
            call.respond(HttpStatusCode.Forbidden, "Session terminated by host")
            return null
        }
        if (!config.requireAuth) return "anonymous"

        val cookieToken = call.request.cookies[COOKIE_NAME]
        val session = cookieToken?.let { sessions[it] }
        if (session != null) {
            if (session.expiresAtMillis > System.currentTimeMillis()) {
                return session.username
            }
            sessions.remove(cookieToken)
        }

        val authHeader = call.request.headers[HttpHeaders.Authorization]
        if (authHeader != null && authHeader.startsWith("Basic ", ignoreCase = true)) {
            val decoded = runCatching {
                String(Base64.getDecoder().decode(authHeader.substring(6).trim()), Charsets.UTF_8)
            }.getOrNull()
            val sep = decoded?.indexOf(':') ?: -1
            if (sep > 0) {
                val u = decoded!!.substring(0, sep)
                val p = decoded.substring(sep + 1)
                if (authenticateUser(config, passwords, u, p)) {
                    return u
                }
            }
        }

        if (redirectToLogin) {
            call.respondRedirect("/Web/Account/Login.htm")
        } else {
            call.respond(HttpStatusCode.Unauthorized, "Authentication required")
        }
        return null
    }

    private fun authenticateUser(
        config: ServerConfig,
        passwords: Map<String, String>,
        username: String,
        password: String,
    ): Boolean {
        val account = config.users.firstOrNull { it.username == username } ?: return false
        if (account.isAnonymous) return true
        val expected = passwords[username] ?: (if (account.secretRef.isNullOrBlank()) "portly" else return false)
        return MessageDigest.isEqual(expected.toByteArray(Charsets.UTF_8), password.toByteArray(Charsets.UTF_8))
    }

    private fun hasPermission(config: ServerConfig, username: String, perm: ServerPermission): Boolean {
        if (!config.requireAuth) return perm == ServerPermission.READ || !config.readOnly
        val acct = config.users.firstOrNull { it.username == username } ?: return false
        return perm in acct.permissions
    }

    private fun remainingLockoutSeconds(ip: String): Long {
        val rec = loginFailuresByIp[ip] ?: return 0L
        val rem = rec.lockedUntilMillis - System.currentTimeMillis()
        return if (rem > 0L) ((rem + 999L) / 1000L) else 0L
    }

    private fun getEffectiveRootDir(config: ServerConfig): File {
        val configured = File(config.rootDirectoryPath)
        val ext = runCatching { android.os.Environment.getExternalStorageDirectory() }.getOrNull()
        if (config.rootTreeUriString.isNullOrBlank()) {
            if (config.rootDirectoryPath.contains("PortlyShare") ||
                config.rootDirectoryPath.contains("com.portly") ||
                !configured.exists()
            ) {
                if (ext != null && ext.exists() && ext.canRead()) {
                    return ext
                }
            }
        }
        return configured.apply { mkdirs() }
    }

    private fun resolveVirtualFile(root: File, virtualPath: String): File? {
        val direct = PathValidator.canonicalizeUnderRoot(root, virtualPath).getOrNull()
        if (direct != null && direct.exists()) return direct
        val norm = PathValidator.normalizeRemotePath(virtualPath)
        if (norm.equals("/Downloads", ignoreCase = true) || norm.equals("/Download", ignoreCase = true)) {
            val dl = File(root, "Download")
            if (dl.exists()) return dl
            val dls = File(root, "Downloads")
            if (dls.exists()) return dls
        }
        return direct
    }

    private fun listDirectory(config: ServerConfig, virtualPath: String): List<WebFileEntryDto>? {
        val norm = PathValidator.normalizeRemotePath(virtualPath)
        val treeUri = config.rootTreeUriString?.takeIf { it.isNotBlank() }?.let(Uri::parse)
        return if (treeUri != null && context != null) {
            val dir = resolveSafDoc(context, treeUri, norm) ?: return null
            if (!dir.isDirectory) return null
            dir.listFiles().mapNotNull { child ->
                val name = child.name ?: return@mapNotNull null
                val path = PathValidator.joinRemotePath(norm, name)
                val size = if (child.isDirectory) 0L else child.length()
                val mod = child.lastModified()
                WebFileEntryDto(name, path, child.isDirectory, size, Formatters.formatBytes(size), mod, Formatters.formatTimestamp(mod))
            }.sortedWith(compareBy<WebFileEntryDto> { !it.isDirectory }.thenBy { it.name.lowercase() })
        } else {
            val root = getEffectiveRootDir(config)
            val dir = resolveVirtualFile(root, virtualPath) ?: return null
            if (!dir.exists() || !dir.isDirectory) return null
            (dir.listFiles() ?: emptyArray()).map { file ->
                val path = PathValidator.joinRemotePath(norm, file.name)
                val size = if (file.isDirectory) 0L else file.length()
                val mod = file.lastModified()
                WebFileEntryDto(file.name, path, file.isDirectory, size, Formatters.formatBytes(size), mod, Formatters.formatTimestamp(mod))
            }.sortedWith(compareBy<WebFileEntryDto> { !it.isDirectory }.thenBy { it.name.lowercase() })
        }
    }

    private fun searchDirectory(config: ServerConfig, startPath: String, query: String): List<WebFileEntryDto>? {
        if (query.isBlank()) return listDirectory(config, startPath)
        val q = query.lowercase()
        val treeUri = config.rootTreeUriString?.takeIf { it.isNotBlank() }?.let(Uri::parse)
        val results = mutableListOf<WebFileEntryDto>()
        if (treeUri != null && context != null) {
            val rootDoc = resolveSafDoc(context, treeUri, startPath) ?: return null
            val queue = ArrayDeque<Pair<DocumentFile, String>>()
            queue.addLast(rootDoc to PathValidator.normalizeRemotePath(startPath))
            while (queue.isNotEmpty() && results.size < 200) {
                val (doc, vPath) = queue.removeFirst()
                for (child in doc.listFiles()) {
                    val name = child.name ?: continue
                    val childV = PathValidator.joinRemotePath(vPath, name)
                    if (name.lowercase().contains(q)) {
                        val size = if (child.isDirectory) 0L else child.length()
                        val mod = child.lastModified()
                        results += WebFileEntryDto(name, childV, child.isDirectory, size, Formatters.formatBytes(size), mod, Formatters.formatTimestamp(mod))
                    }
                    if (child.isDirectory) queue.addLast(child to childV)
                }
            }
            return results
        } else {
            val root = getEffectiveRootDir(config)
            val start = resolveVirtualFile(root, startPath) ?: return null
            val queue = ArrayDeque<Pair<File, String>>()
            queue.addLast(start to PathValidator.normalizeRemotePath(startPath))
            while (queue.isNotEmpty() && results.size < 200) {
                val (dir, vPath) = queue.removeFirst()
                for (child in dir.listFiles() ?: emptyArray()) {
                    val childV = PathValidator.joinRemotePath(vPath, child.name)
                    if (child.name.lowercase().contains(q)) {
                        val size = if (child.isDirectory) 0L else child.length()
                        val mod = child.lastModified()
                        results += WebFileEntryDto(child.name, childV, child.isDirectory, size, Formatters.formatBytes(size), mod, Formatters.formatTimestamp(mod))
                    }
                    if (child.isDirectory) queue.addLast(child to childV)
                }
            }
            return results
        }
    }

    private suspend fun serveFileDownload(
        call: ApplicationCall,
        config: ServerConfig,
        username: String,
        reqPath: String,
    ) {
        val ip = call.request.origin.remoteAddress
        val treeUri = config.rootTreeUriString?.takeIf { it.isNotBlank() }?.let(Uri::parse)
        if (treeUri != null && context != null) {
            val doc = resolveSafDoc(context, treeUri, reqPath)
            if (doc == null || !doc.exists() || doc.isDirectory) {
                call.respond(HttpStatusCode.Forbidden, "Unavailable or unsafe path")
                return
            }
            val size = doc.length()
            val name = doc.name ?: "download"
            call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=\"${PathValidator.sanitizeFileName(name)}\"")
            call.response.header(HttpHeaders.AcceptRanges, "bytes")
            val input = context.contentResolver.openInputStream(doc.uri)
            if (input == null) {
                call.respond(HttpStatusCode.InternalServerError, "Cannot open file")
                return
            }
            streamInputWithRange(call, input, size, ip, username, reqPath)
        } else {
            val root = getEffectiveRootDir(config)
            val target = resolveVirtualFile(root, reqPath)
            if (target == null) {
                call.respond(HttpStatusCode.Forbidden, "Path traversal blocked")
                return
            }
            if (!target.exists() || !target.isFile) {
                call.respond(HttpStatusCode.NotFound, "File not found")
                return
            }
            call.response.header(
                HttpHeaders.ContentDisposition,
                "attachment; filename=\"${PathValidator.sanitizeFileName(target.name)}\"",
            )
            recordClientActivity(ip, username, "DOWNLOAD", reqPath, target.length())
            call.respondFile(target)
        }
    }

    private suspend fun streamInputWithRange(
        call: ApplicationCall,
        input: InputStream,
        totalSize: Long,
        ip: String,
        username: String,
        reqPath: String,
    ) {
        input.use { stream ->
            val rangeHeader = call.request.headers[HttpHeaders.Range]
            val parsedRange = parseSingleRange(rangeHeader, totalSize)
            if (parsedRange != null) {
                val (start, end) = parsedRange
                val len = (end - start + 1).coerceAtLeast(0L)
                if (start > 0L) stream.skip(start)
                call.response.header(HttpHeaders.ContentRange, "bytes $start-$end/$totalSize")
                call.response.header(HttpHeaders.ContentLength, len.toString())
                call.respondBytesWriter(status = HttpStatusCode.PartialContent) {
                    val buf = ByteArray(ChecksumUtils.STREAM_BUFFER_SIZE)
                    var remaining = len
                    while (remaining > 0L) {
                        val toRead = minOf(buf.size.toLong(), remaining).toInt()
                        val read = stream.read(buf, 0, toRead)
                        if (read <= 0) break
                        writeFully(buf, 0, read)
                        remaining -= read
                    }
                }
                recordClientActivity(ip, username, "DOWNLOAD", reqPath, len)
            } else {
                call.respondBytesWriter(status = HttpStatusCode.OK) {
                    val buf = ByteArray(ChecksumUtils.STREAM_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val read = stream.read(buf)
                        if (read <= 0) break
                        writeFully(buf, 0, read)
                        total += read
                    }
                    recordClientActivity(ip, username, "DOWNLOAD", reqPath, total)
                }
            }
        }
    }

    private suspend fun handleUpload(
        call: ApplicationCall,
        config: ServerConfig,
        username: String,
        dirPath: String,
    ) {
        val ip = call.request.origin.remoteAddress
        val treeUri = config.rootTreeUriString?.takeIf { it.isNotBlank() }?.let(Uri::parse)
        val root = getEffectiveRootDir(config)
        val targetLocalDir = if (treeUri == null) {
            resolveVirtualFile(root, dirPath)
        } else {
            null
        }
        val targetSafDir = if (treeUri != null && context != null) {
            resolveSafDoc(context, treeUri, dirPath)
        } else {
            null
        }

        if (targetLocalDir == null && targetSafDir == null) {
            call.respond(HttpStatusCode.Forbidden, "Unsafe upload directory")
            return
        }

        var uploadedCount = 0
        var recipient: String? = null
        var category: String? = null
        var remarks: String? = null
        val multipart = call.receiveMultipart()
        multipart.forEachPart { part ->
            when (part) {
                is PartData.FormItem -> {
                    when (part.name) {
                        "recipient" -> recipient = part.value.takeIf { it.isNotBlank() }
                        "category" -> category = part.value.takeIf { it.isNotBlank() }
                        "remarks" -> remarks = part.value.takeIf { it.isNotBlank() }
                    }
                }
                is PartData.FileItem -> {
                    val safeName = PathValidator.sanitizeFileName(part.originalFileName ?: "upload.bin")
                    val channel = part.provider()
                    var bytes = 0L
                    if (targetSafDir != null && context != null) {
                        val created = targetSafDir.createFile(Formatters.guessMimeType(safeName), safeName)
                        if (created != null) {
                            context.contentResolver.openOutputStream(created.uri)?.use { out ->
                                bytes = channel.copyTo(out)
                            }
                        }
                    } else if (targetLocalDir != null) {
                        val destFile = PathValidator.canonicalizeUnderRoot(targetLocalDir, safeName).getOrNull()
                        if (destFile != null) {
                            destFile.parentFile?.mkdirs()
                            destFile.outputStream().buffered(ChecksumUtils.STREAM_BUFFER_SIZE).use { out ->
                                bytes = channel.copyTo(out)
                            }
                        }
                    }
                    uploadedCount++
                    recordClientActivity(ip, username, "UPLOAD", "$dirPath/$safeName", bytes)
                }
                else -> Unit
            }
            part.dispose()
        }
        val details = buildList {
            recipient?.let { add("Recipient: $it") }
            category?.let { add("Category: $it") }
            remarks?.let { add("Remarks: $it") }
        }.takeIf { it.isNotEmpty() }?.joinToString(", ", prefix = " (", postfix = ")") ?: ""
        appendLog(LogLevel.INFO, "$username uploaded $uploadedCount file(s) to $dirPath$details", config)
        call.respond(HttpStatusCode.OK, "Uploaded $uploadedCount file(s)")
    }

    private fun createFolder(config: ServerConfig, parentPath: String, name: String): Boolean {
        val safeName = PathValidator.sanitizeFileName(name)
        val treeUri = config.rootTreeUriString?.takeIf { it.isNotBlank() }?.let(Uri::parse)
        return if (treeUri != null && context != null) {
            val parent = resolveSafDoc(context, treeUri, parentPath) ?: return false
            parent.createDirectory(safeName) != null
        } else {
            val root = getEffectiveRootDir(config)
            val parent = resolveVirtualFile(root, parentPath) ?: return false
            val target = PathValidator.canonicalizeUnderRoot(parent, safeName).getOrNull() ?: return false
            target.mkdirs() || target.isDirectory
        }
    }

    private fun renameItem(config: ServerConfig, path: String, newName: String): Boolean {
        val safeName = PathValidator.sanitizeFileName(newName)
        val treeUri = config.rootTreeUriString?.takeIf { it.isNotBlank() }?.let(Uri::parse)
        return if (treeUri != null && context != null) {
            resolveSafDoc(context, treeUri, path)?.renameTo(safeName) == true
        } else {
            val root = getEffectiveRootDir(config)
            val src = resolveVirtualFile(root, path) ?: return false
            if (src == root.canonicalFile) return false
            val dest = File(src.parentFile, safeName)
            PathValidator.canonicalizeUnderRoot(root, dest.relativeTo(root.canonicalFile).path).getOrNull() ?: return false
            src.renameTo(dest)
        }
    }

    private fun deleteItem(config: ServerConfig, path: String): Boolean {
        val treeUri = config.rootTreeUriString?.takeIf { it.isNotBlank() }?.let(Uri::parse)
        return if (treeUri != null && context != null) {
            resolveSafDoc(context, treeUri, path)?.delete() == true
        } else {
            val root = getEffectiveRootDir(config)
            val target = resolveVirtualFile(root, path) ?: return false
            if (target == root.canonicalFile) return false
            target.deleteRecursively()
        }
    }

    private fun getDeviceInfo(): HttpDeviceInfo {
        val model = "${android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${android.os.Build.MODEL}"
        val osVer = "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})"
        val ext = runCatching { android.os.Environment.getExternalStorageDirectory() }.getOrNull()
        val root = currentConfig?.let { getEffectiveRootDir(it) } ?: ext
        val rootPath = root?.absolutePath ?: ext?.path ?: "/"
        val stat = runCatching { android.os.StatFs(rootPath) }.getOrNull()
            ?: runCatching { android.os.StatFs(android.os.Environment.getExternalStorageDirectory().path) }.getOrNull()
        val total = stat?.totalBytes ?: (128L * 1024 * 1024 * 1024)
        val free = stat?.availableBytes ?: (64L * 1024 * 1024 * 1024)
        val used = (total - free).coerceAtLeast(0L)
        val pct = if (total > 0L) ((used * 100) / total).toInt() else 0
        val hasAccess = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            android.os.Environment.isExternalStorageManager()
        } else {
            ext?.canRead() ?: true
        }
        return HttpDeviceInfo(
            deviceName = model,
            androidVersion = osVer,
            serverVersion = "2026.11.0",
            totalStorageBytes = total,
            freeStorageBytes = free,
            usedStorageBytes = used,
            totalFormatted = Formatters.formatBytes(total),
            freeFormatted = Formatters.formatBytes(free),
            usedFormatted = Formatters.formatBytes(used),
            usedPercent = pct,
            hasStorageAccess = hasAccess,
        )
    }

    private suspend fun serveFilePreview(call: ApplicationCall, config: ServerConfig, username: String, reqPath: String) {
        val ip = call.request.origin.remoteAddress
        val treeUri = config.rootTreeUriString?.takeIf { it.isNotBlank() }?.let(Uri::parse)
        if (treeUri != null && context != null) {
            val doc = resolveSafDoc(context, treeUri, reqPath)
            if (doc == null || !doc.exists() || doc.isDirectory) {
                call.respond(HttpStatusCode.Forbidden, "Unavailable or unsafe path")
                return
            }
            val input = context.contentResolver.openInputStream(doc.uri) ?: run {
                call.respond(HttpStatusCode.InternalServerError, "Cannot open file")
                return
            }
            val ext = (doc.name ?: "").substringAfterLast('.', "").lowercase()
            val mimeType = resolveMimeType(ext)
            call.response.header(HttpHeaders.ContentDisposition, "inline; filename=\"${PathValidator.sanitizeFileName(doc.name ?: "file")}\"")
            call.response.header(HttpHeaders.ContentType, mimeType.toString())
            streamInputWithRange(call, input, doc.length(), ip, username, reqPath)
        } else {
            val root = getEffectiveRootDir(config)
            val target = resolveVirtualFile(root, reqPath) ?: run {
                call.respond(HttpStatusCode.Forbidden, "Path traversal blocked")
                return
            }
            if (!target.exists() || !target.isFile) {
                call.respond(HttpStatusCode.NotFound, "File not found")
                return
            }
            val ext = target.extension.lowercase()
            val mimeType = resolveMimeType(ext)
            call.response.header(HttpHeaders.ContentDisposition, "inline; filename=\"${PathValidator.sanitizeFileName(target.name)}\"")
            call.response.header(HttpHeaders.ContentType, mimeType.toString())
            recordClientActivity(ip, username, "PREVIEW", reqPath, target.length())
            call.respondFile(target)
        }
    }

    private suspend fun serveZipDownload(call: ApplicationCall, config: ServerConfig, username: String, paths: List<String>) {
        val ip = call.request.origin.remoteAddress
        val root = getEffectiveRootDir(config)
        val tempDir = context?.cacheDir ?: File(System.getProperty("java.io.tmpdir") ?: ".")
        val tempZip = File.createTempFile("portly_export_", ".zip", tempDir)
        try {
            java.util.zip.ZipOutputStream(tempZip.outputStream().buffered(ChecksumUtils.STREAM_BUFFER_SIZE)).use { zos ->
                for (p in paths) {
                    val file = resolveVirtualFile(root, p) ?: continue
                    if (!file.exists()) continue
                    addEntryToZip(zos, file, file.name)
                }
            }
            call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=\"portly_download.zip\"")
            recordClientActivity(ip, username, "ZIP_DOWNLOAD", paths.joinToString(","), tempZip.length())
            call.respondFile(tempZip)
        } finally {
            tempZip.delete()
        }
    }

    private fun addEntryToZip(zos: java.util.zip.ZipOutputStream, file: File, relPath: String) {
        if (file.isDirectory) {
            val children = file.listFiles() ?: emptyArray()
            if (children.isEmpty()) {
                zos.putNextEntry(java.util.zip.ZipEntry("$relPath/"))
                zos.closeEntry()
            } else {
                for (c in children) {
                    addEntryToZip(zos, c, "$relPath/${c.name}")
                }
            }
        } else {
            zos.putNextEntry(java.util.zip.ZipEntry(relPath))
            file.inputStream().buffered(ChecksumUtils.STREAM_BUFFER_SIZE).use { it.copyTo(zos) }
            zos.closeEntry()
        }
    }

    private fun resolveMimeType(ext: String): ContentType = when (ext) {
        "jpg", "jpeg" -> ContentType.Image.JPEG
        "png" -> ContentType.Image.PNG
        "gif" -> ContentType.Image.GIF
        "webp" -> ContentType.parse("image/webp")
        "svg" -> ContentType.Image.SVG
        "bmp" -> ContentType.parse("image/bmp")
        "mp4" -> ContentType.Video.MP4
        "webm" -> ContentType.parse("video/webm")
        "mkv" -> ContentType.parse("video/x-matroska")
        "mp3" -> ContentType.Audio.MPEG
        "wav" -> ContentType.parse("audio/wav")
        "ogg" -> ContentType.Audio.OGG
        "m4a", "aac" -> ContentType.parse("audio/mp4")
        "flac" -> ContentType.parse("audio/flac")
        "pdf" -> ContentType.parse("application/pdf")
        "txt", "log", "md" -> ContentType.Text.Plain
        "json" -> ContentType.Application.Json
        "xml" -> ContentType.Text.Xml
        "html", "htm" -> ContentType.Text.Html
        "css" -> ContentType.Text.CSS
        "js" -> ContentType.parse("text/javascript")
        "py", "kt", "java", "sh", "yml", "yaml" -> ContentType.Text.Plain
        else -> ContentType.Application.OctetStream
    }

    private fun resolveSafDoc(ctx: Context, rootUri: Uri, virtualPath: String): DocumentFile? {
        if (virtualPath.contains("..") || virtualPath.contains('\u0000')) return null
        var cur = DocumentFile.fromTreeUri(ctx, rootUri) ?: return null
        val norm = PathValidator.normalizeRemotePath(virtualPath)
        if (norm == "/") return cur
        for (seg in norm.trimStart('/').split('/')) {
            if (seg.isEmpty()) continue
            cur = cur.findFile(seg) ?: return null
        }
        return cur
    }

    private fun parseSingleRange(header: String?, totalSize: Long): Pair<Long, Long>? {
        if (header == null || !header.startsWith("bytes=") || totalSize <= 0L) return null
        val spec = header.removePrefix("bytes=").substringBefore(',').trim()
        val parts = spec.split('-')
        if (parts.size != 2) return null
        val start = parts[0].toLongOrNull() ?: 0L
        val end = parts[1].toLongOrNull()?.coerceAtMost(totalSize - 1) ?: (totalSize - 1)
        if (start > end || start < 0L) return null
        return start to end
    }

    private suspend fun resolvePasswords(config: ServerConfig): Map<String, String> {
        if (passwordResolver != null) return passwordResolver.invoke(config)
        val vault = credentialVault ?: return emptyMap()
        val map = mutableMapOf<String, String>()
        for (user in config.users) {
            if (!user.isAnonymous && !user.secretRef.isNullOrBlank()) {
                val creds = vault.loadCredentials(user.secretRef, user.username)
                creds.password?.let { map[user.username] = it }
            }
        }
        return map
    }

    private fun newSessionToken(): String {
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun recordClientActivity(ip: String, username: String, action: String, file: String?, deltaBytes: Long) {
        if (deltaBytes > 0L) {
            val total = totalBytesServed.addAndGet(deltaBytes)
            _state.update { it.copy(totalBytesServed = total) }
        }
        val prev = activeClients[ip]
        activeClients[ip] = ConnectedClientInfo(
            clientId = ip,
            serverType = ServerType.HTTP,
            remoteAddress = ip,
            username = username,
            currentAction = action,
            currentFile = file,
            bytesTransferred = (prev?.bytesTransferred ?: 0L) + deltaBytes,
            connectedAtMillis = prev?.connectedAtMillis ?: System.currentTimeMillis(),
        )
        _clients.value = activeClients.values.toList()
        _state.update { it.copy(activeClientCount = activeClients.size) }
    }

    private fun appendLog(level: LogLevel, rawMessage: String, config: ServerConfig?) {
        val redacted = LogRedactor.redact(rawMessage, config?.rootDirectoryPath)
        _logs.update { current ->
            (listOf(ServerLogEntry(UUID.randomUUID().toString(), ServerType.HTTP, System.currentTimeMillis(), level, redacted)) + current).take(200)
        }
    }

    private fun buildUrls(port: Int): List<String> =
        (localAddresses + "127.0.0.1").distinct().map { ip -> "http://$ip:$port" }

    private fun isLanOrLoopback(ip: String): Boolean {
        val addr = runCatching { java.net.InetAddress.getByName(ip) }.getOrNull() ?: return false
        if (addr.isLoopbackAddress || addr.isSiteLocalAddress || addr.isLinkLocalAddress) return true
        val bytes = addr.address
        if (bytes.size == 4) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            if (b0 == 100 && (b1 and 0xC0) == 64) return true
        }
        return false
    }

    private companion object {
        const val COOKIE_NAME = "portly_session"
        const val SESSION_TTL_MS = 30 * 60 * 1000L
    }
}
