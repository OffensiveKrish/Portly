package com.portly.core.model

@JvmInline
value class TransferId(val value: String)

enum class TransferDirection {
    UPLOAD,
    DOWNLOAD,
    LOCAL_COPY,
    LOCAL_MOVE,
    PEER_SEND,
    PEER_RECEIVE,
}

enum class TransferStatus {
    QUEUED,
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED,
}

enum class ConflictPolicy {
    ASK,
    RENAME,
    SKIP,
    OVERWRITE_ALWAYS,
}

data class TransferSpec(
    val id: TransferId,
    val groupId: String? = null,
    val direction: TransferDirection,
    val protocol: ProtocolType?,
    val profileId: String? = null,
    val fileName: String,
    val sourcePath: String,
    val sourceUriString: String? = null,
    val destinationPath: String,
    val destinationUriString: String? = null,
    val totalBytes: Long,
    val sourceModifiedMillis: Long = 0L,
    val conflictPolicy: ConflictPolicy = ConflictPolicy.ASK,
    val verifyChecksum: Boolean = true,
    val expectedChecksum: String? = null,
    val checksumAlgorithm: String = "SHA-256",
    val maxRetries: Int = 3,
)

data class TransferProgress(
    val bytesTransferred: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val etaSeconds: Long? = null,
) {
    val percent: Float
        get() = when {
            totalBytes <= 0L -> 0f
            bytesTransferred >= totalBytes -> 100f
            else -> (bytesTransferred.toDouble() / totalBytes.toDouble() * 100.0).toFloat().coerceIn(0f, 100f)
        }
}

data class TransferState(
    val id: TransferId,
    val spec: TransferSpec,
    val status: TransferStatus,
    val progress: TransferProgress = TransferProgress(totalBytes = spec.totalBytes),
    val attemptCount: Int = 0,
    val error: ProtocolError? = null,
    val awaitingConflictResolution: Boolean = false,
    val checksumVerified: Boolean? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val completedAtMillis: Long? = null,
)

data class TransferHistoryEntry(
    val id: String,
    val direction: TransferDirection,
    val protocol: ProtocolType?,
    val profileName: String?,
    val fileName: String,
    val sourcePath: String,
    val destinationPath: String,
    val totalBytes: Long,
    val bytesTransferred: Long,
    val status: TransferStatus,
    val averageSpeedBytesPerSec: Long,
    val checksumVerified: Boolean?,
    val errorSummary: String?,
    val startedAtMillis: Long,
    val finishedAtMillis: Long,
)

enum class ActivitySeverity {
    INFO,
    WARN,
    ERROR,
}

data class ActivityLogEntry(
    val id: Long = 0L,
    val timestampMillis: Long = System.currentTimeMillis(),
    val category: String,
    val severity: ActivitySeverity,
    val message: String,
    val detail: String? = null,
)
