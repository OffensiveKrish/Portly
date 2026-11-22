package com.portly.feature.connections

import com.portly.core.model.AuthType
import com.portly.core.model.ConnectionProfile
import com.portly.core.model.FtpsMode
import com.portly.core.model.ProtocolType
import com.portly.core.model.ResolvedCredentials
import com.portly.core.model.TlsVersionPolicy
import com.portly.core.security.Pbkdf2Crypto
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URI
import java.util.UUID

@Serializable
data class ExportedProfileItem(
    val id: String,
    val name: String,
    val protocol: ProtocolType,
    val host: String,
    val port: Int,
    val username: String,
    val authType: AuthType,
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
    val isFavorite: Boolean = false,
)

@Serializable
data class ExportedSecretEntry(
    val profileId: String,
    val username: String,
    val password: String? = null,
    val privateKeyPem: String? = null,
    val passphrase: String? = null,
)

@Serializable
data class ProfileExportBundle(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val exportedAtMillis: Long = System.currentTimeMillis(),
    val profiles: List<ExportedProfileItem>,
    val encryptedSecretsBlob: String? = null,
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

data class ParsedQuickConnect(
    val protocol: ProtocolType,
    val host: String,
    val port: Int,
    val username: String,
    val rootPath: String,
)

object ProfilePortabilityCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun exportProfiles(
        profiles: List<ConnectionProfile>,
        credentialsByProfileId: Map<String, ResolvedCredentials> = emptyMap(),
        includeEncryptedCredentials: Boolean = false,
        passphrase: CharArray? = null,
    ): String {
        val items = profiles.map { p ->
            ExportedProfileItem(
                id = p.id,
                name = p.name,
                protocol = p.protocol,
                host = p.host,
                port = p.port,
                username = p.username,
                authType = p.authType,
                passiveMode = p.passiveMode,
                ftpsMode = p.ftpsMode,
                tlsVersionPolicy = p.tlsVersionPolicy,
                useHttpsForWebDav = p.useHttpsForWebDav,
                smbLegacyMode = p.smbLegacyMode,
                smbDomain = p.smbDomain,
                smbShare = p.smbShare,
                rootPath = p.rootPath,
                connectTimeoutMs = p.connectTimeoutMs,
                notes = p.notes,
                isFavorite = p.isFavorite,
            )
        }

        val encryptedBlob = if (includeEncryptedCredentials) {
            val pass = passphrase?.takeIf { it.isNotEmpty() }
                ?: throw IllegalArgumentException("Passphrase required to export encrypted credentials")
            val secretEntries = profiles.mapNotNull { profile ->
                val creds = credentialsByProfileId[profile.id] ?: return@mapNotNull null
                ExportedSecretEntry(
                    profileId = profile.id,
                    username = creds.username,
                    password = creds.password,
                    privateKeyPem = creds.privateKeyPem,
                    passphrase = creds.passphrase,
                )
            }
            val rawSecretsJson = json.encodeToString(secretEntries)
            Pbkdf2Crypto.encryptWithPassphrase(rawSecretsJson, pass)
        } else {
            null
        }

        return json.encodeToString(
            ProfileExportBundle(
                schemaVersion = ProfileExportBundle.CURRENT_SCHEMA_VERSION,
                profiles = items,
                encryptedSecretsBlob = encryptedBlob,
            ),
        )
    }

    fun bundleHasEncryptedSecrets(rawJson: String): Boolean {
        val bundle = json.decodeFromString<ProfileExportBundle>(rawJson)
        return !bundle.encryptedSecretsBlob.isNullOrBlank()
    }

    fun importProfiles(
        rawJson: String,
        passphrase: CharArray? = null,
    ): List<Pair<ConnectionProfile, ResolvedCredentials?>> {
        val bundle = json.decodeFromString<ProfileExportBundle>(rawJson)
        require(bundle.schemaVersion >= 1) { "Unsupported profile export schema version ${bundle.schemaVersion}" }

        val decryptedSecrets: Map<String, ResolvedCredentials> =
            if (!bundle.encryptedSecretsBlob.isNullOrBlank() && passphrase != null && passphrase.isNotEmpty()) {
                val decryptedJson = Pbkdf2Crypto.decryptWithPassphrase(bundle.encryptedSecretsBlob, passphrase)
                json.decodeFromString<List<ExportedSecretEntry>>(decryptedJson)
                    .associate { entry ->
                        entry.profileId to ResolvedCredentials(
                            username = entry.username,
                            password = entry.password,
                            privateKeyPem = entry.privateKeyPem,
                            passphrase = entry.passphrase,
                        )
                    }
            } else {
                emptyMap()
            }

        return bundle.profiles.map { item ->
            val newId = item.id.ifBlank { UUID.randomUUID().toString() }
            val profile = ConnectionProfile(
                id = newId,
                name = item.name,
                protocol = item.protocol,
                host = item.host,
                port = item.port,
                username = item.username,
                authType = item.authType,
                passiveMode = item.passiveMode,
                ftpsMode = item.ftpsMode,
                tlsVersionPolicy = item.tlsVersionPolicy,
                useHttpsForWebDav = item.useHttpsForWebDav,
                smbLegacyMode = item.smbLegacyMode,
                smbDomain = item.smbDomain,
                smbShare = item.smbShare,
                rootPath = item.rootPath,
                connectTimeoutMs = item.connectTimeoutMs,
                notes = item.notes,
                isFavorite = item.isFavorite,
            )
            profile to decryptedSecrets[item.id]
        }
    }

    fun parseQuickConnectInput(rawInput: String, fallbackProtocol: ProtocolType = ProtocolType.SFTP): ParsedQuickConnect? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return null
        val normalized = if (trimmed.contains("://")) trimmed else "${fallbackProtocol.scheme}://$trimmed"
        val uri = runCatching { URI(normalized) }.getOrNull() ?: return null
        val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
        val protocol = when (uri.scheme?.lowercase()) {
            "ftp" -> ProtocolType.FTP
            "ftps" -> ProtocolType.FTPS
            "sftp", "ssh" -> ProtocolType.SFTP
            "webdav", "dav", "davs", "http", "https" -> ProtocolType.WEBDAV
            "smb" -> ProtocolType.SMB
            else -> fallbackProtocol
        }
        val port = if (uri.port in 1..65535) uri.port else protocol.defaultPort
        val user = uri.userInfo?.substringBefore(':').orEmpty()
        val path = uri.path?.takeIf { it.isNotBlank() } ?: "/"
        return ParsedQuickConnect(
            protocol = protocol,
            host = host,
            port = port,
            username = user,
            rootPath = path,
        )
    }
}
