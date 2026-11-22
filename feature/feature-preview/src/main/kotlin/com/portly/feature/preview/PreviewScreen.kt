package com.portly.feature.preview

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.webkit.MimeTypeMap
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.NavigateBefore
import androidx.compose.material.icons.outlined.NavigateNext
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.portly.core.common.Formatters
import com.portly.core.model.ProtocolError
import com.portly.core.ui.components.EmptyStateView
import com.portly.core.ui.components.PortlyErrorCard
import com.portly.core.ui.components.SkeletonList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipInputStream

enum class PreviewKind {
    IMAGE,
    VIDEO,
    AUDIO,
    PDF,
    CSV,
    TEXT,
    ZIP,
    EXTERNAL_DOC,
}

data class ZipTreeEntry(
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val compressedSize: Long,
)

fun resolvePreviewKind(fileName: String, mimeType: String): PreviewKind {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when {
        mimeType.startsWith("image/") || ext in setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "heic") ->
            PreviewKind.IMAGE
        mimeType.startsWith("video/") || ext in setOf("mp4", "mkv", "webm", "mov", "avi") ->
            PreviewKind.VIDEO
        mimeType.startsWith("audio/") || ext in setOf("mp3", "wav", "ogg", "flac", "m4a", "aac") ->
            PreviewKind.AUDIO
        mimeType == "application/pdf" || ext == "pdf" ->
            PreviewKind.PDF
        mimeType == "text/csv" || ext == "csv" ->
            PreviewKind.CSV
        mimeType == "application/zip" || ext == "zip" ->
            PreviewKind.ZIP
        mimeType.startsWith("text/") || ext in setOf(
            "txt", "md", "log", "json", "xml", "yaml", "yml", "toml", "ini", "conf",
            "kt", "kts", "java", "py", "js", "ts", "html", "css", "sh", "gradle", "c", "cpp", "h", "rs", "go",
        ) -> PreviewKind.TEXT
        else -> PreviewKind.EXTERNAL_DOC
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    filePath: String?,
    uriString: String? = null,
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val resolvedName = remember(filePath, uriString) {
        when {
            !filePath.isNullOrBlank() -> File(filePath).name
            !uriString.isNullOrBlank() -> Uri.parse(uriString).lastPathSegment?.substringAfterLast('/') ?: "Preview"
            else -> "Preview"
        }
    }
    val mimeType = remember(resolvedName) { Formatters.guessMimeType(resolvedName) }
    val kind = remember(resolvedName, mimeType) { resolvePreviewKind(resolvedName, mimeType) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = resolvedName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (!filePath.isNullOrBlank() || !uriString.isNullOrBlank()) {
                        IconButton(
                            onClick = {
                                openExternally(context, filePath, uriString, mimeType)
                            },
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = "Open with")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (filePath.isNullOrBlank() && uriString.isNullOrBlank()) {
                EmptyStateView(
                    icon = Icons.Outlined.Description,
                    title = "No file selected",
                    subtitle = "Select a file from File Manager to preview images, media, PDFs, text, CSV, or ZIP contents.",
                )
            } else {
                when (kind) {
                    PreviewKind.IMAGE -> ImagePreview(filePath, uriString)
                    PreviewKind.VIDEO, PreviewKind.AUDIO -> MediaPreview(filePath, uriString)
                    PreviewKind.PDF -> PdfPreview(filePath, uriString)
                    PreviewKind.TEXT -> TextPreview(filePath, uriString)
                    PreviewKind.CSV -> CsvPreview(filePath, uriString)
                    PreviewKind.ZIP -> ZipPreview(filePath, uriString)
                    PreviewKind.EXTERNAL_DOC -> ExternalDocFallback(
                        fileName = resolvedName,
                        mimeType = mimeType,
                        filePath = filePath,
                        uriString = uriString,
                    )
                }
            }
        }
    }
}

