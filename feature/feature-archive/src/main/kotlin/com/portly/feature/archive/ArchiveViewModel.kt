package com.portly.feature.archive

import android.net.Uri
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ArchiveUiState(
    val archivePath: String? = null,
    val archiveUri: Uri? = null,
    val displayTitle: String = "ZIP Archive Manager",
    val entries: List<ZipArchiveEntryInfo> = emptyList(),
    val selectedEntries: Set<String> = emptySet(),
    val filterQuery: String = "",
    val pendingCreateSources: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val progress: ZipProgress? = null,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
) {
    val visibleEntries: List<ZipArchiveEntryInfo>
        get() {
            val q = filterQuery.trim().lowercase()
            return if (q.isEmpty()) entries else entries.filter { it.entryPath.lowercase().contains(q) }
        }
}

@HiltViewModel
class ArchiveViewModel @Inject constructor(
    private val zipArchiveManager: ZipArchiveManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArchiveUiState())
    val uiState: StateFlow<ArchiveUiState> = _uiState.asStateFlow()

    fun initialize(archivePath: String?, sourcePaths: List<String>) {
        val current = _uiState.value
        if (archivePath != null && archivePath != current.archivePath) {
            openArchiveFile(archivePath)
        } else if (sourcePaths.isNotEmpty() && sourcePaths != current.pendingCreateSources) {
            _uiState.update {
                it.copy(
                    pendingCreateSources = sourcePaths,
                    errorMessage = null,
                    statusMessage = "Ready to compress ${sourcePaths.size} selected item(s).",
                )
            }
        }
    }

    fun openArchiveFile(path: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    archivePath = path,
                    archiveUri = null,
                    displayTitle = File(path).name,
                    selectedEntries = emptySet(),
                    isLoading = true,
                    errorMessage = null,
                )
            }
            zipArchiveManager.listEntries(path)
                .onSuccess { entries ->
                    _uiState.update { it.copy(entries = entries, isLoading = false, statusMessage = "${entries.size} entry(ies) loaded.") }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(entries = emptyList(), isLoading = false, errorMessage = err.message ?: "Failed to read archive.") }
                }
        }
    }

    fun openArchiveUri(uri: Uri) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    archivePath = null,
                    archiveUri = uri,
                    displayTitle = uri.lastPathSegment?.substringAfterLast('/') ?: "Archive.zip",
                    selectedEntries = emptySet(),
                    isLoading = true,
                    errorMessage = null,
                )
            }
            zipArchiveManager.listEntriesFromUri(uri)
                .onSuccess { entries ->
                    _uiState.update { it.copy(entries = entries, isLoading = false, statusMessage = "${entries.size} entry(ies) loaded.") }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(entries = emptyList(), isLoading = false, errorMessage = err.message ?: "Failed to read archive URI.") }
                }
        }
    }

    fun setFilterQuery(query: String) {
        _uiState.update { it.copy(filterQuery = query) }
    }

    fun toggleSelectEntry(entryPath: String) {
        _uiState.update { state ->
            val next = state.selectedEntries.toMutableSet()
            if (!next.add(entryPath)) next.remove(entryPath)
            state.copy(selectedEntries = next)
        }
    }

    fun selectAllVisible() {
        _uiState.update { state ->
            state.copy(selectedEntries = state.visibleEntries.map { it.entryPath }.toSet())
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedEntries = emptySet()) }
    }

    fun defaultExtractDirectory(): String {
        val current = _uiState.value
        val file = current.archivePath?.let(::File)
        val baseDir = file?.parentFile ?: defaultRootDir()
        val folderName = (file?.nameWithoutExtension ?: "ExtractedArchive").ifBlank { "ExtractedArchive" }
        return File(baseDir, folderName).absolutePath
    }

    fun defaultCreateZipPath(): String {
        val sources = _uiState.value.pendingCreateSources
        val first = sources.firstOrNull()?.let(::File)
        val parentDir = first?.parentFile ?: defaultRootDir()
        val baseName = if (sources.size == 1 && first != null) {
            first.nameWithoutExtension.ifBlank { first.name }
        } else {
            "PortlyArchive"
        }
        return File(parentDir, "$baseName.zip").absolutePath
    }

    fun extract(destinationDir: String, selectedOnly: Boolean) {
        val state = _uiState.value
        val selection = if (selectedOnly) state.selectedEntries else null
        if (selectedOnly && selection.isNullOrEmpty()) {
            _uiState.update { it.copy(errorMessage = "Select at least one entry to extract.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, progress = null) }
            val result = when {
                state.archivePath != null -> zipArchiveManager.extractArchive(
                    archivePath = state.archivePath,
                    destinationDirPath = destinationDir,
                    selectedEntryPaths = selection,
                    onProgress = { p -> _uiState.update { it.copy(progress = p) } },
                )
                state.archiveUri != null -> zipArchiveManager.extractArchiveFromUri(
                    archiveUri = state.archiveUri,
                    destinationDirPath = destinationDir,
                    selectedEntryPaths = selection,
                    onProgress = { p -> _uiState.update { it.copy(progress = p) } },
                )
                else -> Result.failure(IllegalStateException("No archive open."))
            }
            result
                .onSuccess { count ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            progress = null,
                            statusMessage = "Extracted $count item(s) to $destinationDir",
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            progress = null,
                            errorMessage = err.message ?: "Extraction failed.",
                        )
                    }
                }
        }
    }

    fun createZip(outputZipPath: String) {
        val sources = _uiState.value.pendingCreateSources
        if (sources.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "No source files selected to compress.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, progress = null) }
            zipArchiveManager.createArchive(
                sourcePaths = sources,
                outputZipPath = outputZipPath,
                onProgress = { p -> _uiState.update { it.copy(progress = p) } },
            ).onSuccess { zipBytes ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        progress = null,
                        pendingCreateSources = emptyList(),
                        statusMessage = "Created ${File(outputZipPath).name} ($zipBytes bytes)",
                    )
                }
                openArchiveFile(outputZipPath)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        progress = null,
                        errorMessage = err.message ?: "Failed to create archive.",
                    )
                }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, statusMessage = null) }
    }

    private fun defaultRootDir(): File =
        Environment.getExternalStorageDirectory()
}
