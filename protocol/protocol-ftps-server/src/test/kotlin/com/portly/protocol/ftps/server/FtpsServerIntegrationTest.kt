package com.portly.protocol.ftps.server

import android.content.Context
import com.portly.core.model.ServerConfig
import com.portly.core.model.ServerPermission
import com.portly.core.model.ServerStatus
import com.portly.core.model.ServerType
import com.portly.core.model.ServerUserAccount
import com.portly.core.security.CertificateManager
import kotlinx.coroutines.runBlocking
import org.apache.commons.net.ftp.FTPSClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.lang.reflect.Proxy
import java.security.KeyStore

class FtpsServerIntegrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private var server: ApacheFtpsProtocolServer? = null

    @After
    fun tearDown() {
        runBlocking { server?.stop() }
    }

    @Test
    fun `ftps server serves TLS control and data channels cleanly`() = runBlocking {
        val root = tempFolder.newFolder("ftps_share")
        val certPair = CertificateManager.createSelfSignedCertificate("Portly FTPS Server", listOf("127.0.0.1"))
        val ksFile = File(tempFolder.root, "srv.p12")
        val ks = KeyStore.getInstance("PKCS12").apply {
            load(null, "changeit".toCharArray())
            setKeyEntry("ftps", certPair.privateKey, "changeit".toCharArray(), arrayOf(certPair.certificate))
        }
        ksFile.outputStream().use { ks.store(it, "changeit".toCharArray()) }

        val certMgr = CertificateManager(tempFolder.newFolder("certs"))
        val srv = ApacheFtpsProtocolServer(
            context = null,
            credentialVault = null,
            certificateManager = certMgr,
            passwordResolver = { mapOf("alice" to "tls-pass") },
            keystoreFileOverride = ksFile,
        )
        server = srv

        val cfg = ServerConfig(
            serverType = ServerType.FTPS,
            port = 0,
            rootDirectoryPath = root.absolutePath,
            requireAuth = true,
            users = listOf(
                ServerUserAccount(
                    "alice",
                    permissions = setOf(ServerPermission.READ, ServerPermission.WRITE, ServerPermission.MKDIR),
                ),
            ),
        )
        assertTrue(srv.start(cfg).isSuccess)
        assertEquals(ServerStatus.RUNNING, srv.state.value.status)

        val fp = CertificateManager.sha256Fingerprint(certPair.certificate)
        val tm = certMgr.buildTrustManager("127.0.0.1", srv.state.value.port, setOf(fp), emptyList())
        val client = FTPSClient(false, certMgr.buildSslContext(tm)).apply {
            trustManager = tm
        }
        client.connect("127.0.0.1", srv.state.value.port)
        assertTrue(client.login("alice", "tls-pass"))
        client.execPBSZ(0)
        client.execPROT("P")
        client.enterLocalPassiveMode()
        assertTrue(client.storeFile("/tls_test.txt", ByteArrayInputStream("tls-ok".toByteArray())))
        assertEquals("tls-ok", File(root, "tls_test.txt").readText())
        client.logout()
        client.disconnect()
    }
}
