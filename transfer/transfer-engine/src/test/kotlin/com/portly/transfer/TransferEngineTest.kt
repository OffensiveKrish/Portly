package com.portly.transfer

import com.portly.core.common.ChecksumUtils
import com.portly.core.common.ProtocolException
import com.portly.core.model.ActivityLogEntry
import com.portly.core.model.AppSettings
import com.portly.core.model.AuthType
import com.portly.core.model.ConflictPolicy
import com.portly.core.model.ConnectionProfile
import com.portly.core.model.ConnectionTestResult
import com.portly.core.model.ProtocolClient
import com.portly.core.model.ProtocolClientFactory
import com.portly.core.model.ProtocolError
import com.portly.core.model.ProtocolInputHandle
import com.portly.core.model.ProtocolOutputHandle
import com.portly.core.model.ProtocolType
import com.portly.core.model.RemoteFile
import com.portly.core.model.ResolvedCredentials
import com.portly.core.model.TransferDirection
import com.portly.core.model.TransferHistoryEntry
import com.portly.core.model.TransferId
import com.portly.core.model.TransferSpec
import com.portly.core.model.TransferState
import com.portly.core.model.TransferStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicInteger

class TransferEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun tearDown() {
        testScope.cancel()
    }

    @Test
    fun `download streams 64KB chunks and verifies SHA-256 checksum`() = runBlocking {
        val payload = ByteArray(200_000) { (it % 251).toByte() }
        val expectedHash = ChecksumUtils.computeDigest(ByteArrayInputStream(payload), "SHA-256")
        val fakeClient = FakeProtocolClient(files = mutableMapOf("/remote.bin" to payload))
        val store = InMemoryTransferStore()
        val localIo = JvmLocalEndpointIo()
        val engine = createEngine(store, localIo, fakeClient)

        val dest = File(tempFolder.root, "local.bin")
        val spec = TransferSpec(
            id = TransferId("t1"),
            direction = TransferDirection.DOWNLOAD,
            protocol = ProtocolType.SFTP,
            profileId = "p1",
            fileName = "local.bin",
            sourcePath = "/remote.bin",
            destinationPath = dest.absolutePath,
            totalBytes = payload.size.toLong(),
            conflictPolicy = ConflictPolicy.OVERWRITE_ALWAYS,
        )

        engine.enqueue(spec)
        val finalState = awaitTerminal(engine, spec.id)

        assertEquals(TransferStatus.COMPLETED, finalState.status)
        assertEquals(true, finalState.checksumVerified)
        assertEquals(expectedHash, ChecksumUtils.computeFileDigest(dest, "SHA-256"))
    }

    @Test
    fun `checksum mismatch transitions transfer to FAILED`() = runBlocking {
        val payload = "hello portly".toByteArray()
        val fakeClient = FakeProtocolClient(
            files = mutableMapOf("/bad.txt" to payload),
            forcedChecksum = "0000deadbeef",
        )
        val store = InMemoryTransferStore()
        val engine = createEngine(store, JvmLocalEndpointIo(), fakeClient)

        val dest = File(tempFolder.root, "bad.txt")
        val spec = TransferSpec(
            id = TransferId("t_bad_hash"),
            direction = TransferDirection.DOWNLOAD,
            protocol = ProtocolType.SFTP,
            profileId = "p1",
            fileName = "bad.txt",
            sourcePath = "/bad.txt",
            destinationPath = dest.absolutePath,
            totalBytes = payload.size.toLong(),
            maxRetries = 0,
        )

        engine.enqueue(spec)
        val finalState = awaitTerminal(engine, spec.id)

        assertEquals(TransferStatus.FAILED, finalState.status)
        assertTrue(finalState.error is ProtocolError.ChecksumMismatch)
    }

    @Test
    fun `transient error retries with backoff and completes`() = runBlocking {
        val payload = "retry succeeds".toByteArray()
        val attempts = AtomicInteger(0)
        val fakeClient = FakeProtocolClient(
            files = mutableMapOf("/retry.txt" to payload),
            failFirstNConnects = 2,
            connectAttempts = attempts,
        )
        val store = InMemoryTransferStore()
        val engine = createEngine(store, JvmLocalEndpointIo(), fakeClient)

        val dest = File(tempFolder.root, "retry.txt")
        val spec = TransferSpec(
            id = TransferId("t_retry"),
            direction = TransferDirection.DOWNLOAD,
            protocol = ProtocolType.SFTP,
            profileId = "p1",
            fileName = "retry.txt",
            sourcePath = "/retry.txt",
            destinationPath = dest.absolutePath,
            totalBytes = payload.size.toLong(),
            maxRetries = 3,
        )

        engine.enqueue(spec)
        val finalState = awaitTerminal(engine, spec.id)

        assertEquals(TransferStatus.COMPLETED, finalState.status)
        assertEquals(3, attempts.get())
        assertEquals("retry succeeds", dest.readText())
    }

    @Test
    fun `permanent failure can be retried back to QUEUED and COMPLETED`() = runBlocking {
        val payload = "recovered".toByteArray()
        val fakeClient = FakeProtocolClient(
            files = mutableMapOf("/perm.txt" to payload),
            rejectAuth = true,
        )
        val store = InMemoryTransferStore()
        val engine = createEngine(store, JvmLocalEndpointIo(), fakeClient)

        val dest = File(tempFolder.root, "perm.txt")
        val spec = TransferSpec(
            id = TransferId("t_perm"),
            direction = TransferDirection.DOWNLOAD,
            protocol = ProtocolType.SFTP,
            profileId = "p1",
            fileName = "perm.txt",
            sourcePath = "/perm.txt",
            destinationPath = dest.absolutePath,
            totalBytes = payload.size.toLong(),
        )

        engine.enqueue(spec)
        val failed = awaitTerminal(engine, spec.id)
        assertEquals(TransferStatus.FAILED, failed.status)

        fakeClient.rejectAuth = false
        engine.retry(spec.id)
        val recovered = withTimeout(5_000) {
            engine.observeTransfer(spec.id).first { it?.status == TransferStatus.COMPLETED }!!
        }
        assertEquals(TransferStatus.COMPLETED, recovered.status)
        assertEquals("recovered", dest.readText())
    }

    @Test
    fun `duplicate detection skips transfer when destination size and mtime match`() = runBlocking {
        val payload = "already synced".toByteArray()
        val dest = File(tempFolder.root, "same.txt").apply {
            writeBytes(payload)
            setLastModified(1_700_000_000_000L)
        }
        val fakeClient = FakeProtocolClient(
            files = mutableMapOf("/same.txt" to payload),
            mtimeMillis = 1_700_000_000_500L,
        )
        val store = InMemoryTransferStore()
        val engine = createEngine(store, JvmLocalEndpointIo(), fakeClient)

        val spec = TransferSpec(
            id = TransferId("t_dup"),
            direction = TransferDirection.DOWNLOAD,
            protocol = ProtocolType.SFTP,
            profileId = "p1",
            fileName = "same.txt",
            sourcePath = "/same.txt",
            destinationPath = dest.absolutePath,
            totalBytes = payload.size.toLong(),
            sourceModifiedMillis = 1_700_000_000_500L,
            conflictPolicy = ConflictPolicy.ASK,
        )

        engine.enqueue(spec)
        val completed = awaitTerminal(engine, spec.id)
        assertEquals(TransferStatus.COMPLETED, completed.status)
        assertEquals(0, fakeClient.openInputCalls)
    }

    @Test
    fun `network loss pauses active remote transfer and restore resumes it`() = runBlocking {
        val payload = ByteArray(128_000) { 1 }
        val fakeClient = FakeProtocolClient(files = mutableMapOf("/slow.bin" to payload), chunkDelayMs = 50L)
        val store = InMemoryTransferStore()
        val engine = createEngine(store, JvmLocalEndpointIo(), fakeClient)

        val dest = File(tempFolder.root, "slow.bin")
        val spec = TransferSpec(
            id = TransferId("t_net"),
            direction = TransferDirection.DOWNLOAD,
            protocol = ProtocolType.SFTP,
            profileId = "p1",
            fileName = "slow.bin",
            sourcePath = "/slow.bin",
            destinationPath = dest.absolutePath,
            totalBytes = payload.size.toLong(),
            conflictPolicy = ConflictPolicy.OVERWRITE_ALWAYS,
        )

        engine.enqueue(spec)
        withTimeout(5_000) {
            engine.observeTransfer(spec.id).first { it?.status == TransferStatus.RUNNING }
        }
        engine.pauseAllForNetworkLoss()
        withTimeout(5_000) {
            engine.observeTransfer(spec.id).first { it?.status == TransferStatus.PAUSED }
        }
        fakeClient.chunkDelayMs = 0L
        engine.resumeNetworkPaused()
        val completed = awaitTerminal(engine, spec.id)
        assertEquals(TransferStatus.COMPLETED, completed.status)
    }

    @Test
    fun `conflict ASK pauses and resolveConflict RENAME produces unique file`() = runBlocking {
        val dest = File(tempFolder.root, "doc.txt").apply { writeText("existing") }
        val payload = "incoming".toByteArray()
        val fakeClient = FakeProtocolClient(files = mutableMapOf("/doc.txt" to payload))
        val store = InMemoryTransferStore()
        val engine = createEngine(store, JvmLocalEndpointIo(), fakeClient)

        val spec = TransferSpec(
            id = TransferId("t_conflict"),
            direction = TransferDirection.DOWNLOAD,
            protocol = ProtocolType.SFTP,
            profileId = "p1",
            fileName = "doc.txt",
            sourcePath = "/doc.txt",
            destinationPath = dest.absolutePath,
            totalBytes = payload.size.toLong(),
            conflictPolicy = ConflictPolicy.ASK,
        )

        engine.enqueue(spec)
        val paused = withTimeout(5_000) {
            engine.observeTransfer(spec.id).first {
                it?.status == TransferStatus.PAUSED && it.awaitingConflictResolution
            }!!
        }
        assertTrue(paused.awaitingConflictResolution)

        engine.resolveConflict(spec.id, ConflictPolicy.RENAME)
        val completed = awaitTerminal(engine, spec.id)
        assertEquals(TransferStatus.COMPLETED, completed.status)
        assertEquals("existing", dest.readText())
        val renamed = File(tempFolder.root, "doc (1).txt")
        assertTrue(renamed.exists())
        assertEquals("incoming", renamed.readText())
    }

    @Test
    fun `resume continues download from existing part file offset`() = runBlocking {
        val payload = ByteArray(100_000) { (it % 199).toByte() }
        val dest = File(tempFolder.root, "resume.bin")
        val partFile = File(tempFolder.root, "resume.bin.part").apply {
            writeBytes(payload.copyOfRange(0, 40_000))
        }
        assertTrue(partFile.exists())

        val fakeClient = FakeProtocolClient(files = mutableMapOf("/resume.bin" to payload))
        val store = InMemoryTransferStore()
        val engine = createEngine(store, JvmLocalEndpointIo(), fakeClient)

        val spec = TransferSpec(
            id = TransferId("t_resume"),
            direction = TransferDirection.DOWNLOAD,
            protocol = ProtocolType.SFTP,
            profileId = "p1",
            fileName = "resume.bin",
            sourcePath = "/resume.bin",
            destinationPath = dest.absolutePath,
            totalBytes = payload.size.toLong(),
            conflictPolicy = ConflictPolicy.OVERWRITE_ALWAYS,
        )

        engine.enqueue(spec)
        val completed = awaitTerminal(engine, spec.id)
        assertEquals(TransferStatus.COMPLETED, completed.status)
        assertEquals(40_000L, fakeClient.lastRequestedOffset)
        assertTrue(payload.contentEquals(dest.readBytes()))
    }

    @Test
    fun `folder upload walks recursively and queues one entry per file`() = runBlocking {
        val rootDir = tempFolder.newFolder("batch")
        File(rootDir, "a.txt").writeText("one")
        val sub = File(rootDir, "sub").apply { mkdirs() }
        File(sub, "b.txt").writeText("two")

        val fakeClient = FakeProtocolClient(files = mutableMapOf())
        val store = InMemoryTransferStore()
        val engine = createEngine(store, JvmLocalEndpointIo(), fakeClient)

        val groupId = engine.enqueueFolderUpload(
            localDirPath = rootDir.absolutePath,
            remoteDirPath = "/uploads",
            protocol = ProtocolType.SFTP,
            profileId = "p1",
            conflictPolicy = ConflictPolicy.OVERWRITE_ALWAYS,
        )

        val all = withTimeout(5_000) {
            engine.observeTransfers().first { list ->
                list.count { it.spec.groupId == groupId && it.status == TransferStatus.COMPLETED } == 2
            }
        }
        assertEquals(2, all.count { it.spec.groupId == groupId })
        assertEquals("one", String(fakeClient.files["/uploads/a.txt"]!!))
        assertEquals("two", String(fakeClient.files["/uploads/sub/b.txt"]!!))
    }

    private fun createEngine(
        store: InMemoryTransferStore,
        localIo: LocalEndpointIo,
        client: FakeProtocolClient,
    ): PortlyTransferEngine = PortlyTransferEngine(
        store = store,
        localIo = localIo,
        clientFactories = mapOf(ProtocolType.SFTP to ProtocolClientFactory { client }),
        settingsFlow = MutableStateFlow(AppSettings()),
        ioDispatcher = Dispatchers.IO,
        appScope = testScope,
        baseRetryDelayMs = 10L,
        progressPersistIntervalMs = 50L,
    )

    private suspend fun awaitTerminal(engine: PortlyTransferEngine, id: TransferId): TransferState {
        return withTimeout(8_000) {
            engine.observeTransfer(id).first {
                it != null && it.status in setOf(
                    TransferStatus.COMPLETED,
                    TransferStatus.FAILED,
                    TransferStatus.CANCELLED,
                )
            }!!
        }
    }
}

