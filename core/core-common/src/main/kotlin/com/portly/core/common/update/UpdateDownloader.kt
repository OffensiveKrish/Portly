package com.portly.core.common.update

import android.content.Context
import com.portly.core.common.AppResult
import com.portly.core.model.UpdateInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed interface DownloadProgress {
    data class InProgress(val bytesDownloaded: Long, val totalBytes: Long, val progress: Float) : DownloadProgress
    data class Completed(val file: File) : DownloadProgress
}

@Singleton
class UpdateDownloader @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun downloadApk(updateInfo: UpdateInfo): Flow<DownloadProgress> = flow {
        val updatesDir = File(context.cacheDir, "updates")
        if (!updatesDir.exists()) {
            updatesDir.mkdirs()
        }

        val destinationFile = File(updatesDir, "Portly-${updateInfo.versionName}.apk")
        val tempPartFile = File(updatesDir, "Portly-${updateInfo.versionName}.apk.part")

        if (tempPartFile.exists()) {
            tempPartFile.delete()
        }

        val request = Request.Builder()
            .url(updateInfo.apkDownloadUrl)
            .header("User-Agent", "Portly-Android-Downloader")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("APK download failed with HTTP ${response.code}: ${response.message}")
            }

            val body = response.body
            val contentLength = body.contentLength().takeIf { it > 0 } ?: updateInfo.apkSizeBytes

            body.byteStream().use { inputStream ->
                FileOutputStream(tempPartFile).use { outputStream ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesCopied = 0L
                    var lastEmittedBytes = 0L

                    while (true) {
                        val bytesRead = inputStream.read(buffer)
                        if (bytesRead <= 0) break
                        outputStream.write(buffer, 0, bytesRead)
                        bytesCopied += bytesRead

                        // Throttle progress emissions to every ~128KB or at completion
                        if (bytesCopied - lastEmittedBytes >= 128 * 1024 || (contentLength > 0 && bytesCopied >= contentLength)) {
                            val progress = if (contentLength > 0) (bytesCopied.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f) else 0f
                            emit(DownloadProgress.InProgress(bytesCopied, contentLength, progress))
                            lastEmittedBytes = bytesCopied
                        }
                    }
                    outputStream.flush()
                }
            }

            if (destinationFile.exists()) {
                destinationFile.delete()
            }
            if (!tempPartFile.renameTo(destinationFile)) {
                tempPartFile.copyTo(destinationFile, overwrite = true)
                tempPartFile.delete()
            }

            emit(DownloadProgress.Completed(destinationFile))
        }
    }.flowOn(Dispatchers.IO)
}
