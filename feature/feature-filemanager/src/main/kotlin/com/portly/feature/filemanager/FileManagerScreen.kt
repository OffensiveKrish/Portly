package com.portly.feature.filemanager

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.portly.core.common.Formatters
import com.portly.core.model.LocalFile
import com.portly.core.model.SortDirection
import com.portly.core.model.SortField
import com.portly.core.model.StorageVolumeInfo
import com.portly.core.ui.components.BreadcrumbBar
import com.portly.core.ui.components.EmptyStateView
import com.portly.core.ui.components.PortlyConfirmDialog
import com.portly.core.ui.components.PortlyErrorCard
import com.portly.core.ui.components.SkeletonList
import com.portly.core.ui.components.colorForFile
import com.portly.core.ui.components.iconForFile
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(
    initialRemoteProfileId: String? = null,
    onOpenPreview: (filePath: String?, uriString: String?) -> Unit = { _, _ -> },
    onOpenArchive: (archivePath: String?, sourcePaths: List<String>) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    viewModel: FileManagerViewModel = hiltViewModel(),
    remoteViewModel: RemoteBrowserViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val remoteState by remoteViewModel.uiState.collectAsStateWithLifecycle()
    val savedProfiles by remoteViewModel.savedProfiles.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedPaneTab by remember { mutableIntStateOf(if (initialRemoteProfileId != null) 1 else 0) }

    LaunchedEffect(initialRemoteProfileId) {
        if (!initialRemoteProfileId.isNullOrBlank()) {
            selectedPaneTab = 1
            remoteViewModel.connectProfile(initialRemoteProfileId)
        }
    }

    LaunchedEffect(remoteState.statusMessage) {
        val msg = remoteState.statusMessage ?: return@LaunchedEffect
        remoteViewModel.clearStatusMessage()
        snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
    }

    var showSortMenu by remember { mutableStateOf(false) }
    var showStorageDialog by remember { mutableStateOf(false) }
    var showMkdirDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<LocalFile?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val safLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            viewModel.onSafTreeSelected(uri)
        }
    }

    val allFilesAccessLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        viewModel.onPermissionGranted()
    }

    BackHandler(enabled = state.selectedPaths.isNotEmpty() || state.isSearchMode || state.currentPath != state.rootPath) {
        when {
            state.selectedPaths.isNotEmpty() -> viewModel.clearSelection()
            state.isSearchMode -> viewModel.setSearchActive(false)
            else -> viewModel.navigateUp()
        }
    }

    LaunchedEffect(state.snackbarEventMessage, state.pendingTrashUndo) {
        val msg = state.snackbarEventMessage ?: return@LaunchedEffect
        val hasUndo = state.pendingTrashUndo != null
        viewModel.clearSnackbarMessage()
        val result = snackbarHostState.showSnackbar(
            message = msg,
            actionLabel = if (hasUndo) "Undo" else null,
            duration = SnackbarDuration.Short,
        )
        if (hasUndo) {
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoDelete()
            } else {
                viewModel.finalizePendingDelete()
            }
        }
    }

    val isCurrentFav = remember(favorites, state.currentPath) {
        favorites.any { it.path == state.currentPath }
    }

    val selectionCount = state.selectedPaths.size

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (selectionCount > 0) {
                TopAppBar(
                    title = { Text("$selectionCount selected") },
                    navigationIcon = {
                        IconButton(onClick = viewModel::clearSelection) {
                            Icon(Icons.Outlined.Close, contentDescription = "Clear selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = viewModel::selectAll) {
                            Icon(Icons.Outlined.SelectAll, contentDescription = "Select all")
                        }
                        IconButton(onClick = viewModel::copySelected) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy")
                        }
                        IconButton(onClick = viewModel::cutSelected) {
                            Icon(Icons.Outlined.ContentCut, contentDescription = "Cut")
                        }
                        if (selectionCount == 1) {
                            val selectedItem = state.files.firstOrNull { it.path in state.selectedPaths }
                            if (selectedItem != null) {
                                if (!selectedItem.isDirectory && selectedItem.name.lowercase().endsWith(".zip")) {
                                    IconButton(
                                        onClick = {
                                            viewModel.clearSelection()
                                            onOpenArchive(selectedItem.path, emptyList())
                                        },
                                    ) {
                                        Icon(Icons.Outlined.FolderZip, contentDescription = "Open in Archive Manager")
                                    }
                                }
                                IconButton(onClick = { renameTarget = selectedItem }) {
                                    Icon(Icons.Outlined.DriveFileRenameOutline, contentDescription = "Rename")
                                }
                                IconButton(onClick = { viewModel.openProperties(selectedItem) }) {
                                    Icon(Icons.Outlined.Info, contentDescription = "Properties")
                                }
                            }
                        }
                        IconButton(
                            onClick = {
                                val paths = state.files
                                    .filter { it.path in state.selectedPaths }
                                    .mapNotNull { it.path.takeIf { p -> !p.startsWith("content://") } }
                                viewModel.clearSelection()
                                onOpenArchive(null, paths)
                            },
                        ) {
                            Icon(Icons.Outlined.Archive, contentDescription = "Compress to ZIP")
                        }
                        IconButton(
                            onClick = {
                                val selectedItems = state.files.filter { it.path in state.selectedPaths }
                                shareLocalFiles(context, selectedItems)
                            },
                        ) {
                            Icon(Icons.Outlined.Share, contentDescription = "Share")
                        }
                        if (remoteState.isConnected) {
                            IconButton(
                                onClick = {
                                    val selectedItems = state.files.filter { it.path in state.selectedPaths }
                                    remoteViewModel.queueUploadFrom(selectedItems)
                                    viewModel.clearSelection()
                                },
                            ) {
                                Icon(Icons.Outlined.CloudUpload, contentDescription = "Upload to remote")
                            }
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete")
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = {
                        val currentName = File(state.currentPath).name
                        val displayTitle = if (currentName == "0" || currentName.isEmpty() || state.currentPath == state.rootPath || state.currentPath.trimEnd('/').endsWith("/0")) {
                            "Internal Storage"
                        } else {
                            currentName
                        }
                        Text(
                            text = displayTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    actions = {
                        IconButton(onClick = { viewModel.setSearchActive(!state.isSearchMode) }) {
                            Icon(Icons.Outlined.Search, contentDescription = "Search")
                        }
                        IconButton(onClick = viewModel::toggleGridView) {
                            Icon(
                                imageVector = if (state.isGridView) Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.GridView,
                                contentDescription = "Toggle grid or list",
                            )
                        }
                        IconButton(onClick = { viewModel.toggleCurrentFolderFavorite(isCurrentFav) }) {
                            Icon(
                                imageVector = if (isCurrentFav) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                                contentDescription = "Toggle favorite folder",
                            )
                        }
                        IconButton(onClick = { showStorageDialog = true }) {
                            Icon(Icons.Outlined.Storage, contentDescription = "Storage volumes")
                        }
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = "Sort and view options")
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false },
                            ) {
                                SortField.entries.forEach { field ->
                                    DropdownMenuItem(
                                        text = {
                                            val active = state.sortField == field
                                            val arrow = if (active) {
                                                if (state.sortDirection == SortDirection.ASCENDING) " ↑" else " ↓"
                                            } else {
                                                ""
                                            }
                                            Text("Sort by ${field.name.lowercase().replaceFirstChar { it.uppercase() }}$arrow")
                                        },
                                        onClick = {
                                            val nextDir = if (state.sortField == field && state.sortDirection == SortDirection.ASCENDING) {
                                                SortDirection.DESCENDING
                                            } else {
                                                SortDirection.ASCENDING
                                            }
                                            viewModel.updateSort(field, nextDir)
                                            showSortMenu = false
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = {
                                        Text(if (state.showHiddenFiles) "Hide hidden files" else "Show hidden files")
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (state.showHiddenFiles) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                            contentDescription = null,
                                        )
                                    },
                                    onClick = {
                                        viewModel.toggleHiddenFiles()
                                        showSortMenu = false
                                    },
                                )
                            }
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.End) {
                if (state.clipboard != null) {
                    FloatingActionButton(onClick = viewModel::pasteClipboard) {
                        Icon(Icons.Outlined.ContentPaste, contentDescription = "Paste here")
                    }
                }
                FloatingActionButton(onClick = { showMkdirDialog = true }) {
                    Icon(Icons.Outlined.CreateNewFolder, contentDescription = "New folder")
                }
            }
        },
    ) { padding ->
        val localSelectedItems = remember(state.files, state.selectedPaths) {
            state.files.filter { it.path in state.selectedPaths }
        }
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val isWideScreen = maxWidth >= 700.dp

            val localPaneContent: @Composable (Modifier) -> Unit = { paneMod ->
                Column(modifier = paneMod.fillMaxSize()) {
                    if (!state.hasAllFilesAccess && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "Grant All Files Access to browse shared device folders, or pick an SD/USB folder via SAF.",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(
                                    onClick = {
                                        val intent = Intent(
                                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                            Uri.parse("package:${context.packageName}"),
                                        )
                                        allFilesAccessLauncher.launch(intent)
                                    },
                                ) {
                                    Text("Grant")
                                }
                            }
                        }
                    }

                    if (state.isSearchMode) {
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = viewModel::updateSearchQuery,
                            label = { Text("Search recursively in current folder") },
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = { viewModel.setSearchActive(false) }) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Close search")
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                    }

                    if (state.activeTreeUri == null) {
                        val breadcrumbs = remember(state.currentPath, state.rootPath) {
                            buildBreadcrumbs(state.rootPath, state.currentPath)
                        }
                        BreadcrumbBar(
                            segments = breadcrumbs,
                            onNavigate = { target -> viewModel.navigateToPath(target, null) },
                        )
                    }

                    if (favorites.isNotEmpty() || state.recentFiles.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            favorites.forEach { fav ->
                                AssistChip(
                                    onClick = { viewModel.navigateToPath(fav.path, null) },
                                    label = { Text(fav.label) },
                                    leadingIcon = { Icon(Icons.Outlined.Star, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                )
                            }
                            state.recentFiles.take(5).forEach { recent ->
                                FilterChip(
                                    selected = false,
                                    onClick = { onOpenPreview(recent.path, recent.uriString) },
                                    label = { Text(recent.name) },
                                )
                            }
                        }
                    }

                    when {
                        state.error != null -> PortlyErrorCard(
                            error = state.error!!,
                            onRetry = viewModel::refresh,
                            modifier = Modifier.padding(16.dp),
                        )
                        state.isLoading -> SkeletonList(itemCount = 8, modifier = Modifier.padding(top = 8.dp))
                        state.files.isEmpty() -> EmptyStateView(
                            icon = Icons.Outlined.FolderOpen,
                            title = if (state.isSearchMode) "No matching files" else "Folder is empty",
                            subtitle = if (state.isSearchMode) "Try another keyword." else "Create a folder or copy files here.",
                        )
                        state.isGridView -> LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 148.dp),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(state.files, key = { it.path }) { item ->
                                LocalFileGridCard(
                                    file = item,
                                    selected = item.path in state.selectedPaths,
                                    onClick = {
                                        if (state.selectedPaths.isNotEmpty()) {
                                            viewModel.toggleSelection(item)
                                        } else if (item.isDirectory) {
                                            viewModel.navigateToPath(item.path, item.uriString)
                                        } else {
                                            viewModel.recordRecentFile(item)
                                            onOpenPreview(item.path, item.uriString)
                                        }
                                    },
                                    onLongClick = { viewModel.toggleSelection(item) },
                                )
                            }
                        }
                        else -> LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(state.files, key = { it.path }) { item ->
                                LocalFileListRow(
                                    file = item,
                                    selected = item.path in state.selectedPaths,
                                    onClick = {
                                        if (state.selectedPaths.isNotEmpty()) {
                                            viewModel.toggleSelection(item)
                                        } else if (item.isDirectory) {
                                            viewModel.navigateToPath(item.path, item.uriString)
                                        } else {
                                            viewModel.recordRecentFile(item)
                                            onOpenPreview(item.path, item.uriString)
                                        }
                                    },
                                    onLongClick = { viewModel.toggleSelection(item) },
                                    onMoreInfo = { viewModel.openProperties(item) },
                                    modifier = Modifier.dragAndDropSource { _ ->
                                        DragAndDropTransferData(ClipData.newPlainText("portly_local_path", item.path))
                                    },
                                )
                            }
                            item { Spacer(modifier = Modifier.height(80.dp)) }
                        }
                    }
                }
            }

            val remoteDropTarget = remember(state.files, remoteState.isConnected, remoteState.currentPath) {
                object : DragAndDropTarget {
                    override fun onDrop(event: DragAndDropEvent): Boolean {
                        val dragEvent = event.toAndroidDragEvent()
                        val clip = dragEvent.clipData ?: return false
                        if (clip.itemCount == 0) return false
                        val droppedPath = clip.getItemAt(0).text?.toString() ?: return false
                        val matched = state.files.firstOrNull { it.path == droppedPath } ?: return false
                        remoteViewModel.queueUploadFrom(listOf(matched))
                        return true
                    }
                }
            }

            val remotePaneContent: @Composable (Modifier) -> Unit = { paneMod ->
                RemoteBrowserPane(
                    state = remoteState,
                    savedProfiles = savedProfiles,
                    localSelectedFiles = localSelectedItems,
                    localCurrentDirPath = state.currentPath,
                    onConnectProfile = remoteViewModel::connectProfile,
                    onDisconnect = remoteViewModel::disconnect,
                    onRefresh = remoteViewModel::refresh,
                    onNavigateTo = remoteViewModel::navigateTo,
                    onNavigateUp = { remoteViewModel.navigateUp() },
                    onToggleSelect = remoteViewModel::toggleSelection,
                    onMkdir = remoteViewModel::mkdir,
                    onRename = remoteViewModel::rename,
                    onDeleteSelected = remoteViewModel::deleteSelected,
                    onChmod = remoteViewModel::chmod,
                    onDownloadSelectedToLocal = remoteViewModel::queueDownloadTo,
                    onUploadLocalSelected = remoteViewModel::queueUploadFrom,
                    onTrustUntrustedCert = remoteViewModel::approveUntrustedCertificate,
                    modifier = paneMod.dragAndDropTarget(
                        shouldStartDragAndDrop = { event ->
                            event.mimeTypes().contains("text/plain")
                        },
                        target = remoteDropTarget,
                    ),
                )
            }

            if (isWideScreen) {
                Row(modifier = Modifier.fillMaxSize()) {
                    localPaneContent(Modifier.weight(1f))
                    VerticalDivider(modifier = Modifier.fillMaxHeight())
                    remotePaneContent(Modifier.weight(1f))
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    PrimaryTabRow(selectedTabIndex = selectedPaneTab) {
                        Tab(
                            selected = selectedPaneTab == 0,
                            onClick = { selectedPaneTab = 0 },
                            text = { Text("Local") },
                        )
                        Tab(
                            selected = selectedPaneTab == 1,
                            onClick = { selectedPaneTab = 1 },
                            text = {
                                Text(
                                    if (remoteState.isConnected) {
                                        "Remote (${remoteState.activeProfile?.protocol?.displayName ?: ""})"
                                    } else {
                                        "Remote"
                                    },
                                )
                            },
                        )
                    }
                    HorizontalDivider()
                    if (selectedPaneTab == 0) {
                        localPaneContent(Modifier.fillMaxSize())
                    } else {
                        remotePaneContent(Modifier.fillMaxSize())
                    }
                }
            }
        }
    }

    if (showStorageDialog) {
        StorageOverviewDialog(
            volumes = state.volumes,
            onSelectVolume = { vol ->
                viewModel.navigateToVolume(vol)
                showStorageDialog = false
            },
            onMountSaf = {
                safLauncher.launch(null)
                showStorageDialog = false
            },
            onDismiss = { showStorageDialog = false },
        )
    }

    if (showMkdirDialog) {
        NameInputDialog(
            title = "New Folder",
            initialName = "",
            confirmLabel = "Create",
            onConfirm = { name ->
                viewModel.createFolder(name)
                showMkdirDialog = false
            },
            onDismiss = { showMkdirDialog = false },
        )
    }

    renameTarget?.let { target ->
        NameInputDialog(
            title = "Rename",
            initialName = target.name,
            confirmLabel = "Rename",
            onConfirm = { newName ->
                viewModel.renameFile(target, newName)
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }

    if (showDeleteConfirm) {
        PortlyConfirmDialog(
            title = "Delete $selectionCount item(s)?",
            consequenceText = "Selected files or folders will be deleted. You can undo immediately from the snackbar.",
            confirmLabel = "Delete",
            onConfirm = {
                viewModel.deleteSelected()
                showDeleteConfirm = false
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }

    state.propertiesFile?.let { file ->
        PropertiesDialog(
            file = file,
            checksum = state.propertiesChecksum,
            isComputingChecksum = state.isComputingChecksum,
            onDismiss = viewModel::closeProperties,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LocalFileListRow(
    file: LocalFile,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoreInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val fileColor = if (selected) MaterialTheme.colorScheme.primary else colorForFile(file.name, file.isDirectory, file.mimeType)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(fileColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (selected) Icons.Outlined.CheckBox else iconForFile(file.name, file.isDirectory, file.mimeType),
                    contentDescription = null,
                    tint = fileColor,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(if (file.isDirectory) "${file.childCount ?: 0} items" else Formatters.formatBytes(file.sizeBytes))
                        append(" • ")
                        append(Formatters.formatTimestamp(file.lastModifiedMillis))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onMoreInfo) {
                Icon(Icons.Outlined.Info, contentDescription = "Properties for ${file.name}")
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LocalFileGridCard(
    file: LocalFile,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val fileColor = if (selected) MaterialTheme.colorScheme.primary else colorForFile(file.name, file.isDirectory, file.mimeType)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(fileColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (selected) Icons.Outlined.CheckBox else iconForFile(file.name, file.isDirectory, file.mimeType),
                    contentDescription = null,
                    tint = fileColor,
                    modifier = Modifier.size(24.dp),
                )
            }
            Text(
                text = file.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (file.isDirectory) "Folder" else Formatters.formatBytes(file.sizeBytes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StorageOverviewDialog(
    volumes: List<StorageVolumeInfo>,
    onSelectVolume: (StorageVolumeInfo) -> Unit,
    onMountSaf: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Storage Volumes") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                volumes.forEach { vol ->
                    Card(
                        onClick = { onSelectVolume(vol) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = if (vol.isRemovable) Icons.Outlined.SdStorage else Icons.Outlined.Storage,
                                    contentDescription = null,
                                )
                                Text(vol.label, style = MaterialTheme.typography.titleSmall)
                            }
                            if (vol.totalBytes > 0L) {
                                LinearProgressIndicator(
                                    progress = { vol.usageFraction },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Text(
                                    text = "${Formatters.formatBytes(vol.usedBytes)} used of ${Formatters.formatBytes(vol.totalBytes)} (${Formatters.formatBytes(vol.availableBytes)} free)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                Text(
                                    text = "SAF mounted external folder",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                OutlinedButton(
                    onClick = onMountSaf,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Mount SD Card / USB Folder (SAF)")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun NameInputDialog(
    title: String,
    initialName: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Name") },
                singleLine = true,
            )
        },
        confirmButton = {
            Button(
                enabled = text.isNotBlank(),
                onClick = { onConfirm(text.trim()) },
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun PropertiesDialog(
    file: LocalFile,
    checksum: String?,
    isComputingChecksum: Boolean,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Properties") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Name: ${file.name}", style = MaterialTheme.typography.bodyMedium)
                Text("Type: ${file.mimeType}", style = MaterialTheme.typography.bodySmall)
                Text(
                    text = if (file.isDirectory) "Items: ${file.childCount ?: 0}" else "Size: ${Formatters.formatBytes(file.sizeBytes)} (${file.sizeBytes} bytes)",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text("Modified: ${Formatters.formatTimestamp(file.lastModifiedMillis)}", style = MaterialTheme.typography.bodySmall)
                Text("Permissions: ${file.permissions.toPosixString()}", style = MaterialTheme.typography.bodySmall)
                Text("Path: ${file.path}", style = MaterialTheme.typography.bodySmall)
                if (!file.isDirectory) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("SHA-256:", style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = when {
                            isComputingChecksum -> "Computing streaming hash…"
                            checksum != null -> checksum
                            else -> "Unavailable"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

private fun buildBreadcrumbs(rootPath: String, currentPath: String): List<Pair<String, String>> {
    val rawRoot = File(rootPath).name
    val rootName = if (rawRoot == "0" || rawRoot.isEmpty() || rootPath.trimEnd('/').endsWith("/0")) {
        "Internal Storage"
    } else {
        rawRoot
    }
    if (currentPath == rootPath) return listOf(rootName to rootPath)
    val relative = currentPath.removePrefix(rootPath).trimStart(File.separatorChar)
    if (relative.isEmpty()) return listOf(rootName to rootPath)
    val result = mutableListOf(rootName to rootPath)
    var acc = rootPath
    for (part in relative.split(File.separatorChar)) {
        if (part.isEmpty()) continue
        acc = File(acc, part).absolutePath
        result += part to acc
    }
    return result
}

private fun shareLocalFiles(context: Context, items: List<LocalFile>) {
    val uris = items.filter { !it.isDirectory }.mapNotNull { item ->
        runCatching {
            if (!item.uriString.isNullOrBlank()) {
                Uri.parse(item.uriString)
            } else {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(item.path))
            }
        }.getOrNull()
    }
    if (uris.isEmpty()) return

    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
            type = items.first().mimeType
            putExtra(Intent.EXTRA_STREAM, uris.first())
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
    }
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, "Share via"))
}