private class InMemoryTransferStore : TransferStore {
    private val items = MutableStateFlow<Map<String, Pair<TransferState, Boolean>>>(emptyMap())
    val history = mutableListOf<TransferHistoryEntry>()
    val logs = mutableListOf<ActivityLogEntry>()

    override fun observeAll(): Flow<List<TransferState>> =
        items.map { map -> map.values.map { it.first }.sortedByDescending { it.createdAtMillis } }

    override fun observeById(id: TransferId): Flow<TransferState?> =
        items.map { map -> map[id.value]?.first }

    override suspend fun getById(id: TransferId): TransferState? = items.value[id.value]?.first

    override suspend fun getActiveOrQueued(): List<TransferState> =
        items.value.values.map { it.first }.filter {
            it.status == TransferStatus.QUEUED || it.status == TransferStatus.RUNNING
        }

    override suspend fun getNetworkPaused(): List<TransferState> =
        items.value.values.filter { it.first.status == TransferStatus.PAUSED && it.second }.map { it.first }

    override suspend fun upsert(state: TransferState, pausedByNetworkLoss: Boolean) {
        items.update { it + (state.id.value to (state to pausedByNetworkLoss)) }
    }

    override suspend fun recordHistory(entry: TransferHistoryEntry) {
        history += entry
    }