@Composable
private fun ImagePreview(filePath: String?, uriString: String?) {
    val model: Any? = uriString?.let(Uri::parse) ?: filePath?.let(::File)
    Box(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = model,
            contentDescription = "Image preview",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun MediaPreview(filePath: String?, uriString: String?) {
    val context = LocalContext.current
    val mediaUri = remember(filePath, uriString) {
        uriString?.let(Uri::parse) ?: Uri.fromFile(File(requireNotNull(filePath)))
    }
    val exoPlayer = remember(mediaUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(mediaUri))
            prepare()
        }
    }
    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun PdfPreview(filePath: String?, uriString: String?) {
    val context = LocalContext.current
    var pageIndex by remember { mutableIntStateOf(0) }
    var pageCount by remember { mutableIntStateOf(0) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf<ProtocolError?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(filePath, uriString, pageIndex) {
        loading = true
        error = null
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val pfd: ParcelFileDescriptor = when {
                    !uriString.isNullOrBlank() ->
                        context.contentResolver.openFileDescriptor(Uri.parse(uriString), "r")
                            ?: error("Unable to open PDF URI")
                    !filePath.isNullOrBlank() ->
                        ParcelFileDescriptor.open(File(filePath), ParcelFileDescriptor.MODE_READ_ONLY)
                    else -> error("Missing PDF source")
                }
                pfd.use { descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        val count = renderer.pageCount
                        if (count <= 0) error("PDF contains no pages")
                        val clamped = pageIndex.coerceIn(0, count - 1)
                        renderer.openPage(clamped).use { page ->
                            val bmp = Bitmap.createBitmap(
                                (page.width * 2).coerceAtMost(2400),
                                (page.height * 2).coerceAtMost(3200),
                                Bitmap.Config.ARGB_8888,
                            )
                            bmp.eraseColor(android.graphics.Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            Pair(count, bmp)
                        }
                    }
                }
            }
        }
        loading = false
        result.onSuccess { (count, rendered) ->
            pageCount = count
            bitmap = rendered
        }.onFailure { t ->
            error = ProtocolError.ProtocolFailure(
                userMessage = "Could not render PDF document.",
                nextStep = "Ensure the PDF is not corrupted or password-protected.",
                technicalDetail = t.message,
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (pageCount > 0) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(
                    enabled = pageIndex > 0,
                    onClick = { pageIndex = (pageIndex - 1).coerceAtLeast(0) },
                ) {
                    Icon(Icons.Outlined.NavigateBefore, contentDescription = "Previous page")
                }
                Text("Page ${pageIndex + 1} of $pageCount", style = MaterialTheme.typography.labelLarge)
                IconButton(
                    enabled = pageIndex < pageCount - 1,
                    onClick = { pageIndex = (pageIndex + 1).coerceAtMost(pageCount - 1) },
                ) {
                    Icon(Icons.Outlined.NavigateNext, contentDescription = "Next page")
                }
            }
        }
        when {
            error != null -> PortlyErrorCard(error = error!!, modifier = Modifier.padding(16.dp))
            loading && bitmap == null -> SkeletonList(itemCount = 4, modifier = Modifier.padding(top = 16.dp))
            bitmap != null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = "PDF page ${pageIndex + 1}",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun TextPreview(filePath: String?, uriString: String?) {
    val context = LocalContext.current
    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    var truncated by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<ProtocolError?>(null) }

    LaunchedEffect(filePath, uriString) {
        loading = true
        error = null
        val maxBytes = 512 * 1024
        val result = withContext(Dispatchers.IO) {
            runCatching {
                openInputStream(context, filePath, uriString).use { stream ->
                    val buffer = ByteArray(64 * 1024)
                    val out = java.io.ByteArrayOutputStream()
                    var total = 0
                    var wasTruncated = false
                    while (true) {
                        val remaining = maxBytes - total
                        if (remaining <= 0) {
                            wasTruncated = stream.read() != -1
                            break
                        }
                        val read = stream.read(buffer, 0, minOf(buffer.size, remaining))
                        if (read <= 0) break
                        out.write(buffer, 0, read)
                        total += read
                    }
                    val text = out.toString(Charsets.UTF_8.name())
                    Pair(text.lines(), wasTruncated)
                }
            }
        }
        loading = false
        result.onSuccess { (readLines, wasTruncated) ->
            lines = readLines
            truncated = wasTruncated
        }.onFailure { t ->
            error = ProtocolError.ProtocolFailure(
                userMessage = "Could not read text file.",
                nextStep = "Check file permissions and try again.",
                technicalDetail = t.message,
            )
        }
    }

    when {
        error != null -> PortlyErrorCard(error = error!!, modifier = Modifier.padding(16.dp))
        loading -> SkeletonList(itemCount = 8, modifier = Modifier.padding(top = 12.dp))
        else -> LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            if (truncated) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.padding(bottom = 8.dp),
                    ) {
                        Text(
                            text = "Preview capped at first 512 KB for low memory usage.",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
            }
            itemsIndexed(lines) { index, line ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "${index + 1}".padStart(4, ' '),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                }
            }
        }
    }
}

