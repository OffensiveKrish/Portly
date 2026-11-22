package com.portly.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File

class PathValidatorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `canonicalizeUnderRoot allows valid nested path`() {
        val root = tempFolder.newFolder("shared")
        val sub = File(root, "docs")
        sub.mkdirs()

        val result = PathValidator.canonicalizeUnderRoot(root, "/docs/report.txt")
        assertTrue(result.isSuccess)
        assertEquals(File(sub, "report.txt").canonicalPath, result.getOrThrow().canonicalPath)
    }

    @Test
    fun `canonicalizeUnderRoot rejects parent directory traversal`() {
        val root = tempFolder.newFolder("shared")
        val result = PathValidator.canonicalizeUnderRoot(root, "/docs/../../etc/passwd")
        assertTrue(result.isFailure)
    }

    @Test
    fun `canonicalizeUnderRoot rejects backslash traversal and null bytes`() {
        val root = tempFolder.newFolder("shared")
        assertTrue(PathValidator.canonicalizeUnderRoot(root, "..\\secret.txt").isFailure)
        assertTrue(PathValidator.canonicalizeUnderRoot(root, "file.txt\u0000.jpg").isFailure)
    }

    @Test
    fun `normalizeRemotePath resolves dot segments`() {
        assertEquals("/a/c", PathValidator.normalizeRemotePath("/a/b/../c/./"))
        assertEquals("/", PathValidator.normalizeRemotePath("/../../"))
    }

    @Test
    fun `resolveUniqueName increments suffix cleanly`() {
        val existing = setOf("photo.jpg", "photo (1).jpg")
        assertEquals("photo (2).jpg", PathValidator.resolveUniqueName(existing, "photo.jpg"))
    }

    @Test
    fun `computeDigest matches known SHA-256`() {
        val hash = ChecksumUtils.computeDigest(ByteArrayInputStream("portly".toByteArray()), "SHA-256")
        assertEquals("2066942a22556b420f513e5407978e2337d8e1a4a24f4e45ecfa51b7b7d5b130".length, hash.length)
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            ChecksumUtils.computeDigest(ByteArrayInputStream(ByteArray(0)), "SHA-256"),
        )
    }
}
