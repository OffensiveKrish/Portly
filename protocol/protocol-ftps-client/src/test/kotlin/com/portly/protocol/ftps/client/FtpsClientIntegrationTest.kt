package com.portly.protocol.ftps.client

import android.content.Context
import com.portly.core.common.ProtocolException
import com.portly.core.model.AuthType
import com.portly.core.model.ConnectionProfile
import com.portly.core.model.FtpsMode
import com.portly.core.model.ProtocolError
import com.portly.core.model.ProtocolType
import com.portly.core.model.ResolvedCredentials
import com.portly.core.model.TlsVersionPolicy
import com.portly.core.security.CertificateManager
import kotlinx.coroutines.runBlocking
import org.apache.ftpserver.DataConnectionConfigurationFactory
import org.apache.ftpserver.FtpServer
import org.apache.ftpserver.FtpServerFactory
import org.apache.ftpserver.ftplet.Authority
import org.apache.ftpserver.listener.ListenerFactory
import org.apache.ftpserver.ssl.SslConfigurationFactory
import org.apache.ftpserver.usermanager.impl.BaseUser
import org.apache.ftpserver.usermanager.impl.WritePermission
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.lang.reflect.Proxy
import java.security.KeyStore
import java.security.cert.X509Certificate

class FtpsClientIntegrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var serverRoot: File
    private lateinit var server: FtpServer
    private lateinit var certFingerprint: String
    private var port: Int = 0

    @Before
    fun setUp() {
        serverRoot = tempFolder.newFolder("ftps_root")
        val certPair = CertificateManager.createSelfSignedCertificate(
            commonName = "Portly FTPS Test",
            sanIpAddresses = listOf("127.0.0.1"),
        )
        certFingerprint = CertificateManager.sha256Fingerprint(certPair.certificate)
        val ksFile = File(tempFolder.root, "test_ftps.p12")
        val ks = KeyStore.getInstance("PKCS12").apply {
            load(null, "changeit".toCharArray())
            setKeyEntry("ftps", certPair.privateKey, "changeit".toCharArray(), arrayOf(certPair.certificate))
        }
        ksFile.outputStream().use { ks.store(it, "changeit".toCharArray()) }

        val sslFactory = SslConfigurationFactory().apply {
            keystoreFile = ksFile
            keystorePassword = "changeit"
            keystoreType = "PKCS12"
            keyPassword = "changeit"
            setSslProtocol("TLSv1.3", "TLSv1.2")
        }
        val sslConfig = sslFactory.createSslConfiguration()

        val dataConn = DataConnectionConfigurationFactory().apply {
            sslConfiguration = sslConfig
        }.createDataConnectionConfiguration()

        val listener = ListenerFactory().apply {
            port = 0
            sslConfiguration = sslConfig
            isImplicitSsl = false
            dataConnectionConfiguration = dataConn
        }.createListener()

        val factory = FtpServerFactory().apply {
            addListener("default", listener)
            userManager.save(
                BaseUser().apply {
                    name = "alice"
                    password = "secret"
                    homeDirectory = serverRoot.absolutePath
                    authorities = listOf<Authority>(WritePermission())
                },
            )
        }
        server = factory.createServer()
        server.start()
        port = factory.listeners["default"]!!.port
    }

    @After
    fun tearDown() {
        server.stop()
    }

    @Test
    fun `explicit ftps rejects untrusted cert with CertificateUntrusted then succeeds when fingerprint trusted`() = runBlocking {
        val trusted = mutableSetOf<String>()
        val store = object : TrustedCertStore {
            override suspend fun trustedHostFingerprints(host: String, port: Int): Set<String> = trusted
            override suspend fun userCaCertificates(): List<X509Certificate> = emptyList()
        }
        val certMgr = CertificateManager(tempFolder.newFolder("certs"))
        val client = CommonsFtpsProtocolClient(store, certMgr)
        val profile = ConnectionProfile(
            id = "ftps1",
            name = "Test FTPS",
            protocol = ProtocolType.FTPS,
            host = "127.0.0.1",
            port = port,
            username = "alice",
            authType = AuthType.PASSWORD,
            ftpsMode = FtpsMode.EXPLICIT,
            tlsVersionPolicy = TlsVersionPolicy.TLS_1_2_OR_1_3,
        )
        val creds = ResolvedCredentials("alice", "secret")

        val untrusted = client.connect(profile, creds)
        assertTrue(untrusted.isFailure)
        val ex = untrusted.exceptionOrNull() as ProtocolException
        assertTrue(ex.protocolError is ProtocolError.CertificateUntrusted)
        assertEquals(certFingerprint, (ex.protocolError as ProtocolError.CertificateUntrusted).fingerprintSha256)

        trusted += certFingerprint
        assertTrue(client.connect(profile, creds).isSuccess)
        val out = client.openOutputStream("/secure.txt").getOrThrow()
        out.stream.write("encrypted payload".toByteArray())
        assertTrue(out.complete().isSuccess)
        assertEquals("encrypted payload", File(serverRoot, "secure.txt").readText())
        client.disconnect()
    }
}
