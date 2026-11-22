package com.portly.protocol.ftp.server

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.portly.core.common.Formatters
import com.portly.core.common.PathValidator
import com.portly.core.model.ServerConfig
import com.portly.core.model.ServerPermission
import org.apache.ftpserver.ftplet.FileSystemFactory
import org.apache.ftpserver.ftplet.FileSystemView
import org.apache.ftpserver.ftplet.FtpException
import org.apache.ftpserver.ftplet.FtpFile
import org.apache.ftpserver.ftplet.User
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

internal class PortlyFtpFileSystemFactory(
    private val context: Context?,
    private val config: ServerConfig,
    private val userPermissionsProvider: (String) -> Set<ServerPermission>,
) : FileSystemFactory {

    override fun createFileSystemView(user: User): FileSystemView {
        val perms = userPermissionsProvider(user.name)
        val effectivePerms = if (config.readOnly) {
            perms.intersect(setOf(ServerPermission.READ))
        } else {
            perms
        }
        val treeUri = config.rootTreeUriString?.takeIf { it.isNotBlank() }?.let(Uri::parse)
        return if (treeUri != null && context != null) {
            SafFtpFileSystemView(context, treeUri, effectivePerms)
        } else {
            val rootDir = File(config.rootDirectoryPath).apply { mkdirs() }
            LocalSandboxedFtpFileSystemView(rootDir, effectivePerms)
        }
    }
}

private class LocalSandboxedFtpFileSystemView(
    private val rootDir: File,
    private val permissions: Set<ServerPermission>,
) : FileSystemView {

    private var currentPath: String = "/"

    override fun getHomeDirectory(): FtpFile = LocalSandboxedFtpFile(rootDir, "/", rootDir, permissions)

    override fun getWorkingDirectory(): FtpFile = getFile(currentPath)

    override fun changeWorkingDirectory(dir: String): Boolean {
        val resolvedVirtual = resolveVirtualPath(dir)
        val target = PathValidator.canonicalizeUnderRoot(rootDir, resolvedVirtual).getOrNull() ?: return false
        if (!target.exists() || !target.isDirectory) return false
        currentPath = resolvedVirtual
        return true
    }

    override fun getFile(file: String): FtpFile {
        val virtual = resolveVirtualPath(file)
        val canonical = PathValidator.canonicalizeUnderRoot(rootDir, virtual)
            .getOrElse {
                // Return a non-existent denied stub so Apache FtpServer responds with 550 cleanly.
                return DeniedFtpFile(virtual)
            }
        return LocalSandboxedFtpFile(rootDir, virtual, canonical, permissions)
    }

    override fun isRandomAccessible(): Boolean = true

    override fun dispose() = Unit

    private fun resolveVirtualPath(requested: String): String {
        if (requested.isBlank() || requested == ".") return currentPath
        val base = if (requested.startsWith("/")) requested else {
            if (currentPath == "/") "/$requested" else "$currentPath/$requested"
        }
        return PathValidator.normalizeRemotePath(base)
    }
}

private class DeniedFtpFile(private val path: String) : FtpFile {
    override fun getAbsolutePath(): String = path
    override fun getName(): String = path.substringAfterLast('/').ifEmpty { "/" }
    override fun isHidden(): Boolean = false
    override fun isDirectory(): Boolean = false
    override fun isFile(): Boolean = false
    override fun doesExist(): Boolean = false
    override fun isReadable(): Boolean = false
    override fun isWritable(): Boolean = false
    override fun isRemovable(): Boolean = false
    override fun getOwnerName(): String = "portly"
    override fun getGroupName(): String = "portly"
    override fun getLinkCount(): Int = 0
    override fun getLastModified(): Long = 0L
    override fun setLastModified(time: Long): Boolean = false
    override fun getSize(): Long = 0L
    override fun getPhysicalFile(): Any = path
    override fun mkdir(): Boolean = false
    override fun delete(): Boolean = false
    override fun move(destination: FtpFile): Boolean = false
    override fun listFiles(): List<FtpFile> = emptyList()
    override fun createOutputStream(offset: Long): OutputStream = throw SecurityException("Access denied")
    override fun createInputStream(offset: Long): InputStream = throw SecurityException("Access denied")
}

