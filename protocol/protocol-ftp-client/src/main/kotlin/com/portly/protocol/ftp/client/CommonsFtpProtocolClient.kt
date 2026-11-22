package com.portly.protocol.ftp.client

import com.portly.core.common.Formatters
import com.portly.core.common.PathValidator
import com.portly.core.common.ProtocolException
import com.portly.core.model.AuthType
import com.portly.core.model.ConnectionProfile
import com.portly.core.model.ConnectionTestResult
import com.portly.core.model.FilePermissions
import com.portly.core.model.ProtocolClient
import com.portly.core.model.ProtocolError
import com.portly.core.model.ProtocolInputHandle
import com.portly.core.model.ProtocolOutputHandle
import com.portly.core.model.ProtocolType
import com.portly.core.model.RemoteFile
import com.portly.core.model.ResolvedCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.FTPReply
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

internal open class CommonsFtpProtocolClient(
    override val protocolType: ProtocolType = ProtocolType.FTP,
) : ProtocolClient {

    protected var ftpClient: FTPClient? = null
    protected var activeProfile: ConnectionProfile? = null

    override val isConnected: Boolean
        get() = ftpClient?.isConnected == true

    protected open fun createFtpClient(): FTPClient = FTPClient()

    protected open suspend fun onBeforeLogin(client: FTPClient, profile: ConnectionProfile) = Unit

    override suspend fun connect(
        profile: ConnectionProfile,
        credentials: ResolvedCredentials,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            disconnectInternal()
            val client = createFtpClient().apply {
                controlEncoding = StandardCharsets.UTF_8.name()
                connectTimeout = profile.connectTimeoutMs
                defaultTimeout = profile.connectTimeoutMs
                setDataTimeout(java.time.Duration.ofMillis(profile.connectTimeoutMs.toLong()))
            }

            client.connect(profile.host, profile.port)
            if (!FTPReply.isPositiveCompletion(client.replyCode)) {
                val err = FtpClientErrorMapper.fromReplyCode(client.replyCode, client.replyString)
                runCatching { client.disconnect() }
                throw ProtocolException(err)
            }

            onBeforeLogin(client, profile)

            val user = if (profile.authType == AuthType.ANONYMOUS) {
                "anonymous"
            } else {
                credentials.username.ifBlank { profile.username }.ifBlank { "anonymous" }
            }
            val pass = if (profile.authType == AuthType.ANONYMOUS) {
                "portly@local"
            } else {
                credentials.password ?: ""
            }

            if (!client.login(user, pass)) {
                val err = FtpClientErrorMapper.fromReplyCode(client.replyCode, client.replyString)
                runCatching { client.disconnect() }
                throw ProtocolException(err)
            }

            client.setFileType(FTP.BINARY_FILE_TYPE)
            if (profile.passiveMode) {
                client.enterLocalPassiveMode()
            } else {
                client.enterLocalActiveMode()
            }

            val root = PathValidator.normalizeRemotePath(profile.rootPath)
            if (root != "/") {
                client.changeWorkingDirectory(root)
            }

            ftpClient = client
            activeProfile = profile
            Unit
        }.recoverCatching { err ->
            if (err is ProtocolException) throw err
            throw ProtocolException(FtpClientErrorMapper.fromException(err), err)
        }
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        disconnectInternal()
    }

    private fun disconnectInternal() {
        val client = ftpClient
        ftpClient = null
        activeProfile = null
        if (client != null) {
            runCatching {
                if (client.isConnected) {
                    client.logout()
                    client.disconnect()
                }
            }
        }
    }

    override suspend fun testConnection(
        profile: ConnectionProfile,
        credentials: ResolvedCredentials,
    ): ConnectionTestResult = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val conn = connect(profile, credentials)
        val latency = (System.currentTimeMillis() - started).coerceAtLeast(1L)
        if (conn.isFailure) {
            val err = (conn.exceptionOrNull() as? ProtocolException)?.protocolError
                ?: FtpClientErrorMapper.fromException(conn.exceptionOrNull()!!)
            return@withContext ConnectionTestResult(
                success = false,
                latencyMs = latency,
                error = err,
            )
        }
        val client = ftpClient
        val cwd = runCatching { client?.printWorkingDirectory() }.getOrNull()
        val banner = runCatching { client?.replyString?.trim() }.getOrNull()
        disconnectInternal()
        ConnectionTestResult(
            success = true,
            latencyMs = latency,
            serverBanner = banner,
            currentDirectory = cwd,
        )
    }

    override suspend fun list(path: String): Result<List<RemoteFile>> = withContext(Dispatchers.IO) {
        runCatching {
            val client = requireConnected()
            val normalized = PathValidator.normalizeRemotePath(path)
            val entries = runCatching {
                if (client.hasFeature("MLSD")) client.mlistDir(normalized) else null
            }.getOrNull() ?: client.listFiles(normalized)

            entries.filterNotNull()
                .filter { it.name != "." && it.name != ".." }
                .map { ftpFile -> ftpFileToRemoteFile(normalized, ftpFile) }
        }.recoverCatching { err ->
            if (err is ProtocolException) throw err
            throw ProtocolException(FtpClientErrorMapper.fromException(err), err)
        }
    }

    override suspend fun stat(path: String): Result<RemoteFile> = withContext(Dispatchers.IO) {
        runCatching {
            val client = requireConnected()
            val normalized = PathValidator.normalizeRemotePath(path)
            if (normalized == "/") {
                return@runCatching RemoteFile(
                    path = "/",
                    name = "/",
                    isDirectory = true,
                    sizeBytes = 0L,
                    lastModifiedMillis = 0L,
                    mimeType = "inode/directory",
                )
            }
            val mlst = runCatching {
                if (client.hasFeature("MLST")) client.mlistFile(normalized) else null
            }.getOrNull()
            if (mlst != null) {
                return@runCatching ftpFileToRemoteFile(PathValidator.parentRemotePath(normalized), mlst)
            }
            val parent = PathValidator.parentRemotePath(normalized)
            val targetName = normalized.substringAfterLast('/')
            val found = client.listFiles(parent)?.firstOrNull { it.name == targetName }
                ?: throw ProtocolException(ProtocolError.PathNotFound(technicalDetail = normalized))
            ftpFileToRemoteFile(parent, found)
        }.recoverCatching { err ->
            if (err is ProtocolException) throw err
            throw ProtocolException(FtpClientErrorMapper.fromException(err), err)
        }
    }

    override suspend fun mkdir(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = requireConnected()
            val normalized = PathValidator.normalizeRemotePath(path)
            if (!client.makeDirectory(normalized)) {
                throw ProtocolException(FtpClientErrorMapper.fromReplyCode(client.replyCode, client.replyString))
            }
            Unit
        }.recoverCatching { err ->
            if (err is ProtocolException) throw err
            throw ProtocolException(FtpClientErrorMapper.fromException(err), err)
        }
    }

    override suspend fun rename(oldPath: String, newPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = requireConnected()
            val from = PathValidator.normalizeRemotePath(oldPath)
            val to = PathValidator.normalizeRemotePath(newPath)
            if (!client.rename(from, to)) {
                throw ProtocolException(FtpClientErrorMapper.fromReplyCode(client.replyCode, client.replyString))
            }
            Unit
        }.recoverCatching { err ->
            if (err is ProtocolException) throw err
            throw ProtocolException(FtpClientErrorMapper.fromException(err), err)
        }
    }

    override suspend fun delete(path: String, recursive: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = requireConnected()
            val normalized = PathValidator.normalizeRemotePath(path)
            deleteInternal(client, normalized, recursive)
        }.recoverCatching { err ->
            if (err is ProtocolException) throw err
            throw ProtocolException(FtpClientErrorMapper.fromException(err), err)
        }
    }

    private fun deleteInternal(client: FTPClient, path: String, recursive: Boolean) {
        if (client.deleteFile(path)) return
        if (recursive) {
            val children = client.listFiles(path) ?: emptyArray()
            for (child in children) {
                if (child.name == "." || child.name == "..") continue
                val childPath = PathValidator.joinRemotePath(path, child.name)
                if (child.isDirectory) {
                    deleteInternal(client, childPath, true)
                } else if (!client.deleteFile(childPath)) {
                    throw ProtocolException(FtpClientErrorMapper.fromReplyCode(client.replyCode, client.replyString))
                }
            }
        }
        if (!client.removeDirectory(path)) {
            throw ProtocolException(FtpClientErrorMapper.fromReplyCode(client.replyCode, client.replyString))
        }
    }

    override suspend fun chmod(path: String, posixMode: Int): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = requireConnected()
            val normalized = PathValidator.normalizeRemotePath(path)
            val octal = Integer.toOctalString(posixMode and 0b111_111_111)
            if (!client.sendSiteCommand("CHMOD $octal $normalized")) {
                throw ProtocolException(FtpClientErrorMapper.fromReplyCode(client.replyCode, client.replyString))
            }
            Unit
        }.recoverCatching { err ->
            if (err is ProtocolException) throw err
            throw ProtocolException(FtpClientErrorMapper.fromException(err), err)
        }
    }

    override suspend fun openInputStream(path: String, offset: Long): Result<ProtocolInputHandle> =
        withContext(Dispatchers.IO) {
            runCatching {
                val client = requireConnected()
                val normalized = PathValidator.normalizeRemotePath(path)
                client.restartOffset = offset.coerceAtLeast(0L)
                val rawStream = client.retrieveFileStream(normalized)
                    ?: throw ProtocolException(FtpClientErrorMapper.fromReplyCode(client.replyCode, client.replyString))
                object : ProtocolInputHandle {
                    override val stream: InputStream = rawStream
                    private var finalized = false

                    override suspend fun complete(): Result<Unit> = withContext(Dispatchers.IO) {
                        runCatching {
                            if (!finalized) {
                                finalized = true
                                runCatching { rawStream.close() }
                                if (!client.completePendingCommand()) {
                                    throw ProtocolException(
                                        FtpClientErrorMapper.fromReplyCode(client.replyCode, client.replyString),
                                    )
                                }
                            }
                            Unit
                        }
                    }

                    override fun close() {
                        runCatching { rawStream.close() }
                        if (!finalized) {
                            finalized = true
                            runCatching { client.completePendingCommand() }
                        }
                    }
                }
            }.recoverCatching { err ->
                if (err is ProtocolException) throw err
                throw ProtocolException(FtpClientErrorMapper.fromException(err), err)
            }
        }

    override suspend fun openOutputStream(
        path: String,
        offset: Long,
        totalSize: Long,
    ): Result<ProtocolOutputHandle> = withContext(Dispatchers.IO) {
        runCatching {
            val client = requireConnected()
            val normalized = PathValidator.normalizeRemotePath(path)
            val rawStream = if (offset > 0L) {
                client.restartOffset = offset
                client.storeFileStream(normalized)
            } else {
                client.restartOffset = 0L
                client.storeFileStream(normalized)
            } ?: throw ProtocolException(FtpClientErrorMapper.fromReplyCode(client.replyCode, client.replyString))

            object : ProtocolOutputHandle {
                override val stream: OutputStream = rawStream
                private var finalized = false

                override suspend fun complete(): Result<Unit> = withContext(Dispatchers.IO) {
                    runCatching {
                        if (!finalized) {
                            finalized = true
                            runCatching { rawStream.flush() }
                            runCatching { rawStream.close() }
                            if (!client.completePendingCommand()) {
                                throw ProtocolException(
                                    FtpClientErrorMapper.fromReplyCode(client.replyCode, client.replyString),
                                )
                            }
                        }
                        Unit
                    }
                }

                override fun close() {
                    runCatching { rawStream.close() }
                    if (!finalized) {
                        finalized = true
                        runCatching { client.completePendingCommand() }
                    }
                }
            }
        }.recoverCatching { err ->
            if (err is ProtocolException) throw err
            throw ProtocolException(FtpClientErrorMapper.fromException(err), err)
        }
    }

    override suspend fun computeChecksum(path: String, algorithm: String): Result<String?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val client = requireConnected()
                val normalized = PathValidator.normalizeRemotePath(path)
                val cmd = when (algorithm.uppercase()) {
                    "SHA-256", "SHA256" -> "XSHA256"
                    "SHA-1", "SHA1" -> "XSHA1"
                    "MD5" -> "MD5"
                    else -> return@runCatching null
                }
                val reply = client.sendCommand(cmd, normalized)
                if (!FTPReply.isPositiveCompletion(reply)) {
                    return@runCatching null
                }
                val text = client.replyString?.trim().orEmpty()
                val hexMatch = Regex("""\b[0-9a-fA-F]{32,64}\b""").find(text)
                hexMatch?.value?.lowercase()
            }
        }

    private fun requireConnected(): FTPClient =
        ftpClient?.takeIf { it.isConnected }
            ?: throw ProtocolException(ProtocolError.ConnectionFailed("Not connected to FTP server."))

    private fun ftpFileToRemoteFile(parentPath: String, ftpFile: FTPFile): RemoteFile {
        val fullPath = PathValidator.joinRemotePath(parentPath, ftpFile.name)
        val perms = extractPermissions(ftpFile)
        return RemoteFile(
            path = fullPath,
            name = ftpFile.name,
            isDirectory = ftpFile.isDirectory,
            sizeBytes = if (ftpFile.isDirectory) 0L else ftpFile.size.coerceAtLeast(0L),
            lastModifiedMillis = ftpFile.timestamp?.timeInMillis ?: 0L,
            mimeType = Formatters.guessMimeType(ftpFile.name, ftpFile.isDirectory),
            permissions = perms,
            owner = ftpFile.user?.takeIf { it.isNotBlank() },
            group = ftpFile.group?.takeIf { it.isNotBlank() },
            isSymlink = ftpFile.isSymbolicLink,
            symlinkTarget = ftpFile.link,
        )
    }

    private fun extractPermissions(ftpFile: FTPFile): FilePermissions {
        var mode = 0
        val accesses = intArrayOf(FTPFile.USER_ACCESS, FTPFile.GROUP_ACCESS, FTPFile.WORLD_ACCESS)
        val perms = intArrayOf(FTPFile.READ_PERMISSION, FTPFile.WRITE_PERMISSION, FTPFile.EXECUTE_PERMISSION)
        for (aIdx in accesses.indices) {
            for (pIdx in perms.indices) {
                if (ftpFile.hasPermission(accesses[aIdx], perms[pIdx])) {
                    val bit = 8 - (aIdx * 3 + pIdx)
                    mode = mode or (1 shl bit)
                }
            }
        }
        return FilePermissions(
            posixMode = if (mode != 0) mode else null,
            readable = ftpFile.hasPermission(FTPFile.USER_ACCESS, FTPFile.READ_PERMISSION),
            writable = ftpFile.hasPermission(FTPFile.USER_ACCESS, FTPFile.WRITE_PERMISSION),
            executable = ftpFile.hasPermission(FTPFile.USER_ACCESS, FTPFile.EXECUTE_PERMISSION),
        )
    }
}
