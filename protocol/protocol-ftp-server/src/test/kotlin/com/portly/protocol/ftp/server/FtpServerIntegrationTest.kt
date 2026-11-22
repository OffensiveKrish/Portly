package com.portly.protocol.ftp.server

import com.portly.core.model.ServerConfig
import com.portly.core.model.ServerPermission
import com.portly.core.model.ServerStatus
import com.portly.core.model.ServerType
import com.portly.core.model.ServerUserAccount
import kotlinx.coroutines.runBlocking
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File

class FtpServerIntegrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private var server: ApacheFtpProtocolServer? = null

    @After
    fun tearDown() {
        runBlocking {
            server?.stop()
        }
    }

    @Test
    fun `ftp server enforces root sandbox traversal block, permissions, and redacts logs`() = runBlocking {
        val root = tempFolder.newFolder("ftp_share")
        val outside = tempFolder.newFile("secret_outside.txt").apply { writeText("TOP_SECRET") }
        File(root, "hello.txt").writeText("welcome")

        val srv = ApacheFtpProtocolServer(
            context = null,
            credentialVault = null,
            passwordResolver = {
                mapOf("reader" to "pass1", "writer" to "pass2")
            },
        )
        server = srv

        val config = ServerConfig(
            serverType = ServerType.FTP,
            port = 0,
            rootDirectoryPath = root.absolutePath,
            readOnly = false,
            requireAuth = true,
            users = listOf(
                ServerUserAccount("reader", permissions = setOf(ServerPermission.READ)),
                ServerUserAccount(
                    "writer",
                    permissions = setOf(ServerPermission.READ, ServerPermission.WRITE, ServerPermission.MKDIR, ServerPermission.DELETE),
                ),
            ),
        )
        assertTrue(srv.start(config).isSuccess)
        assertEquals(ServerStatus.RUNNING, srv.state.value.status)
        val port = srv.state.value.port

        val client = FTPClient()
        client.connect("127.0.0.1", port)
        assertTrue(client.login("reader", "pass1"))
        client.enterLocalPassiveMode()
        client.setFileType(FTP.BINARY_FILE_TYPE)

        // Reader can list & read inside root
        val files = client.listFiles("/")
        assertTrue(files.any { it.name == "hello.txt" })

        // Reader cannot upload
        val uploadDenied = client.storeFile("/blocked.txt", ByteArrayInputStream("nope".toByteArray()))
        assertFalse(uploadDenied)

        // Path traversal attempt outside root must be rejected (550 / clamped to virtual root)
        val traversalResult = client.retrieveFileStream("/../../${outside.name}")
        assertTrue(traversalResult == null)
        client.changeWorkingDirectory("/../..")
        assertEquals("/", client.printWorkingDirectory())
        client.logout()
        client.disconnect()

        // Writer can upload & mkdir
        client.connect("127.0.0.1", port)
        assertTrue(client.login("writer", "pass2"))
        client.enterLocalPassiveMode()
        assertTrue(client.makeDirectory("/sub"))
        assertTrue(client.storeFile("/sub/data.txt", ByteArrayInputStream("uploaded".toByteArray())))
        assertEquals("uploaded", File(root, "sub/data.txt").readText())
        client.logout()
        client.disconnect()

        // Logs must never contain plaintext passwords
        val allLogText = srv.logs.value.joinToString("\n") { it.message }
        assertFalse(allLogText.contains("pass1"))
        assertFalse(allLogText.contains("pass2"))
    }
}
