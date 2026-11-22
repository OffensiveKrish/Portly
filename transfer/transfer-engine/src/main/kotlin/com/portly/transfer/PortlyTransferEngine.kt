package com.portly.transfer

import com.portly.core.common.ApplicationScope
import com.portly.core.common.ChecksumUtils
import com.portly.core.common.IoDispatcher
import com.portly.core.common.PathValidator
import com.portly.core.common.ProtocolException
import com.portly.core.datastore.PortlySettingsDataStore
import com.portly.core.model.ActivityLogEntry
import com.portly.core.model.ActivitySeverity
import com.portly.core.model.AppSettings
import com.portly.core.model.ConflictPolicy
import com.portly.core.model.ProtocolClient
import com.portly.core.model.ProtocolClientFactory
import com.portly.core.model.ProtocolError
import com.portly.core.model.ProtocolType
import com.portly.core.model.RemoteFile
import com.portly.core.model.TransferDirection
import com.portly.core.model.TransferEngine
import com.portly.core.model.TransferHistoryEntry
import com.portly.core.model.TransferId
import com.portly.core.model.TransferProgress
import com.portly.core.model.TransferSpec
import com.portly.core.model.TransferState
import com.portly.core.model.TransferStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class PortlyTransferEngine(
    private val store: TransferStore,
    private val localIo: LocalEndpointIo,
    private val clientFactories: Map<ProtocolType, @JvmSuppressWildcards ProtocolClientFactory>,
    private val settingsFlow: Flow<AppSettings>,
    private val ioDispatcher: CoroutineDispatcher,
    private val appScope: CoroutineScope,
    private val baseRetryDelayMs: Long = 1_000L,
    private val progressPersistIntervalMs: Long = 400L,
) : TransferEngine {

    @Inject
    constructor(
        store: TransferStore,
        localIo: LocalEndpointIo,
        clientFactories: Map<ProtocolType, @JvmSuppressWildcards ProtocolClientFactory>,
        settingsDataStore: PortlySettingsDataStore,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
        @ApplicationScope appScope: CoroutineScope,
    ) : this(
        store = store,
        localIo = localIo,
        clientFactories = clientFactories,
        settingsFlow = settingsDataStore.settings,
        ioDispatcher = ioDispatcher,
        appScope = appScope,
    )

    private enum class InterruptReason {
        NONE,
        PAUSE,
        PAUSE_NETWORK,
        CANCEL,
    }

    private val scheduleMutex = Mutex()
    private val runningJobs = ConcurrentHashMap<String, Job>()
    private val interruptReasons = ConcurrentHashMap<String, InterruptReason>()
    private val protocolSemaphores = ConcurrentHashMap<ProtocolType, Semaphore>()
    private val _schedulerTrigger = MutableStateFlow(0L)

    init {
        appScope.launch(ioDispatcher) {
            _schedulerTrigger.collect {
                schedulePending()
            }
        }
    }

    override fun enqueue(spec: TransferSpec): Flow<TransferState> {
        appScope.launch(ioDispatcher) {
            val initial = TransferState(
                id = spec.id,
                spec = spec,
                status = TransferStatus.QUEUED,
                progress = TransferProgress(totalBytes = spec.totalBytes),
            )
            interruptReasons.remove(spec.id.value)
            store.upsert(initial, pausedByNetworkLoss = false)
            triggerSchedule()
        }
        return observeTransfer(spec.id).map { state ->
            state ?: TransferState(id = spec.id, spec = spec, status = TransferStatus.QUEUED)
        }
    }

    override fun pause(id: TransferId) {
        interruptReasons[id.value] = InterruptReason.PAUSE
        runningJobs[id.value]?.cancel()
        appScope.launch(ioDispatcher) {
            val current = store.getById(id) ?: return@launch
            if (current.status == TransferStatus.QUEUED || current.status == TransferStatus.RUNNING) {
                store.upsert(
                    current.copy(
                        status = TransferStatus.PAUSED,
                        updatedAtMillis = System.currentTimeMillis(),
                    ),
                    pausedByNetworkLoss = false,
                )
            }
        }
    }

    override fun resume(id: TransferId) {
        interruptReasons.remove(id.value)
        appScope.launch(ioDispatcher) {
            val current = store.getById(id) ?: return@launch
            if (current.status == TransferStatus.PAUSED) {
                store.upsert(
                    current.copy(
                        status = TransferStatus.QUEUED,
                        error = null,
                        awaitingConflictResolution = false,
                        updatedAtMillis = System.currentTimeMillis(),
                    ),
                    pausedByNetworkLoss = false,
                )
                triggerSchedule()
            }
        }
    }

    override fun cancel(id: TransferId) {
        interruptReasons[id.value] = InterruptReason.CANCEL
        runningJobs[id.value]?.cancel()
        appScope.launch(ioDispatcher) {
            val current = store.getById(id) ?: return@launch
            if (current.status in setOf(TransferStatus.QUEUED, TransferStatus.RUNNING, TransferStatus.PAUSED)) {
                val cancelled = current.copy(
                    status = TransferStatus.CANCELLED,
                    updatedAtMillis = System.currentTimeMillis(),
                    completedAtMillis = System.currentTimeMillis(),
                )
                store.upsert(cancelled, pausedByNetworkLoss = false)
                localIo.deletePart(current.spec.destinationPath, current.spec.destinationUriString)
                recordTerminalHistory(cancelled)
            }
        }
    }

    override fun retry(id: TransferId) {
        interruptReasons.remove(id.value)
        appScope.launch(ioDispatcher) {
            val current = store.getById(id) ?: return@launch
            if (current.status == TransferStatus.FAILED || current.status == TransferStatus.CANCELLED) {
                store.upsert(
                    current.copy(
                        status = TransferStatus.QUEUED,
                        error = null,
                        awaitingConflictResolution = false,
                        updatedAtMillis = System.currentTimeMillis(),
                        completedAtMillis = null,
                    ),
                    pausedByNetworkLoss = false,
                )
                triggerSchedule()
            }
        }
    }

    override fun resolveConflict(id: TransferId, policy: ConflictPolicy) {
        interruptReasons.remove(id.value)
        appScope.launch(ioDispatcher) {
            val current = store.getById(id) ?: return@launch
            val updatedSpec = current.spec.copy(conflictPolicy = policy)
            store.upsert(
                current.copy(
                    spec = updatedSpec,
                    status = TransferStatus.QUEUED,
                    awaitingConflictResolution = false,
                    error = null,
                    updatedAtMillis = System.currentTimeMillis(),
                ),
                pausedByNetworkLoss = false,
            )
            triggerSchedule()
        }
    }

    override fun observeTransfers(): Flow<List<TransferState>> = store.observeAll()

    override fun observeTransfer(id: TransferId): Flow<TransferState?> = store.observeById(id)

    override suspend fun reconcilePending() {
        val activeOrQueued = store.getActiveOrQueued()
        val now = System.currentTimeMillis()
        for (item in activeOrQueued) {
            if (item.status == TransferStatus.RUNNING) {
                store.upsert(
                    item.copy(status = TransferStatus.PAUSED, updatedAtMillis = now),
                    pausedByNetworkLoss = false,
                )
            }
        }
        triggerSchedule()
    }

    override suspend fun pauseAllForNetworkLoss() {
        val activeOrQueued = store.getActiveOrQueued()
        val now = System.currentTimeMillis()
        for (item in activeOrQueued) {
            if (item.spec.protocol != null) {
                interruptReasons[item.id.value] = InterruptReason.PAUSE_NETWORK
                runningJobs[item.id.value]?.cancel()
                store.upsert(
                    item.copy(status = TransferStatus.PAUSED, updatedAtMillis = now),
                    pausedByNetworkLoss = true,
                )
            }
        }
    }

    override suspend fun resumeNetworkPaused() {
        val paused = store.getNetworkPaused()
        val now = System.currentTimeMillis()
        for (item in paused) {
            interruptReasons.remove(item.id.value)
            store.upsert(
                item.copy(status = TransferStatus.QUEUED, updatedAtMillis = now),
                pausedByNetworkLoss = false,
            )
        }
        if (paused.isNotEmpty()) triggerSchedule()
    }

    suspend fun enqueueFolderUpload(
        localDirPath: String,
        remoteDirPath: String,
        protocol: ProtocolType,
        profileId: String,
        conflictPolicy: ConflictPolicy,
    ): String {
        val groupId = "grp_${UUID.randomUUID()}"
        val queue = ArrayDeque<Pair<String, String>>()
        queue.addLast(localDirPath to remoteDirPath)
        while (queue.isNotEmpty()) {
            val (curLocal, curRemote) = queue.removeFirst()
            val children = localIo.listChildren(curLocal, null)
            for (child in children) {
                val targetRemote = PathValidator.joinRemotePath(curRemote, child.name)
                if (child.isDirectory) {
                    queue.addLast(child.path to targetRemote)
                } else {
                    enqueue(
                        TransferSpec(
                            id = TransferId(UUID.randomUUID().toString()),
                            groupId = groupId,
                            direction = TransferDirection.UPLOAD,
                            protocol = protocol,
                            profileId = profileId,
                            fileName = child.name,
                            sourcePath = child.path,
                            sourceUriString = child.uriString,
                            destinationPath = targetRemote,
                            totalBytes = child.sizeBytes,
                            sourceModifiedMillis = child.lastModifiedMillis,
                            conflictPolicy = conflictPolicy,
                        ),
                    )
                }
            }
        }
        return groupId
    }

    suspend fun enqueueFolderDownload(
        client: ProtocolClient,
        remoteDirPath: String,
        localDirPath: String,
        protocol: ProtocolType,
        profileId: String,
        conflictPolicy: ConflictPolicy,
    ): String {
        val groupId = "grp_${UUID.randomUUID()}"
        val queue = ArrayDeque<Pair<String, String>>()
        queue.addLast(remoteDirPath to localDirPath)
        while (queue.isNotEmpty()) {
            val (curRemote, curLocal) = queue.removeFirst()
            val remoteEntries: List<RemoteFile> = client.list(curRemote).getOrDefault(emptyList())
            for (entry in remoteEntries) {
                val targetLocal = File(curLocal, PathValidator.sanitizeFileName(entry.name)).absolutePath
                if (entry.isDirectory) {
                    queue.addLast(entry.path to targetLocal)
                } else {
                    enqueue(
                        TransferSpec(
                            id = TransferId(UUID.randomUUID().toString()),
                            groupId = groupId,
                            direction = TransferDirection.DOWNLOAD,
                            protocol = protocol,
                            profileId = profileId,
                            fileName = entry.name,
                            sourcePath = entry.path,
                            destinationPath = targetLocal,
                            totalBytes = entry.sizeBytes,
                            sourceModifiedMillis = entry.lastModifiedMillis,
                            conflictPolicy = conflictPolicy,
                        ),
                    )
                }
            }
        }
        return groupId
    }

    private fun triggerSchedule() {
        _schedulerTrigger.update { it + 1L }
    }

    private suspend fun schedulePending(): Unit = scheduleMutex.withLock {
        val settings = settingsFlow.first()
        val maxConcurrency = settings.defaultTransferConcurrency.coerceIn(1, 6)
        val activeJobs = runningJobs.filterValues { it.isActive }.size
        val availableSlots = (maxConcurrency - activeJobs).coerceAtLeast(0)
        if (availableSlots == 0) return@withLock

        val queued = store.getActiveOrQueued()
            .filter { it.status == TransferStatus.QUEUED && !runningJobs.containsKey(it.id.value) }
            .sortedBy { it.createdAtMillis }

        for (item in queued.take(availableSlots)) {
            val job = appScope.launch(ioDispatcher) {
                try {
                    executeWithPolicy(item, settings)
                } finally {
                    runningJobs.remove(item.id.value)
                    triggerSchedule()
                }
            }
            runningJobs[item.id.value] = job
        }
    }

    private suspend fun executeWithPolicy(initialState: TransferState, settings: AppSettings) {
        val id = initialState.id
        var current = initialState

        val protocol = current.spec.protocol
        if (protocol != null) {
            val semaphore = protocolSemaphores.computeIfAbsent(protocol) {
                Semaphore(perProtocolLimit(protocol))
            }
            semaphore.withPermit {
                runTransferLoop(current, settings)
            }
        } else {
            runTransferLoop(current, settings)
        }
    }

    private suspend fun runTransferLoop(initialState: TransferState, settings: AppSettings) {
        var state = initialState
        val spec = state.spec

        while (true) {
            val interrupt = interruptReasons[spec.id.value]
            if (interrupt != null && interrupt != InterruptReason.NONE) {
                handleInterrupt(state, interrupt)
                return
            }

            state = state.copy(
                status = TransferStatus.RUNNING,
                error = null,
                updatedAtMillis = System.currentTimeMillis(),
            )
            store.upsert(state, pausedByNetworkLoss = false)

            val attemptOutcome = runCatching {
                executeSingleAttempt(state, settings)
            }

            val postInterrupt = interruptReasons[spec.id.value]
            if (postInterrupt != null && postInterrupt != InterruptReason.NONE) {
                val latest = store.getById(spec.id) ?: state
                handleInterrupt(latest, postInterrupt)
                return
            }

            if (attemptOutcome.isSuccess) {
                val finalState = attemptOutcome.getOrThrow()
                store.upsert(finalState, pausedByNetworkLoss = false)
                if (finalState.status in setOf(TransferStatus.COMPLETED, TransferStatus.CANCELLED)) {
                    recordTerminalHistory(finalState)
                }
                return
            }

            val throwable = attemptOutcome.exceptionOrNull()!!
            if (throwable is CancellationException) {
                val latest = store.getById(spec.id) ?: state
                val reason = interruptReasons[spec.id.value] ?: InterruptReason.PAUSE
                handleInterrupt(latest, reason)
                return
            }

            val mappedError = mapToProtocolError(throwable)
            val nextAttempt = state.attemptCount + 1
            val canRetry = settings.autoRetryTransientErrors &&
                mappedError.isTransient &&
                nextAttempt <= spec.maxRetries

            if (canRetry) {
                state = state.copy(
                    attemptCount = nextAttempt,
                    error = mappedError,
                    updatedAtMillis = System.currentTimeMillis(),
                )
                store.upsert(state, pausedByNetworkLoss = false)
                val backoffMs = (baseRetryDelayMs shl (nextAttempt - 1)).coerceAtMost(30_000L)
                delay(backoffMs)
                continue
            } else {
                val failedState = state.copy(
                    status = TransferStatus.FAILED,
                    attemptCount = nextAttempt,
                    error = mappedError,
                    updatedAtMillis = System.currentTimeMillis(),
                    completedAtMillis = System.currentTimeMillis(),
                )
                store.upsert(failedState, pausedByNetworkLoss = false)
                recordTerminalHistory(failedState)
                store.logActivity(
                    ActivityLogEntry(
                        category = "Transfer",
                        severity = ActivitySeverity.ERROR,
                        message = "Transfer failed for ${spec.fileName}: ${mappedError.userMessage}",
                        detail = mappedError.technicalDetail,
                    ),
                )
                return
            }
        }
    }

    private suspend fun handleInterrupt(state: TransferState, reason: InterruptReason) {
        val now = System.currentTimeMillis()
        when (reason) {
            InterruptReason.PAUSE -> {
                store.upsert(state.copy(status = TransferStatus.PAUSED, updatedAtMillis = now), pausedByNetworkLoss = false)
            }
            InterruptReason.PAUSE_NETWORK -> {
                store.upsert(state.copy(status = TransferStatus.PAUSED, updatedAtMillis = now), pausedByNetworkLoss = true)
            }
            InterruptReason.CANCEL -> {
                val cancelled = state.copy(
                    status = TransferStatus.CANCELLED,
                    updatedAtMillis = now,
                    completedAtMillis = now,
                )
                store.upsert(cancelled, pausedByNetworkLoss = false)
                localIo.deletePart(state.spec.destinationPath, state.spec.destinationUriString)
                recordTerminalHistory(cancelled)
            }
            InterruptReason.NONE -> Unit
        }
    }

    private suspend fun executeSingleAttempt(state: TransferState, settings: AppSettings): TransferState {
        val spec = state.spec
        return when (spec.direction) {
            TransferDirection.LOCAL_COPY, TransferDirection.LOCAL_MOVE -> executeLocalTransfer(state)
            TransferDirection.DOWNLOAD, TransferDirection.PEER_RECEIVE -> executeDownload(state, settings)
            TransferDirection.UPLOAD, TransferDirection.PEER_SEND -> executeUpload(state, settings)
        }
    }

    private suspend fun executeLocalTransfer(state: TransferState): TransferState {
        val spec = state.spec
        val destStat = localIo.stat(spec.destinationPath, spec.destinationUriString)
        val resolvedDestPath = resolveLocalDestination(spec, destStat)
            ?: return conflictOrSkipState(state, destStat)

        localIo.copyLocal(spec.sourcePath, spec.sourceUriString, resolvedDestPath, spec.destinationUriString)
        if (spec.direction == TransferDirection.LOCAL_MOVE) {
            localIo.deleteSource(spec.sourcePath, spec.sourceUriString)
        }
        val now = System.currentTimeMillis()
        return state.copy(
            status = TransferStatus.COMPLETED,
            progress = TransferProgress(
                bytesTransferred = spec.totalBytes,
                totalBytes = spec.totalBytes,
                speedBytesPerSec = 0L,
                etaSeconds = 0L,
            ),
            checksumVerified = null,
            updatedAtMillis = now,
            completedAtMillis = now,
        )
    }

    private suspend fun executeDownload(state: TransferState, settings: AppSettings): TransferState {
        val spec = state.spec
        val protocol = spec.protocol ?: throw ProtocolException(ProtocolError.UnsupportedOperation())
        val profileId = spec.profileId ?: throw ProtocolException(ProtocolError.ConnectionFailed())
        val (profile, creds) = store.loadConnection(profileId)
            ?: throw ProtocolException(ProtocolError.ConnectionFailed("Connection profile missing."))
        val factory = clientFactories[protocol]
            ?: throw ProtocolException(ProtocolError.UnsupportedOperation("Protocol ${protocol.displayName} is not registered yet."))

        val client = factory.create()
        client.connect(profile, creds).getOrThrow()
        try {
            val remoteStat = client.stat(spec.sourcePath).getOrNull()
            val expectedTotal = remoteStat?.sizeBytes ?: spec.totalBytes
            val destStat = localIo.stat(spec.destinationPath, spec.destinationUriString)
            val resolvedDestPath = resolveLocalDestination(
                spec.copy(totalBytes = expectedTotal, sourceModifiedMillis = remoteStat?.lastModifiedMillis ?: spec.sourceModifiedMillis),
                destStat,
            ) ?: return conflictOrSkipState(state, destStat)

            val partSize = if (protocol.supportsResume) {
                localIo.currentPartSize(resolvedDestPath, spec.destinationUriString)
            } else {
                0L
            }
            val resumeOffset = if (partSize in 1 until expectedTotal) partSize else 0L
            val append = resumeOffset > 0L

            val inputHandle = client.openInputStream(spec.sourcePath, offset = resumeOffset).getOrThrow()
            val outputHandle = localIo.openTempPartOutput(resolvedDestPath, spec.destinationUriString, append = append)
            val digest = if (settings.verifyChecksums && spec.verifyChecksum && resumeOffset == 0L) {
                MessageDigest.getInstance(spec.checksumAlgorithm)
            } else {
                null
            }

            var bytesTransferred = resumeOffset
            val tracker = RollingSpeedTracker()
            var lastPersistAt = System.currentTimeMillis()

            try {
                inputHandle.use { inHandle ->
                    outputHandle.use { outHandle ->
                        val buffer = ByteArray(ChecksumUtils.STREAM_BUFFER_SIZE)
                        while (true) {
                            if (interruptReasons[spec.id.value] != null) {
                                throw CancellationException("Interrupted")
                            }
                            val read = inHandle.stream.read(buffer)
                            if (read <= 0) break
                            outHandle.stream.write(buffer, 0, read)
                            digest?.update(buffer, 0, read)
                            bytesTransferred += read

                            val now = System.currentTimeMillis()
                            tracker.record(read.toLong(), now)
                            if (now - lastPersistAt >= progressPersistIntervalMs) {
                                lastPersistAt = now
                                val speed = tracker.speedBytesPerSec(now)
                                val eta = tracker.etaSeconds((expectedTotal - bytesTransferred).coerceAtLeast(0L), now)
                                store.upsert(
                                    state.copy(
                                        progress = TransferProgress(bytesTransferred, expectedTotal, speed, eta),
                                        updatedAtMillis = now,
                                    ),
                                )
                            }
                        }
                        inHandle.complete().getOrThrow()
                        outHandle.commit(resolvedDestPath, spec.destinationUriString)
                    }
                }
            } catch (t: Throwable) {
                throw t
            }

            val checksumVerified = verifyTransferChecksum(
                spec = spec,
                settings = settings,
                localInlineHash = digest?.digest()?.joinToString("") { "%02x".format(it) },
                client = client,
                remotePath = spec.sourcePath,
            )

            val now = System.currentTimeMillis()
            return state.copy(
                spec = spec.copy(destinationPath = resolvedDestPath, totalBytes = expectedTotal),
                status = TransferStatus.COMPLETED,
                progress = TransferProgress(bytesTransferred, expectedTotal, tracker.speedBytesPerSec(now), 0L),
                checksumVerified = checksumVerified,
                updatedAtMillis = now,
                completedAtMillis = now,
            )
        } finally {
            runCatching { client.disconnect() }
        }
    }

    private suspend fun executeUpload(state: TransferState, settings: AppSettings): TransferState {
        val spec = state.spec
        val protocol = spec.protocol ?: throw ProtocolException(ProtocolError.UnsupportedOperation())
        val profileId = spec.profileId ?: throw ProtocolException(ProtocolError.ConnectionFailed())
        val (profile, creds) = store.loadConnection(profileId)
            ?: throw ProtocolException(ProtocolError.ConnectionFailed("Connection profile missing."))
        val factory = clientFactories[protocol]
            ?: throw ProtocolException(ProtocolError.UnsupportedOperation("Protocol ${protocol.displayName} is not registered yet."))

        val client = factory.create()
        client.connect(profile, creds).getOrThrow()
        try {
            val sourceStat = localIo.stat(spec.sourcePath, spec.sourceUriString)
            val totalBytes = if (sourceStat.exists) sourceStat.sizeBytes else spec.totalBytes
            val existingRemote = client.stat(spec.destinationPath).getOrNull()

            var resumeOffset = 0L
            var finalRemotePath = spec.destinationPath

            if (existingRemote != null && !existingRemote.isDirectory) {
                val isDuplicate = isDuplicateBySizeAndMtime(
                    sizeA = totalBytes,
                    mtimeA = sourceStat.lastModifiedMillis,
                    sizeB = existingRemote.sizeBytes,
                    mtimeB = existingRemote.lastModifiedMillis,
                )
                if (isDuplicate && spec.conflictPolicy != ConflictPolicy.OVERWRITE_ALWAYS) {
                    val now = System.currentTimeMillis()
                    return state.copy(
                        status = TransferStatus.COMPLETED,
                        progress = TransferProgress(totalBytes, totalBytes, 0L, 0L),
                        updatedAtMillis = now,
                        completedAtMillis = now,
                    )
                }

                if (protocol.supportsResume && existingRemote.sizeBytes in 1 until totalBytes &&
                    spec.conflictPolicy == ConflictPolicy.OVERWRITE_ALWAYS
                ) {
                    resumeOffset = existingRemote.sizeBytes
                } else {
                    when (spec.conflictPolicy) {
                        ConflictPolicy.ASK -> {
                            return state.copy(
                                status = TransferStatus.PAUSED,
                                awaitingConflictResolution = true,
                                updatedAtMillis = System.currentTimeMillis(),
                            )
                        }
                        ConflictPolicy.SKIP -> {
                            val now = System.currentTimeMillis()
                            return state.copy(
                                status = TransferStatus.COMPLETED,
                                progress = TransferProgress(0L, totalBytes, 0L, 0L),
                                updatedAtMillis = now,
                                completedAtMillis = now,
                            )
                        }
                        ConflictPolicy.RENAME -> {
                            val parent = PathValidator.parentRemotePath(spec.destinationPath)
                            val siblings = client.list(parent).getOrDefault(emptyList()).map { it.name }.toSet()
                            val uniqueName = PathValidator.resolveUniqueName(siblings, spec.fileName)
                            finalRemotePath = PathValidator.joinRemotePath(parent, uniqueName)
                        }
                        ConflictPolicy.OVERWRITE_ALWAYS -> Unit
                    }
                }
            }

            val outputHandle = client.openOutputStream(
                path = finalRemotePath,
                offset = resumeOffset,
                totalSize = totalBytes,
            ).getOrThrow()
            val inputStream = localIo.openInput(spec.sourcePath, spec.sourceUriString, offset = resumeOffset)
            val digest = if (settings.verifyChecksums && spec.verifyChecksum && resumeOffset == 0L) {
                MessageDigest.getInstance(spec.checksumAlgorithm)
            } else {
                null
            }

            var bytesTransferred = resumeOffset
            val tracker = RollingSpeedTracker()
            var lastPersistAt = System.currentTimeMillis()

            inputStream.use { inStream ->
                outputHandle.use { outHandle ->
                    val buffer = ByteArray(ChecksumUtils.STREAM_BUFFER_SIZE)
                    while (true) {
                        if (interruptReasons[spec.id.value] != null) {
                            throw CancellationException("Interrupted")
                        }
                        val read = inStream.read(buffer)
                        if (read <= 0) break
                        outHandle.stream.write(buffer, 0, read)
                        digest?.update(buffer, 0, read)
                        bytesTransferred += read

                        val now = System.currentTimeMillis()
                        tracker.record(read.toLong(), now)
                        if (now - lastPersistAt >= progressPersistIntervalMs) {
                            lastPersistAt = now
                            val speed = tracker.speedBytesPerSec(now)
                            val eta = tracker.etaSeconds((totalBytes - bytesTransferred).coerceAtLeast(0L), now)
                            store.upsert(
                                state.copy(
                                    progress = TransferProgress(bytesTransferred, totalBytes, speed, eta),
                                    updatedAtMillis = now,
                                ),
                            )
                        }
                    }
                    outHandle.complete().getOrThrow()
                }
            }

            val checksumVerified = verifyTransferChecksum(
                spec = spec,
                settings = settings,
                localInlineHash = digest?.digest()?.joinToString("") { "%02x".format(it) },
                client = client,
                remotePath = finalRemotePath,
            )

            val now = System.currentTimeMillis()
            return state.copy(
                spec = spec.copy(destinationPath = finalRemotePath, totalBytes = totalBytes),
                status = TransferStatus.COMPLETED,
                progress = TransferProgress(bytesTransferred, totalBytes, tracker.speedBytesPerSec(now), 0L),
                checksumVerified = checksumVerified,
                updatedAtMillis = now,
                completedAtMillis = now,
            )
        } finally {
            runCatching { client.disconnect() }
        }
    }

    private suspend fun verifyTransferChecksum(
        spec: TransferSpec,
        settings: AppSettings,
        localInlineHash: String?,
        client: ProtocolClient,
        remotePath: String,
    ): Boolean? {
        if (!settings.verifyChecksums || !spec.verifyChecksum || localInlineHash == null) return null
        val expected = spec.expectedChecksum
            ?: client.computeChecksum(remotePath, spec.checksumAlgorithm).getOrNull()
            ?: return null
        if (!expected.equals(localInlineHash, ignoreCase = true)) {
            throw ProtocolException(
                ProtocolError.ChecksumMismatch(expected = expected, actual = localInlineHash),
            )
        }
        return true
    }

    private suspend fun resolveLocalDestination(spec: TransferSpec, destStat: LocalStat): String? {
        if (!destStat.exists || destStat.isDirectory) return spec.destinationPath
        val isDuplicate = isDuplicateBySizeAndMtime(
            sizeA = spec.totalBytes,
            mtimeA = spec.sourceModifiedMillis,
            sizeB = destStat.sizeBytes,
            mtimeB = destStat.lastModifiedMillis,
        )
        if (isDuplicate && spec.conflictPolicy != ConflictPolicy.OVERWRITE_ALWAYS) {
            return null
        }
        return when (spec.conflictPolicy) {
            ConflictPolicy.OVERWRITE_ALWAYS -> spec.destinationPath
            ConflictPolicy.RENAME -> {
                val siblings = localIo.listSiblings(spec.destinationPath, spec.destinationUriString)
                val newName = PathValidator.resolveUniqueName(siblings, spec.fileName)
                File(File(spec.destinationPath).parentFile, newName).absolutePath
            }
            ConflictPolicy.ASK, ConflictPolicy.SKIP -> null
        }
    }

    private fun conflictOrSkipState(state: TransferState, destStat: LocalStat): TransferState {
        val spec = state.spec
        val isDuplicate = destStat.exists && isDuplicateBySizeAndMtime(
            sizeA = spec.totalBytes,
            mtimeA = spec.sourceModifiedMillis,
            sizeB = destStat.sizeBytes,
            mtimeB = destStat.lastModifiedMillis,
        )
        val now = System.currentTimeMillis()
        return if (isDuplicate || spec.conflictPolicy == ConflictPolicy.SKIP) {
            state.copy(
                status = TransferStatus.COMPLETED,
                progress = TransferProgress(
                    bytesTransferred = if (isDuplicate) spec.totalBytes else 0L,
                    totalBytes = spec.totalBytes,
                ),
                updatedAtMillis = now,
                completedAtMillis = now,
            )
        } else {
            state.copy(
                status = TransferStatus.PAUSED,
                awaitingConflictResolution = true,
                updatedAtMillis = now,
            )
        }
    }

    private suspend fun recordTerminalHistory(state: TransferState) {
        val elapsedSec = ((state.completedAtMillis ?: System.currentTimeMillis()) - state.createdAtMillis)
            .coerceAtLeast(1000L) / 1000L
        store.recordHistory(
            TransferHistoryEntry(
                id = state.id.value,
                direction = state.spec.direction,
                protocol = state.spec.protocol,
                profileName = state.spec.profileId,
                fileName = state.spec.fileName,
                sourcePath = state.spec.sourcePath,
                destinationPath = state.spec.destinationPath,
                totalBytes = state.spec.totalBytes,
                bytesTransferred = state.progress.bytesTransferred,
                status = state.status,
                averageSpeedBytesPerSec = state.progress.bytesTransferred / elapsedSec,
                checksumVerified = state.checksumVerified,
                errorSummary = state.error?.userMessage,
                startedAtMillis = state.createdAtMillis,
                finishedAtMillis = state.completedAtMillis ?: System.currentTimeMillis(),
            ),
        )
    }

    private fun mapToProtocolError(throwable: Throwable): ProtocolError {
        if (throwable is ProtocolException) return throwable.protocolError
        return when (throwable) {
            is java.net.SocketTimeoutException -> ProtocolError.Timeout(technicalDetail = throwable.message)
            is java.io.IOException -> ProtocolError.ConnectionFailed(
                userMessage = "Network or I/O error during transfer.",
                technicalDetail = throwable.message,
                isTransient = true,
            )
            else -> ProtocolError.ProtocolFailure(
                userMessage = throwable.message ?: "Unexpected transfer error.",
                technicalDetail = throwable.javaClass.simpleName,
            )
        }
    }

    companion object {
        fun isDuplicateBySizeAndMtime(
            sizeA: Long,
            mtimeA: Long,
            sizeB: Long,
            mtimeB: Long,
        ): Boolean {
            if (sizeA < 0L || sizeB < 0L) return false
            if (sizeA != sizeB) return false
            if (mtimeA <= 0L || mtimeB <= 0L) return false
            return abs(mtimeA - mtimeB) <= 2_000L
        }

        fun perProtocolLimit(protocol: ProtocolType): Int = when (protocol) {
            ProtocolType.FTP, ProtocolType.FTPS -> 2
            ProtocolType.SFTP -> 3
            ProtocolType.WEBDAV, ProtocolType.HTTP, ProtocolType.HTTPS -> 4
            ProtocolType.SMB -> 3
        }
    }
}
