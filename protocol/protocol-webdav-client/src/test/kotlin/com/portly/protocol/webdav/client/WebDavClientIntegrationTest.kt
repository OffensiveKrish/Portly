package com.portly.protocol.webdav.client

import com.portly.core.common.ProtocolException
import com.portly.core.model.AuthType
import com.portly.core.model.ConnectionProfile
import com.portly.core.model.ProtocolError
import com.portly.core.model.ProtocolType
import com.portly.core.model.ResolvedCredentials
import com.portly.core.security.CertificateManager
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WebDavClientIntegrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var mockServer: MockWebServer

    @Before
    fun setUp() {
        mockServer = MockWebServer()
        mockServer.start()
    }

    @After
    fun tearDown() {
        mockServer.shutdown()
    }

    @Test
    fun `webdav client handles digest auth, propfind, ranged download, put upload, mkdir, move, delete`() = runBlocking {
        val uploaded = mutableMapOf<String, ByteArray>()
        val sampleData = "0123456789ABCDEF".toByteArray()

        mockServer.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val auth = request.getHeader("Authorization").orEmpty()
                if (!auth.startsWith("Digest ") || !auth.contains("username=\"alice\"")) {
                    return MockResponse()
                        .setResponseCode(401)
                        .addHeader("WWW-Authenticate", """Digest realm="portly", nonce="dcd98b7102dd2f0e8b11d0f600bfb0c093", qop="auth"""")
                }

                return when (request.method) {
                    "PROPFIND" -> {
                        val depth = request.getHeader("Depth")
                        if (depth == "0") {
                            MockResponse().setResponseCode(207).setBody(
                                """
                                <D:multistatus xmlns:D="DAV:">
                                  <D:response>
                                    <D:href>/</D:href>
                                    <D:propstat><D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat>
                                  </D:response>
                                </D:multistatus>
                                """.trimIndent(),
                            )
                        } else {
                            MockResponse().setResponseCode(207).setBody(
                                """
                                <D:multistatus xmlns:D="DAV:">
                                  <D:response>
                                    <D:href>/</D:href>
                                    <D:propstat><D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat>
                                  </D:response>
                                  <D:response>
                                    <D:href>/docs/</D:href>
                                    <D:propstat><D:prop><D:displayname>docs</D:displayname><D:resourcetype><D:collection/></D:resourcetype></D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat>
                                  </D:response>
                                  <D:response>
                                    <D:href>/hello.txt</D:href>
                                    <D:propstat><D:prop><D:getcontentlength>16</D:getcontentlength><D:getcontenttype>text/plain</D:getcontenttype></D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat>
                                  </D:response>
                                </D:multistatus>
                                """.trimIndent(),
                            )
                        }
                    }
                    "GET" -> {
                        val range = request.getHeader("Range")
                        if (range == "bytes=4-") {
                            MockResponse().setResponseCode(206).setBody(String(sampleData.copyOfRange(4, sampleData.size)))
                        } else {
                            MockResponse().setResponseCode(200).setBody(String(sampleData))
                        }
                    }
                    "PUT" -> {
                        uploaded[request.path!!] = request.body.readByteArray()
                        MockResponse().setResponseCode(201)
                    }
                    "MKCOL" -> MockResponse().setResponseCode(201)
                    "MOVE" -> MockResponse().setResponseCode(204)
                    "DELETE" -> MockResponse().setResponseCode(204)
                    else -> MockResponse().setResponseCode(405)
                }
            }
        }

        val certMgr = CertificateManager(tempFolder.newFolder("certs"))
        val client = OkHttpWebDavProtocolClient(
            certStore = object : WebDavCertStore {
                override suspend fun trustedHostFingerprints(host: String, port: Int): Set<String> = emptySet()
            },
            certificateManager = certMgr,
        )

        val profile = ConnectionProfile(
            id = "wd1",
            name = "Mock WebDAV",
            protocol = ProtocolType.WEBDAV,
            host = mockServer.hostName,
            port = mockServer.port,
            username = "alice",
            authType = AuthType.PASSWORD,
            useHttpsForWebDav = false,
        )
        val creds = ResolvedCredentials("alice", "webdav-pw")

        assertTrue(client.connect(profile, creds).isSuccess)
        val list = client.list("/").getOrThrow()
        assertEquals(2, list.size)
        assertTrue(list[0].isDirectory)
        assertEquals("hello.txt", list[1].name)
        assertEquals(16L, list[1].sizeBytes)

        val inHandle = client.openInputStream("/hello.txt", offset = 4L).getOrThrow()
        assertEquals("456789ABCDEF", String(inHandle.stream.readBytes()))
        assertTrue(inHandle.complete().isSuccess)

        val outHandle = client.openOutputStream("/up.txt", 0L, 6L).getOrThrow()
        outHandle.stream.write("dav-up".toByteArray())
        assertTrue(outHandle.complete().isSuccess)
        assertEquals("dav-up", String(uploaded["/up.txt"]!!))

        assertTrue(client.mkdir("/newdir").isSuccess)
        assertTrue(client.rename("/up.txt", "/up2.txt").isSuccess)
        assertTrue(client.delete("/up2.txt").isSuccess)
        client.disconnect()
    }

    @Test
    fun `webdav 401 maps to AuthenticationFailed`() = runBlocking {
        mockServer.enqueue(MockResponse().setResponseCode(401))
        val client = OkHttpWebDavProtocolClient(
            certStore = object : WebDavCertStore {
                override suspend fun trustedHostFingerprints(host: String, port: Int): Set<String> = emptySet()
            },
            certificateManager = CertificateManager(tempFolder.newFolder("certs2")),
        )
        val res = client.connect(
            ConnectionProfile("w2", "Fail", ProtocolType.WEBDAV, mockServer.hostName, mockServer.port, "alice", useHttpsForWebDav = false),
            ResolvedCredentials("alice", "bad"),
        )
        assertTrue(res.isFailure)
        assertTrue((res.exceptionOrNull() as ProtocolException).protocolError is ProtocolError.AuthenticationFailed)
    }
}
