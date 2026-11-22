package com.portly.protocol.smb.client

import com.portly.core.common.Formatters
import com.portly.core.common.PathValidator
import com.portly.core.common.ProtocolException
import com.portly.core.datastore.PortlySettingsDataStore
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
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtStatus
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbAuthException
import jcifs.smb.SmbException
import jcifs.smb.SmbFile
import jcifs.smb.SmbRandomAccessFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.SocketTimeoutException
import java.util.Properties

internal class JcifsSmbProtocolClient(
    private val settingsDataStore: PortlySettingsDataStore?,
    private val legacyOverrideForTests: Boolean? = null,
) : ProtocolClient {

    override val protocolType: ProtocolType = ProtocolType.SMB

    private var cifsContext: CIFSContext? = null
    private var activeProfile: ConnectionProfile? = null

    override val isConnected: Boolean
        get() = cifsContext != null && activeProfile != null

    override suspend fun connect(
        profile: ConnectionProfile,
        credentials: ResolvedCredentials,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val globalLegacy = legacyOverrideForTests
                ?: settingsDataStore?.settings?.first()?.smbLegacyEnabled
                ?: false
            val allowSmb1 = globalLegacy && profile.smbLegacyMode

            val props = buildJcifsProperties(
                timeoutMs = profile.connectTimeoutMs,
                allowSmb1Legacy = allowSmb1,
            )
            val baseCtx = BaseContext(PropertyConfiguration(props))
            val ctx = when (profile.authType) {
                AuthType.ANONYMOUS -> baseCtx.withAnonymousCredentials()
                else -> {
                    val user = credentials.username.ifBlank { profile.username }
                    if (user.isBlank()) {
                        baseCtx.withAnonymousCredentials()
                    } else {
                        baseCtx.withCredentials(
                            NtlmPasswordAuthenticator(
                                profile.smbDomain.ifBlank { null },
                                user,
                                credentials.password ?: "",
                            ),
                        )
                    }
                }
            }

            // Probe root or configured share to validate reachability and auth
            val probeUrl = buildSmbUrl(profile, "/")
            val rootFile = SmbFile(probeUrl, ctx)
            rootFile.connect()

            cifsContext = ctx
            activeProfile = profile
            Unit
        }.recoverCatching { err ->
            cifsContext = null
            activeProfile = null
            throw ProtocolException(mapSmbError(err), err)
        }
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        runCatching { cifsContext?.close() }
        cifsContext = null
        activeProfile = null
    }

    override suspend fun testConnection(
        profile: ConnectionProfile,
        credentials: ResolvedCredentials,
    ): ConnectionTestResult = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val res = connect(profile, credentials)
        val latency = (System.currentTimeMillis() - started).coerceAtLeast(1L)
        if (res.isFailure) {
            val err = (res.exceptionOrNull() as? ProtocolException)?.protocolError
                ?: mapSmbError(res.exceptionOrNull()!!)
            return@withContext ConnectionTestResult(false, latency, error = err)
        }
        val ctx = cifsContext!!
        val shareCount = runCatching { SmbFile(buildSmbUrl(profile, "/"), ctx).listFiles()?.size ?: 0 }.getOrDefault(0)
        disconnect()
        ConnectionTestResult(
            success = true,
            latencyMs = latency,
            serverBanner = if (profile.smbShare.isBlank()) "SMB2/3 ($shareCount shares)" else "SMB2/3 (${profile.smbShare})",
            currentDirectory = PathValidator.normalizeRemotePath(profile.rootPath),
        )
    }

    override suspend fun list(path: String): Result<List<RemoteFile>> = withContext(Dispatchers.IO) {
        runCatching {
            val (ctx, profile) = requireConnected()
            val norm = PathValidator.normalizeRemotePath(path)
            val url = buildSmbUrl(profile, norm, ensureTrailingSlash = true)
            val dir = SmbFile(url, ctx)
            val entries = dir.listFiles() ?: emptyArray()
            entries.mapNotNull { smb ->
                val rawName = smb.name.trimEnd('/')
                if (rawName.isEmpty() || rawName == "." || rawName == "..") return@mapNotNull null
                // Hide administrative IPC$/ADMIN$ shares in root browse unless explicitly targeted
                if (norm == "/" && profile.smbShare.isBlank() && rawName.endsWith("$")) {
                    return@mapNotNull null
                }
                smbFileToRemoteFile(PathValidator.joinRemotePath(norm, rawName), smb, rawName)
            }
        }.recoverCatching { err -> throw ProtocolException(mapSmbError(err), err) }
    }

    override suspend fun stat(path: String): Result<RemoteFile> = withContext(Dispatchers.IO) {
        runCatching {
            val (ctx, profile) = requireConnected()
            val norm = PathValidator.normalizeRemotePath(path)
            val smb = SmbFile(buildSmbUrl(profile, norm), ctx)
            if (!smb.exists()) throw ProtocolException(ProtocolError.PathNotFound(technicalDetail = norm))
            val name = if (norm == "/") "/" else smb.name.trimEnd('/')
            smbFileToRemoteFile(norm, smb, name)
        }.recoverCatching { err -> throw ProtocolException(mapSmbError(err), err) }
    }

    override suspend fun mkdir(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val (ctx, profile) = requireConnected()
            val norm = PathValidator.normalizeRemotePath(path)
            SmbFile(buildSmbUrl(profile, norm, ensureTrailingSlash = true), ctx).mkdirs()
        }.recoverCatching { err -> throw ProtocolException(mapSmbError(err), err) }
    }

    override suspend fun rename(oldPath: String, newPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val (ctx, profile) = requireConnected()
            val src = SmbFile(buildSmbUrl(profile, PathValidator.normalizeRemotePath(oldPath)), ctx)
            val dst = SmbFile(buildSmbUrl(profile, PathValidator.normalizeRemotePath(newPath)), ctx)
            src.renameTo(dst)
        }.recoverCatching { err -> throw ProtocolException(mapSmbError(err), err) }
    }

    override suspend fun delete(path: String, recursive: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val (ctx, profile) = requireConnected()
            val norm = PathValidator.normalizeRemotePath(path)
            val target = SmbFile(buildSmbUrl(profile, norm), ctx)
            if (target.isDirectory && recursive) {
                deleteRecursive(target)
            } else {
                target.delete()
            }
        }.recoverCatching { err -> throw ProtocolException(mapSmbError(err), err) }
    }

    private fun deleteRecursive(dir: SmbFile) {
        if (dir.isDirectory) {
            dir.listFiles()?.forEach { child ->
                deleteRecursive(child)
            }
        }
        dir.delete()
    }

    override suspend fun chmod(path: String, posixMode: Int): Result<Unit> =
        Result.failure(ProtocolException(ProtocolError.UnsupportedOperation("SMB does not support POSIX chmod.")))

    override suspend fun openInputStream(path: String, offset: Long): Result<ProtocolInputHandle> =
        withContext(Dispatchers.IO) {
            runCatching {
                val (ctx, profile) = requireConnected()
                val smb = SmbFile(buildSmbUrl(profile, PathValidator.normalizeRemotePath(path)), ctx)
                val stream: InputStream = if (offset > 0L) {
                    val raf = SmbRandomAccessFile(smb, "r")
                    raf.seek(offset)
                    object : InputStream() {
                        override fun read(): Int = raf.read()
                        override fun read(b: ByteArray, off: Int, len: Int): Int = raf.read(b, off, len)
                        override fun close() = raf.close()
                    }
                } else {
                    smb.openInputStream()
                }
                object : ProtocolInputHandle {
                    override val stream: InputStream = stream
                    override suspend fun complete(): Result<Unit> = runCatching { stream.close() }
                    override fun close() = runCatching { stream.close() }.let { }
                }
            }.recoverCatching { err -> throw ProtocolException(mapSmbError(err), err) }
        }

    override suspend fun openOutputStream(
        path: String,
        offset: Long,
        totalSize: Long,
    ): Result<ProtocolOutputHandle> = withContext(Dispatchers.IO) {
        runCatching {
            val (ctx, profile) = requireConnected()
            val smb = SmbFile(buildSmbUrl(profile, PathValidator.normalizeRemotePath(path)), ctx)
            val stream: OutputStream = if (offset > 0L) {
                val raf = SmbRandomAccessFile(smb, "rw")
                raf.seek(offset)
                object : OutputStream() {
                    override fun write(b: Int) = raf.write(b)
                    override fun write(b: ByteArray, off: Int, len: Int) = raf.write(b, off, len)
                    override fun close() = raf.close()
                }
            } else {
                smb.openOutputStream()
            }
            object : ProtocolOutputHandle {
                override val stream: OutputStream = stream
                override suspend fun complete(): Result<Unit> = runCatching {
                    stream.flush()
                    stream.close()
                }
                override fun close() = runCatching { stream.close() }.let { }
            }
        }.recoverCatching { err -> throw ProtocolException(mapSmbError(err), err) }
    }

    override suspend fun computeChecksum(path: String, algorithm: String): Result<String?> =
        Result.success(null)

    private fun requireConnected(): Pair<CIFSContext, ConnectionProfile> {
        val ctx = cifsContext
        val profile = activeProfile
        if (ctx == null || profile == null) {
            throw ProtocolException(ProtocolError.ConnectionFailed("Not connected to SMB server."))
        }
        return ctx to profile
    }

    private fun smbFileToRemoteFile(fullPath: String, smb: SmbFile, name: String): RemoteFile {
        val isDir = runCatching { smb.isDirectory }.getOrDefault(false)
        val size = if (isDir) 0L else runCatching { smb.length() }.getOrDefault(0L)
        val mtime = runCatching { smb.lastModified }.getOrDefault(0L)
        return RemoteFile(
            path = fullPath,
            name = name,
            isDirectory = isDir,
            sizeBytes = size,
            lastModifiedMillis = mtime,
            mimeType = Formatters.guessMimeType(name, isDir),
            permissions = FilePermissions(
                readable = runCatching { smb.canRead() }.getOrDefault(true),
                writable = runCatching { smb.canWrite() }.getOrDefault(true),
            ),
        )
    }

    companion object {
        fun buildJcifsProperties(timeoutMs: Int, allowSmb1Legacy: Boolean): Properties = Properties().apply {
            setProperty("jcifs.smb.client.enableSMB2", "true")
            setProperty("jcifs.smb.client.useSMB2Negotiation", "true")
            setProperty("jcifs.smb.client.minVersion", if (allowSmb1Legacy) "SMB1" else "SMB202")
            setProperty("jcifs.smb.client.maxVersion", "SMB311")
            setProperty("jcifs.smb.client.connTimeout", timeoutMs.toString())
            setProperty("jcifs.smb.client.soTimeout", timeoutMs.toString())
            setProperty("jcifs.smb.client.responseTimeout", timeoutMs.toString())
            setProperty("jcifs.resolveOrder", "DNS,BCAST")
        }

        fun buildSmbUrl(
            profile: ConnectionProfile,
            virtualPath: String,
            ensureTrailingSlash: Boolean = false,
        ): String {
            val norm = PathValidator.normalizeRemotePath(virtualPath).trimStart('/')
            val portSuffix = if (profile.port == 445) "" else ":${profile.port}"
            val sharePart = profile.smbShare.trim().trim('/')
            val combined = when {
                sharePart.isEmpty() && norm.isEmpty() -> ""
                sharePart.isEmpty() -> norm
                norm.isEmpty() -> sharePart
                else -> "$sharePart/$norm"
            }
            val base = if (combined.isEmpty()) {
                "smb://${profile.host}$portSuffix/"
            } else {
                "smb://${profile.host}$portSuffix/$combined"
            }
            return if (ensureTrailingSlash && !base.endsWith("/")) "$base/" else base
        }

        fun mapSmbError(throwable: Throwable): ProtocolError {
            if (throwable is ProtocolException) return throwable.protocolError
            return when (throwable) {
                is SmbAuthException -> ProtocolError.AuthenticationFailed(
                    userMessage = "SMB authentication failed.",
                    nextStep = "Verify username, password, and workgroup/domain.",
                    technicalDetail = throwable.message,
                )
                is SmbException -> when (throwable.ntStatus) {
                    NtStatus.NT_STATUS_LOGON_FAILURE -> ProtocolError.AuthenticationFailed(
                        userMessage = "SMB authentication failed.",
                        nextStep = "Verify username, password, and workgroup/domain.",
                        technicalDetail = throwable.message,
                    )
                    NtStatus.NT_STATUS_ACCESS_DENIED,
                    -> ProtocolError.PermissionDenied(
                        technicalDetail = throwable.message,
                        protocolCode = throwable.ntStatus,
                    )
                    NtStatus.NT_STATUS_OBJECT_NAME_NOT_FOUND,
                    NtStatus.NT_STATUS_OBJECT_PATH_NOT_FOUND,
                    NtStatus.NT_STATUS_BAD_NETWORK_NAME,
                    -> ProtocolError.PathNotFound(
                        technicalDetail = throwable.message,
                        protocolCode = throwable.ntStatus,
                    )
                    else -> ProtocolError.ProtocolFailure(
                        userMessage = "SMB error: ${throwable.message ?: "operation failed"}",
                        technicalDetail = "NT_STATUS=0x${Integer.toHexString(throwable.ntStatus)}",
                        protocolCode = throwable.ntStatus,
                    )
                }
                is SocketTimeoutException -> ProtocolError.Timeout(technicalDetail = throwable.message)
                is IOException -> ProtocolError.ConnectionFailed(
                    userMessage = "Could not connect to SMB host.",
                    nextStep = "Ensure device and host are on the same LAN and SMB2/3 (port 445) is enabled.",
                    technicalDetail = throwable.message,
                    isTransient = true,
                )
                else -> ProtocolError.ProtocolFailure(
                    userMessage = throwable.message ?: "Unexpected SMB error.",
                    technicalDetail = throwable.javaClass.simpleName,
                )
            }
        }
    }
}
