package com.portly.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ProtocolType(
    val displayName: String,
    val scheme: String,
    val defaultPort: Int,
    val isEncryptedByDefault: Boolean,
    val supportsResume: Boolean,
    val supportsChmod: Boolean,
) {
    FTP(
        displayName = "FTP",
        scheme = "ftp",
        defaultPort = 21,
        isEncryptedByDefault = false,
        supportsResume = true,
        supportsChmod = true,
    ),
    FTPS(
        displayName = "FTPS",
        scheme = "ftps",
        defaultPort = 21,
        isEncryptedByDefault = true,
        supportsResume = true,
        supportsChmod = true,
    ),
    SFTP(
        displayName = "SFTP",
        scheme = "sftp",
        defaultPort = 22,
        isEncryptedByDefault = true,
        supportsResume = true,
        supportsChmod = true,
    ),
    WEBDAV(
        displayName = "WebDAV",
        scheme = "https",
        defaultPort = 443,
        isEncryptedByDefault = true,
        supportsResume = true,
        supportsChmod = false,
    ),
    SMB(
        displayName = "SMB",
        scheme = "smb",
        defaultPort = 445,
        isEncryptedByDefault = false,
        supportsResume = true,
        supportsChmod = false,
    ),
    HTTP(
        displayName = "HTTP",
        scheme = "http",
        defaultPort = 8080,
        isEncryptedByDefault = false,
        supportsResume = true,
        supportsChmod = false,
    ),
    HTTPS(
        displayName = "HTTPS",
        scheme = "https",
        defaultPort = 8443,
        isEncryptedByDefault = true,
        supportsResume = true,
        supportsChmod = false,
    ),
}

@Serializable
enum class ServerType(
    val displayName: String,
    val scheme: String,
    val defaultPort: Int,
    val isEncrypted: Boolean,
) {
    FTP("FTP Server", "ftp", 2121, false),
    FTPS("FTPS Server", "ftps", 2122, true),
    HTTP("HTTP Server", "http", 8080, false),
    HTTPS("HTTPS Server", "https", 8443, true),
}
