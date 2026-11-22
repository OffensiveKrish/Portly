package com.portly.protocol.https.server

import com.portly.core.model.ServerConfig
import com.portly.core.model.ServerPermission
import com.portly.core.model.ServerStatus
import com.portly.core.model.ServerType
import com.portly.core.model.ServerUserAccount
import com.portly.core.security.CertificateManager
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.KeyStore
import javax.net.ssl.HostnameVerifier

class HttpsServerIntegrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private var server: KtorHttpsProtocolServer? = null

    @After
    fun tearDown() {
        runBlocking { server?.stop() }
    }

    @Test
    fun `https server enforces TLS, Secure HttpOnly SameSite cookies, and range download`() = runBlocking {
        val root = tempFolder.newFolder("https_share")
        val sample = ByteArray(512) { (it % 197).toByte() }
        File(root, "tls.bin").writeBytes(sample)

        val certMgr = CertificateManager(tempFolder.newFolder("certs"))
        val certPair = CertificateManager.createSelfSignedCertificate("Portly HTTPS Test", listOf("127.0.0.1"))
        val fp = CertificateManager.sha256Fingerprint(certPair.certificate)
        val ksFile = File(tempFolder.root, "https_test.p12")
        val ks = KeyStore.getInstance("PKCS12").apply {
            load(null, "changeit".toCharArray())
            setKeyEntry("https", certPair.privateKey, "changeit".toCharArray(), arrayOf(certPair.certificate))
        }
        ksFile.outputStream().use { ks.store(it, "changeit".toCharArray()) }

        val srv = KtorHttpsProtocolServer(
            context = null,
            credentialVault = null,
            certificateManager = certMgr,
            passwordResolver = { mapOf("alice" to "https-pass") },
            keystoreFileOverride = ksFile,
        )
        server = srv

        val cfg = ServerConfig(
            serverType = ServerType.HTTPS,
            port = 0,
            rootDirectoryPath = root.absolutePath,
            requireAuth = true,
            users = listOf(
                ServerUserAccount("alice", permissions = setOf(ServerPermission.READ, ServerPermission.WRITE)),
            ),
        )
        assertTrue(srv.start(cfg).isSuccess)
        assertEquals(ServerStatus.RUNNING, srv.state.value.status)
        val port = srv.state.value.port

        val tm = certMgr.buildTrustManager("127.0.0.1", port, setOf(fp), emptyList())
        val sslCtx = certMgr.buildSslContext(tm)
        val cookies = mutableListOf<Cookie>()

        val client = OkHttpClient.Builder()
            .sslSocketFactory(sslCtx.socketFactory, tm)
            .hostnameVerifier(HostnameVerifier { host, _ -> host == "127.0.0.1" })
            .followRedirects(false)
            .cookieJar(object : CookieJar {
                override fun saveFromResponse(url: HttpUrl, list: List<Cookie>) {
                    cookies.clear()
                    cookies.addAll(list)
                }
                override fun loadForRequest(url: HttpUrl): List<Cookie> = cookies
            })
            .build()

        val loginRes = client.newCall(
            Request.Builder()
                .url("https://127.0.0.1:$port/login")
                .post(FormBody.Builder().add("username", "alice").add("password", "https-pass").build())
                .build(),
        ).execute()
        assertEquals(302, loginRes.code)
        val setCookie = loginRes.header("Set-Cookie") ?: ""
        assertTrue(setCookie.contains("HttpOnly"))
        assertTrue(setCookie.contains("Secure"))
        assertTrue(setCookie.contains("SameSite=Lax"))
        loginRes.close()

        val dl = client.newCall(
            Request.Builder()
                .url("https://127.0.0.1:$port/download?path=/tls.bin")
                .header("Range", "bytes=64-127")
                .build(),
        ).execute()
        assertEquals(206, dl.code)
        assertTrue(sample.copyOfRange(64, 128).contentEquals(dl.body!!.bytes()))
        dl.close()
    }
}
