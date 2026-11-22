package com.portly.feature.filemanager

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.portly.core.database.entity.FavoritePathEntity
import com.portly.core.datastore.PortlySettingsDataStore
import com.portly.core.model.LocalFile
import com.portly.core.model.ProtocolError
import com.portly.core.model.SortDirection
import com.portly.core.model.SortField
import com.portly.core.model.StorageVolumeInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

enum class ClipboardMode {
    COPY,
    CUT,
}

data class FileClipboard(
    val items: List<LocalFile>,
    val mode: ClipboardMode,
)

data class FileManagerUiState(
    val currentPath: String = "",
    val activeTreeUri: String? = null,
    val rootPath: String = "",
    val files: List<LocalFile> = emptyList(),
    val volumes: List<StorageVolumeInfo> = emptyList(),
    val selectedPaths: Set<String> = emptySet(),
    val isGridView: Boolean = false,
    val showHiddenFiles: Boolean = false,
    val sortField: SortField = SortField.NAME,
    val sortDirection: SortDirection = SortDirection.ASCENDING,
    val searchQuery: String = "",
    val isSearchMode: Boolean = false,
    val isLoading: Boolean = true,
    val error: ProtocolError? = null,
    val clipboard: FileClipboard? = null,
    val recentFiles: List<LocalFile> = emptyList(),
    val hasAllFilesAccess: Boolean = true,
    val propertiesFile: LocalFile? = null,
    val propertiesChecksum: String? = null,
    val isComputingChecksum: Boolean = false,
    val pendingTrashUndo: List<StagedTrashItem>? = null,
    val snackbarEventMessage: String? = null,
)