@Composable
private fun CsvPreview(filePath: String?, uriString: String?) {
    val context = LocalContext.current
    var rows by remember { mutableStateOf<List<List<String>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<ProtocolError?>(null) }

    LaunchedEffect(filePath, uriString) {
        loading = true
        val result = withContext(Dispatchers.IO) {
            runCatching {
                openInputStream(context, filePath, uriString).bufferedReader().useLines { seq ->
                    seq.take(500).map { line -> line.split(',').map { it.trim() } }.toList()
                }
            }
        }
        loading = false
        result.onSuccess { rows = it }
            .onFailure { t ->
                error = ProtocolError.ProtocolFailure(
                    userMessage = "Could not parse CSV file.",
                    nextStep = "Open as plain text or external app.",
                    technicalDetail = t.message,
                )
            }
    }

    when {
        error != null -> PortlyErrorCard(error = error!!, modifier = Modifier.padding(16.dp))
        loading -> SkeletonList(itemCount = 6, modifier = Modifier.padding(top = 12.dp))
        else -> LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(rows) { rowIdx, cells ->
                Surface(
                    color = if (rowIdx == 0) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = MaterialTheme.shapes.small,
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        cells.forEach { cell ->
                            Text(
                                text = cell,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = if (rowIdx == 0) FontWeight.Bold else FontWeight.Normal,
                                    fontFamily = FontFamily.Monospace,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZipPreview(filePath: String?, uriString: String?) {
    val context = LocalContext.current
    var entries by remember { mutableStateOf<List<ZipTreeEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<ProtocolError?>(null) }

    LaunchedEffect(filePath, uriString) {
        loading = true
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val list = mutableListOf<ZipTreeEntry>()
                ZipInputStream(openInputStream(context, filePath, uriString).buffered(64 * 1024)).use { zis ->
                    while (true) {
                        val entry = zis.nextEntry ?: break
                        list += ZipTreeEntry(
                            name = entry.name,
                            isDirectory = entry.isDirectory,
                            size = entry.size.coerceAtLeast(0L),
                            compressedSize = entry.compressedSize.coerceAtLeast(0L),
                        )
                        zis.closeEntry()
                    }
                }
                list
            }
        }
        loading = false
        result.onSuccess { entries = it }
            .onFailure { t ->
                error = ProtocolError.ProtocolFailure(
                    userMessage = "Could not inspect ZIP archive.",
                    nextStep = "Confirm the archive is a valid ZIP file.",
                    technicalDetail = t.message,
                )
            }
    }

    when {
        error != null -> PortlyErrorCard(error = error!!, modifier = Modifier.padding(16.dp))
        loading -> SkeletonList(itemCount = 6, modifier = Modifier.padding(top = 12.dp))
        entries.isEmpty() -> EmptyStateView(
            icon = Icons.Outlined.Archive,
            title = "Empty ZIP archive",
            subtitle = "This archive contains no entries.",
        )
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(entries) { entry ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(entry.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = if (entry.isDirectory) "Directory" else Formatters.formatBytes(entry.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExternalDocFallback(
    fileName: String,
    mimeType: String,
    filePath: String?,
    uriString: String?,
) {
    val context = LocalContext.current
    var launchError by remember { mutableStateOf<ProtocolError?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(fileName, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "MIME: $mimeType • Opens with an external viewer app on your device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                val ok = openExternally(context, filePath, uriString, mimeType)
                if (!ok) {
                    launchError = ProtocolError.UnsupportedOperation(
                        userMessage = "No installed app can open \"$fileName\".",
                        nextStep = "Install a compatible document viewer from your app store or transfer the file to a desktop.",
                        technicalDetail = "MIME: $mimeType",
                    )
                }
            },
        ) {
            Text("Open with external app")
        }
        launchError?.let { err ->
            Spacer(modifier = Modifier.height(16.dp))
            PortlyErrorCard(error = err)
        }
    }
}

private fun openInputStream(context: Context, filePath: String?, uriString: String?): java.io.InputStream {
    return when {
        !uriString.isNullOrBlank() ->
            context.contentResolver.openInputStream(Uri.parse(uriString))
                ?: error("Unable to open stream for URI")
        !filePath.isNullOrBlank() -> File(filePath).inputStream()
        else -> error("No file path or URI provided")
    }
}

private fun openExternally(
    context: Context,
    filePath: String?,
    uriString: String?,
    mimeType: String,
): Boolean {
    return runCatching {
        val contentUri = when {
            !uriString.isNullOrBlank() -> Uri.parse(uriString)
            !filePath.isNullOrBlank() -> FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                File(filePath),
            )
            else -> return false
        }
        val resolvedMime = mimeType.takeIf { it != "application/octet-stream" }
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                filePath?.substringAfterLast('.', "")?.lowercase().orEmpty(),
            )
            ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, resolvedMime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Open with"))
        true
    }.recoverCatching { err ->
        if (err is ActivityNotFoundException) false else throw err
    }.getOrDefault(false)
}
