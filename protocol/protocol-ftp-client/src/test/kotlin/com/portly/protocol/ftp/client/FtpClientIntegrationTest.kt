package com.portly.protocol.ftp.client

import com.portly.core.common.ProtocolException
import com.portly.core.model.AuthType
import com.portly.core.model.ConnectionProfile
import com.portly.core.model.ProtocolError
import com.portly.core.model.ProtocolType
import com.portly.core.model.ResolvedCredentials
import kotlinx.coroutines.runBlocking
import org.apache.ftpserver.FtpServer
import org.apache.ftpserver.FtpServerFactory
import org.apache.ftpserver.ftplet.Authority
import org.apache.ftpserver.listener.ListenerFactory
import org.apache.ftpserver.usermanager.impl.BaseUser
import org.apache.ftpserver.usermanager.impl.WritePermission
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FtpClientIntegrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var serverRoot: File
    private lateinit var server: FtpServer
    private var port: Int = 0

    @Before
    fun setUp() {
        serverRoot = tempFolder.newFolder("ftp_root")
        val factory = FtpServerFactory()
        val listenerFactory = ListenerFactory().apply { port = 0 }
        factory.addListener("default", listenerFactory.createListener())

        val user = BaseUser().apply {
            name = "alice"
            password = "secret"
            homeDirectory = serverRoot.absolutePath
            authorities = listOf<Authority>(WritePermission())
        }
        factory.userManager.save(user)

        server = factory.createServer()
        server.start()
        port = factory.listeners["default"]!!.port
    }

    @After
    fun tearDown() {
        server.stop()
    }

    @Test
    fun `ftp client performs real round-trip mkdir upload list resume-download rename delete`() = runBlocking {
        val client = CommonsFtpProtocolClient()
        val profile = ConnectionProfile(
            id = "p1",
            name = "Embedded FTP",
            protocol = ProtocolType.FTP,
            host = "127.0.0.1",
            port = port,
            username = "alice",
            authType = AuthType.PASSWORD,
            passiveMode = true,
        )
        val creds = ResolvedCredentials("alice", "secret")

        val testResult = client.testConnection(profile, creds)
        assertTrue(testResult.success)

        assertTrue(client.connect(profile, creds).isSuccess)
        assertTrue(client.mkdir("/docs").isSuccess)

        val payload = ByteArray(150_000) { (it % 127).toByte() }
        val outHandle = client.openOutputStream("/docs/sample.bin").getOrThrow()
        outHandle.stream.write(payload)
        assertTrue(outHandle.complete().isSuccess)

        val entries = client.list("/docs").getOrThrow()
        assertEquals(1, entries.size)
        assertEquals("sample.bin", entries[0].name)
        assertEquals(payload.size.toLong(), entries[0].sizeBytes)

        // Resume download from offset 50_000 via FTP REST
        val inHandle = client.openInputStream("/docs/sample.bin", offset = 50_000L).getOrThrow()
        val tail = inHandle.stream.readBytes()
        assertTrue(inHandle.complete().isSuccess)
        assertTrue(payload.copyOfRange(50_000, payload.size).contentEquals(tail))

        assertTrue(client.rename("/docs/sample.bin", "/docs/renamed.bin").isSuccess)
        assertTrue(File(serverRoot, "docs/renamed.bin").exists())

        assertTrue(client.delete("/docs", recursive = true).isSuccess)
        assertFalse(File(serverRoot, "docs").exists())

        client.disconnect()
    }

    @Test
    fun `wrong password maps to AuthenticationFailed`() = runBlocking {
        val client = CommonsFtpProtocolClient()
        val profile = ConnectionProfile(
            id = "p2",
            name = "Bad Auth",
            protocol = ProtocolType.FTP,
            host = "127.0.0.1",
            port = port,
            username = "alice",
        )
        val result = client.connect(profile, ResolvedCredentials("alice", "wrong"))
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull() as ProtocolException
        assertTrue(ex.protocolError is ProtocolError.AuthenticationFailed)
    }
}
