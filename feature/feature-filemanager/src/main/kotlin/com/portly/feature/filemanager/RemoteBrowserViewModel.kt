package com.portly.feature.filemanager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.portly.core.common.PathValidator
import com.portly.core.common.ProtocolException
import com.portly.core.database.CredentialVault
import com.portly.core.database.dao.ConnectionProfileDao
import com.portly.core.database.dao.TrustedCertDao
import com.portly.core.database.entity.TrustedCertEntity
import com.portly.core.database.toDomain
import com.portly.core.datastore.PortlySettingsDataStore
import com.portly.core.model.ConflictPolicy
import com.portly.core.model.ConnectionProfile
import com.portly.core.model.LocalFile
import com.portly.core.model.ProtocolClient
import com.portly.core.model.ProtocolClientFactory
import com.portly.core.model.ProtocolError
import com.portly.core.model.ProtocolType
import com.portly.core.model.RemoteFile
import com.portly.core.model.TransferDirection
import com.portly.core.model.TransferEngine
import com.portly.core.model.TransferId
import com.portly.core.model.TransferSpec
import com.portly.transfer.PortlyTransferEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

data class RemoteBrowserUiState(
    val activeProfile: ConnectionProfile? = null,
    val isConnected: Boolean = false,
    val currentPath: String = "/",
    val files: List<RemoteFile> = emptyList(),
    val selectedPaths: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val error: ProtocolError? = null,
    val statusMessage: String? = null,
)

