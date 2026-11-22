package com.portly.core.model

enum class SortField {
    NAME,
    DATE,
    SIZE,
    TYPE,
}

enum class SortDirection {
    ASCENDING,
    DESCENDING,
}

data class FilePermissions(
    val posixMode: Int? = null,
    val readable: Boolean = true,
    val writable: Boolean = true,
    val executable: Boolean = false,
) {
    fun toPosixString(): String {
        val mode = posixMode ?: return buildString {
            append(if (readable) 'r' else '-')
            append(if (writable) 'w' else '-')
            append(if (executable) 'x' else '-')
        }
        val chars = CharArray(9)
        val flags = intArrayOf(
            0b100_000_000, 0b010_000_000, 0b001_000_000,
            0b000_100_000, 0b000_010_000, 0b000_001_000,
            0b000_000_100, 0b000_000_010, 0b000_000_001,
        )
        val symbols = charArrayOf('r', 'w', 'x', 'r', 'w', 'x', 'r', 'w', 'x')
        for (i in flags.indices) {
            chars[i] = if ((mode and flags[i]) != 0) symbols[i] else '-'
        }
        return String(chars)
    }
}

data class LocalFile(
    val path: String,
    val uriString: String? = null,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModifiedMillis: Long,
    val mimeType: String,
    val isHidden: Boolean,
    val isReadOnly: Boolean,
    val permissions: FilePermissions = FilePermissions(writable = !isReadOnly),
    val childCount: Int? = null,
)

data class RemoteFile(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModifiedMillis: Long,
    val mimeType: String,
    val permissions: FilePermissions? = null,
    val owner: String? = null,
    val group: String? = null,
    val isSymlink: Boolean = false,
    val symlinkTarget: String? = null,
)

data class StorageVolumeInfo(
    val id: String,
    val label: String,
    val rootPath: String,
    val rootUriString: String? = null,
    val totalBytes: Long,
    val availableBytes: Long,
    val isRemovable: Boolean,
    val isUsb: Boolean = false,
) {
    val usedBytes: Long
        get() = (totalBytes - availableBytes).coerceAtLeast(0L)

    val usageFraction: Float
        get() = if (totalBytes > 0L) usedBytes.toFloat() / totalBytes.toFloat() else 0f
}
