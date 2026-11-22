package com.portly.protocol.ftps.client

import com.portly.core.common.Formatters
import com.portly.core.common.PathValidator
import com.portly.core.common.ProtocolException
import com.portly.core.database.dao.TrustedCertDao
import com.portly.core.model.AuthType
import com.portly.core.model.ConnectionProfile
import com.portly.core.model.ConnectionTestResult
import com.portly.core.model.FilePermissions
import com.portly.core.model.FtpsMode
import com.portly.core.model.ProtocolClient
import com.portly.core.model.ProtocolError
import com.portly.core.model.ProtocolInputHandle
import com.portly.core.model.ProtocolOutputHandle
import com.portly.core.model.ProtocolType
import com.portly.core.model.RemoteFile
import com.portly.core.model.ResolvedCredentials
import com.portly.core.model.TlsVersionPolicy
import com.portly.core.security.CertificateManager
import com.portly.core.security.CertificateUntrustedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.FTPReply
import org.apache.commons.net.ftp.FTPSClient
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.security.cert.X509Certificate
import javax.net.ssl.SSLHandshakeException

internal interface TrustedCertStore {
    suspend fun trustedHostFingerprints(host: String, port: Int): Set<String>
    suspend fun userCaCertificates(): List<X509Certificate>
}

internal class RoomTrustedCertStore(
    private val trustedCertDao: TrustedCertDao,
    private val certificateManager: CertificateManager,
) : TrustedCertStore {
    override suspend fun trustedHostFingerprints(host: String, port: Int): Set<String> =
        trustedCertDao.getForHost(host, port).map { it.sha256Fingerprint }.toSet()

    override suspend fun userCaCertificates(): List<X509Certificate> {
        // User CAs imported as PEM/DER in subject field or managed externally; system trust + pinned fingerprints apply.
        return emptyList()
    }
}

