package com.portly.core.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.io.InputStream
import java.io.OutputStream

data class ConnectionTestResult(
    val success: Boolean,
    val latencyMs: Long,
    val serverBanner: String? = null,
    val currentDirectory: String? = null,
    val error: ProtocolError? = null,
)

interface ProtocolClient {
    val protocolType: ProtocolType
    val isConnected: Boolean

    suspend fun connect(
        profile: ConnectionProfile,
        credentials: ResolvedCredentials,
    ): Result<Unit>

    suspend fun disconnect()

    suspend fun testConnection(
        profile: ConnectionProfile,
        credentials: ResolvedCredentials,
    ): ConnectionTestResult

    suspend fun list(path: String): Result<List<RemoteFile>>

    suspend fun stat(path: String): Result<RemoteFile>

    suspend fun mkdir(path: String): Result<Unit>

    suspend fun rename(oldPath: String, newPath: String): Result<Unit>

    suspend fun delete(path: String, recursive: Boolean = false): Result<Unit>

    suspend fun chmod(path: String, posixMode: Int): Result<Unit>

    /**
     * Opens a streaming [InputStream] starting at [offset] bytes for resumable downloads.
     * Callers MUST invoke [finalizeStream] or close cleanly via returned [ProtocolInputHandle].
     */
    suspend fun openInputStream(path: String, offset: Long = 0L): Result<ProtocolInputHandle>

    /**
     * Opens a streaming [OutputStream] for uploads. When [offset] > 0, appends/resumes where supported.
     */
    suspend fun openOutputStream(
        path: String,
        offset: Long = 0L,
        totalSize: Long = -1L,
    ): Result<ProtocolOutputHandle>

    suspend fun computeChecksum(path: String, algorithm: String = "SHA-256"): Result<String?>
}

interface ProtocolInputHandle : AutoCloseable {
    val stream: InputStream
    suspend fun complete(): Result<Unit>
}

interface ProtocolOutputHandle : AutoCloseable {
    val stream: OutputStream
    suspend fun complete(): Result<Unit>
}

fun interface ProtocolClientFactory {
    fun create(): ProtocolClient
}

interface ProtocolServer {
    val serverType: ServerType
    val state: StateFlow<ServerRuntimeState>
    val clients: StateFlow<List<ConnectedClientInfo>>
    val logs: StateFlow<List<ServerLogEntry>>

    suspend fun start(config: ServerConfig): Result<Unit>
    suspend fun stop()
    suspend fun kickClient(clientId: String): Result<Unit>
    fun updateLocalAddresses(addresses: List<String>)
}

fun interface ProtocolServerFactory {
    fun create(): ProtocolServer
}

interface TransferEngine {
    fun enqueue(spec: TransferSpec): Flow<TransferState>
    fun pause(id: TransferId)
    fun resume(id: TransferId)
    fun cancel(id: TransferId)
    fun retry(id: TransferId)
    fun resolveConflict(id: TransferId, policy: ConflictPolicy)
    fun observeTransfers(): Flow<List<TransferState>>
    fun observeTransfer(id: TransferId): Flow<TransferState?>
    suspend fun reconcilePending()
    suspend fun pauseAllForNetworkLoss()
    suspend fun resumeNetworkPaused()
}
