package com.portly.protocol.webdav.client

import com.portly.core.common.ChecksumUtils
import com.portly.core.common.PathValidator
import com.portly.core.common.ProtocolException
import com.portly.core.database.dao.TrustedCertDao
import com.portly.core.model.AuthType
import com.portly.core.model.ConnectionProfile
import com.portly.core.model.ConnectionTestResult
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
import okhttp3.ConnectionSpec
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.TlsVersion
import okio.BufferedSink
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLHandshakeException

internal interface WebDavCertStore {
    suspend fun trustedHostFingerprints(host: String, port: Int): Set<String>
}

internal class RoomWebDavCertStore(
    private val trustedCertDao: TrustedCertDao,
) : WebDavCertStore {
    override suspend fun trustedHostFingerprints(host: String, port: Int): Set<String> =
        trustedCertDao.getForHost(host, port).map { it.sha256Fingerprint }.toSet()
}

internal class OkHttpWebDavProtocolClient(
    private val certStore: WebDavCertStore,
    private val certificateManager: CertificateManager,
) : ProtocolClient {

    override val protocolType: ProtocolType = ProtocolType.WEBDAV

    private var httpClient: OkHttpClient? = null
    private var activeProfile: ConnectionProfile? = null
    private var activeCreds: ResolvedCredentials? = null
    private var baseUrl: String = ""
    @Volatile
    private var lastDigestChallenge: String? = null

    override val isConnected: Boolean
        get() = httpClient != null && activeProfile != null

    override suspend fun connect(
        profile: ConnectionProfile,
        credentials: ResolvedCredentials,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val scheme = if (profile.useHttpsForWebDav) "https" else "http"
            val builder = OkHttpClient.Builder()
                .connectTimeout(profile.connectTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
                .readTimeout(profile.connectTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
                .writeTimeout(profile.connectTimeoutMs.toLong(), TimeUnit.MILLISECONDS)

            if (profile.useHttpsForWebDav) {
                val pinned = certStore.trustedHostFingerprints(profile.host, profile.port)
                val tm = certificateManager.buildTrustManager(
                    host = profile.host,
                    port = profile.port,
                    trustedHostFingerprints = pinned,
                    userCaCertificates = emptyList(),
                )
                val sslCtx = certificateManager.buildSslContext(tm)
                val tlsSpec = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                    .tlsVersions(
                        *(if (profile.tlsVersionPolicy == TlsVersionPolicy.TLS_1_3_ONLY) {
                            arrayOf(TlsVersion.TLS_1_3)
                        } else {
                            arrayOf(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2)
                        }),
                    )
                    .build()
                builder.sslSocketFactory(sslCtx.socketFactory, tm)
                builder.connectionSpecs(listOf(tlsSpec))
            } else {
                builder.connectionSpecs(listOf(ConnectionSpec.CLEARTEXT))
            }

            val client = builder.build()
            val root = PathValidator.normalizeRemotePath(profile.rootPath)
            val builtBase = "$scheme://${profile.host}:${profile.port}"

            httpClient = client
            activeProfile = profile
            activeCreds = credentials
            baseUrl = builtBase

            // Verify connectivity + credentials with Depth: 0 PROPFIND on root
            val req = buildRequest("PROPFIND", buildUrl(root)) {
                header("Depth", "0")
                method("PROPFIND", PROPFIND_BODY.toRequestBody(XML_MEDIA))
            }
            executeWithAuth(client, req, profile, credentials).use { response ->
                if (!response.isSuccessful && response.code != 207) {
                    throw ProtocolException(mapHttpCode(response.code, response.message))
                }
            }
            Unit
        }.recoverCatching { err ->
            httpClient = null
            activeProfile = null
            activeCreds = null
            throw ProtocolException(mapError(err), err)
        }
    }

    override suspend fun disconnect() {
        httpClient = null
        activeProfile = null
        activeCreds = null
        lastDigestChallenge = null
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
            return@withContext ConnectionTestResult(false, latency, error = err)
        }
        val root = PathValidator.normalizeRemotePath(profile.rootPath)
        disconnect()
        ConnectionTestResult(
            success = true,
            latencyMs = latency,
            serverBanner = if (profile.useHttpsForWebDav) "WebDAV over HTTPS" else "WebDAV over HTTP",
            currentDirectory = root,
        )
    }

    override suspend fun list(path: String): Result<List<RemoteFile>> = withContext(Dispatchers.IO) {
        runCatching {
            val (client, profile, creds) = requireConnected()
            val norm = PathValidator.normalizeRemotePath(path)
            val req = buildRequest("PROPFIND", buildUrl(norm, trailingSlash = true)) {
                header("Depth", "1")
                method("PROPFIND", PROPFIND_BODY.toRequestBody(XML_MEDIA))
            }
            executeWithAuth(client, req, profile, creds).use { resp ->
                if (!resp.isSuccessful && resp.code != 207) {
                    throw ProtocolException(mapHttpCode(resp.code, resp.message))
                }
                val xml = resp.body.string()
                WebDavXmlParser.parseMultiStatus(xml, norm, excludeSelf = true)
            }
        }.recoverCatching { err -> throw ProtocolException(mapError(err), err) }
    }

    override suspend fun stat(path: String): Result<RemoteFile> = withContext(Dispatchers.IO) {
        runCatching {
            val (client, profile, creds) = requireConnected()
            val norm = PathValidator.normalizeRemotePath(path)
            val req = buildRequest("PROPFIND", buildUrl(norm)) {
                header("Depth", "0")
                method("PROPFIND", PROPFIND_BODY.toRequestBody(XML_MEDIA))
            }
            executeWithAuth(client, req, profile, creds).use { resp ->
                if (!resp.isSuccessful && resp.code != 207) {
                    throw ProtocolException(mapHttpCode(resp.code, resp.message))
                }
                val xml = resp.body.string()
                WebDavXmlParser.parseMultiStatus(xml, norm, excludeSelf = false).firstOrNull()
                    ?: throw ProtocolException(ProtocolError.PathNotFound(technicalDetail = norm))
            }
        }.recoverCatching { err -> throw ProtocolException(mapError(err), err) }
    }

    override suspend fun mkdir(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val (client, profile, creds) = requireConnected()
            val norm = PathValidator.normalizeRemotePath(path)
            val req = buildRequest("MKCOL", buildUrl(norm, trailingSlash = true)) {
                method("MKCOL", ByteArray(0).toRequestBody(null))
            }
            executeWithAuth(client, req, profile, creds).use { resp ->
                if (!resp.isSuccessful && resp.code != 201) {
                    throw ProtocolException(mapHttpCode(resp.code, resp.message))
                }
            }
            Unit
        }.recoverCatching { err -> throw ProtocolException(mapError(err), err) }
    }

    override suspend fun rename(oldPath: String, newPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val (client, profile, creds) = requireConnected()
            val srcUrl = buildUrl(PathValidator.normalizeRemotePath(oldPath))
            val dstUrl = buildUrl(PathValidator.normalizeRemotePath(newPath))
            val req = buildRequest("MOVE", srcUrl) {
                header("Destination", dstUrl)
                header("Overwrite", "T")
                method("MOVE", ByteArray(0).toRequestBody(null))
            }
            executeWithAuth(client, req, profile, creds).use { resp ->
                if (!resp.isSuccessful && resp.code != 201 && resp.code != 204) {
                    throw ProtocolException(mapHttpCode(resp.code, resp.message))
                }
            }
            Unit
        }.recoverCatching { err -> throw ProtocolException(mapError(err), err) }
    }

    override suspend fun delete(path: String, recursive: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val (client, profile, creds) = requireConnected()
            val req = buildRequest("DELETE", buildUrl(PathValidator.normalizeRemotePath(path))) {
                delete()
            }
            executeWithAuth(client, req, profile, creds).use { resp ->
                if (!resp.isSuccessful && resp.code != 204) {
                    throw ProtocolException(mapHttpCode(resp.code, resp.message))
                }
            }
            Unit
        }.recoverCatching { err -> throw ProtocolException(mapError(err), err) }
    }

    override suspend fun chmod(path: String, posixMode: Int): Result<Unit> =
        Result.failure(ProtocolException(ProtocolError.UnsupportedOperation("WebDAV does not support POSIX chmod.")))

    override suspend fun openInputStream(path: String, offset: Long): Result<ProtocolInputHandle> =
        withContext(Dispatchers.IO) {
            runCatching {
                val (client, profile, creds) = requireConnected()
                val req = buildRequest("GET", buildUrl(PathValidator.normalizeRemotePath(path))) {
                    get()
                    if (offset > 0L) header("Range", "bytes=$offset-")
                }
                val resp = executeWithAuth(client, req, profile, creds)
                if (!resp.isSuccessful && resp.code != 206) {
                    val code = resp.code
                    val msg = resp.message
                    resp.close()
                    throw ProtocolException(mapHttpCode(code, msg))
                }
                val stream = resp.body.byteStream()
                object : ProtocolInputHandle {
                    override val stream: InputStream = stream
                    override suspend fun complete(): Result<Unit> = runCatching {
                        resp.close()
                    }
                    override fun close() = resp.close()
                }
            }.recoverCatching { err -> throw ProtocolException(mapError(err), err) }
        }

    override suspend fun openOutputStream(
        path: String,
        offset: Long,
        totalSize: Long,
    ): Result<ProtocolOutputHandle> = withContext(Dispatchers.IO) {
        runCatching {
            val (client, profile, creds) = requireConnected()
            val url = buildUrl(PathValidator.normalizeRemotePath(path))
            val pipedIn = PipedInputStream(ChecksumUtils.STREAM_BUFFER_SIZE)
            val pipedOut = PipedOutputStream(pipedIn)

            val streamingBody = object : RequestBody() {
                override fun contentType() = OCTET_MEDIA
                override fun contentLength(): Long = if (totalSize >= 0L) totalSize else -1L
                override fun isOneShot(): Boolean = true
                override fun writeTo(sink: BufferedSink) {
                    val buf = ByteArray(ChecksumUtils.STREAM_BUFFER_SIZE)
                    while (true) {
                        val read = pipedIn.read(buf)
                        if (read <= 0) break
                        sink.write(buf, 0, read)
                    }
                }
            }

            val uploadFuture = CompletableFuture<Response>()
            val req = buildRequest("PUT", url) {
                put(streamingBody)
            }
            val worker = Thread {
                runCatching { executeWithAuth(client, req, profile, creds) }
                    .onSuccess { uploadFuture.complete(it) }
                    .onFailure { uploadFuture.completeExceptionally(it) }
            }
            worker.start()

            object : ProtocolOutputHandle {
                override val stream: OutputStream = pipedOut
                override suspend fun complete(): Result<Unit> = withContext(Dispatchers.IO) {
                    runCatching {
                        pipedOut.flush()
                        pipedOut.close()
                        val resp = uploadFuture.get()
                        resp.use {
                            if (!it.isSuccessful && it.code != 201 && it.code != 204) {
                                throw ProtocolException(mapHttpCode(it.code, it.message))
                            }
                        }
                        Unit
                    }
                }
                override fun close() {
                    runCatching { pipedOut.close() }
                    runCatching { pipedIn.close() }
                }
            }
        }.recoverCatching { err -> throw ProtocolException(mapError(err), err) }
    }

    override suspend fun computeChecksum(path: String, algorithm: String): Result<String?> =
        Result.success(null)

    private fun executeWithAuth(
        client: OkHttpClient,
        baseRequest: Request,
        profile: ConnectionProfile,
        creds: ResolvedCredentials,
    ): Response {
        val initialBuilder = baseRequest.newBuilder()
        val hasCreds = profile.authType != AuthType.ANONYMOUS &&
            (creds.username.isNotBlank() || !creds.password.isNullOrEmpty())
        val cachedChallenge = lastDigestChallenge
        if (hasCreds) {
            val preemptiveDigest = cachedChallenge?.let {
                DigestAuthHelper.buildDigestHeader(
                    wwwAuthenticate = it,
                    method = baseRequest.method,
                    uriPath = baseRequest.url.encodedPath,
                    username = creds.username,
                    password = creds.password.orEmpty(),
                )
            }
            initialBuilder.header(
                "Authorization",
                preemptiveDigest ?: Credentials.basic(creds.username, creds.password.orEmpty()),
            )
        }
        val firstResp = client.newCall(initialBuilder.build()).execute()
        if (firstResp.code != 401 || !hasCreds) return firstResp

        val wwwAuth = firstResp.header("WWW-Authenticate").orEmpty()
        val digestAuth = DigestAuthHelper.buildDigestHeader(
            wwwAuthenticate = wwwAuth,
            method = baseRequest.method,
            uriPath = baseRequest.url.encodedPath,
            username = creds.username,
            password = creds.password.orEmpty(),
        ) ?: return firstResp

        lastDigestChallenge = wwwAuth
        firstResp.close()
        if (baseRequest.body?.isOneShot() == true) {
            throw ProtocolException(
                ProtocolError.AuthenticationFailed(
                    userMessage = "WebDAV Digest nonce expired during streaming upload.",
                    nextStep = "Retry the upload to refresh the Digest authentication challenge.",
                ),
            )
        }
        val retryReq = baseRequest.newBuilder()
            .header("Authorization", digestAuth)
            .build()
        return client.newCall(retryReq).execute()
    }

    private inline fun buildRequest(method: String, url: String, block: Request.Builder.() -> Unit): Request {
        return Request.Builder().url(url).apply(block).build()
    }

    private fun buildUrl(remotePath: String, trailingSlash: Boolean = false): String {
        val encoded = remotePath.trimStart('/')
            .split('/')
            .filter { it.isNotEmpty() }
            .joinToString("/") { seg ->
                URLEncoder.encode(seg, "UTF-8").replace("+", "%20")
            }
        val pathPart = if (encoded.isEmpty()) "/" else "/$encoded"
        val suffix = if (trailingSlash && !pathPart.endsWith("/")) "/" else ""
        return "$baseUrl$pathPart$suffix"
    }

    private fun requireConnected(): Triple<OkHttpClient, ConnectionProfile, ResolvedCredentials> {
        val c = httpClient
        val p = activeProfile
        val cr = activeCreds
        if (c == null || p == null || cr == null) {
            throw ProtocolException(ProtocolError.ConnectionFailed("Not connected to WebDAV server."))
        }
        return Triple(c, p, cr)
    }

    private fun mapHttpCode(code: Int, message: String): ProtocolError = when (code) {
        401 -> ProtocolError.AuthenticationFailed(technicalDetail = "HTTP $code $message", protocolCode = code)
        403 -> ProtocolError.PermissionDenied(technicalDetail = "HTTP $code $message", protocolCode = code)
        404 -> ProtocolError.PathNotFound(technicalDetail = "HTTP $code $message", protocolCode = code)
        else -> ProtocolError.ProtocolFailure(
            userMessage = "WebDAV server returned HTTP $code.",
            technicalDetail = "HTTP $code $message",
            protocolCode = code,
            isTransient = code in 500..599,
        )
    }

    private fun mapError(throwable: Throwable): ProtocolError {
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
                userMessage = "WebDAV TLS handshake failed.",
                technicalDetail = throwable.message,
            )
            is SocketTimeoutException -> ProtocolError.Timeout(technicalDetail = throwable.message)
            is IOException -> ProtocolError.ConnectionFailed(technicalDetail = throwable.message, isTransient = true)
            else -> ProtocolError.ProtocolFailure(
                userMessage = throwable.message ?: "Unexpected WebDAV error.",
                technicalDetail = throwable.javaClass.simpleName,
            )
        }
    }

    private companion object {
        val XML_MEDIA = "application/xml; charset=utf-8".toMediaType()
        val OCTET_MEDIA = "application/octet-stream".toMediaType()
        const val PROPFIND_BODY = """<?xml version="1.0" encoding="utf-8" ?><D:propfind xmlns:D="DAV:"><D:allprop/></D:propfind>"""
    }
}
