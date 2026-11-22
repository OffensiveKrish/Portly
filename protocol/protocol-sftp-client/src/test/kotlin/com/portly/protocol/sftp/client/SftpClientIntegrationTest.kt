package com.portly.protocol.sftp.client

import com.portly.core.common.ProtocolException
import com.portly.core.database.entity.KnownHostEntity
import com.portly.core.model.AuthType
import com.portly.core.model.ConnectionProfile
import com.portly.core.model.HostKeyMode
import com.portly.core.model.ProtocolError
import com.portly.core.model.ProtocolType
import com.portly.core.model.ResolvedCredentials
import kotlinx.coroutines.runBlocking
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.password.PasswordAuthenticator
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.sftp.server.SftpSubsystemFactory
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.StringWriter
import java.security.KeyPairGenerator

class SftpClientIntegrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var sftpRoot: File
    private lateinit var sshd: SshServer
    private lateinit var clientKeyPair: java.security.KeyPair
    private var port: Int = 0

    @Before
    fun setUp() {
        sftpRoot = tempFolder.newFolder("sftp_root")
        clientKeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

        sshd = SshServer.setUpDefaultServer().apply {
            host = "127.0.0.1"
            port = 0
            keyPairProvider = SimpleGeneratorHostKeyProvider(File(tempFolder.root, "hostkey.ser").toPath())
            subsystemFactories = listOf(SftpSubsystemFactory())
            fileSystemFactory = VirtualFileSystemFactory(sftpRoot.toPath())
            passwordAuthenticator = PasswordAuthenticator { user, pass, _ ->
                user == "alice" && pass == "sftp-secret"
            }
            publickeyAuthenticator = PublickeyAuthenticator { user, key, _ ->
                user == "keyuser" && key == clientKeyPair.public
            }
        }
        sshd.start()
        port = sshd.port
    }

    @After
    fun tearDown() {
        sshd.stop(true)
    }

    @Test
    fun `sftp client performs password round-trip, ranged resume, and known_hosts mismatch detection`() = runBlocking {
        val store = InMemoryHostKeyStore()
        val client = SshjSftpProtocolClient(store)
        val profile = ConnectionProfile(
            id = "s1",
            name = "Test SFTP",
            protocol = ProtocolType.SFTP,
            host = "127.0.0.1",
            port = port,
            username = "alice",
            authType = AuthType.PASSWORD,
        )
        val creds = ResolvedCredentials("alice", "sftp-secret")

        val testRes = client.testConnection(profile, creds)
        assertTrue(testRes.success)
        assertTrue(store.entries["127.0.0.1:$port"]?.trusted == true)

        assertTrue(client.connect(profile, creds).isSuccess)
        assertTrue(client.mkdir("/work").isSuccess)

        val payload = ByteArray(140_000) { (it % 173).toByte() }
        val outHandle = client.openOutputStream("/work/blob.bin").getOrThrow()
        outHandle.stream.write(payload)
        assertTrue(outHandle.complete().isSuccess)

        val entries = client.list("/work").getOrThrow()
        assertEquals(1, entries.size)
        assertEquals(payload.size.toLong(), entries[0].sizeBytes)

        val inHandle = client.openInputStream("/work/blob.bin", offset = 50_000L).getOrThrow()
        val tail = inHandle.stream.readBytes()
        assertTrue(inHandle.complete().isSuccess)
        assertTrue(payload.copyOfRange(50_000, payload.size).contentEquals(tail))

        assertTrue(client.rename("/work/blob.bin", "/work/blob2.bin").isSuccess)
        assertTrue(File(sftpRoot, "work/blob2.bin").exists())

        assertTrue(client.delete("/work", recursive = true).isSuccess)
        assertFalse(File(sftpRoot, "work").exists())
        client.disconnect()

        // Simulate host key rotation -> must fail with HostKeyMismatch
        val existing = store.entries["127.0.0.1:$port"]!!
        store.entries["127.0.0.1:$port"] = existing.copy(fingerprintSha256 = "SHA256:tampered-old-key")
        val mismatchRes = client.connect(profile, creds)
        assertTrue(mismatchRes.isFailure)
        val ex = mismatchRes.exceptionOrNull() as ProtocolException
        assertTrue(ex.protocolError is ProtocolError.HostKeyMismatch)
    }

    @Test
    fun `sftp client authenticates using private key PEM`() = runBlocking {
        val pemWriter = StringWriter()
        JcaPEMWriter(pemWriter).use { it.writeObject(clientKeyPair.private) }
        val pem = pemWriter.toString()

        val client = SshjSftpProtocolClient(InMemoryHostKeyStore())
        val profile = ConnectionProfile(
            id = "s2",
            name = "Key SFTP",
            protocol = ProtocolType.SFTP,
            host = "127.0.0.1",
            port = port,
            username = "keyuser",
            authType = AuthType.PRIVATE_KEY,
        )
        val result = client.connect(
            profile,
            ResolvedCredentials(username = "keyuser", privateKeyPem = pem),
        )
        assertTrue(result.isSuccess)
        client.disconnect()
    }
}

private class InMemoryHostKeyStore : HostKeyStore {
    val entries = mutableMapOf<String, KnownHostEntity>()
    override suspend fun get(host: String, port: Int): KnownHostEntity? = entries["$host:$port"]
    override suspend fun upsert(entity: KnownHostEntity) {
        entries["${entity.host}:${entity.port}"] = entity
    }
    override suspend fun mode(): HostKeyMode = HostKeyMode.STRICT
}
