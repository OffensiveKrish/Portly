package com.portly.feature.archive

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.portly.core.common.Formatters
import com.portly.core.ui.components.EmptyStateView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(
    archivePath: String?,
    sourcePaths: List<String> = emptyList(),
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ArchiveViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showExtractDialog by remember { mutableStateOf(false) }
    var extractSelectedOnly by remember { mutableStateOf(false) }
    var extractTargetPath by remember { mutableStateOf("") }

    var showCreateDialog by remember { mutableStateOf(false) }
    var createOutputPath by remember { mutableStateOf("") }

    val openZipPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.openArchiveUri(uri)
        }
    }

    LaunchedEffect(archivePath, sourcePaths) {
        viewModel.initialize(archivePath, sourcePaths)
        if (archivePath == null && sourcePaths.isNotEmpty()) {
            createOutputPath = viewModel.defaultCreateZipPath()
            showCreateDialog = true
        }
    }

    LaunchedEffect(state.statusMessage, state.errorMessage) {
        val msg = state.errorMessage ?: state.statusMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.displayTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { openZipPicker.launch(arrayOf("application/zip", "application/x-zip-compressed")) }) {
                        Icon(Icons.Outlined.FolderOpen, contentDescription = "Open ZIP")
                    }
                    if (state.visibleEntries.isNotEmpty()) {
                        IconButton(onClick = viewModel::selectAllVisible) {
                            Icon(Icons.Outlined.SelectAll, contentDescription = "Select all entries")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            if (state.isLoading) {
                val p = state.progress
                if (p != null && p.totalEntries > 0) {
                    LinearProgressIndicator(
                        progress = { p.processedEntries.toFloat() / p.totalEntries.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "${p.processedEntries}/${p.totalEntries}: ${p.currentEntryName}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(8.dp))
            }

            if (state.pendingCreateSources.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Ready to compress ${state.pendingCreateSources.size} item(s)",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = state.pendingCreateSources.joinToString(", ") { it.substringAfterLast('/') },
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                createOutputPath = viewModel.defaultCreateZipPath()
                                showCreateDialog = true
                            },
                        ) {
                            Icon(Icons.Outlined.CreateNewFolder, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Create ZIP")
                        }
                    }
                }
            }

            if (state.archivePath == null && state.archiveUri == null) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    EmptyStateView(
                        icon = Icons.Outlined.Archive,
                        title = "ZIP Archive Manager",
                        subtitle = "Open an existing .zip file or select files in File Manager to create a new ZIP archive.",
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { openZipPicker.launch(arrayOf("application/zip", "application/x-zip-compressed")) },
                    ) {
                        Text("Open ZIP File")
                    }
                }
            } else {
                OutlinedTextField(
                    value = state.filterQuery,
                    onValueChange = viewModel::setFilterQuery,
                    label = { Text("Filter entries") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            extractSelectedOnly = false
                            extractTargetPath = viewModel.defaultExtractDirectory()
                            showExtractDialog = true
                        },
                        enabled = !state.isLoading && state.entries.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.Unarchive, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Extract All (${state.entries.size})")
                    }
                    FilledTonalButton(
                        onClick = {
                            extractSelectedOnly = true
                            extractTargetPath = viewModel.defaultExtractDirectory()
                            showExtractDialog = true
                        },
                        enabled = !state.isLoading && state.selectedEntries.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Extract (${state.selectedEntries.size})")
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(state.visibleEntries, key = { it.entryPath }) { entry ->
                        val checked = entry.entryPath in state.selectedEntries
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.toggleSelectEntry(entry.entryPath) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { viewModel.toggleSelectEntry(entry.entryPath) },
                            )
                            Icon(
                                imageVector = if (entry.isDirectory) Icons.Outlined.Folder else Icons.Outlined.InsertDriveFile,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.entryPath,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (!entry.isDirectory) {
                                    Text(
                                        text = "${Formatters.formatBytes(entry.uncompressedSizeBytes)} " +
                                            "(compressed ${Formatters.formatBytes(entry.compressedSizeBytes)}, ${entry.compressionPercent}%)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showExtractDialog) {
        AlertDialog(
            onDismissRequest = { showExtractDialog = false },
            title = { Text(if (extractSelectedOnly) "Extract Selected Entries" else "Extract All Entries") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Destination folder on device (Zip-Slip path traversal protection active):",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = extractTargetPath,
                        onValueChange = { extractTargetPath = it },
                        label = { Text("Destination folder") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExtractDialog = false
                        viewModel.extract(extractTargetPath.trim(), extractSelectedOnly)
                    },
                    enabled = extractTargetPath.isNotBlank(),
                ) {
                    Text("Extract")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showExtractDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create ZIP Archive") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Compressing ${state.pendingCreateSources.size} item(s) with 64KB streaming I/O:",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = createOutputPath,
                        onValueChange = { createOutputPath = it },
                        label = { Text("Output .zip path") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCreateDialog = false
                        viewModel.createZip(createOutputPath.trim())
                    },
                    enabled = createOutputPath.isNotBlank(),
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            },
        )
    }
}
