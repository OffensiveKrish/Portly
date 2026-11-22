package com.portly.core.database

import com.portly.core.database.entity.ActivityLogEntity
import com.portly.core.database.entity.ConnectionProfileEntity
import com.portly.core.database.entity.KnownHostEntity
import com.portly.core.database.entity.TransferEntity
import com.portly.core.database.entity.TransferHistoryEntity
import com.portly.core.database.entity.TrustedCertEntity
import com.portly.core.model.ActivityLogEntry
import com.portly.core.model.ActivitySeverity
import com.portly.core.model.AuthType
import com.portly.core.model.ConflictPolicy
import com.portly.core.model.ConnectionProfile
import com.portly.core.model.FtpsMode
import com.portly.core.model.KnownHostEntry
import com.portly.core.model.ProtocolError
import com.portly.core.model.ProtocolType
import com.portly.core.model.TlsVersionPolicy
import com.portly.core.model.TransferDirection
import com.portly.core.model.TransferHistoryEntry
import com.portly.core.model.TransferId
import com.portly.core.model.TransferProgress
import com.portly.core.model.TransferSpec
import com.portly.core.model.TransferState
import com.portly.core.model.TransferStatus
import com.portly.core.model.TrustedCertEntry

fun ConnectionProfileEntity.toDomain(): ConnectionProfile = ConnectionProfile(
    id = id,
    name = name,
    protocol = ProtocolType.valueOf(protocol),
    host = host,
    port = port,
    username = username,
    authType = AuthType.valueOf(authType),
    authSecretRef = authSecretRef,
    passiveMode = passiveMode,
    ftpsMode = FtpsMode.valueOf(ftpsMode),
    tlsVersionPolicy = TlsVersionPolicy.valueOf(tlsVersionPolicy),
    useHttpsForWebDav = useHttpsForWebDav,
    smbLegacyMode = smbLegacyMode,
    smbDomain = smbDomain,
    smbShare = smbShare,
    rootPath = rootPath,
    connectTimeoutMs = connectTimeoutMs,
    notes = notes,
    lastConnectedAtMillis = lastConnectedAtMillis,
    isFavorite = isFavorite,
    createdAtMillis = createdAtMillis,
)

fun ConnectionProfile.toEntity(): ConnectionProfileEntity = ConnectionProfileEntity(
    id = id,
    name = name,
    protocol = protocol.name,
    host = host,
    port = port,
    username = username,
    authType = authType.name,
    authSecretRef = authSecretRef,
    passiveMode = passiveMode,
    ftpsMode = ftpsMode.name,
    tlsVersionPolicy = tlsVersionPolicy.name,
    useHttpsForWebDav = useHttpsForWebDav,
    smbLegacyMode = smbLegacyMode,
    smbDomain = smbDomain,
    smbShare = smbShare,
    rootPath = rootPath,
    connectTimeoutMs = connectTimeoutMs,
    notes = notes,
    lastConnectedAtMillis = lastConnectedAtMillis,
    isFavorite = isFavorite,
    createdAtMillis = createdAtMillis,
)

fun TransferEntity.toDomain(): TransferState {
    val spec = TransferSpec(
        id = TransferId(id),
        groupId = groupId,
        direction = TransferDirection.valueOf(direction),
        protocol = protocol?.let { ProtocolType.valueOf(it) },
        profileId = profileId,
        fileName = fileName,
        sourcePath = sourcePath,
        sourceUriString = sourceUriString,
        destinationPath = destinationPath,
        destinationUriString = destinationUriString,
        totalBytes = totalBytes,
        sourceModifiedMillis = sourceModifiedMillis,
        conflictPolicy = ConflictPolicy.valueOf(conflictPolicy),
        verifyChecksum = verifyChecksum,
        expectedChecksum = expectedChecksum,
        checksumAlgorithm = checksumAlgorithm,
        maxRetries = maxRetries,
    )
    val error = errorUserMessage?.let { msg ->
        ProtocolError.ProtocolFailure(
            userMessage = msg,
            nextStep = errorNextStep ?: "Retry the transfer.",
            technicalDetail = errorTechnicalDetail,
            protocolCode = errorCode,
        )
    }
    return TransferState(
        id = spec.id,
        spec = spec,
        status = TransferStatus.valueOf(status),
        progress = TransferProgress(
            bytesTransferred = bytesTransferred,
            totalBytes = totalBytes,
            speedBytesPerSec = speedBytesPerSec,
            etaSeconds = etaSeconds,
        ),
        attemptCount = attemptCount,
        error = error,
        awaitingConflictResolution = awaitingConflictResolution,
        checksumVerified = checksumVerified,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
        completedAtMillis = completedAtMillis,
    )
}

