package com.portly.protocol.sftp.client

import com.portly.core.database.dao.KnownHostDao
import com.portly.core.database.entity.KnownHostEntity
import com.portly.core.datastore.PortlySettingsDataStore
import com.portly.core.model.HostKeyMode
import com.portly.core.model.ProtocolError
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import java.security.MessageDigest
import java.security.PublicKey
import java.util.Base64

internal class HostKeyMismatchException(
    val host: String,
    val port: Int,
    val algorithm: String,
    val expectedFingerprint: String,
    val actualFingerprint: String,
) : RuntimeException("Host key mismatch for $host:$port") {
    fun toProtocolError(): ProtocolError.HostKeyMismatch = ProtocolError.HostKeyMismatch(
        host = host,
        port = port,
        expectedFingerprint = expectedFingerprint,
        actualFingerprint = actualFingerprint,
    )
}

internal interface HostKeyStore {
    suspend fun get(host: String, port: Int): KnownHostEntity?
    suspend fun upsert(entity: KnownHostEntity)
    suspend fun mode(): HostKeyMode
}

internal class RoomHostKeyStore(
    private val knownHostDao: KnownHostDao,
    private val settingsDataStore: PortlySettingsDataStore,
) : HostKeyStore {
    override suspend fun get(host: String, port: Int): KnownHostEntity? = knownHostDao.get(host, port)
    override suspend fun upsert(entity: KnownHostEntity) = knownHostDao.upsert(entity)
    override suspend fun mode(): HostKeyMode = settingsDataStore.settings.first().strictHostKeyMode
}

internal class PortlySshHostKeyVerifier(
    private val store: HostKeyStore,
    private val configuredPort: Int,
) : HostKeyVerifier {

    override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
        val targetPort = if (port > 0) port else configuredPort
        val algorithm = KeyType.fromKey(key).toString()
        val fingerprint = sha256Fingerprint(key)
        val now = System.currentTimeMillis()

        return runBlocking {
            val existing = store.get(hostname, targetPort)
            if (existing == null) {
                // Pin first-seen host key (TOFU).
                store.upsert(
                    KnownHostEntity(
                        host = hostname,
                        port = targetPort,
                        algorithm = algorithm,
                        fingerprintSha256 = fingerprint,
                        firstSeenAtMillis = now,
                        lastVerifiedAtMillis = now,
                        trusted = true,
                    ),
                )
                true
            } else if (existing.fingerprintSha256 == fingerprint) {
                if (existing.trusted) {
                    store.upsert(existing.copy(lastVerifiedAtMillis = now))
                    true
                } else {
                    false
                }
            } else {
                // Key changed: always reject handshake and surface HostKeyMismatch with both fingerprints.
                // Strict mode hard-fails; TOFU_WARN lets UI offer explicit user re-pinning.
                val mode = store.mode()
                throw HostKeyMismatchException(
                    host = hostname,
                    port = targetPort,
                    algorithm = algorithm,
                    expectedFingerprint = "${existing.fingerprintSha256} (mode=$mode)",
                    actualFingerprint = fingerprint,
                )
            }
        }
    }

    override fun findExistingAlgorithms(hostname: String, port: Int): List<String> {
        val targetPort = if (port > 0) port else configuredPort
        return runBlocking {
            store.get(hostname, targetPort)?.algorithm?.let { listOf(it) } ?: emptyList()
        }
    }

    companion object {
        fun sha256Fingerprint(key: PublicKey): String {
            val buf = Buffer.PlainBuffer().putPublicKey(key)
            val digest = MessageDigest.getInstance("SHA-256").digest(buf.compactData)
            return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(digest)
        }
    }
}
