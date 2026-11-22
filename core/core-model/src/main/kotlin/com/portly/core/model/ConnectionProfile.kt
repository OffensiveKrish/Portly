package com.portly.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class AuthType {
    ANONYMOUS,
    PASSWORD,
    PRIVATE_KEY,
}

@Serializable
enum class FtpsMode {
    EXPLICIT,
    IMPLICIT,
}

@Serializable
enum class TlsVersionPolicy {
    TLS_1_2_OR_1_3,
    TLS_1_3_ONLY,
}

@Serializable
enum class HostKeyMode {
    STRICT,
    TOFU_WARN,
}

@Serializable
data class ConnectionProfile(
    val id: String,
    val name: String,
    val protocol: ProtocolType,
    val host: String,
    val port: Int = protocol.defaultPort,
    val username: String = "",
    val authType: AuthType = AuthType.PASSWORD,
    /** Reference key in encrypted storage; never serialized in plaintext exports. */
    val authSecretRef: String? = null,
    val passiveMode: Boolean = true,
    val ftpsMode: FtpsMode = FtpsMode.EXPLICIT,
    val tlsVersionPolicy: TlsVersionPolicy = TlsVersionPolicy.TLS_1_2_OR_1_3,
    val useHttpsForWebDav: Boolean = true,
    val smbLegacyMode: Boolean = false,
    val smbDomain: String = "",
    val smbShare: String = "",
    val rootPath: String = "/",
    val connectTimeoutMs: Int = 15_000,
    val notes: String = "",
    val lastConnectedAtMillis: Long? = null,
    val isFavorite: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis(),
)

data class ResolvedCredentials(
    val username: String,
    val password: String? = null,
    val privateKeyPem: String? = null,
    val passphrase: String? = null,
)
