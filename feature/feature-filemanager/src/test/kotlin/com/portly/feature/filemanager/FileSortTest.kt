package com.portly.feature.filemanager

import com.portly.core.model.LocalFile
import com.portly.core.model.SortDirection
import com.portly.core.model.SortField
import org.junit.Assert.assertEquals
import org.junit.Test

class FileSortTest {

    private val sample = listOf(
        LocalFile("/b.txt", null, "b.txt", false, 100L, 10L, "text/plain", false, false),
        LocalFile("/dirA", null, "dirA", true, 0L, 5L, "inode/directory", false, false),
        LocalFile("/a.pdf", null, "a.pdf", false, 500L, 20L, "application/pdf", false, false),
    )

    @Test
    fun `directories always sort before files`() {
        val sortedAsc = LocalFileRepository.sortFiles(sample, SortField.NAME, SortDirection.ASCENDING)
        assertEquals(listOf("dirA", "a.pdf", "b.txt"), sortedAsc.map { it.name })

        val sortedSizeDesc = LocalFileRepository.sortFiles(sample, SortField.SIZE, SortDirection.DESCENDING)
        assertEquals(listOf("dirA", "a.pdf", "b.txt"), sortedSizeDesc.map { it.name })
    }
}