    override suspend fun logActivity(entry: ActivityLogEntry) {
        logs += entry
    }

    override suspend fun loadConnection(profileId: String): Pair<ConnectionProfile, ResolvedCredentials> {
        val profile = ConnectionProfile(
            id = profileId,
            name = "Test",
            protocol = ProtocolType.SFTP,
            host = "127.0.0.1",
            port = 22,
            username = "tester",
            authType = AuthType.PASSWORD,
        )
        return profile to ResolvedCredentials("tester", "secret")
    }
}

private class JvmLocalEndpointIo : LocalEndpointIo {
    override suspend fun stat(path: String, uriString: String?): LocalStat {
        val f = File(path)
        return LocalStat(f.exists(), f.isDirectory, if (f.isFile) f.length() else 0L, f.lastModified())
    }

    override suspend fun listChildren(path: String, uriString: String?): List<LocalChild> {
        return File(path).listFiles()?.map { f ->
            LocalChild(f.name, f.absolutePath, null, f.isDirectory, if (f.isFile) f.length() else 0L, f.lastModified())
        } ?: emptyList()
    }

    override suspend fun listSiblings(path: String, uriString: String?): Set<String> =
        File(path).parentFile?.list()?.toSet() ?: emptySet()

    override suspend fun openInput(path: String, uriString: String?, offset: Long): InputStream {
        val fis = FileInputStream(File(path))
        if (offset > 0) fis.skip(offset)
        return fis
    }

