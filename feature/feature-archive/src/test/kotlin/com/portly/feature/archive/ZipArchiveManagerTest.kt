package com.portly.feature.archive

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipArchiveManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val manager = ZipArchiveManager()

    @Test
    fun `create list and extract archive round-trip preserves files and directories`() = runBlocking {
        val srcRoot = tempFolder.newFolder("src")
        val subDir = File(srcRoot, "docs").apply { mkdirs() }
        val f1 = File(srcRoot, "hello.txt").apply { writeText("Hello Portly Archive!") }
        val f2 = File(subDir, "nested.txt").apply { writeText("Nested 64KB stream content.") }
        val emptyDir = File(srcRoot, "empty").apply { mkdirs() }
        val zipFile = File(tempFolder.root, "bundle.zip")

        val zipBytes = manager.createArchive(
            sourcePaths = listOf(f1.absolutePath, subDir.absolutePath, emptyDir.absolutePath),
            outputZipPath = zipFile.absolutePath,
        ).getOrThrow()
        assertTrue(zipBytes > 0L)
        assertTrue(zipFile.exists())

        val entries = manager.listEntries(zipFile.absolutePath).getOrThrow()
        assertTrue(entries.any { it.entryPath == "hello.txt" && !it.isDirectory })
        assertTrue(entries.any { it.entryPath == "docs/nested.txt" && !it.isDirectory })
        assertTrue(entries.any { it.entryPath == "empty/" && it.isDirectory })

        val outAll = tempFolder.newFolder("out_all")
        val count = manager.extractArchive(zipFile.absolutePath, outAll.absolutePath).getOrThrow()
        assertEquals(3, count)
        assertEquals("Hello Portly Archive!", File(outAll, "hello.txt").readText())
        assertEquals("Nested 64KB stream content.", File(outAll, "docs/nested.txt").readText())
        assertTrue(File(outAll, "empty").isDirectory)

        val outSelected = tempFolder.newFolder("out_selected")
        val selCount = manager.extractArchive(
            archivePath = zipFile.absolutePath,
            destinationDirPath = outSelected.absolutePath,
            selectedEntryPaths = setOf("docs/nested.txt"),
        ).getOrThrow()
        assertEquals(1, selCount)
        assertFalse(File(outSelected, "hello.txt").exists())
        assertEquals("Nested 64KB stream content.", File(outSelected, "docs/nested.txt").readText())
    }

    @Test
    fun `extractArchive rejects zip slip traversal entries before writing outside root`() = runBlocking {
        val maliciousZip = File(tempFolder.root, "evil.zip")
        ZipOutputStream(FileOutputStream(maliciousZip)).use { zos ->
            zos.putNextEntry(ZipEntry("../../escaped.txt"))
            zos.write("pwnd".toByteArray())
            zos.closeEntry()
        }

        val destDir = tempFolder.newFolder("sandbox")
        val result = manager.extractArchive(maliciousZip.absolutePath, destDir.absolutePath)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
        assertFalse(File(tempFolder.root, "escaped.txt").exists())
    }
}
