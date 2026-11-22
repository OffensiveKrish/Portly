package com.portly.core.model

import kotlinx.serialization.Serializable

enum class ServerStatus {
    STOPPED,
    STARTING,
    RUNNING,
    ERROR,
}

enum class ServerPermission {
    READ,
    WRITE,
    DELETE,
    MKDIR,
}

@Serializable
data class ServerUserAccount(
    val username: String,
    val secretRef: String? = null,
    val isAnonymous: Boolean = false,
    val permissions: Set<ServerPermission> = setOf(
        ServerPermission.READ,
        ServerPermission.WRITE,
        ServerPermission.DELETE,
        ServerPermission.MKDIR,
    ),
)

@Serializable
data class ServerConfig(
    val serverType: ServerType,
    val port: Int = serverType.defaultPort,
    val rootDirectoryPath: String,
    val rootTreeUriString: String? = null,
    val readOnly: Boolean = false,
    val maxConnections: Int = 10,
    val maxConnectionsPerIp: Int = 4,
    val lanOnlyBind: Boolean = true,
    val allowPublicBindOptIn: Boolean = false,
    val passivePortRange: String = "50000-50100",
    val requireAuth: Boolean = true,
    val users: List<ServerUserAccount> = emptyList(),
    val tlsVersionPolicy: TlsVersionPolicy = TlsVersionPolicy.TLS_1_2_OR_1_3,
    val keystorePath: String? = null,
)

data class ServerRuntimeState(
    val serverType: ServerType,
    val status: ServerStatus = ServerStatus.STOPPED,
    val port: Int = serverType.defaultPort,
    val activeUrls: List<String> = emptyList(),
    val activeClientCount: Int = 0,
    val totalBytesServed: Long = 0L,
    val startedAtMillis: Long? = null,
    val userStoppedExplicitly: Boolean = false,
    val lastError: ProtocolError? = null,
)

data class ConnectedClientInfo(
    val clientId: String,
    val serverType: ServerType,
    val remoteAddress: String,
    val username: String,
    val currentAction: String,
    val currentFile: String? = null,
    val bytesTransferred: Long = 0L,
    val connectedAtMillis: Long = System.currentTimeMillis(),
)

enum class LogLevel {
    INFO,
    WARN,
    ERROR,
}

data class ServerLogEntry(
    val id: String,
    val serverType: ServerType,
    val timestampMillis: Long,
    val level: LogLevel,
    val message: String,
)

data class KnownHostEntry(
    val host: String,
    val port: Int,
    val algorithm: String,
    val fingerprintSha256: String,
    val firstSeenAtMillis: Long,
    val lastVerifiedAtMillis: Long,
    val trusted: Boolean,
)

data class TrustedCertEntry(
    val host: String,
    val port: Int,
    val sha256Fingerprint: String,
    val subject: String,
    val issuer: String,
    val validFromMillis: Long,
    val validUntilMillis: Long,
    val isUserCa: Boolean = false,
    val trustedAtMillis: Long = System.currentTimeMillis(),
)

@Serializable
data class PairingTokenPayload(
    val protocol: ProtocolType,
    val host: String,
    val port: Int,
    val token: String,
    val deviceName: String,
    val expiresAtMillis: Long,
    val sha256CertFingerprint: String? = null,
)