private class LocalSandboxedFtpFile(
    private val rootDir: File,
    private val virtualPath: String,
    private val file: File,
    private val permissions: Set<ServerPermission>,
) : FtpFile {

    override fun getAbsolutePath(): String = virtualPath

    override fun getName(): String = if (virtualPath == "/") "/" else file.name

    override fun isHidden(): Boolean = file.isHidden || file.name.startsWith(".")

    override fun isDirectory(): Boolean = file.isDirectory

    override fun isFile(): Boolean = file.isFile

    override fun doesExist(): Boolean = file.exists()

    override fun isReadable(): Boolean =
        ServerPermission.READ in permissions && file.canRead()

    override fun isWritable(): Boolean {
        if (file.isDirectory) return ServerPermission.MKDIR in permissions || ServerPermission.WRITE in permissions
        return ServerPermission.WRITE in permissions && (!file.exists() || file.canWrite())
    }

    override fun isRemovable(): Boolean =
        virtualPath != "/" && ServerPermission.DELETE in permissions

    override fun getOwnerName(): String = "portly"

    override fun getGroupName(): String = "portly"

    override fun getLinkCount(): Int = if (file.isDirectory) 3 else 1

    override fun getLastModified(): Long = file.lastModified()

    override fun setLastModified(time: Long): Boolean =
        ServerPermission.WRITE in permissions && file.setLastModified(time)

    override fun getSize(): Long = if (file.isFile) file.length() else 0L

    override fun getPhysicalFile(): Any = file

    override fun mkdir(): Boolean {
        if (ServerPermission.MKDIR !in permissions) return false
        return PathValidator.canonicalizeUnderRoot(rootDir, virtualPath).getOrNull()?.mkdirs() == true
    }

    override fun delete(): Boolean {
        if (!isRemovable) return false
        val target = PathValidator.canonicalizeUnderRoot(rootDir, virtualPath).getOrNull() ?: return false
        return if (target.isDirectory) target.delete() else target.delete()
    }

    override fun move(destination: FtpFile): Boolean {
        if (ServerPermission.WRITE !in permissions || ServerPermission.DELETE !in permissions) return false
        val destFile = (destination as? LocalSandboxedFtpFile)?.file ?: return false
        PathValidator.canonicalizeUnderRoot(rootDir, destination.absolutePath).getOrNull() ?: return false
        return file.renameTo(destFile)
    }

    override fun listFiles(): List<FtpFile> {
        if (ServerPermission.READ !in permissions || !file.isDirectory) return emptyList()
        val children = file.listFiles() ?: return emptyList()
        return children.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
            .mapNotNull { child ->
                val childVirtual = if (virtualPath == "/") "/${child.name}" else "$virtualPath/${child.name}"
                val verified = PathValidator.canonicalizeUnderRoot(rootDir, childVirtual).getOrNull()
                    ?: return@mapNotNull null
                LocalSandboxedFtpFile(rootDir, childVirtual, verified, permissions)
            }
    }

    override fun createOutputStream(offset: Long): OutputStream {
        if (ServerPermission.WRITE !in permissions) throw SecurityException("Write permission denied")
        val verified = PathValidator.canonicalizeUnderRoot(rootDir, virtualPath)
            .getOrElse { throw FtpException("Unsafe path") }
        verified.parentFile?.mkdirs()
        if (offset > 0L && verified.exists()) {
            val raf = java.io.RandomAccessFile(verified, "rw").apply { seek(offset) }
            return object : OutputStream() {
                override fun write(b: Int) = raf.write(b)
                override fun write(b: ByteArray, off: Int, len: Int) = raf.write(b, off, len)
                override fun close() = raf.close()
            }
        }
        return FileOutputStream(verified, false)
    }

    override fun createInputStream(offset: Long): InputStream {
        if (ServerPermission.READ !in permissions) throw SecurityException("Read permission denied")
        val verified = PathValidator.canonicalizeUnderRoot(rootDir, virtualPath)
            .getOrElse { throw FtpException("Unsafe path") }
        val fis = FileInputStream(verified)
        if (offset > 0L) {
            var remaining = offset
            while (remaining > 0L) {
                val skipped = fis.skip(remaining)
                if (skipped <= 0L) break
                remaining -= skipped
            }
        }
        return fis
    }
}

