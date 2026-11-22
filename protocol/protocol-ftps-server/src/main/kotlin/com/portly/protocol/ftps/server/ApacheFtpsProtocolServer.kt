package com.portly.protocol.ftps.server

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.portly.core.common.Formatters
import com.portly.core.common.PathValidator
import com.portly.core.common.ProtocolException
import com.portly.core.database.CredentialVault
import com.portly.core.model.ConnectedClientInfo
import com.portly.core.model.LogLevel
import com.portly.core.model.ProtocolError
import com.portly.core.model.ProtocolServer
import com.portly.core.model.ResolvedCredentials
import com.portly.core.model.ServerConfig
import com.portly.core.model.ServerLogEntry
import com.portly.core.model.ServerPermission
import com.portly.core.model.ServerRuntimeState
import com.portly.core.model.ServerStatus
import com.portly.core.model.ServerType
import com.portly.core.model.ServerUserAccount
import com.portly.core.model.TlsVersionPolicy
import com.portly.core.security.CertificateManager
import com.portly.core.security.LogRedactor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.apache.ftpserver.ConnectionConfigFactory
import org.apache.ftpserver.DataConnectionConfigurationFactory
import org.apache.ftpserver.FtpServer
import org.apache.ftpserver.FtpServerFactory
import org.apache.ftpserver.ftplet.Authentication
import org.apache.ftpserver.ftplet.AuthenticationFailedException
import org.apache.ftpserver.ftplet.Authority
import org.apache.ftpserver.ftplet.DefaultFtplet
import org.apache.ftpserver.ftplet.FileSystemFactory
import org.apache.ftpserver.ftplet.FileSystemView
import org.apache.ftpserver.ftplet.FtpException
import org.apache.ftpserver.ftplet.FtpFile
import org.apache.ftpserver.ftplet.FtpReply
import org.apache.ftpserver.ftplet.FtpRequest
import org.apache.ftpserver.ftplet.FtpSession
import org.apache.ftpserver.ftplet.FtpletResult
import org.apache.ftpserver.ftplet.User
import org.apache.ftpserver.ftplet.UserManager
import org.apache.ftpserver.listener.ListenerFactory
import org.apache.ftpserver.ssl.SslConfigurationFactory
import org.apache.ftpserver.usermanager.AnonymousAuthentication
import org.apache.ftpserver.usermanager.UsernamePasswordAuthentication
import org.apache.ftpserver.usermanager.impl.BaseUser
import org.apache.ftpserver.usermanager.impl.ConcurrentLoginPermission
import org.apache.ftpserver.usermanager.impl.TransferRatePermission
import org.apache.ftpserver.usermanager.impl.WritePermission
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

