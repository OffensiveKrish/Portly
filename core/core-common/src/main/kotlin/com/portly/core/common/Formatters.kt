package com.portly.core.common

import android.webkit.MimeTypeMap
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

object Formatters {

    fun formatBytes(bytes: Long): String {
        if (bytes < 0L) return "0 B"
        if (bytes < 1024L) return "$bytes B"
        val exp = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceAtMost(5)
        val unit = "KMGTPE"[exp - 1]
        val value = bytes / 1024.0.pow(exp.toDouble())
        return String.format(Locale.US, "%.1f %sB", value, unit)
    }

    fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0L) return "0 B/s"
        return "${formatBytes(bytesPerSec)}/s"
    }

    fun formatEta(seconds: Long?): String {
        if (seconds == null || seconds < 0L) return "--"
        val hrs = seconds / 3600
        val mins = (seconds % 3600) / 60
        val secs = seconds % 60
        return when {
            hrs > 0 -> String.format(Locale.US, "%dh %02dm", hrs, mins)
            mins > 0 -> String.format(Locale.US, "%dm %02ds", mins, secs)
            else -> String.format(Locale.US, "%ds", secs)
        }
    }

    fun formatTimestamp(millis: Long): String {
        if (millis <= 0L) return "--"
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return formatter.format(Date(millis))
    }

    fun guessMimeType(fileName: String, isDirectory: Boolean = false): String {
        if (isDirectory) return "inode/directory"
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.US)
        if (ext.isEmpty()) return "application/octet-stream"
        return when (ext) {
            "md", "markdown" -> "text/markdown"
            "kt", "kts", "java", "py", "js", "ts", "json", "xml", "yaml", "yml", "toml", "sh", "gradle" -> "text/plain"
            "txt", "log", "ini", "conf" -> "text/plain"
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            "csv" -> "text/csv"
            "zip" -> "application/zip"
            "pdf" -> "application/pdf"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            "bin" -> "application/octet-stream"
            else -> runCatching {
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            }.getOrNull() ?: "application/octet-stream"
        }
    }
}
