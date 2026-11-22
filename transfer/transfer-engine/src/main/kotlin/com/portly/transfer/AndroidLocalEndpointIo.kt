package com.portly.transfer

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.portly.core.common.ChecksumUtils
import com.portly.core.common.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidLocalEndpointIo @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : LocalEndpointIo {

    override suspend fun stat(path: String, uriString: String?): LocalStat = withContext(ioDispatcher) {
        if (!uriString.isNullOrBlank()) {
            val doc = DocumentFile.fromSingleUri(context, Uri.parse(uriString))
            if (doc == null || !doc.exists()) {
                LocalStat(exists = false, isDirectory = false, sizeBytes = 0L, lastModifiedMillis = 0L)
            } else {
                LocalStat(
                    exists = true,
                    isDirectory = doc.isDirectory,
                    sizeBytes = doc.length(),
                    lastModifiedMillis = doc.lastModified(),
                )
            }
        } else {
            val file = File(path)
            LocalStat(
                exists = file.exists(),
                isDirectory = file.isDirectory,
                sizeBytes = if (file.isFile) file.length() else 0L,
                lastModifiedMillis = file.lastModified(),
            )
        }
    }

    override suspend fun listChildren(path: String, uriString: String?): List<LocalChild> = withContext(ioDispatcher) {
        if (!uriString.isNullOrBlank()) {
            val doc = DocumentFile.fromTreeUri(context, Uri.parse(uriString)) ?: return@withContext emptyList()
            doc.listFiles().mapNotNull { child ->
                val name = child.name ?: return@mapNotNull null
                LocalChild(
                    name = name,
                    path = child.uri.toString(),
                    uriString = child.uri.toString(),
                    isDirectory = child.isDirectory,
                    sizeBytes = if (child.isDirectory) 0L else child.length(),
                    lastModifiedMillis = child.lastModified(),
                )
            }
        } else {
            val dir = File(path)
            val children = dir.listFiles() ?: return@withContext emptyList()
            children.map { f ->
                LocalChild(
                    name = f.name,
                    path = f.absolutePath,
                    uriString = null,
                    isDirectory = f.isDirectory,
                    sizeBytes = if (f.isFile) f.length() else 0L,
                    lastModifiedMillis = f.lastModified(),
                )
            }
        }
    }

    override suspend fun listSiblings(path: String, uriString: String?): Set<String> = withContext(ioDispatcher) {
        if (!uriString.isNullOrBlank()) {
            emptySet()
        } else {
            File(path).parentFile?.list()?.toSet() ?: emptySet()
        }
    }

    override suspend fun openInput(path: String, uriString: String?, offset: Long): InputStream =
        withContext(ioDispatcher) {
            val raw = if (!uriString.isNullOrBlank()) {
                context.contentResolver.openInputStream(Uri.parse(uriString))
                    ?: error("Cannot open content URI for reading: $uriString")
            } else {
                FileInputStream(File(path))
            }
            val buffered = BufferedInputStream(raw, ChecksumUtils.STREAM_BUFFER_SIZE)
            if (offset > 0L) {
                var remaining = offset
                while (remaining > 0L) {
                    val skipped = buffered.skip(remaining)
                    if (skipped <= 0L) {
                        if (buffered.read() == -1) break
                        remaining--
                    } else {
                        remaining -= skipped
                    }
                }
            }
            buffered
        }

    override suspend fun openTempPartOutput(
        destinationPath: String,
        destinationUriString: String?,
        append: Boolean,
    ): PartOutputHandle = withContext(ioDispatcher) {
        if (!destinationUriString.isNullOrBlank()) {
            val uri = Uri.parse(destinationUriString)
            val mode = if (append) "wa" else "w"
            val stream = context.contentResolver.openOutputStream(uri, mode)
                ?: error("Cannot open SAF output stream")
            object : PartOutputHandle {
                override val stream: OutputStream = BufferedOutputStream(stream, ChecksumUtils.STREAM_BUFFER_SIZE)
                override suspend fun commit(finalDestinationPath: String, finalDestinationUriString: String?) {
                    this.stream.flush()
                }
                override fun close() {
                    this.stream.close()
                }
            }
        } else {
            val finalFile = File(destinationPath)
            finalFile.parentFile?.mkdirs()
            val partFile = File(finalFile.parentFile, "${finalFile.name}.part")
            val fos = FileOutputStream(partFile, append)
            object : PartOutputHandle {
                override val stream: OutputStream = BufferedOutputStream(fos, ChecksumUtils.STREAM_BUFFER_SIZE)
                override suspend fun commit(finalDestinationPath: String, finalDestinationUriString: String?) {
                    this.stream.flush()
                    this.stream.close()
                    val target = File(finalDestinationPath)
                    if (target.exists()) target.delete()
                    if (!partFile.renameTo(target)) {
                        partFile.inputStream().use { input ->
                            target.outputStream().use { out ->
                                val buf = ByteArray(ChecksumUtils.STREAM_BUFFER_SIZE)
                                while (true) {
                                    val r = input.read(buf)
                                    if (r <= 0) break
                                    out.write(buf, 0, r)
                                }
                            }
                        }
                        partFile.delete()
                    }
                }
                override fun close() {
                    runCatching { this.stream.close() }
                }
            }
        }
    }

    override suspend fun currentPartSize(destinationPath: String, destinationUriString: String?): Long =
        withContext(ioDispatcher) {
            if (!destinationUriString.isNullOrBlank()) {
                0L
            } else {
                val finalFile = File(destinationPath)
                val partFile = File(finalFile.parentFile, "${finalFile.name}.part")
                if (partFile.exists()) partFile.length() else 0L
            }
        }

    override suspend fun deletePart(destinationPath: String, destinationUriString: String?) {
        withContext(ioDispatcher) {
            if (destinationUriString.isNullOrBlank()) {
                val finalFile = File(destinationPath)
                File(finalFile.parentFile, "${finalFile.name}.part").delete()
            }
        }
    }

    override suspend fun copyLocal(
        sourcePath: String,
        sourceUriString: String?,
        destPath: String,
        destUriString: String?,
    ) {
        withContext(ioDispatcher) {
            val part = openTempPartOutput(destPath, destUriString, append = false)
            openInput(sourcePath, sourceUriString, 0L).use { input ->
                part.use { outHandle ->
                    val buf = ByteArray(ChecksumUtils.STREAM_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buf)
                        if (read <= 0) break
                        outHandle.stream.write(buf, 0, read)
                    }
                    outHandle.commit(destPath, destUriString)
                }
            }
        }
    }

    override suspend fun deleteSource(path: String, uriString: String?) {
        withContext(ioDispatcher) {
            if (!uriString.isNullOrBlank()) {
                DocumentFile.fromSingleUri(context, Uri.parse(uriString))?.delete()
            } else {
                File(path).delete()
            }
        }
    }
}
