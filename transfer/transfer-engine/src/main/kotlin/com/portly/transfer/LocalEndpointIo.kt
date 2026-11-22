package com.portly.transfer

import java.io.InputStream
import java.io.OutputStream

data class LocalStat(
    val exists: Boolean,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModifiedMillis: Long,
)

interface LocalEndpointIo {
    suspend fun stat(path: String, uriString: String?): LocalStat
    suspend fun listChildren(path: String, uriString: String?): List<LocalChild>
    suspend fun listSiblings(path: String, uriString: String?): Set<String>
    suspend fun openInput(path: String, uriString: String?, offset: Long): InputStream
    suspend fun openTempPartOutput(destinationPath: String, destinationUriString: String?, append: Boolean): PartOutputHandle
    suspend fun currentPartSize(destinationPath: String, destinationUriString: String?): Long
    suspend fun deletePart(destinationPath: String, destinationUriString: String?)
    suspend fun copyLocal(sourcePath: String, sourceUriString: String?, destPath: String, destUriString: String?)
    suspend fun deleteSource(path: String, uriString: String?)
}

data class LocalChild(
    val name: String,
    val path: String,
    val uriString: String?,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModifiedMillis: Long,
)

interface PartOutputHandle : AutoCloseable {
    val stream: OutputStream
    suspend fun commit(finalDestinationPath: String, finalDestinationUriString: String?)
}