internal class ApacheFtpsProtocolServer(
    private val context: Context?,
    private val credentialVault: CredentialVault?,
    private val certificateManager: CertificateManager,
    private val passwordResolver: (suspend (ServerConfig) -> Map<String, String>)? = null,
    private val keystoreFileOverride: File? = null,
) : ProtocolServer {

    override val serverType: ServerType = ServerType.FTPS

    private val _state = MutableStateFlow(ServerRuntimeState(serverType = ServerType.FTPS))
    override val state: StateFlow<ServerRuntimeState> = _state.asStateFlow()

    private val _clients = MutableStateFlow<List<ConnectedClientInfo>>(emptyList())
    override val clients: StateFlow<List<ConnectedClientInfo>> = _clients.asStateFlow()

    private val _logs = MutableStateFlow<List<ServerLogEntry>>(emptyList())
    override val logs: StateFlow<List<ServerLogEntry>> = _logs.asStateFlow()

    private var server: FtpServer? = null
    private var currentConfig: ServerConfig? = null
    private var localAddresses: List<String> = listOf("127.0.0.1")
    private val activeSessions = ConcurrentHashMap<String, FtpSession>()
    private val totalBytesServed = AtomicLong(0L)

    override suspend fun start(config: ServerConfig): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            stopInternal(userStopped = false)
            _state.value = ServerRuntimeState(
                serverType = ServerType.FTPS,
                status = ServerStatus.STARTING,
                port = config.port,
            )

            val keystorePassword = resolveKeystorePassword()
            val ksFile = if (keystoreFileOverride != null) {
                keystoreFileOverride
            } else {
                certificateManager.generateOrLoadServerKeystore(
                    serverName = "ftps",
                    sanIpAddresses = localAddresses,
                    keystorePassword = keystorePassword,
                ).keystoreFile
            }

            val enabledTls = when (config.tlsVersionPolicy) {
                TlsVersionPolicy.TLS_1_3_ONLY -> arrayOf("TLSv1.3")
                TlsVersionPolicy.TLS_1_2_OR_1_3 -> arrayOf("TLSv1.3", "TLSv1.2")
            }

            val sslConfig = SslConfigurationFactory().apply {
                keystoreFile = ksFile
                this.keystorePassword = String(keystorePassword)
                keystoreType = "PKCS12"
                keyPassword = String(keystorePassword)
                setSslProtocol(*enabledTls)
            }.createSslConfiguration()

            val passwords = resolvePasswords(config)
            val userManager = FtpsUserManager(config, passwords)
            val fileSystemFactory = FtpsFileSystemFactory(context, config, userManager::permissionsFor)

            val dataConnConfig = DataConnectionConfigurationFactory().apply {
                passivePorts = config.passivePortRange
                sslConfiguration = sslConfig
                if (config.lanOnlyBind && !config.allowPublicBindOptIn) {
                    localAddresses.firstOrNull { it != "127.0.0.1" }?.let { passiveExternalAddress = it }
                }
            }.createDataConnectionConfiguration()

            val listenerFactory = ListenerFactory().apply {
                port = config.port
                sslConfiguration = sslConfig
                isImplicitSsl = false
                dataConnectionConfiguration = dataConnConfig
                if (config.lanOnlyBind && !config.allowPublicBindOptIn) {
                    serverAddress = localAddresses.firstOrNull() ?: "127.0.0.1"
                }
            }

            val connectionConfig = ConnectionConfigFactory().apply {
                maxLogins = config.maxConnections.coerceAtLeast(1)
                isAnonymousLoginEnabled = !config.requireAuth || config.users.any { it.isAnonymous }
                maxAnonymousLogins = config.maxConnections.coerceAtLeast(1)
                maxLoginFailures = 5
                loginFailureDelay = 500
            }.createConnectionConfig()

            val factory = FtpServerFactory().apply {
                addListener("default", listenerFactory.createListener())
                this.connectionConfig = connectionConfig
                this.userManager = userManager
                this.fileSystem = fileSystemFactory
                this.ftplets = mapOf("portly_ftps" to createFtplet(config))
            }

            val created = factory.createServer()
            created.start()
            val actualPort = factory.listeners["default"]?.port ?: config.port
            server = created
            currentConfig = config.copy(port = actualPort)
            totalBytesServed.set(0L)

            appendLog(LogLevel.INFO, "FTPS (Explicit TLS) server started on port $actualPort", config)
            _state.value = ServerRuntimeState(
                serverType = ServerType.FTPS,
                status = ServerStatus.RUNNING,
                port = actualPort,
                activeUrls = buildUrls(actualPort),
                activeClientCount = 0,
                totalBytesServed = 0L,
                startedAtMillis = System.currentTimeMillis(),
                userStoppedExplicitly = false,
            )
            Unit
        }.onFailure { err ->
            appendLog(LogLevel.ERROR, "Failed to start FTPS server: ${err.message}", config)
            _state.value = ServerRuntimeState(
                serverType = ServerType.FTPS,
                status = ServerStatus.ERROR,
                port = config.port,
                lastError = ProtocolError.ProtocolFailure(
                    userMessage = "Could not start FTPS server on port ${config.port}.",
                    nextStep = "Check port availability and TLS keystore permissions.",
                    technicalDetail = err.message,
                ),
            )
        }
    }

    override suspend fun stop() = withContext(Dispatchers.IO) {
        stopInternal(userStopped = true)
    }

    private fun stopInternal(userStopped: Boolean) {
        val srv = server
        server = null
        activeSessions.values.forEach { forceCloseSession(it) }
        activeSessions.clear()
        _clients.value = emptyList()
        if (srv != null) {
            runCatching { srv.stop() }
            appendLog(LogLevel.INFO, "FTPS server stopped", currentConfig)
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
            val session = activeSessions.remove(clientId)
                ?: throw ProtocolException(ProtocolError.PathNotFound("Client session no longer active."))
            forceCloseSession(session)
            _clients.update { list -> list.filterNot { it.clientId == clientId } }
            _state.update { it.copy(activeClientCount = activeSessions.size) }
            appendLog(LogLevel.WARN, "Disconnected FTPS client $clientId", currentConfig)
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

    private suspend fun resolveKeystorePassword(): CharArray {
        if (keystoreFileOverride != null) return "changeit".toCharArray()
        val vault = credentialVault ?: return "portly_local_keystore".toCharArray()
        val loaded = vault.loadCredentials(KEYSTORE_SECRET_REF, "ftps_keystore")
        val existing = loaded.password
        if (!existing.isNullOrEmpty()) return existing.toCharArray()
        val generatedBytes = ByteArray(24).also { SecureRandom().nextBytes(it) }
        val generated = Base64.getUrlEncoder().withoutPadding().encodeToString(generatedBytes)
        vault.saveCredentials(KEYSTORE_SECRET_REF, ResolvedCredentials("ftps_keystore", generated))
        return generated.toCharArray()
    }

    private suspend fun resolvePasswords(config: ServerConfig): Map<String, String> {
        if (passwordResolver != null) return passwordResolver.invoke(config)
        val vault = credentialVault ?: return emptyMap()
        val result = mutableMapOf<String, String>()
        for (user in config.users) {
            if (!user.isAnonymous && !user.secretRef.isNullOrBlank()) {
                val creds = vault.loadCredentials(user.secretRef, user.username)
                creds.password?.let { result[user.username] = it }
            }
        }
        return result
    }

    private fun forceCloseSession(session: FtpSession) {
        runCatching {
            session.setAttribute(ATTR_KICKED, true)
            session.dataConnection?.closeDataConnection()
            val ioField = session.javaClass.getDeclaredField("ioSession").apply { isAccessible = true }
            val ioSession = ioField.get(session)
            ioSession?.javaClass?.getMethod("closeNow")?.invoke(ioSession)
        }
    }

    private fun buildUrls(port: Int): List<String> =
        localAddresses.distinct().map { ip -> "ftps://$ip:$port" }

    private fun createFtplet(config: ServerConfig): DefaultFtplet = object : DefaultFtplet() {
        override fun onConnect(session: FtpSession): FtpletResult? {
            val remoteIp = session.clientAddress?.address?.hostAddress ?: "unknown"
            val perIpCount = activeSessions.values.count {
                (it.clientAddress?.address?.hostAddress ?: "") == remoteIp
            }
            if (perIpCount >= config.maxConnectionsPerIp) {
                appendLog(LogLevel.WARN, "Rejected connection from $remoteIp (per-IP limit reached)", config)
                return FtpletResult.DISCONNECT
            }
            val id = session.sessionId.toString()
            activeSessions[id] = session
            updateClientsList(id, remoteIp, "anonymous", "TLS Connected", null)
            appendLog(LogLevel.INFO, "FTPS client connected from $remoteIp", config)
            return FtpletResult.DEFAULT
        }

        override fun onDisconnect(session: FtpSession): FtpletResult? {
            val id = session.sessionId.toString()
            activeSessions.remove(id)
            _clients.update { list -> list.filterNot { it.clientId == id } }
            _state.update { it.copy(activeClientCount = activeSessions.size) }
            return FtpletResult.DEFAULT
        }

        override fun beforeCommand(session: FtpSession, request: FtpRequest): FtpletResult? {
            if (session.getAttribute(ATTR_KICKED) == true) return FtpletResult.DISCONNECT
            val cmd = request.command.uppercase()
            val id = session.sessionId.toString()
            val remoteIp = session.clientAddress?.address?.hostAddress ?: "unknown"
            val username = session.user?.name ?: "authenticating"
            val arg = if (cmd == "PASS") "[REDACTED]" else request.argument
            updateClientsList(id, remoteIp, username, cmd, arg)
            if (cmd !in setOf("USER", "PASS", "QUIT")) {
                appendLog(LogLevel.INFO, "$username executed $cmd ${arg.orEmpty()}".trim(), config)
            }
            return FtpletResult.DEFAULT
        }

        override fun onDownloadEnd(session: FtpSession, request: FtpRequest): FtpletResult? {
            val size = runCatching { session.fileSystemView.getFile(request.argument).size.coerceAtLeast(0L) }.getOrDefault(0L)
            addServedBytes(session.sessionId.toString(), size)
            return FtpletResult.DEFAULT
        }

        override fun onUploadEnd(session: FtpSession, request: FtpRequest): FtpletResult? {
            val size = runCatching { session.fileSystemView.getFile(request.argument).size.coerceAtLeast(0L) }.getOrDefault(0L)
            addServedBytes(session.sessionId.toString(), size)
            return FtpletResult.DEFAULT
        }

        override fun afterCommand(session: FtpSession, request: FtpRequest, reply: FtpReply): FtpletResult? {
            if (reply.code >= 500) {
                appendLog(LogLevel.WARN, "Reply ${reply.code} for ${request.command}", config)
            }
            return FtpletResult.DEFAULT
        }
    }

    private fun updateClientsList(clientId: String, remoteAddress: String, username: String, action: String, file: String?) {
        _clients.update { existing ->
            val prev = existing.firstOrNull { it.clientId == clientId }
            val updated = ConnectedClientInfo(
                clientId = clientId,
                serverType = ServerType.FTPS,
                remoteAddress = remoteAddress,
                username = username,
                currentAction = action,
                currentFile = file,
                bytesTransferred = prev?.bytesTransferred ?: 0L,
                connectedAtMillis = prev?.connectedAtMillis ?: System.currentTimeMillis(),
            )
            existing.filterNot { it.clientId == clientId } + updated
        }
        _state.update { it.copy(activeClientCount = activeSessions.size) }
    }

    private fun addServedBytes(clientId: String, delta: Long) {
        if (delta <= 0L) return
        val total = totalBytesServed.addAndGet(delta)
        _clients.update { list ->
            list.map { if (it.clientId == clientId) it.copy(bytesTransferred = it.bytesTransferred + delta) else it }
        }
        _state.update { it.copy(totalBytesServed = total) }
    }

    private fun appendLog(level: LogLevel, rawMessage: String, config: ServerConfig?) {
        val redacted = LogRedactor.redact(rawMessage, config?.rootDirectoryPath)
        _logs.update { current ->
            (listOf(ServerLogEntry(UUID.randomUUID().toString(), ServerType.FTPS, System.currentTimeMillis(), level, redacted)) + current).take(200)
        }
    }

    private companion object {
        const val ATTR_KICKED = "portly.ftps.kicked"
        const val KEYSTORE_SECRET_REF = "portly_ftps_server_keystore_pw"
    }
}

private class FtpsUserManager(
    private val config: ServerConfig,
    private val resolvedPasswords: Map<String, String>,
) : UserManager {
    private val usersByName = config.users.associateBy { it.username }

    fun permissionsFor(username: String): Set<ServerPermission> {
        return usersByName[username]?.permissions ?: if (!config.requireAuth) setOf(ServerPermission.READ) else emptySet()
    }

    override fun getUserByName(username: String): User? {
        val account = usersByName[username] ?: if (!config.requireAuth && username == "anonymous") {
            ServerUserAccount("anonymous", isAnonymous = true)
        } else {
            return null
        }
        return toFtpUser(account)
    }

    override fun getAllUserNames(): Array<String> = usersByName.keys.toTypedArray()
    override fun delete(username: String) = Unit
    override fun save(user: User) = Unit
    override fun doesExist(username: String): Boolean = getUserByName(username) != null
    override fun getAdminName(): String = config.users.firstOrNull()?.username ?: "admin"
    override fun isAdmin(username: String): Boolean = username == adminName

    override fun authenticate(authentication: Authentication): User = when (authentication) {
        is AnonymousAuthentication -> {
            val anon = usersByName["anonymous"]
            if (anon != null && anon.isAnonymous) toFtpUser(anon)
            else if (!config.requireAuth) getUserByName("anonymous")!!
            else throw AuthenticationFailedException("Anonymous login disabled")
        }
        is UsernamePasswordAuthentication -> {
            val uname = authentication.username ?: throw AuthenticationFailedException("Missing username")
            val account = usersByName[uname] ?: throw AuthenticationFailedException("Unknown user")
            if (account.isAnonymous) toFtpUser(account)
            else {
                val expected = resolvedPasswords[uname] ?: throw AuthenticationFailedException("No credentials configured")
                if (!MessageDigest.isEqual(expected.toByteArray(), (authentication.password ?: "").toByteArray())) {
                    throw AuthenticationFailedException("Invalid credentials")
                }
                toFtpUser(account)
            }
        }
        else -> throw AuthenticationFailedException("Unsupported auth")
    }

    private fun toFtpUser(account: ServerUserAccount): BaseUser {
        val authorities = mutableListOf<Authority>()
        val canWrite = !config.readOnly && (
            ServerPermission.WRITE in account.permissions ||
                ServerPermission.MKDIR in account.permissions ||
                ServerPermission.DELETE in account.permissions
            )
        if (canWrite) authorities += WritePermission()
        authorities += ConcurrentLoginPermission(config.maxConnections, config.maxConnectionsPerIp)
        authorities += TransferRatePermission(0, 0)
        return BaseUser().apply {
            name = account.username
            homeDirectory = "/"
            enabled = true
            maxIdleTime = 300
            this.authorities = authorities
        }
    }
}

private class FtpsFileSystemFactory(
    private val context: Context?,
    private val config: ServerConfig,
    private val userPermissionsProvider: (String) -> Set<ServerPermission>,
) : FileSystemFactory {
    override fun createFileSystemView(user: User): FileSystemView {
        val perms = userPermissionsProvider(user.name)
        val effectivePerms = if (config.readOnly) perms.intersect(setOf(ServerPermission.READ)) else perms
        val treeUri = config.rootTreeUriString?.takeIf { it.isNotBlank() }?.let(Uri::parse)
        return if (treeUri != null && context != null) {
            FtpsSafView(context, treeUri, effectivePerms)
        } else {
            FtpsLocalView(File(config.rootDirectoryPath).apply { mkdirs() }, effectivePerms)
        }
    }
}

private class FtpsLocalView(
    private val rootDir: File,
    private val permissions: Set<ServerPermission>,
) : FileSystemView {
    private var currentPath: String = "/"

    override fun getHomeDirectory(): FtpFile = FtpsLocalFile(rootDir, "/", rootDir, permissions)
    override fun getWorkingDirectory(): FtpFile = getFile(currentPath)
    override fun changeWorkingDirectory(dir: String): Boolean {
        val virtual = resolveVirtual(dir)
        val target = PathValidator.canonicalizeUnderRoot(rootDir, virtual).getOrNull() ?: return false
        if (!target.exists() || !target.isDirectory) return false
        currentPath = virtual
        return true
    }

    override fun getFile(file: String): FtpFile {
        val virtual = resolveVirtual(file)
        val canonical = PathValidator.canonicalizeUnderRoot(rootDir, virtual).getOrNull()
            ?: return FtpsDeniedFile(virtual)
        return FtpsLocalFile(rootDir, virtual, canonical, permissions)
    }

    override fun isRandomAccessible(): Boolean = true
    override fun dispose() = Unit

    private fun resolveVirtual(requested: String): String {
        if (requested.isBlank() || requested == ".") return currentPath
        val base = if (requested.startsWith("/")) requested else if (currentPath == "/") "/$requested" else "$currentPath/$requested"
        return PathValidator.normalizeRemotePath(base)
    }
}

private class FtpsDeniedFile(private val path: String) : FtpFile {
    override fun getAbsolutePath(): String = path
    override fun getName(): String = path.substringAfterLast('/').ifEmpty { "/" }
    override fun isHidden(): Boolean = false
    override fun isDirectory(): Boolean = false
    override fun isFile(): Boolean = false
    override fun doesExist(): Boolean = false
    override fun isReadable(): Boolean = false
    override fun isWritable(): Boolean = false
    override fun isRemovable(): Boolean = false
    override fun getOwnerName(): String = "portly"
    override fun getGroupName(): String = "portly"
    override fun getLinkCount(): Int = 0
    override fun getLastModified(): Long = 0L
    override fun setLastModified(time: Long): Boolean = false
    override fun getSize(): Long = 0L
    override fun getPhysicalFile(): Any = path
    override fun mkdir(): Boolean = false
    override fun delete(): Boolean = false
    override fun move(destination: FtpFile): Boolean = false
    override fun listFiles(): List<FtpFile> = emptyList()
    override fun createOutputStream(offset: Long): OutputStream = throw SecurityException("Access denied")
    override fun createInputStream(offset: Long): InputStream = throw SecurityException("Access denied")
}

private class FtpsLocalFile(
    private val rootDir: File,
    private val virtualPath: String,
    private val file: File,
    private val permissions: Set<ServerPermission>,
) : FtpFile {
    override fun getAbsolutePath(): String = virtualPath
    override fun getName(): String = if (virtualPath == "/") "/" else file.name
    override fun isHidden(): Boolean = file.isHidden || file.name.startsWith(".")
    override fun isDirectory(): Boolean = file.isDirectory
    override fun isFile(): Boolean = file.isFile
    override fun doesExist(): Boolean = file.exists()
    override fun isReadable(): Boolean = ServerPermission.READ in permissions && file.canRead()
    override fun isWritable(): Boolean = if (file.isDirectory) {
        ServerPermission.MKDIR in permissions || ServerPermission.WRITE in permissions
    } else {
        ServerPermission.WRITE in permissions && (!file.exists() || file.canWrite())
    }
    override fun isRemovable(): Boolean = virtualPath != "/" && ServerPermission.DELETE in permissions
    override fun getOwnerName(): String = "portly"
    override fun getGroupName(): String = "portly"
    override fun getLinkCount(): Int = if (file.isDirectory) 3 else 1
    override fun getLastModified(): Long = file.lastModified()
    override fun setLastModified(time: Long): Boolean = ServerPermission.WRITE in permissions && file.setLastModified(time)
    override fun getSize(): Long = if (file.isFile) file.length() else 0L
    override fun getPhysicalFile(): Any = file
    override fun mkdir(): Boolean = ServerPermission.MKDIR in permissions &&
        PathValidator.canonicalizeUnderRoot(rootDir, virtualPath).getOrNull()?.mkdirs() == true
    override fun delete(): Boolean = isRemovable &&
        PathValidator.canonicalizeUnderRoot(rootDir, virtualPath).getOrNull()?.delete() == true
    override fun move(destination: FtpFile): Boolean {
        if (ServerPermission.WRITE !in permissions || ServerPermission.DELETE !in permissions) return false
        val destFile = (destination as? FtpsLocalFile)?.file ?: return false
        PathValidator.canonicalizeUnderRoot(rootDir, destination.absolutePath).getOrNull() ?: return false
        return file.renameTo(destFile)
    }
    override fun listFiles(): List<FtpFile> {
        if (ServerPermission.READ !in permissions || !file.isDirectory) return emptyList()
        return (file.listFiles() ?: emptyArray()).mapNotNull { child ->
            val childVirtual = if (virtualPath == "/") "/${child.name}" else "$virtualPath/${child.name}"
            val verified = PathValidator.canonicalizeUnderRoot(rootDir, childVirtual).getOrNull() ?: return@mapNotNull null
            FtpsLocalFile(rootDir, childVirtual, verified, permissions)
        }
    }
    override fun createOutputStream(offset: Long): OutputStream {
        if (ServerPermission.WRITE !in permissions) throw SecurityException("Write denied")
        val verified = PathValidator.canonicalizeUnderRoot(rootDir, virtualPath).getOrElse { throw FtpException("Unsafe path") }
        verified.parentFile?.mkdirs()
        return FileOutputStream(verified, offset > 0L)
    }
    override fun createInputStream(offset: Long): InputStream {
        if (ServerPermission.READ !in permissions) throw SecurityException("Read denied")
        val verified = PathValidator.canonicalizeUnderRoot(rootDir, virtualPath).getOrElse { throw FtpException("Unsafe path") }
        return FileInputStream(verified).apply { if (offset > 0L) skip(offset) }
    }
}

private class FtpsSafView(
    private val context: Context,
    private val rootTreeUri: Uri,
    private val permissions: Set<ServerPermission>,
) : FileSystemView {
    private var currentPath: String = "/"
    private fun rootDoc(): DocumentFile = DocumentFile.fromTreeUri(context, rootTreeUri) ?: error("Unavailable SAF root")
    override fun getHomeDirectory(): FtpFile = FtpsSafFile(context, rootDoc(), "/", null, permissions)
    override fun getWorkingDirectory(): FtpFile = getFile(currentPath)
    override fun changeWorkingDirectory(dir: String): Boolean {
        val virtual = resolveVirtual(dir)
        val doc = resolveDoc(virtual) ?: return false
        if (!doc.isDirectory) return false
        currentPath = virtual
        return true
    }
    override fun getFile(file: String): FtpFile {
        val virtual = resolveVirtual(file)
        if (virtual.contains("..") || virtual.contains('\u0000')) return FtpsDeniedFile(virtual)
        val doc = resolveDoc(virtual)
        return if (doc != null) FtpsSafFile(context, doc, virtual, null, permissions)
        else {
            val parent = resolveDoc(PathValidator.parentRemotePath(virtual)) ?: return FtpsDeniedFile(virtual)
            FtpsSafFile(context, null, virtual, parent, permissions)
        }
    }
    override fun isRandomAccessible(): Boolean = false
    override fun dispose() = Unit
    private fun resolveVirtual(requested: String): String {
        if (requested.isBlank() || requested == ".") return currentPath
        val base = if (requested.startsWith("/")) requested else if (currentPath == "/") "/$requested" else "$currentPath/$requested"
        return PathValidator.normalizeRemotePath(base)
    }
    private fun resolveDoc(virtualPath: String): DocumentFile? {
        var cur: DocumentFile = rootDoc()
        if (virtualPath == "/") return cur
        for (seg in virtualPath.trimStart('/').split('/')) {
            if (seg.isEmpty()) continue
            cur = cur.findFile(seg) ?: return null
        }
        return cur
    }
}

private class FtpsSafFile(
    private val context: Context,
    private val doc: DocumentFile?,
    private val virtualPath: String,
    private val parentDoc: DocumentFile?,
    private val permissions: Set<ServerPermission>,
) : FtpFile {
    override fun getAbsolutePath(): String = virtualPath
    override fun getName(): String = if (virtualPath == "/") "/" else virtualPath.substringAfterLast('/')
    override fun isHidden(): Boolean = name.startsWith(".")
    override fun isDirectory(): Boolean = doc?.isDirectory == true
    override fun isFile(): Boolean = doc?.isFile == true
    override fun doesExist(): Boolean = doc?.exists() == true
    override fun isReadable(): Boolean = ServerPermission.READ in permissions && doc?.canRead() == true
    override fun isWritable(): Boolean = if (doc == null) ServerPermission.WRITE in permissions && parentDoc != null
    else ServerPermission.WRITE in permissions && doc.canWrite()
    override fun isRemovable(): Boolean = virtualPath != "/" && ServerPermission.DELETE in permissions && doc != null
    override fun getOwnerName(): String = "portly"
    override fun getGroupName(): String = "portly"
    override fun getLinkCount(): Int = 1
    override fun getLastModified(): Long = doc?.lastModified() ?: 0L
    override fun setLastModified(time: Long): Boolean = false
    override fun getSize(): Long = if (doc?.isFile == true) doc.length() else 0L
    override fun getPhysicalFile(): Any = virtualPath
    override fun mkdir(): Boolean = ServerPermission.MKDIR in permissions && parentDoc?.createDirectory(name) != null
    override fun delete(): Boolean = isRemovable && doc?.delete() == true
    override fun move(destination: FtpFile): Boolean = ServerPermission.WRITE in permissions && doc?.renameTo(destination.name) == true
    override fun listFiles(): List<FtpFile> {
        if (ServerPermission.READ !in permissions || doc == null || !doc.isDirectory) return emptyList()
        return doc.listFiles().mapNotNull { child ->
            val n = child.name ?: return@mapNotNull null
            FtpsSafFile(context, child, if (virtualPath == "/") "/$n" else "$virtualPath/$n", doc, permissions)
        }
    }
    override fun createOutputStream(offset: Long): OutputStream {
        if (ServerPermission.WRITE !in permissions) throw SecurityException("Write denied")
        val target = doc ?: parentDoc?.createFile(Formatters.guessMimeType(name), name) ?: throw FtpException("Cannot create file")
        return context.contentResolver.openOutputStream(target.uri, if (offset > 0L) "wa" else "w") ?: throw FtpException("Cannot open stream")
    }
    override fun createInputStream(offset: Long): InputStream {
        if (ServerPermission.READ !in permissions || doc == null) throw SecurityException("Read denied")
        return (context.contentResolver.openInputStream(doc.uri) ?: throw FtpException("Cannot open stream")).apply {
            if (offset > 0L) skip(offset)
        }
    }
}
