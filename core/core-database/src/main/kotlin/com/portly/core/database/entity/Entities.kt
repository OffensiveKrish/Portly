package com.portly.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "connection_profiles")
data class ConnectionProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val protocol: String,
    val host: String,
    val port: Int,
    val username: String,
    val authType: String,
    val authSecretRef: String?,
    val passiveMode: Boolean,
    val ftpsMode: String,
    val tlsVersionPolicy: String,
    val useHttpsForWebDav: Boolean,
    val smbLegacyMode: Boolean,
    val smbDomain: String,
    val smbShare: String,
    val rootPath: String,
    val connectTimeoutMs: Int,
    val notes: String,
    val lastConnectedAtMillis: Long?,
    val isFavorite: Boolean,
    val createdAtMillis: Long,
)

@Entity(tableName = "encrypted_secrets")
data class EncryptedSecretEntity(
    @PrimaryKey val secretRef: String,
    val encryptedBlob: String,
    val updatedAtMillis: Long,
)

@Entity(tableName = "transfers")
data class TransferEntity(
    @PrimaryKey val id: String,
    val groupId: String?,
    val direction: String,
    val protocol: String?,
    val profileId: String?,
    val fileName: String,
    val sourcePath: String,
    val sourceUriString: String?,
    val destinationPath: String,
    val destinationUriString: String?,
    val totalBytes: Long,
    val bytesTransferred: Long,
    val speedBytesPerSec: Long,
    val etaSeconds: Long?,
    val sourceModifiedMillis: Long,
    val conflictPolicy: String,
    val verifyChecksum: Boolean,
    val expectedChecksum: String?,
    val checksumAlgorithm: String,
    val checksumVerified: Boolean?,
    val maxRetries: Int,
    val attemptCount: Int,
    val status: String,
    val awaitingConflictResolution: Boolean,
    val errorUserMessage: String?,
    val errorNextStep: String?,
    val errorTechnicalDetail: String?,
    val errorCode: Int?,
    val pausedByNetworkLoss: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val completedAtMillis: Long?,
)

@Entity(tableName = "transfer_history")
data class TransferHistoryEntity(
    @PrimaryKey val id: String,
    val direction: String,
    val protocol: String?,
    val profileName: String?,
    val fileName: String,
    val sourcePath: String,
    val destinationPath: String,
    val totalBytes: Long,
    val bytesTransferred: Long,
    val status: String,
    val averageSpeedBytesPerSec: Long,
    val checksumVerified: Boolean?,
    val errorSummary: String?,
    val startedAtMillis: Long,
    val finishedAtMillis: Long,
)

@Entity(tableName = "activity_logs")
data class ActivityLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val timestampMillis: Long,
    val category: String,
    val severity: String,
    val message: String,
    val detail: String?,
)

@Entity(tableName = "known_hosts", primaryKeys = ["host", "port"])
data class KnownHostEntity(
    val host: String,
    val port: Int,
    val algorithm: String,
    val fingerprintSha256: String,
    val firstSeenAtMillis: Long,
    val lastVerifiedAtMillis: Long,
    val trusted: Boolean,
)

@Entity(tableName = "trusted_certs", primaryKeys = ["host", "port", "sha256Fingerprint"])
data class TrustedCertEntity(
    val host: String,
    val port: Int,
    val sha256Fingerprint: String,
    val subject: String,
    val issuer: String,
    val validFromMillis: Long,
    val validUntilMillis: Long,
    val isUserCa: Boolean,
    val trustedAtMillis: Long,
)

@Entity(tableName = "favorite_paths")
data class FavoritePathEntity(
    @PrimaryKey val path: String,
    val label: String,
    val isRemote: Boolean,
    val profileId: String?,
    val addedAtMillis: Long,
)
