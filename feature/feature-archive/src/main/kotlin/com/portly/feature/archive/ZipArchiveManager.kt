package com.portly.feature.archive

import android.content.Context
import android.net.Uri
import com.portly.core.common.PathValidator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class ZipArchiveEntryInfo(
    val entryPath: String,
    val name: String,
    val isDirectory: Boolean,
    val compressedSizeBytes: Long,
    val uncompressedSizeBytes: Long,
    val lastModifiedMillis: Long,
) {
    val compressionPercent: Int
        get() = if (isDirectory || uncompressedSizeBytes <= 0L) {
            0
        } else {
            (((uncompressedSizeBytes - compressedSizeBytes).coerceAtLeast(0L) * 100L) / uncompressedSizeBytes).toInt()
        }
}

data class ZipProgress(
    val processedEntries: Int,
    val totalEntries: Int,
    val currentEntryName: String,
)

@Singleton
class ZipArchiveManager internal constructor(
    private val context: Context?,
    @Suppress("UNUSED_PARAMETER") marker: Unit,
) {
    internal constructor() : this(null, Unit)

    @Inject
    constructor(@ApplicationContext context: Context) : this(context, Unit)
    suspend fun listEntries(archivePath: String): Result<List<ZipArchiveEntryInfo>> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(archivePath)
            require(file.exists() && file.isFile) { "Archive not found: $archivePath" }
            val result = mutableListOf<ZipArchiveEntryInfo>()
            ZipFile(file).use { zip ->
                val enumEntries = zip.entries()
                while (enumEntries.hasMoreElements()) {
                    currentCoroutineContext().ensureActive()
                    val entry = enumEntries.nextElement()
                    result.add(entry.toInfo())
                }
            }
            result.sortedWith(compareBy<ZipArchiveEntryInfo> { !it.isDirectory }.thenBy { it.entryPath.lowercase() })
        }
    }

    suspend fun listEntriesFromUri(uri: Uri): Result<List<ZipArchiveEntryInfo>> = withContext(Dispatchers.IO) {
        runCatching {
            val appCtx = context ?: error("Context unavailable for URI access")
            val result = mutableListOf<ZipArchiveEntryInfo>()
            appCtx.contentResolver.openInputStream(uri)?.use { raw ->
                ZipInputStream(BufferedInputStream(raw, STREAM_BUFFER_SIZE)).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        currentCoroutineContext().ensureActive()
                        result.add(entry.toInfo())
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            } ?: error("Cannot open URI: $uri")
            result.sortedWith(compareBy<ZipArchiveEntryInfo> { !it.isDirectory }.thenBy { it.entryPath.lowercase() })
        }
    }

    suspend fun extractArchive(
        archivePath: String,
        destinationDirPath: String,
        selectedEntryPaths: Set<String>? = null,
        onProgress: (ZipProgress) -> Unit = {},
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val source = File(archivePath)
            require(source.exists() && source.isFile) { "Archive not found: $archivePath" }
            val destDir = File(destinationDirPath).apply { mkdirs() }
            require(destDir.exists() && destDir.isDirectory) { "Destination is not a directory: $destinationDirPath" }

            val totalEntries = ZipFile(source).use { zip ->
                val entries = zip.entries()
                var count = 0
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (selectedEntryPaths == null || entry.name in selectedEntryPaths) {
                        count++
                    }
                }
                count
            }

            FileInputStream(source).use { fis ->
                extractFromStream(
                    inputStream = fis,
                    destDir = destDir,
                    totalEntries = totalEntries,
                    selectedEntryPaths = selectedEntryPaths,
                    onProgress = onProgress,
                )
            }
        }
    }

    suspend fun extractArchiveFromUri(
        archiveUri: Uri,
        destinationDirPath: String,
        selectedEntryPaths: Set<String>? = null,
        onProgress: (ZipProgress) -> Unit = {},
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val appCtx = context ?: error("Context unavailable for URI access")
            val destDir = File(destinationDirPath).apply { mkdirs() }
            appCtx.contentResolver.openInputStream(archiveUri)?.use { raw ->
                extractFromStream(
                    inputStream = raw,
                    destDir = destDir,
                    totalEntries = selectedEntryPaths?.size ?: 0,
                    selectedEntryPaths = selectedEntryPaths,
                    onProgress = onProgress,
                )
            } ?: error("Cannot open URI: $archiveUri")
        }
    }

    suspend fun createArchive(
        sourcePaths: List<String>,
        outputZipPath: String,
        onProgress: (ZipProgress) -> Unit = {},
    ): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanSources = sourcePaths
                .map { File(it) }
                .filter { it.exists() }
            require(cleanSources.isNotEmpty()) { "Select at least one valid file or folder to archive." }

            val outputFile = File(outputZipPath)
            outputFile.parentFile?.mkdirs()
            val outputCanonical = outputFile.canonicalPath

            val itemsToPack = mutableListOf<Pair<String, File>>()
            for (src in cleanSources) {
                if (src.isDirectory) {
                    collectDirectoryEntries(
                        basePrefix = src.name,
                        dir = src,
                        excludedCanonicalZipPath = outputCanonical,
                        sink = itemsToPack,
                    )
                } else if (src.canonicalPath != outputCanonical) {
                    itemsToPack.add(src.name to src)
                }
            }

            val total = itemsToPack.size
            val buffer = ByteArray(STREAM_BUFFER_SIZE)
            var packedCount = 0

            ZipOutputStream(BufferedOutputStream(FileOutputStream(outputFile), STREAM_BUFFER_SIZE)).use { zos ->
                for ((entryName, file) in itemsToPack) {
                    currentCoroutineContext().ensureActive()
                    packedCount++
                    onProgress(ZipProgress(packedCount, total, entryName))

                    if (file.isDirectory) {
                        val dirName = if (entryName.endsWith("/")) entryName else "$entryName/"
                        val entry = ZipEntry(dirName).apply { time = file.lastModified() }
                        zos.putNextEntry(entry)
                        zos.closeEntry()
                    } else {
                        val entry = ZipEntry(entryName).apply { time = file.lastModified() }
                        zos.putNextEntry(entry)
                        BufferedInputStream(FileInputStream(file), STREAM_BUFFER_SIZE).use { bis ->
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val read = bis.read(buffer)
                                if (read <= 0) break
                                zos.write(buffer, 0, read)
                            }
                        }
                        zos.closeEntry()
                    }
                }
            }
            outputFile.length()
        }
    }

    private suspend fun extractFromStream(
        inputStream: InputStream,
        destDir: File,
        totalEntries: Int,
        selectedEntryPaths: Set<String>?,
        onProgress: (ZipProgress) -> Unit,
    ): Int {
        val buffer = ByteArray(STREAM_BUFFER_SIZE)
        var extracted = 0
        ZipInputStream(BufferedInputStream(inputStream, STREAM_BUFFER_SIZE)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                currentCoroutineContext().ensureActive()
                val rawName = entry.name

                // Validate EVERY entry against Zip-Slip path traversal before touching disk.
                val safeTarget = PathValidator.canonicalizeUnderRoot(destDir, rawName)
                    .getOrElse { err ->
                        throw SecurityException("Unsafe ZIP entry rejected: $rawName", err)
                    }

                val shouldExtract = selectedEntryPaths == null || rawName in selectedEntryPaths ||
                    selectedEntryPaths.any { sel -> rawName.startsWith(if (sel.endsWith("/")) sel else "$sel/") }

                if (shouldExtract) {
                    extracted++
                    onProgress(ZipProgress(extracted, totalEntries.coerceAtLeast(extracted), rawName))
                    if (entry.isDirectory) {
                        safeTarget.mkdirs()
                    } else {
                        safeTarget.parentFile?.mkdirs()
                        BufferedOutputStream(FileOutputStream(safeTarget), STREAM_BUFFER_SIZE).use { bos ->
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val read = zis.read(buffer)
                                if (read <= 0) break
                                bos.write(buffer, 0, read)
                            }
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return extracted
    }

    private fun collectDirectoryEntries(
        basePrefix: String,
        dir: File,
        excludedCanonicalZipPath: String,
        sink: MutableList<Pair<String, File>>,
    ) {
        val children = dir.listFiles()?.sortedBy { it.name.lowercase() }.orEmpty()
        if (children.isEmpty()) {
            sink.add("$basePrefix/" to dir)
            return
        }
        for (child in children) {
            if (child.canonicalPath == excludedCanonicalZipPath) continue
            val childEntry = "$basePrefix/${child.name}"
            if (child.isDirectory) {
                collectDirectoryEntries(childEntry, child, excludedCanonicalZipPath, sink)
            } else {
                sink.add(childEntry to child)
            }
        }
    }

    private fun ZipEntry.toInfo(): ZipArchiveEntryInfo {
        val normalized = name.trimEnd('/')
        val simpleName = normalized.substringAfterLast('/').ifEmpty { name }
        return ZipArchiveEntryInfo(
            entryPath = name,
            name = simpleName,
            isDirectory = isDirectory || name.endsWith("/"),
            compressedSizeBytes = compressedSize.coerceAtLeast(0L),
            uncompressedSizeBytes = size.coerceAtLeast(0L),
            lastModifiedMillis = time.coerceAtLeast(0L),
        )
    }

    companion object {
        const val STREAM_BUFFER_SIZE = 65_536
    }
}