@HiltViewModel
class RemoteBrowserViewModel @Inject constructor(
    private val profileDao: ConnectionProfileDao,
    private val trustedCertDao: TrustedCertDao,
    private val credentialVault: CredentialVault,
    private val clientFactories: Map<ProtocolType, @JvmSuppressWildcards ProtocolClientFactory>,
    private val transferEngine: TransferEngine,
    private val settingsDataStore: PortlySettingsDataStore,
) : ViewModel() {

    val savedProfiles: StateFlow<List<ConnectionProfile>> = profileDao.observeAll()
        .map { list -> list.map { it.toDomain() } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    private val _uiState = MutableStateFlow(RemoteBrowserUiState())
    val uiState: StateFlow<RemoteBrowserUiState> = _uiState.asStateFlow()

    private var activeClient: ProtocolClient? = null

    fun connectProfile(profileId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, statusMessage = null) }
            val entity = profileDao.getById(profileId)
            if (entity == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = ProtocolError.ConnectionFailed("Connection profile not found."),
                    )
                }
                return@launch
            }
            val profile = entity.toDomain()
            val factory = clientFactories[profile.protocol]
            if (factory == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = ProtocolError.UnsupportedOperation(
                            userMessage = "${profile.protocol.displayName} client is not enabled in this phase yet.",
                            nextStep = "Try FTP or another enabled protocol.",
                        ),
                    )
                }
                return@launch
            }

            runCatching { activeClient?.disconnect() }
            val client = factory.create()
            val creds = credentialVault.loadCredentials(profile.authSecretRef, profile.username)
            val connResult = client.connect(profile, creds)
            if (connResult.isFailure) {
                val err = (connResult.exceptionOrNull() as? ProtocolException)?.protocolError
                    ?: ProtocolError.ConnectionFailed(technicalDetail = connResult.exceptionOrNull()?.message)
                _uiState.update {
                    it.copy(
                        activeProfile = profile,
                        isConnected = false,
                        isLoading = false,
                        error = err,
                    )
                }
                return@launch
            }

            activeClient = client
            profileDao.updateLastConnected(profile.id, System.currentTimeMillis())
            val startPath = PathValidator.normalizeRemotePath(profile.rootPath)
            _uiState.update {
                it.copy(
                    activeProfile = profile,
                    isConnected = true,
                    currentPath = startPath,
                    selectedPaths = emptySet(),
                )
            }
            refresh()
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            runCatching { activeClient?.disconnect() }
            activeClient = null
            _uiState.value = RemoteBrowserUiState()
        }
    }

    fun refresh() {
        val client = activeClient ?: return
        val path = _uiState.value.currentPath
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            client.list(path).onSuccess { entries ->
                val sorted = entries.sortedWith(compareBy<RemoteFile> { !it.isDirectory }.thenBy { it.name.lowercase() })
                _uiState.update { it.copy(files = sorted, isLoading = false, selectedPaths = emptySet()) }
            }.onFailure { err ->
                val mapped = (err as? ProtocolException)?.protocolError
                    ?: ProtocolError.ProtocolFailure("Failed to list $path", technicalDetail = err.message)
                _uiState.update { it.copy(isLoading = false, error = mapped) }
            }
        }
    }

    fun navigateTo(path: String) {
        _uiState.update { it.copy(currentPath = PathValidator.normalizeRemotePath(path), selectedPaths = emptySet()) }
        refresh()
    }

    fun navigateUp(): Boolean {
        val cur = _uiState.value.currentPath
        if (cur == "/") return false
        navigateTo(PathValidator.parentRemotePath(cur))
        return true
    }

    fun toggleSelection(file: RemoteFile) {
        _uiState.update { state ->
            val next = state.selectedPaths.toMutableSet()
            if (!next.add(file.path)) next.remove(file.path)
            state.copy(selectedPaths = next)
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedPaths = emptySet()) }
    }

    fun mkdir(name: String) {
        val client = activeClient ?: return
        val target = PathValidator.joinRemotePath(_uiState.value.currentPath, name)
        viewModelScope.launch {
            client.mkdir(target)
                .onSuccess { refresh() }
                .onFailure { err -> reportFailure("Could not create remote folder.", err) }
        }
    }

    fun rename(file: RemoteFile, newName: String) {
        val client = activeClient ?: return
        val parent = PathValidator.parentRemotePath(file.path)
        val newPath = PathValidator.joinRemotePath(parent, newName)
        viewModelScope.launch {
            client.rename(file.path, newPath)
                .onSuccess { refresh() }
                .onFailure { err -> reportFailure("Could not rename remote item.", err) }
        }
    }

    fun deleteSelected() {
        val client = activeClient ?: return
        val state = _uiState.value
        val selected = state.files.filter { it.path in state.selectedPaths }
        if (selected.isEmpty()) return
        viewModelScope.launch {
            for (item in selected) {
                client.delete(item.path, recursive = item.isDirectory)
                    .onFailure { err ->
                        reportFailure("Failed deleting ${item.name}.", err)
                        return@launch
                    }
            }
            refresh()
        }
    }

    fun chmod(file: RemoteFile, posixMode: Int) {
        val client = activeClient ?: return
        viewModelScope.launch {
            client.chmod(file.path, posixMode)
                .onSuccess { refresh() }
                .onFailure { err -> reportFailure("Could not change permissions.", err) }
        }
    }

    fun queueDownloadTo(localDestinationDir: String) {
        val state = _uiState.value
        val profile = state.activeProfile ?: return
        val client = activeClient ?: return
        val selected = state.files.filter { it.path in state.selectedPaths }
        if (selected.isEmpty()) return

        viewModelScope.launch {
            val policy = settingsDataStore.settings.first().defaultConflictPolicy
            var queued = 0
            for (item in selected) {
                val targetLocal = File(localDestinationDir, PathValidator.sanitizeFileName(item.name)).absolutePath
                if (item.isDirectory && transferEngine is PortlyTransferEngine) {
                    transferEngine.enqueueFolderDownload(
                        client = client,
                        remoteDirPath = item.path,
                        localDirPath = targetLocal,
                        protocol = profile.protocol,
                        profileId = profile.id,
                        conflictPolicy = policy,
                    )
                    queued++
                } else if (!item.isDirectory) {
                    transferEngine.enqueue(
                        TransferSpec(
                            id = TransferId(UUID.randomUUID().toString()),
                            direction = TransferDirection.DOWNLOAD,
                            protocol = profile.protocol,
                            profileId = profile.id,
                            fileName = item.name,
                            sourcePath = item.path,
                            destinationPath = targetLocal,
                            totalBytes = item.sizeBytes,
                            sourceModifiedMillis = item.lastModifiedMillis,
                            conflictPolicy = policy,
                        ),
                    )
                    queued++
                }
            }
            _uiState.update {
                it.copy(
                    selectedPaths = emptySet(),
                    statusMessage = "Queued $queued download(s) — check Transfers tab.",
                )
            }
        }
    }

    fun queueUploadFrom(localItems: List<LocalFile>) {
        val state = _uiState.value
        val profile = state.activeProfile ?: return
        if (!state.isConnected || localItems.isEmpty()) return

        viewModelScope.launch {
            val policy = settingsDataStore.settings.first().defaultConflictPolicy
            var queued = 0
            for (item in localItems) {
                val targetRemote = PathValidator.joinRemotePath(state.currentPath, item.name)
                if (item.isDirectory && transferEngine is PortlyTransferEngine) {
                    transferEngine.enqueueFolderUpload(
                        localDirPath = item.path,
                        remoteDirPath = targetRemote,
                        protocol = profile.protocol,
                        profileId = profile.id,
                        conflictPolicy = policy,
                    )
                    queued++
                } else if (!item.isDirectory) {
                    transferEngine.enqueue(
                        TransferSpec(
                            id = TransferId(UUID.randomUUID().toString()),
                            direction = TransferDirection.UPLOAD,
                            protocol = profile.protocol,
                            profileId = profile.id,
                            fileName = item.name,
                            sourcePath = item.path,
                            sourceUriString = item.uriString,
                            destinationPath = targetRemote,
                            totalBytes = item.sizeBytes,
                            sourceModifiedMillis = item.lastModifiedMillis,
                            conflictPolicy = policy,
                        ),
                    )
                    queued++
                }
            }
            _uiState.update {
                it.copy(statusMessage = "Queued $queued upload(s) — check Transfers tab.")
            }
        }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    fun approveUntrustedCertificate(err: ProtocolError.CertificateUntrusted) {
        val profileId = _uiState.value.activeProfile?.id ?: return
        viewModelScope.launch {
            trustedCertDao.upsert(
                TrustedCertEntity(
                    host = err.host,
                    port = err.port,
                    sha256Fingerprint = err.fingerprintSha256,
                    subject = err.subject,
                    issuer = err.subject,
                    validFromMillis = System.currentTimeMillis(),
                    validUntilMillis = System.currentTimeMillis() + 365L * 86_400_000L,
                    isUserCa = false,
                    trustedAtMillis = System.currentTimeMillis(),
                ),
            )
            connectProfile(profileId)
        }
    }

    private fun reportFailure(fallbackMessage: String, throwable: Throwable) {
        val mapped = (throwable as? ProtocolException)?.protocolError
            ?: ProtocolError.ProtocolFailure(fallbackMessage, technicalDetail = throwable.message)
        _uiState.update { it.copy(error = mapped) }
    }

    override fun onCleared() {
        super.onCleared()
        val client = activeClient
        activeClient = null
        if (client != null) {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                runCatching { client.disconnect() }
            }
        }
    }
}