    override suspend fun openTempPartOutput(
        destinationPath: String,
        destinationUriString: String?,
        append: Boolean,
    ): PartOutputHandle {
        val finalFile = File(destinationPath)
        finalFile.parentFile?.mkdirs()
        val partFile = File(finalFile.parentFile, "${finalFile.name}.part")
        val fos = FileOutputStream(partFile, append)
        return object : PartOutputHandle {
            override val stream: OutputStream = fos
            override suspend fun commit(finalDestinationPath: String, finalDestinationUriString: String?) {
                fos.flush()
                fos.close()
                val target = File(finalDestinationPath)
                if (target.exists()) target.delete()
                partFile.renameTo(target)
            }
            override fun close() {
                runCatching { fos.close() }
            }
        }
    }

    override suspend fun currentPartSize(destinationPath: String, destinationUriString: String?): Long {
        val finalFile = File(destinationPath)
        val part = File(finalFile.parentFile, "${finalFile.name}.part")
        return if (part.exists()) part.length() else 0L
    }

    override suspend fun deletePart(destinationPath: String, destinationUriString: String?) {
        val finalFile = File(destinationPath)
        File(finalFile.parentFile, "${finalFile.name}.part").delete()
    }

    override suspend fun copyLocal(sourcePath: String, sourceUriString: String?, destPath: String, destUriString: String?) {
        File(sourcePath).copyTo(File(destPath), overwrite = true)
    }

