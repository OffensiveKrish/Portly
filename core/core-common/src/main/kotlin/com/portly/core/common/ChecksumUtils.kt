package com.portly.core.common

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

object ChecksumUtils {
    const val STREAM_BUFFER_SIZE = 64 * 1024

    fun computeDigest(inputStream: InputStream, algorithm: String = "SHA-256"): String {
        val digest = MessageDigest.getInstance(algorithm)
        val buffer = ByteArray(STREAM_BUFFER_SIZE)
        while (true) {
            val read = inputStream.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun computeFileDigest(file: File, algorithm: String = "SHA-256"): String =
        file.inputStream().buffered(STREAM_BUFFER_SIZE).use { stream ->
            computeDigest(stream, algorithm)
        }
}
