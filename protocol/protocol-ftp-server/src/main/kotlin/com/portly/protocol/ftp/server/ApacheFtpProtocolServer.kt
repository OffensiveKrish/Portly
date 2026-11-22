package com.portly.protocol.ftp.server

import android.content.Context
import com.portly.core.common.ProtocolException
import com.portly.core.database.CredentialVault
import com.portly.core.model.ConnectedClientInfo
import com.portly.core.model.LogLevel
import com.portly.core.model.ProtocolError
import com.portly.core.model.ProtocolServer
import com.portly.core.model.ServerConfig
import com.portly.core.model.ServerLogEntry
import com.portly.core.model.ServerRuntimeState
import com.portly.core.model.ServerStatus
import com.portly.core.model.ServerType
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
import org.apache.ftpserver.ftplet.DefaultFtplet
import org.apache.ftpserver.ftplet.FtpReply
import org.apache.ftpserver.ftplet.FtpRequest
import org.apache.ftpserver.ftplet.FtpSession
import org.apache.ftpserver.ftplet.FtpletResult
import org.apache.ftpserver.listener.ListenerFactory
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

internal class ApacheFtpProtocolServer(
    private val context: Context?,
    private val credentialVault: CredentialVault?,
    private val passwordResolver: (suspend (ServerConfig) -> Map<String, String>)? = null,
) : ProtocolServer {

    override val serverType: ServerType = ServerType.FTP

    private val _state = MutableStateFlow(ServerRuntimeState(serverType = ServerType.FTP))
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
                serverType = ServerType.FTP,
                status = ServerStatus.STARTING,
                port = config.port,
            )

            val passwords = resolvePasswords(config)
            val userManager = PortlyFtpUserManager(config, passwords)
            val fileSystemFactory = PortlyFtpFileSystemFactory(context, config, userManager::permissionsFor)

            val dataConnConfig = DataConnectionConfigurationFactory().apply {
                passivePorts = config.passivePortRange
                if (config.lanOnlyBind && !config.allowPublicBindOptIn) {
                    localAddresses.firstOrNull { it != "127.0.0.1" }?.let { passiveExternalAddress = it }
                }
            }.createDataConnectionConfiguration()

            val listenerFactory = ListenerFactory().apply {
                port = config.port
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

            val ftplet = createFtplet(config)
            val factory = FtpServerFactory().apply {
                addListener("default", listenerFactory.createListener())
                this.connectionConfig = connectionConfig
                this.userManager = userManager
                this.fileSystem = fileSystemFactory
                this.ftplets = mapOf("portly" to ftplet)
            }

            val created = factory.createServer()
            created.start()
            val actualPort = factory.listeners["default"]?.port ?: config.port
            server = created
            currentConfig = config.copy(port = actualPort)
            totalBytesServed.set(0L)

            appendLog(LogLevel.INFO, "FTP server started on port $actualPort", config)
            _state.value = ServerRuntimeState(
                serverType = ServerType.FTP,
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
            appendLog(LogLevel.ERROR, "Failed to start FTP server: ${err.message}", config)
            _state.value = ServerRuntimeState(
                serverType = ServerType.FTP,
                status = ServerStatus.ERROR,
                port = config.port,
                lastError = ProtocolError.ProtocolFailure(
                    userMessage = "Could not start FTP server on port ${config.port}.",
                    nextStep = "Try another port (e.g. 2121+) or check that no other app is using it.",
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
        activeSessions.values.forEach { session -> forceCloseSession(session) }
        activeSessions.clear()
        _clients.value = emptyList()
        if (srv != null) {
            runCatching { srv.stop() }
            appendLog(LogLevel.INFO, "FTP server stopped", currentConfig)
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
            appendLog(LogLevel.WARN, "Disconnected client $clientId", currentConfig)
            Unit
        }
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

    private fun buildUrls(port: Int): List<String> =
        localAddresses.distinct().map { ip -> "ftp://$ip:$port" }

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
            updateClientsList(id, remoteIp, "anonymous", "Connected", null)
            appendLog(LogLevel.INFO, "Client connected from $remoteIp", config)
            return FtpletResult.DEFAULT
        }

        override fun onDisconnect(session: FtpSession): FtpletResult? {
            val id = session.sessionId.toString()
            activeSessions.remove(id)
            _clients.update { list -> list.filterNot { it.clientId == id } }
            _state.update { it.copy(activeClientCount = activeSessions.size) }
            appendLog(LogLevel.INFO, "Client disconnected ($id)", config)
            return FtpletResult.DEFAULT
        }

        override fun beforeCommand(session: FtpSession, request: FtpRequest): FtpletResult? {
            if (session.getAttribute(ATTR_KICKED) == true) {
                return FtpletResult.DISCONNECT
            }
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
            val id = session.sessionId.toString()
            val size = runCatching {
                session.fileSystemView.getFile(request.argument).size.coerceAtLeast(0L)
            }.getOrDefault(0L)
            addServedBytes(id, size)
            return FtpletResult.DEFAULT
        }

        override fun onUploadEnd(session: FtpSession, request: FtpRequest): FtpletResult? {
            val id = session.sessionId.toString()
            val size = runCatching {
                session.fileSystemView.getFile(request.argument).size.coerceAtLeast(0L)
            }.getOrDefault(0L)
            addServedBytes(id, size)
            return FtpletResult.DEFAULT
        }

        override fun afterCommand(session: FtpSession, request: FtpRequest, reply: FtpReply): FtpletResult? {
            if (reply.code >= 500) {
                appendLog(LogLevel.WARN, "Reply ${reply.code} for ${request.command}", config)
            }
            return FtpletResult.DEFAULT
        }
    }

    private companion object {
        const val ATTR_KICKED = "portly.kicked"
    }

    private fun updateClientsList(
        clientId: String,
        remoteAddress: String,
        username: String,
        action: String,
        file: String?,
    ) {
        _clients.update { existing ->
            val prev = existing.firstOrNull { it.clientId == clientId }
            val updated = ConnectedClientInfo(
                clientId = clientId,
                serverType = ServerType.FTP,
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
        val entry = ServerLogEntry(
            id = UUID.randomUUID().toString(),
            serverType = ServerType.FTP,
            timestampMillis = System.currentTimeMillis(),
            level = level,
            message = redacted,
        )
        _logs.update { current -> (listOf(entry) + current).take(200) }
    }
}