    override suspend fun deleteSource(path: String, uriString: String?) {
        File(path).delete()
    }
}

private class FakeProtocolClient(
    val files: MutableMap<String, ByteArray>,
    var rejectAuth: Boolean = false,
    private val failFirstNConnects: Int = 0,
    private val connectAttempts: AtomicInteger = AtomicInteger(0),
    private val forcedChecksum: String? = null,
    private val mtimeMillis: Long = 1000L,
    var chunkDelayMs: Long = 0L,
) : ProtocolClient {
    override val protocolType: ProtocolType = ProtocolType.SFTP
    override var isConnected: Boolean = false
    var lastRequestedOffset: Long = 0L
    var openInputCalls: Int = 0

    override suspend fun connect(profile: ConnectionProfile, credentials: ResolvedCredentials): Result<Unit> {
        val attempt = connectAttempts.incrementAndGet()
        if (rejectAuth) {
            return Result.failure(ProtocolException(ProtocolError.AuthenticationFailed()))
        }
        if (attempt <= failFirstNConnects) {
            return Result.failure(IOException("Simulated transient connection drop"))
        }
        isConnected = true
        return Result.success(Unit)
    }

    override suspend fun disconnect() {
        isConnected = false
    }

    override suspend fun testConnection(profile: ConnectionProfile, credentials: ResolvedCredentials) =
        ConnectionTestResult(true, 1L)

    override suspend fun list(path: String): Result<List<RemoteFile>> = Result.success(emptyList())

    override suspend fun stat(path: String): Result<RemoteFile> {
        val data = files[path] ?: return Result.failure(ProtocolException(ProtocolError.PathNotFound()))
        return Result.success(
            RemoteFile(path, path.substringAfterLast('/'), false, data.size.toLong(), mtimeMillis, "application/octet-stream"),
        )
    }

    override suspend fun mkdir(path: String): Result<Unit> = Result.success(Unit)
    override suspend fun rename(oldPath: String, newPath: String): Result<Unit> = Result.success(Unit)
    override suspend fun delete(path: String, recursive: Boolean): Result<Unit> = Result.success(Unit)
    override suspend fun chmod(path: String, posixMode: Int): Result<Unit> = Result.success(Unit)

    override suspend fun openInputStream(path: String, offset: Long): Result<ProtocolInputHandle> {
        openInputCalls++
        lastRequestedOffset = offset
        val bytes = files[path] ?: return Result.failure(ProtocolException(ProtocolError.PathNotFound()))
        val slice = bytes.copyOfRange(offset.toInt().coerceIn(0, bytes.size), bytes.size)
        val bais = ByteArrayInputStream(slice)
        val wrapped = object : InputStream() {
            override fun read(): Int = bais.read()
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (chunkDelayMs > 0L) Thread.sleep(chunkDelayMs)
                return bais.read(b, off, len)
            }
            override fun close() = bais.close()
        }
        return Result.success(
            object : ProtocolInputHandle {
                override val stream: InputStream = wrapped
                override suspend fun complete(): Result<Unit> = Result.success(Unit)
                override fun close() = wrapped.close()
            },
        )
    }

    override suspend fun openOutputStream(path: String, offset: Long, totalSize: Long): Result<ProtocolOutputHandle> {
        val baos = ByteArrayOutputStream()
        return Result.success(
            object : ProtocolOutputHandle {
                override val stream: OutputStream = baos
                override suspend fun complete(): Result<Unit> {
                    files[path] = baos.toByteArray()
                    return Result.success(Unit)
                }
                override fun close() = baos.close()
            },
        )
    }

    override suspend fun computeChecksum(path: String, algorithm: String): Result<String?> {
        if (forcedChecksum != null) return Result.success(forcedChecksum)
        val bytes = files[path] ?: return Result.success(null)
        return Result.success(ChecksumUtils.computeDigest(ByteArrayInputStream(bytes), algorithm))
    }
}
