package com.portly.feature.filemanager

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.portly.core.common.Formatters
import com.portly.core.model.ConnectionProfile
import com.portly.core.model.LocalFile
import com.portly.core.model.ProtocolType
import com.portly.core.model.RemoteFile
import com.portly.core.ui.components.BreadcrumbBar
import com.portly.core.ui.components.EmptyStateView
import com.portly.core.ui.components.PlainFtpWarningBanner
import com.portly.core.ui.components.PortlyConfirmDialog
import com.portly.core.ui.components.PortlyErrorCard
import com.portly.core.ui.components.ProtocolBadge
import com.portly.core.ui.components.SkeletonList
import com.portly.core.ui.components.iconForFile

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RemoteBrowserPane(
    state: RemoteBrowserUiState,
    savedProfiles: List<ConnectionProfile>,
    localSelectedFiles: List<LocalFile>,
    localCurrentDirPath: String,
    onConnectProfile: (String) -> Unit,
    onDisconnect: () -> Unit,
    onRefresh: () -> Unit,
    onNavigateTo: (String) -> Unit,
    onNavigateUp: () -> Unit,
    onToggleSelect: (RemoteFile) -> Unit,
    onMkdir: (String) -> Unit,
    onRename: (RemoteFile, String) -> Unit,
    onDeleteSelected: () -> Unit,
    onChmod: (RemoteFile, Int) -> Unit,
    onDownloadSelectedToLocal: (String) -> Unit,
    onUploadLocalSelected: (List<LocalFile>) -> Unit,
    onTrustUntrustedCert: (com.portly.core.model.ProtocolError.CertificateUntrusted) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showMkdir by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<RemoteFile?>(null) }
    var chmodTarget by remember { mutableStateOf<RemoteFile?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        if (!state.isConnected) {
            Text(
                text = "Remote Connections",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            state.error?.let { err ->
                PortlyErrorCard(
                    error = err,
                    onRetry = state.activeProfile?.let { prof -> { onConnectProfile(prof.id) } },
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                if (err is com.portly.core.model.ProtocolError.CertificateUntrusted) {
                    Button(
                        onClick = { onTrustUntrustedCert(err) },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    ) {
                        Text("Trust Certificate (SHA-256 TOFU) & Reconnect")
                    }
                }
            }
            if (state.isLoading) {
                SkeletonList(itemCount = 4)
            } else if (savedProfiles.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Outlined.CloudQueue,
                    title = "No saved connections",
                    subtitle = "Create an FTP, FTPS, SFTP, WebDAV, or SMB connection in the Connect tab first.",
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(savedProfiles, key = { it.id }) { profile ->
                        Card(
                            onClick = { onConnectProfile(profile.id) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(profile.name, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "${profile.host}:${profile.port}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                ProtocolBadge(protocol = profile.protocol)
                            }
                        }
                    }
                }
            }
        } else {
            val profile = state.activeProfile!!
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    IconButton(onClick = onNavigateUp, enabled = state.currentPath != "/") {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Up")
                    }
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row {
                    if (localSelectedFiles.isNotEmpty()) {
                        IconButton(onClick = { onUploadLocalSelected(localSelectedFiles) }) {
                            Icon(Icons.Outlined.CloudUpload, contentDescription = "Upload selected local files")
                        }
                    }
                    if (state.selectedPaths.isNotEmpty()) {
                        IconButton(onClick = { onDownloadSelectedToLocal(localCurrentDirPath) }) {
                            Icon(Icons.Outlined.CloudDownload, contentDescription = "Download to local folder")
                        }
                        if (state.selectedPaths.size == 1) {
                            val selected = state.files.firstOrNull { it.path in state.selectedPaths }
                            if (selected != null) {
                                IconButton(onClick = { renameTarget = selected }) {
                                    Icon(Icons.Outlined.DriveFileRenameOutline, contentDescription = "Rename")
                                }
                                if (profile.protocol.supportsChmod) {
                                    IconButton(onClick = { chmodTarget = selected }) {
                                        Icon(Icons.Outlined.Lock, contentDescription = "Permissions")
                                    }
                                }
                            }
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete remote")
                        }
                    }
                    IconButton(onClick = { showMkdir = true }) {
                        Icon(Icons.Outlined.CreateNewFolder, contentDescription = "New remote folder")
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh remote")
                    }
                    IconButton(onClick = onDisconnect) {
                        Icon(Icons.Outlined.CloudOff, contentDescription = "Disconnect")
                    }
                }
            }

            if (profile.protocol == ProtocolType.FTP) {
                PlainFtpWarningBanner(modifier = Modifier.padding(bottom = 6.dp))
            }

            BreadcrumbBar(
                segments = buildRemoteBreadcrumbs(state.currentPath),
                onNavigate = onNavigateTo,
            )

            state.error?.let { err ->
                PortlyErrorCard(error = err, onRetry = onRefresh, modifier = Modifier.padding(vertical = 8.dp))
            }

            if (state.isLoading) {
                SkeletonList(itemCount = 6)
            } else if (state.files.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Outlined.FolderOpen,
                    title = "Remote folder is empty",
                    subtitle = "Upload files here or create a folder.",
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.files, key = { it.path }) { item ->
                        val selected = item.path in state.selectedPaths
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (state.selectedPaths.isNotEmpty()) {
                                            onToggleSelect(item)
                                        } else if (item.isDirectory) {
                                            onNavigateTo(item.path)
                                        } else {
                                            onToggleSelect(item)
                                        }
                                    },
                                    onLongClick = { onToggleSelect(item) },
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                },
                            ),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 52.dp)
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    imageVector = if (selected) Icons.Outlined.CheckBox else iconForFile(item.name, item.isDirectory, item.mimeType),
                                    contentDescription = null,
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        item.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        buildString {
                                            append(if (item.isDirectory) "Folder" else Formatters.formatBytes(item.sizeBytes))
                                            item.permissions?.let { append(" • ${it.toPosixString()}") }
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(64.dp)) }
                }
            }
        }
    }

    if (showMkdir) {
        RemoteTextDialog(
            title = "New Remote Folder",
            initial = "",
            confirmLabel = "Create",
            onConfirm = {
                onMkdir(it)
                showMkdir = false
            },
            onDismiss = { showMkdir = false },
        )
    }

    renameTarget?.let { target ->
        RemoteTextDialog(
            title = "Rename Remote Item",
            initial = target.name,
            confirmLabel = "Rename",
            onConfirm = {
                onRename(target, it)
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }

    chmodTarget?.let { target ->
        RemoteTextDialog(
            title = "Set POSIX Mode (Octal)",
            initial = "644",
            confirmLabel = "Apply",
            onConfirm = { text ->
                val mode = text.toIntOrNull(8)
                if (mode != null) onChmod(target, mode)
                chmodTarget = null
            },
            onDismiss = { chmodTarget = null },
        )
    }

    if (showDeleteConfirm) {
        PortlyConfirmDialog(
            title = "Delete ${state.selectedPaths.size} remote item(s)?",
            consequenceText = "This permanently deletes the selected files or folders from the remote server.",
            confirmLabel = "Delete",
            onConfirm = {
                onDeleteSelected()
                showDeleteConfirm = false
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

@Composable
private fun RemoteTextDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
            )
        },
        confirmButton = {
            Button(enabled = value.isNotBlank(), onClick = { onConfirm(value.trim()) }) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun buildRemoteBreadcrumbs(path: String): List<Pair<String, String>> {
    if (path == "/" || path.isBlank()) return listOf("/" to "/")
    val parts = path.trimStart('/').split('/').filter { it.isNotEmpty() }
    val list = mutableListOf("/" to "/")
    var acc = ""
    for (part in parts) {
        acc = "$acc/$part"
        list += part to acc
    }
    return list
}