fun TransferState.toEntity(pausedByNetworkLoss: Boolean = false): TransferEntity = TransferEntity(
    id = id.value,
    groupId = spec.groupId,
    direction = spec.direction.name,
    protocol = spec.protocol?.name,
    profileId = spec.profileId,
    fileName = spec.fileName,
    sourcePath = spec.sourcePath,
    sourceUriString = spec.sourceUriString,
    destinationPath = spec.destinationPath,
    destinationUriString = spec.destinationUriString,
    totalBytes = spec.totalBytes,
    bytesTransferred = progress.bytesTransferred,
    speedBytesPerSec = progress.speedBytesPerSec,
    etaSeconds = progress.etaSeconds,
    sourceModifiedMillis = spec.sourceModifiedMillis,
    conflictPolicy = spec.conflictPolicy.name,
    verifyChecksum = spec.verifyChecksum,
    expectedChecksum = spec.expectedChecksum,
    checksumAlgorithm = spec.checksumAlgorithm,
    checksumVerified = checksumVerified,
    maxRetries = spec.maxRetries,
    attemptCount = attemptCount,
    status = status.name,
    awaitingConflictResolution = awaitingConflictResolution,
    errorUserMessage = error?.userMessage,
    errorNextStep = error?.nextStep,
    errorTechnicalDetail = error?.technicalDetail,
    errorCode = error?.protocolCode,
    pausedByNetworkLoss = pausedByNetworkLoss,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
    completedAtMillis = completedAtMillis,
)

fun TransferHistoryEntity.toDomain(): TransferHistoryEntry = TransferHistoryEntry(
    id = id,
    direction = TransferDirection.valueOf(direction),
    protocol = protocol?.let { ProtocolType.valueOf(it) },
    profileName = profileName,
    fileName = fileName,
    sourcePath = sourcePath,
    destinationPath = destinationPath,
    totalBytes = totalBytes,
    bytesTransferred = bytesTransferred,
    status = TransferStatus.valueOf(status),
    averageSpeedBytesPerSec = averageSpeedBytesPerSec,
    checksumVerified = checksumVerified,
    errorSummary = errorSummary,
    startedAtMillis = startedAtMillis,
    finishedAtMillis = finishedAtMillis,
)

fun TransferHistoryEntry.toEntity(): TransferHistoryEntity = TransferHistoryEntity(
    id = id,
    direction = direction.name,
    protocol = protocol?.name,
    profileName = profileName,
    fileName = fileName,
    sourcePath = sourcePath,
    destinationPath = destinationPath,
    totalBytes = totalBytes,
    bytesTransferred = bytesTransferred,
    status = status.name,
    averageSpeedBytesPerSec = averageSpeedBytesPerSec,
    checksumVerified = checksumVerified,
    errorSummary = errorSummary,
    startedAtMillis = startedAtMillis,
    finishedAtMillis = finishedAtMillis,
)

fun ActivityLogEntity.toDomain(): ActivityLogEntry = ActivityLogEntry(
    id = id,
    timestampMillis = timestampMillis,
    category = category,
    severity = ActivitySeverity.valueOf(severity),
    message = message,
    detail = detail,
)

fun KnownHostEntity.toDomain(): KnownHostEntry = KnownHostEntry(
    host = host,
    port = port,
    algorithm = algorithm,
    fingerprintSha256 = fingerprintSha256,
    firstSeenAtMillis = firstSeenAtMillis,
    lastVerifiedAtMillis = lastVerifiedAtMillis,
    trusted = trusted,
)

fun KnownHostEntry.toEntity(): KnownHostEntity = KnownHostEntity(
    host = host,
    port = port,
    algorithm = algorithm,
    fingerprintSha256 = fingerprintSha256,
    firstSeenAtMillis = firstSeenAtMillis,
    lastVerifiedAtMillis = lastVerifiedAtMillis,
    trusted = trusted,
)

fun TrustedCertEntity.toDomain(): TrustedCertEntry = TrustedCertEntry(
    host = host,
    port = port,
    sha256Fingerprint = sha256Fingerprint,
    subject = subject,
    issuer = issuer,
    validFromMillis = validFromMillis,
    validUntilMillis = validUntilMillis,
    isUserCa = isUserCa,
    trustedAtMillis = trustedAtMillis,
)

fun TrustedCertEntry.toEntity(): TrustedCertEntity = TrustedCertEntity(
    host = host,
    port = port,
    sha256Fingerprint = sha256Fingerprint,
    subject = subject,
    issuer = issuer,
    validFromMillis = validFromMillis,
    validUntilMillis = validUntilMillis,
    isUserCa = isUserCa,
    trustedAtMillis = trustedAtMillis,
)
