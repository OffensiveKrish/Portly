package com.portly.feature.filemanager

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import androidx.documentfile.provider.DocumentFile
import com.portly.core.common.ChecksumUtils
import com.portly.core.common.Formatters
import com.portly.core.common.IoDispatcher
import com.portly.core.common.PathValidator
import com.portly.core.database.dao.FavoritePathDao
import com.portly.core.database.entity.FavoritePathEntity
import com.portly.core.model.FilePermissions
import com.portly.core.model.LocalFile
import com.portly.core.model.SortDirection
import com.portly.core.model.SortField
import com.portly.core.model.StorageVolumeInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class StagedTrashItem(
    val originalPath: String,
    val stagedTrashPath: String,
)

@Singleton
class LocalFileRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val favoritePathDao: FavoritePathDao,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val trashDir: File
        get() = File(context.cacheDir, ".portly_trash").apply { mkdirs() }

    fun observeFavorites(): Flow<List<FavoritePathEntity>> =
        favoritePathDao.observeAll().map { list -> list.filter { !it.isRemote } }

    suspend fun toggleFavorite(path: String, label: String, currentlyFavorite: Boolean) {
        if (currentlyFavorite) {
            favoritePathDao.delete(path)
        } else {
            favoritePathDao.upsert(
                FavoritePathEntity(
                    path = path,
                    label = label,
                    isRemote = false,
                    profileId = null,
                    addedAtMillis = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun hasAllFilesAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }

    fun defaultRootDirectory(): File {
        val ext = Environment.getExternalStorageDirectory()
        return if (hasAllFilesAccess() && ext.canRead()) ext else context.filesDir
    }

    suspend fun getStorageVolumes(): List<StorageVolumeInfo> = withContext(ioDispatcher) {
        val result = mutableListOf<StorageVolumeInfo>()
        val primary = Environment.getExternalStorageDirectory()
        runCatching {
            val stat = StatFs(primary.path)
            result += StorageVolumeInfo(
                id = "primary",
                label = "Internal Storage",
                rootPath = primary.absolutePath,
                totalBytes = stat.totalBytes,
                availableBytes = stat.availableBytes,
                isRemovable = false,
            )
        }

        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
        if (storageManager != null) {
            for (vol in storageManager.storageVolumes) {
                if (vol.isPrimary) continue
                val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) vol.directory else null
                val desc = vol.getDescription(context)
                val isUsb = desc.contains("usb", ignoreCase = true)
                if (dir != null && dir.exists()) {
                    runCatching {
                        val stat = StatFs(dir.path)
                        result += StorageVolumeInfo(
                            id = vol.uuid ?: dir.absolutePath,
                            label = desc,
                            rootPath = dir.absolutePath,
                            totalBytes = stat.totalBytes,
                            availableBytes = stat.availableBytes,
                            isRemovable = vol.isRemovable,
                            isUsb = isUsb,
                        )
                    }
                }
            }
        }

        // Include SAF persisted tree volumes (SD card / USB OTG granted via OpenDocumentTree)
        for (perm in context.contentResolver.persistedUriPermissions) {
            if (!perm.isReadPermission) continue
            val doc = DocumentFile.fromTreeUri(context, perm.uri) ?: continue
            result += StorageVolumeInfo(
                id = perm.uri.toString(),
                label = doc.name ?: "External Volume (SAF)",
                rootPath = perm.uri.toString(),
                rootUriString = perm.uri.toString(),
                totalBytes = 0L,
                availableBytes = 0L,
                isRemovable = true,
            )
        }
        result
    }

    fun persistTreePermission(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
    }

    suspend fun listDirectory(
        directoryPath: String,
        treeUriString: String?,
        showHidden: Boolean,
        sortField: SortField,
        sortDirection: SortDirection,
    ): Result<List<LocalFile>> = withContext(ioDispatcher) {
        runCatching {
            val raw = if (!treeUriString.isNullOrBlank()) {
                val doc = DocumentFile.fromTreeUri(context, Uri.parse(treeUriString))
                    ?: error("Cannot access selected SAF folder")
                doc.listFiles().mapNotNull { child ->
                    val name = child.name ?: return@mapNotNull null
                    if (!showHidden && name.startsWith(".")) return@mapNotNull null
                    LocalFile(
                        path = child.uri.toString(),
                        uriString = child.uri.toString(),
                        name = name,
                        isDirectory = child.isDirectory,
                        sizeBytes = if (child.isDirectory) 0L else child.length(),
                        lastModifiedMillis = child.lastModified(),
                        mimeType = child.type ?: Formatters.guessMimeType(name, child.isDirectory),
                        isHidden = name.startsWith("."),
                        isReadOnly = !child.canWrite(),
                        permissions = FilePermissions(
                            readable = child.canRead(),
                            writable = child.canWrite(),
                            executable = false,
                        ),
                    )
                }
            } else {
                val dir = File(directoryPath)
                if (!dir.exists() || !dir.isDirectory) error("Directory does not exist: $directoryPath")
                val children = dir.listFiles() ?: emptyArray()
                children.mapNotNull { file ->
                    if (!showHidden && file.name.startsWith(".")) return@mapNotNull null
                    toLocalFile(file)
                }
            }
            sortFiles(raw, sortField, sortDirection)
        }
    }

    suspend fun searchRecursive(
        rootPath: String,
        query: String,
        showHidden: Boolean,
        maxResults: Int = 250,
    ): List<LocalFile> = withContext(ioDispatcher) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return@withContext emptyList()
        val root = File(rootPath)
        if (!root.exists() || !root.isDirectory) return@withContext emptyList()

        val matches = mutableListOf<LocalFile>()
        val queue = ArrayDeque<File>()
        queue.addLast(root)
        while (queue.isNotEmpty() && matches.size < maxResults) {
            val current = queue.removeFirst()
            val children = current.listFiles() ?: continue
            for (child in children) {
                if (!showHidden && child.name.startsWith(".")) continue
                if (child.name.lowercase().contains(q)) {
                    matches += toLocalFile(child)
                    if (matches.size >= maxResults) break
                }
                if (child.isDirectory && child.canRead()) {
                    queue.addLast(child)
                }
            }
        }
        sortFiles(matches, SortField.NAME, SortDirection.ASCENDING)
    }

    suspend fun mkdir(parentPath: String, treeUriString: String?, folderName: String): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                val clean = PathValidator.sanitizeFileName(folderName)
                if (!treeUriString.isNullOrBlank()) {
                    val parent = DocumentFile.fromTreeUri(context, Uri.parse(treeUriString))
                        ?: error("Cannot access SAF directory")
                    parent.createDirectory(clean) ?: error("Failed to create folder $clean")
                } else {
                    val target = File(parentPath, clean)
                    if (!target.mkdirs() && !target.isDirectory) {
                        error("Failed to create folder $clean")
                    }
                }
                Unit
            }
        }

    suspend fun rename(file: LocalFile, newName: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val clean = PathValidator.sanitizeFileName(newName)
            if (!file.uriString.isNullOrBlank()) {
                val doc = DocumentFile.fromSingleUri(context, Uri.parse(file.uriString))
                    ?: error("Cannot locate SAF document")
                if (!doc.renameTo(clean)) error("Rename failed")
            } else {
                val src = File(file.path)
                val dest = File(src.parentFile, clean)
                if (!src.renameTo(dest)) error("Rename failed")
            }
            Unit
        }
    }

    suspend fun copyOrMoveFiles(
        items: List<LocalFile>,
        destinationDirPath: String,
        destinationTreeUri: String?,
        isMove: Boolean,
    ): Result<Int> = withContext(ioDispatcher) {
        runCatching {
            var count = 0
            for (item in items) {
                if (!destinationTreeUri.isNullOrBlank()) {
                    val destTree = DocumentFile.fromTreeUri(context, Uri.parse(destinationTreeUri))
                        ?: error("Cannot access destination SAF folder")
                    copyIntoSaf(item, destTree)
                    if (isMove) deletePermanently(item)
                    count++
                } else {
                    val destDir = File(destinationDirPath)
                    if (item.uriString != null) {
                        val srcDoc = DocumentFile.fromSingleUri(context, Uri.parse(item.uriString))
                            ?: error("Cannot open source SAF item")
                        copySafToLocalFile(srcDoc, item.name, destDir)
                        if (isMove) srcDoc.delete()
                        count++
                    } else {
                        val srcFile = File(item.path)
                        val existing = destDir.list()?.toSet() ?: emptySet()
                        val targetName = PathValidator.resolveUniqueName(existing, srcFile.name)
                        val destFile = File(destDir, targetName)
                        if (isMove && srcFile.renameTo(destFile)) {
                            count++
                        } else {
                            copyLocalRecursive(srcFile, destFile)
                            if (isMove) srcFile.deleteRecursively()
                            count++
                        }
                    }
                }
            }
            count
        }
    }

    suspend fun stageDeleteForUndo(items: List<LocalFile>): Result<List<StagedTrashItem>> =
        withContext(ioDispatcher) {
            runCatching {
                val staged = mutableListOf<StagedTrashItem>()
                for (item in items) {
                    if (item.uriString != null) {
                        val doc = DocumentFile.fromSingleUri(context, Uri.parse(item.uriString))
                        doc?.delete()
                    } else {
                        val src = File(item.path)
                        val trashTarget = File(trashDir, "${UUID.randomUUID()}_${src.name}")
                        if (src.renameTo(trashTarget)) {
                            staged += StagedTrashItem(src.absolutePath, trashTarget.absolutePath)
                        } else {
                            copyLocalRecursive(src, trashTarget)
                            src.deleteRecursively()
                            staged += StagedTrashItem(src.absolutePath, trashTarget.absolutePath)
                        }
                    }
                }
                staged
            }
        }

    suspend fun restoreFromTrash(staged: List<StagedTrashItem>): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            for (entry in staged) {
                val trashFile = File(entry.stagedTrashPath)
                val originalFile = File(entry.originalPath)
                if (trashFile.exists()) {
                    originalFile.parentFile?.mkdirs()
                    if (!trashFile.renameTo(originalFile)) {
                        copyLocalRecursive(trashFile, originalFile)
                        trashFile.deleteRecursively()
                    }
                }
            }
        }
    }

    suspend fun purgeStagedTrash(staged: List<StagedTrashItem>) = withContext(ioDispatcher) {
        for (entry in staged) {
            runCatching { File(entry.stagedTrashPath).deleteRecursively() }
        }
    }

    suspend fun computeChecksum(file: LocalFile): Result<String> = withContext(ioDispatcher) {
        runCatching {
            if (!file.uriString.isNullOrBlank()) {
                context.contentResolver.openInputStream(Uri.parse(file.uriString))?.use { stream ->
                    ChecksumUtils.computeDigest(stream, "SHA-256")
                } ?: error("Unable to open stream")
            } else {
                ChecksumUtils.computeFileDigest(File(file.path), "SHA-256")
            }
        }
    }

    private fun deletePermanently(item: LocalFile) {
        if (!item.uriString.isNullOrBlank()) {
            DocumentFile.fromSingleUri(context, Uri.parse(item.uriString))?.delete()
        } else {
            File(item.path).deleteRecursively()
        }
    }

    private fun copyLocalRecursive(source: File, destination: File) {
        if (source.isDirectory) {
            destination.mkdirs()
            source.listFiles()?.forEach { child ->
                copyLocalRecursive(child, File(destination, child.name))
            }
        } else {
            destination.parentFile?.mkdirs()
            source.inputStream().buffered(ChecksumUtils.STREAM_BUFFER_SIZE).use { input ->
                destination.outputStream().buffered(ChecksumUtils.STREAM_BUFFER_SIZE).use { output ->
                    streamCopy(input, output)
                }
            }
        }
    }

    private fun copySafToLocalFile(doc: DocumentFile, name: String, destDir: File) {
        if (doc.isDirectory) {
            val subDir = File(destDir, name).apply { mkdirs() }
            doc.listFiles().forEach { child ->
                copySafToLocalFile(child, child.name ?: "item", subDir)
            }
        } else {
            val existing = destDir.list()?.toSet() ?: emptySet()
            val target = File(destDir, PathValidator.resolveUniqueName(existing, name))
            context.contentResolver.openInputStream(doc.uri)?.use { input ->
                target.outputStream().buffered(ChecksumUtils.STREAM_BUFFER_SIZE).use { output ->
                    streamCopy(input, output)
                }
            }
        }
    }

    private fun copyIntoSaf(item: LocalFile, destTree: DocumentFile) {
        if (item.isDirectory && item.uriString == null) {
            val createdDir = destTree.createDirectory(item.name) ?: error("Could not create SAF dir")
            File(item.path).listFiles()?.forEach { child ->
                copyIntoSaf(toLocalFile(child), createdDir)
            }
        } else {
            val createdFile = destTree.createFile(item.mimeType, item.name) ?: error("Could not create SAF file")
            val input = if (item.uriString != null) {
                context.contentResolver.openInputStream(Uri.parse(item.uriString))
            } else {
                File(item.path).inputStream()
            } ?: error("Cannot read source")
            input.use { inStream ->
                context.contentResolver.openOutputStream(createdFile.uri)?.use { outStream ->
                    streamCopy(inStream, outStream)
                } ?: error("Cannot write destination SAF file")
            }
        }
    }

    private fun streamCopy(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(ChecksumUtils.STREAM_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            output.write(buffer, 0, read)
        }
        output.flush()
    }

    private fun toLocalFile(file: File): LocalFile = LocalFile(
        path = file.absolutePath,
        uriString = null,
        name = file.name,
        isDirectory = file.isDirectory,
        sizeBytes = if (file.isDirectory) 0L else file.length(),
        lastModifiedMillis = file.lastModified(),
        mimeType = Formatters.guessMimeType(file.name, file.isDirectory),
        isHidden = file.isHidden || file.name.startsWith("."),
        isReadOnly = !file.canWrite(),
        permissions = FilePermissions(
            readable = file.canRead(),
            writable = file.canWrite(),
            executable = file.canExecute(),
        ),
        childCount = if (file.isDirectory) file.list()?.size else null,
    )

    companion object {
        fun sortFiles(
            files: List<LocalFile>,
            sortField: SortField,
            direction: SortDirection,
        ): List<LocalFile> {
            val comparator = when (sortField) {
                SortField.NAME -> compareBy<LocalFile> { it.name.lowercase() }
                SortField.DATE -> compareBy<LocalFile> { it.lastModifiedMillis }
                SortField.SIZE -> compareBy<LocalFile> { it.sizeBytes }
                SortField.TYPE -> compareBy<LocalFile> { it.name.substringAfterLast('.', "").lowercase() }
                    .thenBy { it.name.lowercase() }
            }
            val directed = if (direction == SortDirection.ASCENDING) comparator else comparator.reversed()
            return files.sortedWith(compareBy<LocalFile> { !it.isDirectory }.then(directed))
        }
    }
}