@HiltViewModel
class FileManagerViewModel @Inject constructor(
    private val repository: LocalFileRepository,
    private val settingsDataStore: PortlySettingsDataStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        FileManagerUiState(
            currentPath = repository.defaultRootDirectory().absolutePath,
            rootPath = repository.defaultRootDirectory().absolutePath,
            hasAllFilesAccess = repository.hasAllFilesAccess(),
        ),
    )
    val uiState: StateFlow<FileManagerUiState> = _uiState.asStateFlow()

    val favorites: StateFlow<List<FavoritePathEntity>> = repository.observeFavorites().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    private var listJob: Job? = null

    init {
        viewModelScope.launch {
            settingsDataStore.settings.collectLatest { settings ->
                _uiState.update {
                    it.copy(
                        showHiddenFiles = settings.showHiddenFiles,
                        sortField = settings.defaultSortField,
                        sortDirection = settings.defaultSortDirection,
                    )
                }
                refresh()
            }
        }
        refreshVolumes()
    }

    fun refreshVolumes() {
        viewModelScope.launch {
            val vols = repository.getStorageVolumes()
            val hasAccess = repository.hasAllFilesAccess()
            _uiState.update { it.copy(volumes = vols, hasAllFilesAccess = hasAccess) }
        }
    }

    fun onPermissionGranted() {
        val defaultRoot = repository.defaultRootDirectory().absolutePath
        _uiState.update {
            it.copy(
                currentPath = defaultRoot,
                rootPath = defaultRoot,
                activeTreeUri = null,
                hasAllFilesAccess = repository.hasAllFilesAccess(),
            )
        }
        refreshVolumes()
        refresh()
    }

    fun onSafTreeSelected(uri: Uri) {
        repository.persistTreePermission(uri)
        refreshVolumes()
        navigateToVolume(
            StorageVolumeInfo(
                id = uri.toString(),
                label = "External Volume",
                rootPath = uri.toString(),
                rootUriString = uri.toString(),
                totalBytes = 0L,
                availableBytes = 0L,
                isRemovable = true,
            ),
        )
    }

    fun navigateToVolume(volume: StorageVolumeInfo) {
        _uiState.update {
            it.copy(
                currentPath = volume.rootPath,
                rootPath = volume.rootPath,
                activeTreeUri = volume.rootUriString,
                selectedPaths = emptySet(),
                isSearchMode = false,
                searchQuery = "",
            )
        }
        refresh()
    }

    fun navigateToPath(path: String, treeUriString: String? = _uiState.value.activeTreeUri) {
        _uiState.update {
            it.copy(
                currentPath = path,
                activeTreeUri = treeUriString,
                selectedPaths = emptySet(),
                isSearchMode = false,
                searchQuery = "",
            )
        }
        refresh()
    }

    fun navigateUp(): Boolean {
        val state = _uiState.value
        if (state.activeTreeUri != null) return false
        val current = File(state.currentPath)
        val root = File(state.rootPath)
        if (current.canonicalPath == root.canonicalPath) return false
        val parent = current.parentFile ?: return false
        navigateToPath(parent.absolutePath, null)
        return true
    }

    fun refresh() {
        listJob?.cancel()
        val state = _uiState.value
        listJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = if (state.isSearchMode && state.searchQuery.isNotBlank() && state.activeTreeUri == null) {
                Result.success(
                    repository.searchRecursive(
                        rootPath = state.currentPath,
                        query = state.searchQuery,
                        showHidden = state.showHiddenFiles,
                    ),
                )
            } else {
                repository.listDirectory(
                    directoryPath = state.currentPath,
                    treeUriString = state.activeTreeUri,
                    showHidden = state.showHiddenFiles,
                    sortField = state.sortField,
                    sortDirection = state.sortDirection,
                )
            }
            result.onSuccess { list ->
                _uiState.update { it.copy(files = list, isLoading = false, error = null) }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = ProtocolError.PermissionDenied(
                            userMessage = "Could not open folder.",
                            nextStep = "Grant storage access or pick a folder via Storage Access Framework.",
                            technicalDetail = err.message,
                        ),
                    )
                }
            }
        }
    }

    fun recordRecentFile(file: LocalFile) {
        if (file.isDirectory) return
        _uiState.update { state ->
            val updated = (listOf(file) + state.recentFiles.filterNot { it.path == file.path }).take(20)
            state.copy(recentFiles = updated)
        }
    }

    fun toggleGridView() {
        _uiState.update { it.copy(isGridView = !it.isGridView) }
    }

    fun toggleHiddenFiles() {
        viewModelScope.launch {
            settingsDataStore.setShowHiddenFiles(!_uiState.value.showHiddenFiles)
        }
    }

    fun updateSort(field: SortField, direction: SortDirection) {
        viewModelScope.launch {
            settingsDataStore.setSort(field, direction)
        }
    }

    fun setSearchActive(active: Boolean) {
        _uiState.update {
            it.copy(
                isSearchMode = active,
                searchQuery = if (active) it.searchQuery else "",
            )
        }
        if (!active) refresh()
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        refresh()
    }

    fun toggleSelection(file: LocalFile) {
        _uiState.update { state ->
            val next = state.selectedPaths.toMutableSet()
            if (!next.add(file.path)) next.remove(file.path)
            state.copy(selectedPaths = next)
        }
    }

    fun selectAll() {
        _uiState.update { state ->
            state.copy(selectedPaths = state.files.map { it.path }.toSet())
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedPaths = emptySet()) }
    }

    fun copySelected() {
        val state = _uiState.value
        val items = state.files.filter { it.path in state.selectedPaths }
        if (items.isEmpty()) return
        _uiState.update {
            it.copy(
                clipboard = FileClipboard(items, ClipboardMode.COPY),
                selectedPaths = emptySet(),
                snackbarEventMessage = "Copied ${items.size} item(s). Navigate and tap Paste.",
            )
        }
    }

    fun cutSelected() {
        val state = _uiState.value
        val items = state.files.filter { it.path in state.selectedPaths }
        if (items.isEmpty()) return
        _uiState.update {
            it.copy(
                clipboard = FileClipboard(items, ClipboardMode.CUT),
                selectedPaths = emptySet(),
                snackbarEventMessage = "Ready to move ${items.size} item(s). Navigate and tap Paste.",
            )
        }
    }

    fun pasteClipboard() {
        val state = _uiState.value
        val clip = state.clipboard ?: return
        viewModelScope.launch {
            val res = repository.copyOrMoveFiles(
                items = clip.items,
                destinationDirPath = state.currentPath,
                destinationTreeUri = state.activeTreeUri,
                isMove = clip.mode == ClipboardMode.CUT,
            )
            res.onSuccess { count ->
                _uiState.update {
                    it.copy(
                        clipboard = if (clip.mode == ClipboardMode.CUT) null else clip,
                        snackbarEventMessage = "${if (clip.mode == ClipboardMode.CUT) "Moved" else "Copied"} $count item(s).",
                    )
                }
                refresh()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        error = ProtocolError.ProtocolFailure(
                            userMessage = "Paste operation failed.",
                            nextStep = "Check destination permissions and free storage space.",
                            technicalDetail = err.message,
                        ),
                    )
                }
            }
        }
    }

    fun createFolder(name: String) {
        val state = _uiState.value
        viewModelScope.launch {
            repository.mkdir(state.currentPath, state.activeTreeUri, name)
                .onSuccess { refresh() }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            error = ProtocolError.ProtocolFailure(
                                userMessage = "Could not create folder \"$name\".",
                                nextStep = "Ensure the folder name is valid and storage is writable.",
                                technicalDetail = err.message,
                            ),
                        )
                    }
                }
        }
    }

    fun renameFile(file: LocalFile, newName: String) {
        viewModelScope.launch {
            repository.rename(file, newName)
                .onSuccess {
                    clearSelection()
                    refresh()
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            error = ProtocolError.ProtocolFailure(
                                userMessage = "Could not rename \"${file.name}\".",
                                nextStep = "Verify no file with that name already exists.",
                                technicalDetail = err.message,
                            ),
                        )
                    }
                }
        }
    }

    fun deleteSelected() {
        val state = _uiState.value
        val items = state.files.filter { it.path in state.selectedPaths }
        if (items.isEmpty()) return
        viewModelScope.launch {
            repository.stageDeleteForUndo(items)
                .onSuccess { staged ->
                    _uiState.update {
                        it.copy(
                            selectedPaths = emptySet(),
                            pendingTrashUndo = staged.ifEmpty { null },
                            snackbarEventMessage = "Deleted ${items.size} item(s).",
                        )
                    }
                    refresh()
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            error = ProtocolError.ProtocolFailure(
                                userMessage = "Could not delete selected items.",
                                nextStep = "Check write permissions and retry.",
                                technicalDetail = err.message,
                            ),
                        )
                    }
                }
        }
    }

    fun undoDelete() {
        val staged = _uiState.value.pendingTrashUndo ?: return
        viewModelScope.launch {
            repository.restoreFromTrash(staged)
            _uiState.update { it.copy(pendingTrashUndo = null) }
            refresh()
        }
    }

    fun finalizePendingDelete() {
        val staged = _uiState.value.pendingTrashUndo ?: return
        _uiState.update { it.copy(pendingTrashUndo = null) }
        viewModelScope.launch {
            repository.purgeStagedTrash(staged)
        }
    }

    fun openProperties(file: LocalFile) {
        _uiState.update {
            it.copy(
                propertiesFile = file,
                propertiesChecksum = null,
                isComputingChecksum = !file.isDirectory,
            )
        }
        if (!file.isDirectory) {
            viewModelScope.launch {
                val hash = repository.computeChecksum(file).getOrNull()
                _uiState.update { state ->
                    if (state.propertiesFile?.path == file.path) {
                        state.copy(propertiesChecksum = hash, isComputingChecksum = false)
                    } else {
                        state
                    }
                }
            }
        }
    }

    fun closeProperties() {
        _uiState.update { it.copy(propertiesFile = null, propertiesChecksum = null, isComputingChecksum = false) }
    }

    fun toggleCurrentFolderFavorite(currentlyFav: Boolean) {
        val state = _uiState.value
        val label = File(state.currentPath).name.ifEmpty { state.currentPath }
        viewModelScope.launch {
            repository.toggleFavorite(state.currentPath, label, currentlyFav)
        }
    }

    fun clearSnackbarMessage() {
        _uiState.update { it.copy(snackbarEventMessage = null) }
    }
}
