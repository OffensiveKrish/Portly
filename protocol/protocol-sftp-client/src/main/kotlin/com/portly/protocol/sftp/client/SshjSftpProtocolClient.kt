package com.portly.protocol.sftp.client

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
import com.portly.core.security.CertificateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.DisconnectReason
import net.schmizz.sshj.sftp.FileMode
import net.schmizz.sshj.sftp.OpenMode
import net.schmizz.sshj.sftp.Response
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.sftp.SFTPException
import net.schmizz.sshj.transport.TransportException
import net.schmizz.sshj.userauth.UserAuthException
import net.schmizz.sshj.userauth.password.PasswordUtils
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.SocketTimeoutException
import java.util.EnumSet

internal class SshjSftpProtocolClient(
    private val hostKeyStore: HostKeyStore,
) : ProtocolClient {

    override val protocolType: ProtocolType = ProtocolType.SFTP

    private var sshClient: SSHClient? = null
    private var sftpClient: SFTPClient? = null

    override val isConnected: Boolean
        get() = sshClient?.isConnected == true && sftpClient != null

    init {
        CertificateManager.ensureBouncyCastleRegistered()
    }

    override suspend fun connect(
        profile: ConnectionProfile,
        credentials: ResolvedCredentials,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            disconnectInternal()
            val ssh = SSHClient(DefaultConfig()).apply {
                connectTimeout = profile.connectTimeoutMs
                timeout = profile.connectTimeoutMs
                addHostKeyVerifier(PortlySshHostKeyVerifier(hostKeyStore, profile.port))
            }
            ssh.connect(profile.host, profile.port)

            val user = credentials.username.ifBlank { profile.username }.ifBlank { "root" }
            when (profile.authType) {
                AuthType.PRIVATE_KEY -> {
                    val pem = credentials.privateKeyPem
                        ?: throw ProtocolException(
                            ProtocolError.AuthenticationFailed(
                                userMessage = "Missing SSH private key.",
                                nextStep = "Attach or paste a private key in the connection profile.",
                            ),
                        )
                    val finder = credentials.passphrase?.takeIf { it.isNotEmpty() }?.let { pass ->
                        PasswordUtils.createOneOff(pass.toCharArray())
                    }
                    val keyProvider = ssh.loadKeys(pem, null, finder)
                    ssh.authPublickey(user, keyProvider)
                }
                AuthType.ANONYMOUS -> {
                    ssh.authPassword(user, "")
                }
                AuthType.PASSWORD -> {
                    ssh.authPassword(user, credentials.password ?: "")
                }
            }

            val sftp = ssh.newSFTPClient()
            sshClient = ssh
            sftpClient = sftp
            Unit
        }.recoverCatching { err ->
            disconnectInternal()
            throw ProtocolException(mapError(err), err)
        }
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        disconnectInternal()
    }

    private fun disconnectInternal() {
        runCatching { sftpClient?.close() }
        runCatching { sshClient?.disconnect() }
        sftpClient = null
        sshClient = null
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
                ?: mapError(conn.exceptionOrNull()!!)
            return@withContext ConnectionTestResult(success = false, latencyMs = latency, error = err)
        }
        val banner = runCatching { sshClient?.transport?.serverVersion }.getOrNull()
        val cwd = runCatching { sftpClient?.canonicalize(".") }.getOrNull()
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
            val sftp = requireSftp()
            val norm = PathValidator.normalizeRemotePath(path)
            sftp.ls(norm)
                .filter { it.name != "." && it.name != ".." }
                .map { info ->
                    val attrs = info.attributes
                    val isDir = info.isDirectory
                    val mode = attrs.mode?.permissionsMask ?: 0
                    RemoteFile(
                        path = PathValidator.joinRemotePath(norm, info.name),
                        name = info.name,
                        isDirectory = isDir,
                        sizeBytes = if (isDir) 0L else attrs.size,
                        lastModifiedMillis = attrs.mtime * 1000L,
                        mimeType = Formatters.guessMimeType(info.name, isDir),
                        permissions = FilePermissions(
                            posixMode = if (mode != 0) mode else null,
                            readable = true,
                            writable = true,
                            executable = (mode and 0b001_000_000) != 0,
                        ),
                        isSymlink = attrs.type == FileMode.Type.SYMLINK,
                    )
                }
        }.recoverCatching { err ->
            throw ProtocolException(mapError(err), err)
        }
    }

    override suspend fun stat(path: String): Result<RemoteFile> = withContext(Dispatchers.IO) {
        runCatching {
            val sftp = requireSftp()
            val norm = PathValidator.normalizeRemotePath(path)
            val attrs = sftp.stat(norm)
            val isDir = attrs.type == FileMode.Type.DIRECTORY
            val name = if (norm == "/") "/" else norm.substringAfterLast('/')
            val mode = attrs.mode?.permissionsMask ?: 0
            RemoteFile(
                path = norm,
                name = name,
                isDirectory = isDir,
                sizeBytes = if (isDir) 0L else attrs.size,
                lastModifiedMillis = attrs.mtime * 1000L,
                mimeType = Formatters.guessMimeType(name, isDir),
                permissions = FilePermissions(posixMode = if (mode != 0) mode else null),
                isSymlink = attrs.type == FileMode.Type.SYMLINK,
            )
        }.recoverCatching { err ->
            throw ProtocolException(mapError(err), err)
        }
    }

    override suspend fun mkdir(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val sftp = requireSftp()
            sftp.mkdirs(PathValidator.normalizeRemotePath(path))
        }.recoverCatching { err ->
            throw ProtocolException(mapError(err), err)
        }
    }

    override suspend fun rename(oldPath: String, newPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val sftp = requireSftp()
            sftp.rename(PathValidator.normalizeRemotePath(oldPath), PathValidator.normalizeRemotePath(newPath))
        }.recoverCatching { err ->
            throw ProtocolException(mapError(err), err)
        }
    }

    override suspend fun delete(path: String, recursive: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val sftp = requireSftp()
            val norm = PathValidator.normalizeRemotePath(path)
            deleteInternal(sftp, norm, recursive)
        }.recoverCatching { err ->
            throw ProtocolException(mapError(err), err)
        }
    }

    private fun deleteInternal(sftp: SFTPClient, path: String, recursive: Boolean) {
        val attrs = sftp.lstat(path)
        if (attrs.type == FileMode.Type.DIRECTORY) {
            if (recursive) {
                for (child in sftp.ls(path)) {
                    if (child.name == "." || child.name == "..") continue
                    deleteInternal(sftp, PathValidator.joinRemotePath(path, child.name), true)
                }
            }
            sftp.rmdir(path)
        } else {
            sftp.rm(path)
        }
    }

    override suspend fun chmod(path: String, posixMode: Int): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val sftp = requireSftp()
            sftp.chmod(PathValidator.normalizeRemotePath(path), posixMode and 0b111_111_111)
        }.recoverCatching { err ->
            throw ProtocolException(mapError(err), err)
        }
    }

    override suspend fun openInputStream(path: String, offset: Long): Result<ProtocolInputHandle> =
        withContext(Dispatchers.IO) {
            runCatching {
                val sftp = requireSftp()
                val norm = PathValidator.normalizeRemotePath(path)
                val remoteFile = sftp.open(norm, EnumSet.of(OpenMode.READ))
                val stream = remoteFile.ReadAheadRemoteFileInputStream(16, offset.coerceAtLeast(0L))
                object : ProtocolInputHandle {
                    override val stream: InputStream = stream
                    override suspend fun complete(): Result<Unit> = runCatching {
                        stream.close()
                        remoteFile.close()
                    }
                    override fun close() {
                        runCatching { stream.close() }
                        runCatching { remoteFile.close() }
                    }
                }
            }.recoverCatching { err ->
                throw ProtocolException(mapError(err), err)
            }
        }

    override suspend fun openOutputStream(
        path: String,
        offset: Long,
        totalSize: Long,
    ): Result<ProtocolOutputHandle> = withContext(Dispatchers.IO) {
        runCatching {
            val sftp = requireSftp()
            val norm = PathValidator.normalizeRemotePath(path)
            val modes = if (offset > 0L) {
                EnumSet.of(OpenMode.WRITE, OpenMode.CREAT)
            } else {
                EnumSet.of(OpenMode.WRITE, OpenMode.CREAT, OpenMode.TRUNC)
            }
            val remoteFile = sftp.open(norm, modes)
            val stream = remoteFile.RemoteFileOutputStream(offset.coerceAtLeast(0L))
            object : ProtocolOutputHandle {
                override val stream: OutputStream = stream
                override suspend fun complete(): Result<Unit> = runCatching {
                    stream.flush()
                    stream.close()
                    remoteFile.close()
                }
                override fun close() {
                    runCatching { stream.close() }
                    runCatching { remoteFile.close() }
                }
            }
        }.recoverCatching { err ->
            throw ProtocolException(mapError(err), err)
        }
    }

    override suspend fun computeChecksum(path: String, algorithm: String): Result<String?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val ssh = sshClient?.takeIf { it.isConnected } ?: return@runCatching null
                val norm = PathValidator.normalizeRemotePath(path)
                val bin = when (algorithm.uppercase()) {
                    "SHA-256", "SHA256" -> "sha256sum"
                    "SHA-1", "SHA1" -> "sha1sum"
                    "MD5" -> "md5sum"
                    else -> return@runCatching null
                }
                val escaped = norm.replace("'", "'\\''")
                ssh.startSession().use { session ->
                    val cmd = session.exec("$bin '$escaped'")
                    val out = cmd.inputStream.bufferedReader().readText().trim()
                    cmd.join()
                    if (cmd.exitStatus == 0) {
                        Regex("""^[0-9a-fA-F]{32,64}""").find(out)?.value?.lowercase()
                    } else {
                        null
                    }
                }
            }
        }

    private fun requireSftp(): SFTPClient =
        sftpClient?.takeIf { sshClient?.isConnected == true }
            ?: throw ProtocolException(ProtocolError.ConnectionFailed("Not connected to SFTP server."))

    private fun mapError(throwable: Throwable): ProtocolError {
        if (throwable is ProtocolException) return throwable.protocolError
        var cur: Throwable? = throwable
        while (cur != null) {
            if (cur is HostKeyMismatchException) return cur.toProtocolError()
            cur = cur.cause
        }
        return when (throwable) {
            is UserAuthException -> ProtocolError.AuthenticationFailed(
                userMessage = "SFTP authentication failed.",
                nextStep = "Check your username, password, or SSH private key and passphrase.",
                technicalDetail = throwable.message,
            )
            is SFTPException -> when (throwable.statusCode) {
                Response.StatusCode.NO_SUCH_FILE -> ProtocolError.PathNotFound(
                    technicalDetail = throwable.message,
                    protocolCode = throwable.statusCode.code,
                )
                Response.StatusCode.PERMISSION_DENIED -> ProtocolError.PermissionDenied(
                    technicalDetail = throwable.message,
                    protocolCode = throwable.statusCode.code,
                )
                else -> ProtocolError.ProtocolFailure(
                    userMessage = "SFTP operation failed (${throwable.statusCode}).",
                    technicalDetail = throwable.message,
                    protocolCode = throwable.statusCode.code,
                )
            }
            is TransportException -> {
                if (throwable.disconnectReason == DisconnectReason.HOST_KEY_NOT_VERIFIABLE) {
                    ProtocolError.ProtocolFailure(
                        userMessage = "SSH host key verification failed.",
                        nextStep = "Inspect and update known_hosts for this server.",
                        technicalDetail = throwable.message,
                    )
                } else {
                    ProtocolError.ConnectionFailed(
                        userMessage = "SSH transport error.",
                        technicalDetail = throwable.message,
                        isTransient = true,
                    )
                }
            }
            is SocketTimeoutException -> ProtocolError.Timeout(technicalDetail = throwable.message)
            is IOException -> ProtocolError.ConnectionFailed(
                userMessage = "Could not connect to SFTP server.",
                technicalDetail = throwable.message,
                isTransient = true,
            )
            else -> ProtocolError.ProtocolFailure(
                userMessage = throwable.message ?: "Unexpected SFTP error.",
                technicalDetail = throwable.javaClass.simpleName,
            )
        }
    }
}