internal class CommonsFtpsProtocolClient(
    private val certStore: TrustedCertStore,
    private val certificateManager: CertificateManager,
) : ProtocolClient {

    override val protocolType: ProtocolType = ProtocolType.FTPS

    private var ftpsClient: FTPSClient? = null

    override val isConnected: Boolean
        get() = ftpsClient?.isConnected == true

    override suspend fun connect(
        profile: ConnectionProfile,
        credentials: ResolvedCredentials,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            disconnectInternal()
            val isImplicit = profile.ftpsMode == FtpsMode.IMPLICIT
            val pinned = certStore.trustedHostFingerprints(profile.host, profile.port)
            val userCas = certStore.userCaCertificates()
            val trustManager = certificateManager.buildTrustManager(
                host = profile.host,
                port = profile.port,
                trustedHostFingerprints = pinned,
                userCaCertificates = userCas,
            )
            val sslContext = certificateManager.buildSslContext(trustManager)

            val client = FTPSClient(isImplicit, sslContext).apply {
                controlEncoding = StandardCharsets.UTF_8.name()
                connectTimeout = profile.connectTimeoutMs
                defaultTimeout = profile.connectTimeoutMs
                setDataTimeout(java.time.Duration.ofMillis(profile.connectTimeoutMs.toLong()))
                this.trustManager = trustManager
                enabledProtocols = when (profile.tlsVersionPolicy) {
                    TlsVersionPolicy.TLS_1_3_ONLY -> arrayOf("TLSv1.3")
                    TlsVersionPolicy.TLS_1_2_OR_1_3 -> arrayOf("TLSv1.3", "TLSv1.2")
                }
            }

            client.connect(profile.host, profile.port)
            if (!FTPReply.isPositiveCompletion(client.replyCode)) {
                val err = mapReplyError(client.replyCode, client.replyString)
                runCatching { client.disconnect() }
                throw ProtocolException(err)
            }

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
                val err = mapReplyError(client.replyCode, client.replyString)
                runCatching { client.disconnect() }
                throw ProtocolException(err)
            }

            client.execPBSZ(0)
            client.execPROT("P")
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

            ftpsClient = client
            Unit
        }.recoverCatching { err ->
            disconnectInternal()
            throw ProtocolException(mapException(err), err)
        }
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        disconnectInternal()
    }

    private fun disconnectInternal() {
        val client = ftpsClient
        ftpsClient = null
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
                ?: mapException(conn.exceptionOrNull()!!)
            return@withContext ConnectionTestResult(success = false, latencyMs = latency, error = err)
        }
        val cwd = runCatching { ftpsClient?.printWorkingDirectory() }.getOrNull()
        val banner = runCatching { ftpsClient?.replyString?.trim() }.getOrNull()
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
                .map { ftpFileToRemoteFile(normalized, it) }
        }.recoverCatching { err ->
            throw ProtocolException(mapException(err), err)
        }
    }

    override suspend fun stat(path: String): Result<RemoteFile> = withContext(Dispatchers.IO) {
        runCatching {
            val client = requireConnected()
            val normalized = PathValidator.normalizeRemotePath(path)
            if (normalized == "/") {
                return@runCatching RemoteFile("/", "/", true, 0L, 0L, "inode/directory")
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
            throw ProtocolException(mapException(err), err)
        }
    }

    override suspend fun mkdir(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = requireConnected()
            if (!client.makeDirectory(PathValidator.normalizeRemotePath(path))) {
                throw ProtocolException(mapReplyError(client.replyCode, client.replyString))
            }
            Unit
        }.recoverCatching { err -> throw ProtocolException(mapException(err), err) }
    }

    override suspend fun rename(oldPath: String, newPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = requireConnected()
            if (!client.rename(PathValidator.normalizeRemotePath(oldPath), PathValidator.normalizeRemotePath(newPath))) {
                throw ProtocolException(mapReplyError(client.replyCode, client.replyString))
            }
            Unit
        }.recoverCatching { err -> throw ProtocolException(mapException(err), err) }
    }

    override suspend fun delete(path: String, recursive: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = requireConnected()
            deleteInternal(client, PathValidator.normalizeRemotePath(path), recursive)
        }.recoverCatching { err -> throw ProtocolException(mapException(err), err) }
    }

    private fun deleteInternal(client: FTPSClient, path: String, recursive: Boolean) {
        if (client.deleteFile(path)) return
        if (recursive) {
            val children = client.listFiles(path) ?: emptyArray()
            for (child in children) {
                if (child.name == "." || child.name == "..") continue
                val childPath = PathValidator.joinRemotePath(path, child.name)
                if (child.isDirectory) deleteInternal(client, childPath, true)
                else if (!client.deleteFile(childPath)) {
                    throw ProtocolException(mapReplyError(client.replyCode, client.replyString))
                }
            }
        }
        if (!client.removeDirectory(path)) {
            throw ProtocolException(mapReplyError(client.replyCode, client.replyString))
        }
    }

    override suspend fun chmod(path: String, posixMode: Int): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = requireConnected()
            val octal = Integer.toOctalString(posixMode and 0b111_111_111)
            if (!client.sendSiteCommand("CHMOD $octal ${PathValidator.normalizeRemotePath(path)}")) {
                throw ProtocolException(mapReplyError(client.replyCode, client.replyString))
            }
            Unit
        }.recoverCatching { err -> throw ProtocolException(mapException(err), err) }
    }

    override suspend fun openInputStream(path: String, offset: Long): Result<ProtocolInputHandle> =
        withContext(Dispatchers.IO) {
            runCatching {
                val client = requireConnected()
                client.restartOffset = offset.coerceAtLeast(0L)
                val rawStream = client.retrieveFileStream(PathValidator.normalizeRemotePath(path))
                    ?: throw ProtocolException(mapReplyError(client.replyCode, client.replyString))
                object : ProtocolInputHandle {
                    override val stream: InputStream = rawStream
                    private var finalized = false
                    override suspend fun complete(): Result<Unit> = withContext(Dispatchers.IO) {
                        runCatching {
                            if (!finalized) {
                                finalized = true
                                runCatching { rawStream.close() }
                                if (!client.completePendingCommand()) {
                                    throw ProtocolException(mapReplyError(client.replyCode, client.replyString))
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
            }.recoverCatching { err -> throw ProtocolException(mapException(err), err) }
        }

    override suspend fun openOutputStream(
        path: String,
        offset: Long,
        totalSize: Long,
    ): Result<ProtocolOutputHandle> = withContext(Dispatchers.IO) {
        runCatching {
            val client = requireConnected()
            client.restartOffset = offset.coerceAtLeast(0L)
            val rawStream = client.storeFileStream(PathValidator.normalizeRemotePath(path))
                ?: throw ProtocolException(mapReplyError(client.replyCode, client.replyString))
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
                                throw ProtocolException(mapReplyError(client.replyCode, client.replyString))
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
        }.recoverCatching { err -> throw ProtocolException(mapException(err), err) }
    }

    override suspend fun computeChecksum(path: String, algorithm: String): Result<String?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val client = requireConnected()
                val cmd = when (algorithm.uppercase()) {
                    "SHA-256", "SHA256" -> "XSHA256"
                    "SHA-1", "SHA1" -> "XSHA1"
                    "MD5" -> "MD5"
                    else -> return@runCatching null
                }
                val reply = client.sendCommand(cmd, PathValidator.normalizeRemotePath(path))
                if (!FTPReply.isPositiveCompletion(reply)) return@runCatching null
                val text = client.replyString?.trim().orEmpty()
                Regex("""\b[0-9a-fA-F]{32,64}\b""").find(text)?.value?.lowercase()
            }
        }

    private fun requireConnected(): FTPSClient =
        ftpsClient?.takeIf { it.isConnected }
            ?: throw ProtocolException(ProtocolError.ConnectionFailed("Not connected to FTPS server."))

    private fun ftpFileToRemoteFile(parentPath: String, ftpFile: FTPFile): RemoteFile {
        val fullPath = PathValidator.joinRemotePath(parentPath, ftpFile.name)
        return RemoteFile(
            path = fullPath,
            name = ftpFile.name,
            isDirectory = ftpFile.isDirectory,
            sizeBytes = if (ftpFile.isDirectory) 0L else ftpFile.size.coerceAtLeast(0L),
            lastModifiedMillis = ftpFile.timestamp?.timeInMillis ?: 0L,
            mimeType = Formatters.guessMimeType(ftpFile.name, ftpFile.isDirectory),
            permissions = FilePermissions(
                readable = ftpFile.hasPermission(FTPFile.USER_ACCESS, FTPFile.READ_PERMISSION),
                writable = ftpFile.hasPermission(FTPFile.USER_ACCESS, FTPFile.WRITE_PERMISSION),
                executable = ftpFile.hasPermission(FTPFile.USER_ACCESS, FTPFile.EXECUTE_PERMISSION),
            ),
        )
    }

    private fun mapReplyError(code: Int, reply: String?): ProtocolError = when (code) {
        530 -> ProtocolError.AuthenticationFailed(technicalDetail = reply, protocolCode = code)
        550 -> ProtocolError.PathNotFound(technicalDetail = reply, protocolCode = code)
        553, 551 -> ProtocolError.PermissionDenied(technicalDetail = reply, protocolCode = code)
        421, 425, 426, 450, 451 -> ProtocolError.ConnectionFailed(technicalDetail = reply, protocolCode = code, isTransient = true)
        else -> ProtocolError.ProtocolFailure(userMessage = "FTPS error $code", technicalDetail = reply, protocolCode = code)
    }

    private fun mapException(throwable: Throwable): ProtocolError {
        if (throwable is ProtocolException) return throwable.protocolError
        var cur: Throwable? = throwable
        while (cur != null) {
            if (cur is CertificateUntrustedException) {
                return ProtocolError.CertificateUntrusted(
                    host = cur.host,
                    port = cur.port,
                    fingerprintSha256 = cur.sha256Fingerprint,
                    subject = cur.subject,
                )
            }
            cur = cur.cause
        }
        return when (throwable) {
            is SSLHandshakeException -> ProtocolError.ProtocolFailure(
                userMessage = "TLS handshake failed.",
                nextStep = "Confirm FTPS explicit/implicit mode and TLS 1.2/1.3 compatibility.",
                technicalDetail = throwable.message,
            )
            is SocketTimeoutException -> ProtocolError.Timeout(technicalDetail = throwable.message)
            is IOException -> ProtocolError.ConnectionFailed(technicalDetail = throwable.message, isTransient = true)
            else -> ProtocolError.ProtocolFailure(
                userMessage = throwable.message ?: "Unexpected FTPS error.",
                technicalDetail = throwable.javaClass.simpleName,
            )
        }
    }
}
