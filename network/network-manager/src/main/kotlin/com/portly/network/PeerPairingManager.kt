package com.portly.network

import android.net.nsd.NsdManager
import android.os.Build
import com.portly.core.model.AuthType
import com.portly.core.model.ConnectionProfile
import com.portly.core.model.FtpsMode
import com.portly.core.model.PairingTokenPayload
import com.portly.core.model.ProtocolType
import com.portly.core.model.ResolvedCredentials
import com.portly.core.model.ServerType
import com.portly.core.model.TlsVersionPolicy
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class ActivePairingOffer(
    val payload: PairingTokenPayload,
    val pairingUri: String,
    val consumed: Boolean = false,
)

@Singleton
class PeerPairingManager private constructor(
    private val nsdDiscoveryManager: NsdDiscoveryManager?,
    private val nowProvider: () -> Long,
) {
    @Inject
    constructor(nsdDiscoveryManager: NsdDiscoveryManager) : this(
        nsdDiscoveryManager = nsdDiscoveryManager,
        nowProvider = { System.currentTimeMillis() },
    )

    internal constructor(nowProvider: () -> Long = { System.currentTimeMillis() }) : this(
        nsdDiscoveryManager = null,
        nowProvider = nowProvider,
    )

    private val random = SecureRandom()
    private val lock = Any()
    private var currentOffer: ActivePairingOffer? = null
    private var nsdRegistration: NsdManager.RegistrationListener? = null

    fun createPairingOffer(
        protocol: ProtocolType,
        host: String,
        port: Int,
        deviceName: String = defaultDeviceName(),
        sha256CertFingerprint: String? = null,
        advertiseNsd: Boolean = true,
        ttlMillis: Long = DEFAULT_TTL_MILLIS,
    ): ActivePairingOffer {
        val tokenBytes = ByteArray(24)
        random.nextBytes(tokenBytes)
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes)
        val payload = PairingTokenPayload(
            protocol = protocol,
            host = host,
            port = port,
            token = token,
            deviceName = deviceName.ifBlank { "Portly Peer" },
            expiresAtMillis = nowProvider() + ttlMillis,
            sha256CertFingerprint = sha256CertFingerprint,
        )
        val uri = encodePairingUri(payload)
        val offer = ActivePairingOffer(payload = payload, pairingUri = uri, consumed = false)
        synchronized(lock) {
            stopBroadcastLocked()
            currentOffer = offer
            val nsd = nsdDiscoveryManager
            if (advertiseNsd && nsd != null) {
                runCatching {
                    nsdRegistration = nsd.registerService(
                        serviceName = "Portly-${payload.deviceName.take(24)}",
                        serviceType = NsdDiscoveryManager.SERVICE_TYPE_PORTLY,
                        port = port,
                    )
                }
            }
        }
        return offer
    }

    fun getActiveOffer(): ActivePairingOffer? = synchronized(lock) {
        val offer = currentOffer ?: return null
        if (offer.consumed || nowProvider() > offer.payload.expiresAtMillis) {
            stopBroadcastLocked()
            currentOffer = null
            null
        } else {
            offer
        }
    }

    fun consumeIssuedToken(token: String): Result<PairingTokenPayload> = synchronized(lock) {
        val offer = currentOffer
            ?: return Result.failure(IllegalStateException("No active pairing offer."))
        if (offer.consumed) {
            return Result.failure(IllegalStateException("Pairing token has already been used."))
        }
        val now = nowProvider()
        if (now > offer.payload.expiresAtMillis) {
            stopBroadcastLocked()
            currentOffer = null
            return Result.failure(IllegalStateException("Pairing token expired (60s window elapsed)."))
        }
        if (offer.payload.token != token) {
            return Result.failure(SecurityException("Invalid pairing token."))
        }
        currentOffer = offer.copy(consumed = true)
        stopBroadcastLocked()
        Result.success(offer.payload)
    }

    fun cancelActiveOffer() = synchronized(lock) {
        stopBroadcastLocked()
        currentOffer = null
    }

    private fun stopBroadcastLocked() {
        val reg = nsdRegistration ?: return
        nsdRegistration = null
        nsdDiscoveryManager?.unregisterService(reg)
    }

    companion object {
        const val DEFAULT_TTL_MILLIS = 60_000L
        const val PEER_USERNAME = "portly-peer"
        private const val URI_PREFIX = "portly://pair?payload="
        private val json = Json { ignoreUnknownKeys = true }

        fun defaultDeviceName(): String =
            runCatching { Build.MODEL?.takeIf { it.isNotBlank() } }.getOrNull() ?: "Android Device"

        fun serverTypeForProtocol(protocol: ProtocolType): ServerType? = when (protocol) {
            ProtocolType.FTPS -> ServerType.FTPS
            ProtocolType.FTP -> ServerType.FTP
            else -> null
        }

        fun encodePairingUri(payload: PairingTokenPayload): String {
            val rawJson = json.encodeToString(payload)
            val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(rawJson.toByteArray(Charsets.UTF_8))
            return "$URI_PREFIX$encoded"
        }

        fun parseAndValidateIncoming(
            rawInput: String,
            nowMillis: Long = System.currentTimeMillis(),
        ): Result<PairingTokenPayload> = runCatching {
            val trimmed = rawInput.trim()
            val payload = when {
                trimmed.startsWith(URI_PREFIX) -> {
                    val b64 = trimmed.removePrefix(URI_PREFIX).substringBefore('&').trim()
                    val decoded = String(Base64.getUrlDecoder().decode(b64), Charsets.UTF_8)
                    json.decodeFromString<PairingTokenPayload>(decoded)
                }
                trimmed.startsWith("{") -> {
                    json.decodeFromString<PairingTokenPayload>(trimmed)
                }
                else -> error("Unrecognized pairing link or QR payload.")
            }
            require(payload.host.isNotBlank()) { "Pairing payload missing host address." }
            require(payload.port in 1..65535) { "Pairing payload has invalid port: ${payload.port}" }
            require(payload.token.length >= 12) { "Pairing payload token is malformed." }
            require(nowMillis <= payload.expiresAtMillis) { "Pairing QR code expired (60-second limit exceeded)." }
            payload
        }

        fun toPeerProfile(payload: PairingTokenPayload): Pair<ConnectionProfile, ResolvedCredentials> {
            val profile = ConnectionProfile(
                id = UUID.randomUUID().toString(),
                name = "${payload.deviceName} (Peer)",
                protocol = payload.protocol,
                host = payload.host,
                port = payload.port,
                username = PEER_USERNAME,
                authType = AuthType.PASSWORD,
                passiveMode = true,
                ftpsMode = FtpsMode.EXPLICIT,
                tlsVersionPolicy = TlsVersionPolicy.TLS_1_2_OR_1_3,
                useHttpsForWebDav = true,
                rootPath = "/",
                notes = "Paired via Portly Peer (${payload.protocol.displayName})",
            )
            val creds = ResolvedCredentials(
                username = PEER_USERNAME,
                password = payload.token,
            )
            return profile to creds
        }
    }
}
