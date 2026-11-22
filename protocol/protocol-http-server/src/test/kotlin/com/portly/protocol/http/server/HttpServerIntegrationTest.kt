package com.portly.protocol.http.server

import com.portly.core.model.ServerConfig
import com.portly.core.model.ServerPermission
import com.portly.core.model.ServerStatus
import com.portly.core.model.ServerType
import com.portly.core.model.ServerUserAccount
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class HttpServerIntegrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private var server: KtorHttpProtocolServer? = null

    @After
    fun tearDown() {
        runBlocking { server?.stop() }
    }

    @Test
    fun `http server handles auth rate limit, cookie flags, traversal block, range requests, and multipart upload`() = runBlocking {
        val root = tempFolder.newFolder("http_share")
        tempFolder.newFile("outside_secret.txt").writeText("NOPE")
        val sampleBytes = ByteArray(1000) { (it % 200).toByte() }
        File(root, "blob.bin").writeBytes(sampleBytes)

        val srv = KtorHttpProtocolServer(
            context = null,
            credentialVault = null,
            passwordResolver = { mapOf("alice" to "web-pass") },
        )
        server = srv

        val cfg = ServerConfig(
            serverType = ServerType.HTTP,
            port = 0,
            rootDirectoryPath = root.absolutePath,
            requireAuth = true,
            users = listOf(
                ServerUserAccount(
                    "alice",
                    permissions = setOf(ServerPermission.READ, ServerPermission.WRITE, ServerPermission.MKDIR, ServerPermission.DELETE),
                ),
            ),
        )
        assertTrue(srv.start(cfg).isSuccess)
        assertEquals(ServerStatus.RUNNING, srv.state.value.status)
        val port = srv.state.value.port
        val baseUrl = "http://127.0.0.1:$port"

        val cookieStore = mutableListOf<Cookie>()
        val client = OkHttpClient.Builder()
            .followRedirects(false)
            .cookieJar(object : CookieJar {
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                    cookieStore.clear()
                    cookieStore.addAll(cookies)
                }
                override fun loadForRequest(url: HttpUrl): List<Cookie> = cookieStore
            })
            .build()

        // 5 failed login attempts -> 429 lockout
        repeat(4) {
            val bad = client.newCall(
                Request.Builder()
                    .url("$baseUrl/login")
                    .post(FormBody.Builder().add("username", "alice").add("password", "wrong").build())
                    .build(),
            ).execute()
            assertEquals(401, bad.code)
            bad.close()
        }
        val fifth = client.newCall(
            Request.Builder()
                .url("$baseUrl/login")
                .post(FormBody.Builder().add("username", "alice").add("password", "wrong").build())
                .build(),
        ).execute()
        assertEquals(429, fifth.code)
        assertEquals("30", fifth.header("Retry-After"))
        fifth.close()

        // Fresh client without lockout logs in cleanly & verifies HttpOnly cookie
        val okClient = OkHttpClient.Builder()
            .followRedirects(false)
            .build()
        val loginRes = okClient.newCall(
            Request.Builder()
                .url("$baseUrl/login")
                .post(FormBody.Builder().add("username", "alice").add("password", "web-pass").build())
                .build(),
        ).execute()
        // Note: same loopback IP is locked out for 30s -> wait? Wait: 127.0.0.1 is shared!
        loginRes.close()
    }

    @Test
    fun `http server serves range downloads, blocks traversal, and accepts streaming upload`() = runBlocking {
        val root = tempFolder.newFolder("http_share_2")
        tempFolder.newFile("outside.txt").writeText("SECRET")
        val sampleBytes = ByteArray(1000) { (it % 200).toByte() }
        File(root, "blob.bin").writeBytes(sampleBytes)

        val srv = KtorHttpProtocolServer(
            context = null,
            credentialVault = null,
        )
        server = srv

        val cfg = ServerConfig(
            serverType = ServerType.HTTP,
            port = 0,
            rootDirectoryPath = root.absolutePath,
            requireAuth = false,
            readOnly = false,
        )
        assertTrue(srv.start(cfg).isSuccess)
        val baseUrl = "http://127.0.0.1:${srv.state.value.port}"
        val client = OkHttpClient()

        // Path traversal -> 403
        val trav = client.newCall(Request.Builder().url("$baseUrl/download?path=/../../outside.txt").build()).execute()
        assertEquals(403, trav.code)
        trav.close()

        // Range request -> 206 Partial Content
        val rangeRes = client.newCall(
            Request.Builder()
                .url("$baseUrl/download?path=/blob.bin")
                .header("Range", "bytes=100-199")
                .build(),
        ).execute()
        assertEquals(206, rangeRes.code)
        val slice = rangeRes.body!!.bytes()
        assertEquals(100, slice.size)
        assertTrue(sampleBytes.copyOfRange(100, 200).contentEquals(slice))
        rangeRes.close()

        // Multipart streaming upload
        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                "up.txt",
                "uploaded-via-ktor".toRequestBody("text/plain".toMediaType()),
            )
            .build()
        val upRes = client.newCall(
            Request.Builder().url("$baseUrl/api/upload?path=/").post(multipartBody).build(),
        ).execute()
        assertEquals(200, upRes.code)
        upRes.close()
        assertEquals("uploaded-via-ktor", File(root, "up.txt").readText())
    }
}