private class SafFtpFileSystemView(
    private val context: Context,
    private val rootTreeUri: Uri,
    private val permissions: Set<ServerPermission>,
) : FileSystemView {

    private var currentPath: String = "/"

    private fun rootDoc(): DocumentFile =
        DocumentFile.fromTreeUri(context, rootTreeUri) ?: error("Unavailable SAF root")

    override fun getHomeDirectory(): FtpFile = SafFtpFile(context, rootDoc(), "/", null, permissions)

    override fun getWorkingDirectory(): FtpFile = getFile(currentPath)

    override fun changeWorkingDirectory(dir: String): Boolean {
        val virtual = resolveVirtualPath(dir)
        val resolved = resolveDoc(virtual) ?: return false
        if (!resolved.isDirectory) return false
        currentPath = virtual
        return true
    }

    override fun getFile(file: String): FtpFile {
        val virtual = resolveVirtualPath(file)
        if (virtual.contains("..") || virtual.contains('\u0000')) return DeniedFtpFile(virtual)
        val doc = resolveDoc(virtual)
        return if (doc != null) {
            SafFtpFile(context, doc, virtual, null, permissions)
        } else {
            val parentPath = PathValidator.parentRemotePath(virtual)
            val parentDoc = resolveDoc(parentPath) ?: return DeniedFtpFile(virtual)
            SafFtpFile(context, null, virtual, parentDoc, permissions)
        }
    }

    override fun isRandomAccessible(): Boolean = false
    override fun dispose() = Unit

    private fun resolveVirtualPath(requested: String): String {
        if (requested.isBlank() || requested == ".") return currentPath
        val base = if (requested.startsWith("/")) requested else {
            if (currentPath == "/") "/$requested" else "$currentPath/$requested"
        }
        return PathValidator.normalizeRemotePath(base)
    }

    private fun resolveDoc(virtualPath: String): DocumentFile? {
        var cur: DocumentFile = rootDoc()
        if (virtualPath == "/") return cur
        for (segment in virtualPath.trimStart('/').split('/')) {
            if (segment.isEmpty()) continue
            cur = cur.findFile(segment) ?: return null
        }
        return cur
    }
}

private class SafFtpFile(
    private val context: Context,
    private val doc: DocumentFile?,
    private val virtualPath: String,
    private val parentDoc: DocumentFile?,
    private val permissions: Set<ServerPermission>,
) : FtpFile {

    override fun getAbsolutePath(): String = virtualPath
    override fun getName(): String = if (virtualPath == "/") "/" else virtualPath.substringAfterLast('/')
    override fun isHidden(): Boolean = name.startsWith(".")
    override fun isDirectory(): Boolean = doc?.isDirectory == true
    override fun isFile(): Boolean = doc?.isFile == true
    override fun doesExist(): Boolean = doc?.exists() == true
    override fun isReadable(): Boolean = ServerPermission.READ in permissions && doc?.canRead() == true
    override fun isWritable(): Boolean = if (doc == null) {
        ServerPermission.WRITE in permissions && parentDoc != null
    } else {
        ServerPermission.WRITE in permissions && doc.canWrite()
    }
    override fun isRemovable(): Boolean = virtualPath != "/" && ServerPermission.DELETE in permissions && doc != null
    override fun getOwnerName(): String = "portly"
    override fun getGroupName(): String = "portly"
    override fun getLinkCount(): Int = 1
    override fun getLastModified(): Long = doc?.lastModified() ?: 0L
    override fun setLastModified(time: Long): Boolean = false
    override fun getSize(): Long = if (doc?.isFile == true) doc.length() else 0L
    override fun getPhysicalFile(): Any = virtualPath

    override fun mkdir(): Boolean {
        if (ServerPermission.MKDIR !in permissions) return false
        return parentDoc?.createDirectory(name) != null
    }

    override fun delete(): Boolean = isRemovable && doc?.delete() == true

    override fun move(destination: FtpFile): Boolean {
        if (ServerPermission.WRITE !in permissions || ServerPermission.DELETE !in permissions) return false
        return doc?.renameTo(destination.name) == true
    }

    override fun listFiles(): List<FtpFile> {
        if (ServerPermission.READ !in permissions || doc == null || !doc.isDirectory) return emptyList()
        return doc.listFiles().mapNotNull { child ->
            val childName = child.name ?: return@mapNotNull null
            val childPath = if (virtualPath == "/") "/$childName" else "$virtualPath/$childName"
            SafFtpFile(context, child, childPath, doc, permissions)
        }
    }

    override fun createOutputStream(offset: Long): OutputStream {
        if (ServerPermission.WRITE !in permissions) throw SecurityException("Write permission denied")
        val target = doc ?: parentDoc?.createFile(Formatters.guessMimeType(name), name)
            ?: throw FtpException("Cannot create SAF file")
        return context.contentResolver.openOutputStream(target.uri, if (offset > 0L) "wa" else "w")
            ?: throw FtpException("Cannot open SAF output stream")
    }

    override fun createInputStream(offset: Long): InputStream {
        if (ServerPermission.READ !in permissions || doc == null) throw SecurityException("Read permission denied")
        val stream = context.contentResolver.openInputStream(doc.uri)
            ?: throw FtpException("Cannot open SAF input stream")
        if (offset > 0L) stream.skip(offset)
        return stream
    }
}
