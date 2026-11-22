package com.portly.feature.connections

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.PhonelinkSetup
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.portly.core.database.CredentialVault
import com.portly.core.database.dao.ConnectionProfileDao
import com.portly.core.database.dao.KnownHostDao
import com.portly.core.database.dao.TrustedCertDao
import com.portly.core.database.toDomain
import com.portly.core.database.toEntity
import com.portly.core.model.AuthType
import com.portly.core.model.ConnectionProfile
import com.portly.core.model.ConnectionTestResult
import com.portly.core.model.FtpsMode
import com.portly.core.model.KnownHostEntry
import com.portly.core.model.PairingTokenPayload
import com.portly.core.model.ProtocolClientFactory
import com.portly.core.model.ProtocolError
import com.portly.core.model.ProtocolType
import com.portly.core.model.ResolvedCredentials
import com.portly.core.model.TlsVersionPolicy
import com.portly.core.model.TrustedCertEntry
import com.portly.core.ui.components.EmptyStateView
import com.portly.core.ui.components.PlainFtpWarningBanner
import com.portly.core.ui.components.PortlyConfirmDialog
import com.portly.core.ui.components.PortlyErrorCard
import com.portly.core.ui.components.ProtocolBadge
import com.portly.network.DiscoveredLanService
import com.portly.network.NsdDiscoveryManager
import com.portly.network.PeerPairingManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ConnectionsViewModel @Inject constructor(
    private val profileDao: ConnectionProfileDao,
    private val knownHostDao: KnownHostDao,
    private val trustedCertDao: TrustedCertDao,
    private val credentialVault: CredentialVault,
    private val clientFactories: Map<ProtocolType, @JvmSuppressWildcards ProtocolClientFactory>,
    private val nsdDiscoveryManager: NsdDiscoveryManager,
) : ViewModel() {

    fun discoverLanSmbHosts(): kotlinx.coroutines.flow.Flow<List<DiscoveredLanService>> =
        nsdDiscoveryManager.discoverServices(NsdDiscoveryManager.SERVICE_TYPE_SMB)

    fun discoverLanPortlyPeers(): kotlinx.coroutines.flow.Flow<List<DiscoveredLanService>> =
        nsdDiscoveryManager.discoverServices(NsdDiscoveryManager.SERVICE_TYPE_PORTLY)

    fun approvePeerPairing(
        payload: PairingTokenPayload,
        onReadyToConnect: (String) -> Unit,
    ) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        if (now > payload.expiresAtMillis) {
            _statusMessage.value = "Pairing token expired (60-second limit exceeded)."
            return@launch
        }
        payload.sha256CertFingerprint?.takeIf { it.isNotBlank() }?.let { fp ->
            trustedCertDao.upsert(
                TrustedCertEntry(
                    host = payload.host,
                    port = payload.port,
                    sha256Fingerprint = fp,
                    subject = "CN=${payload.deviceName}",
                    issuer = "CN=Portly Peer Self-Signed",
                    validFromMillis = now,
                    validUntilMillis = now + 365L * 24L * 3600L * 1000L,
                    isUserCa = false,
                    trustedAtMillis = now,
                ).toEntity(),
            )
        }
        val (baseProfile, creds) = PeerPairingManager.toPeerProfile(payload)
        val secretRef = credentialVault.saveCredentials(null, creds)
        val saved = baseProfile.copy(authSecretRef = secretRef)
        profileDao.upsert(saved.toEntity())
        _statusMessage.value = "Paired with ${payload.deviceName}."
        onReadyToConnect(saved.id)
    }

    val profiles: StateFlow<List<ConnectionProfile>> = profileDao.observeAll()
        .map { list -> list.map { it.toDomain() } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    val knownHosts: StateFlow<List<KnownHostEntry>> = knownHostDao.observeAll()
        .map { list -> list.map { it.toDomain() } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    val trustedCerts: StateFlow<List<TrustedCertEntry>> = trustedCertDao.observeAll()
        .map { list -> list.map { it.toDomain() } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    private val _testResult = MutableStateFlow<Pair<String, ConnectionTestResult>?>(null)
    val testResult: StateFlow<Pair<String, ConnectionTestResult>?> = _testResult.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun saveProfile(
        existing: ConnectionProfile?,
        name: String,
        protocol: ProtocolType,
        host: String,
        port: Int,
        username: String,
        password: String,
        privateKeyPem: String,
        passphrase: String,
        passiveMode: Boolean,
        ftpsMode: FtpsMode,
        tlsVersionPolicy: TlsVersionPolicy,
        useHttpsForWebDav: Boolean,
        smbShare: String,
        smbDomain: String,
        rootPath: String,
        timeoutMs: Int,
        notes: String,
    ) = viewModelScope.launch {
        val authType = when {
            protocol == ProtocolType.SFTP && privateKeyPem.isNotBlank() -> AuthType.PRIVATE_KEY
            username.isBlank() && password.isBlank() && existing?.authSecretRef == null -> AuthType.ANONYMOUS
            else -> AuthType.PASSWORD
        }

        val secretRef = if (authType == AuthType.ANONYMOUS) {
            existing?.authSecretRef?.let { credentialVault.deleteCredentials(it) }
            null
        } else if (password.isNotEmpty() || privateKeyPem.isNotBlank() || existing?.authSecretRef == null) {
            credentialVault.saveCredentials(
                existingRef = existing?.authSecretRef,
                credentials = ResolvedCredentials(
                    username = username.trim(),
                    password = password.ifEmpty { null },
                    privateKeyPem = privateKeyPem.ifBlank { null },
                    passphrase = passphrase.ifEmpty { null },
                ),
            )
        } else {
            existing.authSecretRef
        }

        val profile = ConnectionProfile(
            id = existing?.id ?: UUID.randomUUID().toString(),
            name = name.trim().ifEmpty { host.trim() },
            protocol = protocol,
            host = host.trim(),
            port = port,
            username = username.trim(),
            authType = authType,
            authSecretRef = secretRef,
            passiveMode = passiveMode,
            ftpsMode = ftpsMode,
            tlsVersionPolicy = tlsVersionPolicy,
            useHttpsForWebDav = useHttpsForWebDav,
            smbShare = smbShare.trim(),
            smbDomain = smbDomain.trim(),
            rootPath = rootPath.trim().ifEmpty { "/" },
            connectTimeoutMs = timeoutMs.coerceIn(3_000, 120_000),
            notes = notes.trim(),
            lastConnectedAtMillis = existing?.lastConnectedAtMillis,
            isFavorite = existing?.isFavorite ?: false,
            createdAtMillis = existing?.createdAtMillis ?: System.currentTimeMillis(),
        )
        profileDao.upsert(profile.toEntity())
    }

    fun testConnection(profile: ConnectionProfile) = viewModelScope.launch {
        val factory = clientFactories[profile.protocol]
        if (factory == null) {
            _testResult.value = profile.name to ConnectionTestResult(
                success = false,
                latencyMs = 0L,
                error = ProtocolError.UnsupportedOperation(
                    userMessage = "${profile.protocol.displayName} client is not registered yet.",
                    nextStep = "Implemented in an upcoming protocol phase.",
                ),
            )
            return@launch
        }
        val creds = credentialVault.loadCredentials(profile.authSecretRef, profile.username)
        val result = factory.create().testConnection(profile, creds)
        _testResult.value = profile.name to result
    }

    fun clearTestResult() {
        _testResult.value = null
    }

    fun toggleFavorite(profile: ConnectionProfile) = viewModelScope.launch {
        profileDao.updateFavorite(profile.id, !profile.isFavorite)
    }

    fun deleteProfile(profile: ConnectionProfile) = viewModelScope.launch {
        credentialVault.deleteCredentials(profile.authSecretRef)
        profileDao.deleteById(profile.id)
    }

    fun deleteKnownHost(host: String, port: Int) = viewModelScope.launch {
        knownHostDao.delete(host, port)
    }

    fun deleteTrustedCert(cert: TrustedCertEntry) = viewModelScope.launch {
        trustedCertDao.delete(cert.host, cert.port, cert.sha256Fingerprint)
    }

    fun exportToUri(
        context: Context,
        uri: Uri,
        includeCredentials: Boolean,
        passphrase: String,
    ) = viewModelScope.launch {
        runCatching {
            val all = profileDao.getAllOnce().map { it.toDomain() }
            val credsMap = if (includeCredentials) {
                all.mapNotNull { profile ->
                    profile.authSecretRef?.let { ref ->
                        profile.id to credentialVault.loadCredentials(ref, profile.username)
                    }
                }.toMap()
            } else {
                emptyMap()
            }
            val json = ProfilePortabilityCodec.exportProfiles(
                profiles = all,
                credentialsByProfileId = credsMap,
                includeEncryptedCredentials = includeCredentials,
                passphrase = if (includeCredentials) passphrase.toCharArray() else null,
            )
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }
                ?: error("Cannot write export file")
            all.size
        }.onSuccess { count ->
            _statusMessage.value = "Exported $count profile(s)."
        }.onFailure { err ->
            _statusMessage.value = "Export failed: ${err.message}"
        }
    }

    fun importFromJson(rawJson: String, passphrase: String?) = viewModelScope.launch {
        runCatching {
            val items = ProfilePortabilityCodec.importProfiles(
                rawJson = rawJson,
                passphrase = passphrase?.takeIf { it.isNotEmpty() }?.toCharArray(),
            )
            for ((profile, creds) in items) {
                val ref = if (creds != null) {
                    credentialVault.saveCredentials(null, creds)
                } else {
                    null
                }
                profileDao.upsert(profile.copy(authSecretRef = ref).toEntity())
            }
            items.size
        }.onSuccess { count ->
            _statusMessage.value = "Imported $count profile(s)."
        }.onFailure { err ->
            _statusMessage.value = "Import failed: ${err.message}"
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }
}

@Composable
fun ConnectionsScreen(
    initialPairingUri: String? = null,
    onConsumeInitialPairingUri: () -> Unit = {},
    onConnectProfile: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ConnectionsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val knownHosts by viewModel.knownHosts.collectAsStateWithLifecycle()
    val trustedCerts by viewModel.trustedCerts.collectAsStateWithLifecycle()
    val testResult by viewModel.testResult.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var favoritesOnly by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<ConnectionProfile?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showKnownHostsDialog by remember { mutableStateOf(false) }
    var showTrustedCertsDialog by remember { mutableStateOf(false) }
    var showLanDiscoveryDialog by remember { mutableStateOf(false) }
    var showPairPeerDialog by remember { mutableStateOf(false) }
    var pairingInputText by remember { mutableStateOf("") }
    var showExportDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var pendingExportWithPass by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var pendingImportJsonNeedingPass by remember { mutableStateOf<String?>(null) }
    var deleteCandidate by remember { mutableStateOf<ConnectionProfile?>(null) }

    LaunchedEffect(initialPairingUri) {
        if (!initialPairingUri.isNullOrBlank()) {
            pairingInputText = initialPairingUri
            showPairPeerDialog = true
            onConsumeInitialPairingUri()
        }
    }

    val exportDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val cfg = pendingExportWithPass
        pendingExportWithPass = null
        if (uri != null && cfg != null) {
            viewModel.exportToUri(context, uri, cfg.first, cfg.second)
        }
    }

    val importDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val raw = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
            }.getOrNull()
            if (raw != null) {
                val needsPass = runCatching { ProfilePortabilityCodec.bundleHasEncryptedSecrets(raw) }
                    .getOrDefault(false)
                if (needsPass) {
                    pendingImportJsonNeedingPass = raw
                } else {
                    viewModel.importFromJson(raw, null)
                }
            }
        }
    }

    LaunchedEffect(statusMessage) {
        val msg = statusMessage ?: return@LaunchedEffect
        viewModel.clearStatusMessage()
        snackbarHostState.showSnackbar(msg)
    }

    val filtered = remember(profiles, searchQuery, favoritesOnly) {
        val q = searchQuery.trim().lowercase()
        profiles.filter { p ->
            (!favoritesOnly || p.isFavorite) &&
                (q.isEmpty() || p.name.lowercase().contains(q) || p.host.lowercase().contains(q))
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Outlined.Add, contentDescription = "Add connection")
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "Connections",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = "${profiles.size} saved profile(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showPairPeerDialog = true }) {
                        Icon(Icons.Outlined.PhonelinkSetup, contentDescription = "Pair with Portly peer")
                    }
                    IconButton(onClick = { showLanDiscoveryDialog = true }) {
                        Icon(Icons.Outlined.Radar, contentDescription = "Discover LAN hosts and peers")
                    }
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "More connection actions")
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Import Profiles") },
                                onClick = {
                                    showMoreMenu = false
                                    importDocumentLauncher.launch(arrayOf("application/json", "*/*"))
                                },
                                leadingIcon = { Icon(Icons.Outlined.FileDownload, contentDescription = null) },
                            )
                            DropdownMenuItem(
                                text = { Text("Export Profiles") },
                                onClick = {
                                    showMoreMenu = false
                                    showExportDialog = true
                                },
                                leadingIcon = { Icon(Icons.Outlined.FileUpload, contentDescription = null) },
                            )
                            DropdownMenuItem(
                                text = { Text("SSH Known Hosts") },
                                onClick = {
                                    showMoreMenu = false
                                    showKnownHostsDialog = true
                                },
                                leadingIcon = { Icon(Icons.Outlined.Key, contentDescription = null) },
                            )
                            DropdownMenuItem(
                                text = { Text("Trusted Certificates") },
                                onClick = {
                                    showMoreMenu = false
                                    showTrustedCertsDialog = true
                                },
                                leadingIcon = { Icon(Icons.Outlined.VerifiedUser, contentDescription = null) },
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search profiles or hosts...") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = favoritesOnly,
                    onClick = { favoritesOnly = !favoritesOnly },
                    label = { Text("Favorites") },
                    leadingIcon = if (favoritesOnly) {
                        { Icon(Icons.Outlined.Star, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(16.dp)) }
                    } else null,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            if (filtered.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Outlined.CloudQueue,
                    title = "No matching connections",
                    subtitle = "Save FTP, FTPS, SFTP, WebDAV, or SMB servers with Keystore-encrypted credentials.",
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(filtered, key = { it.id }) { profile ->
                        val protoColor = when (profile.protocol) {
                            ProtocolType.SFTP -> Color(0xFF38BDF8) // Sky
                            ProtocolType.FTPS -> Color(0xFFA78BFA) // Purple
                            ProtocolType.WEBDAV -> Color(0xFF34D399) // Emerald
                            ProtocolType.SMB -> Color(0xFFF97316) // Orange
                            ProtocolType.FTP -> Color(0xFFFBBF24) // Amber
                            ProtocolType.HTTP -> Color(0xFF38BDF8) // Sky
                            ProtocolType.HTTPS -> Color(0xFF34D399) // Emerald
                        }
                        Card(
                            onClick = { onConnectProfile(profile.id) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(protoColor.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.CloudQueue,
                                        contentDescription = null,
                                        tint = protoColor,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(profile.name, style = MaterialTheme.typography.titleMedium)
                                        ProtocolBadge(protocol = profile.protocol)
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        "${profile.host}:${profile.port} • ${profile.username.ifBlank { "anonymous" }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (profile.notes.isNotBlank()) {
                                        Text(
                                            profile.notes,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                IconButton(onClick = { viewModel.testConnection(profile) }) {
                                    Icon(Icons.Outlined.NetworkCheck, contentDescription = "Test connection")
                                }
                                IconButton(onClick = { editingProfile = profile }) {
                                    Icon(Icons.Outlined.Edit, contentDescription = "Edit connection")
                                }
                                IconButton(onClick = { viewModel.toggleFavorite(profile) }) {
                                    Icon(
                                        imageVector = if (profile.isFavorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                                        contentDescription = "Toggle favorite",
                                        tint = if (profile.isFavorite) Color(0xFFFBBF24) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { deleteCandidate = profile }) {
                                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete connection")
                                }
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }

    if (showCreateDialog || editingProfile != null) {
        ConnectionProfileDialog(
            existing = editingProfile,
            onSave = { name, proto, host, port, user, pass, pem, passphrase, passive, ftpsMode, tlsPol, useHttps, smbShare, smbDomain, rootPath, timeoutMs, notes ->
                viewModel.saveProfile(
                    existing = editingProfile,
                    name = name,
                    protocol = proto,
                    host = host,
                    port = port,
                    username = user,
                    password = pass,
                    privateKeyPem = pem,
                    passphrase = passphrase,
                    passiveMode = passive,
                    ftpsMode = ftpsMode,
                    tlsVersionPolicy = tlsPol,
                    useHttpsForWebDav = useHttps,
                    smbShare = smbShare,
                    smbDomain = smbDomain,
                    rootPath = rootPath,
                    timeoutMs = timeoutMs,
                    notes = notes,
                )
                showCreateDialog = false
                editingProfile = null
            },
            onDismiss = {
                showCreateDialog = false
                editingProfile = null
            },
        )
    }

    testResult?.let { (name, res) ->
        AlertDialog(
            onDismissRequest = viewModel::clearTestResult,
            title = { Text("Connection Test: $name") },
            text = {
                if (res.success) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Connected successfully in ${res.latencyMs} ms.", style = MaterialTheme.typography.titleSmall)
                        res.currentDirectory?.let { Text("Root directory: $it", style = MaterialTheme.typography.bodySmall) }
                        res.serverBanner?.let { Text("Server: $it", style = MaterialTheme.typography.bodySmall) }
                    }
                } else {
                    PortlyErrorCard(error = res.error ?: ProtocolError.ConnectionFailed())
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::clearTestResult) { Text("Close") }
            },
        )
    }

    if (showExportDialog) {
        ExportProfilesDialog(
            onConfirm = { includeSecrets, passphrase ->
                showExportDialog = false
                pendingExportWithPass = includeSecrets to passphrase
                exportDocumentLauncher.launch("portly-profiles.json")
            },
            onDismiss = { showExportDialog = false },
        )
    }

    pendingImportJsonNeedingPass?.let { rawJson ->
        ImportPassphraseDialog(
            onConfirm = { pass ->
                pendingImportJsonNeedingPass = null
                viewModel.importFromJson(rawJson, pass)
            },
            onDismiss = { pendingImportJsonNeedingPass = null },
        )
    }

    if (showKnownHostsDialog) {
        AlertDialog(
            onDismissRequest = { showKnownHostsDialog = false },
            title = { Text("SSH Known Hosts") },
            text = {
                if (knownHosts.isEmpty()) {
                    Text("No SSH host keys pinned yet.")
                } else {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        knownHosts.forEach { entry ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${entry.host}:${entry.port} (${entry.algorithm})", style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            entry.fingerprintSha256,
                                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        )
                                    }
                                    IconButton(onClick = { viewModel.deleteKnownHost(entry.host, entry.port) }) {
                                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "Forget host key")
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showKnownHostsDialog = false }) { Text("Close") }
            },
        )
    }

    if (showLanDiscoveryDialog) {
        val discoveredSmb by remember { viewModel.discoverLanSmbHosts() }
            .collectAsStateWithLifecycle(initialValue = emptyList())
        val discoveredPeers by remember { viewModel.discoverLanPortlyPeers() }
            .collectAsStateWithLifecycle(initialValue = emptyList())
        AlertDialog(
            onDismissRequest = { showLanDiscoveryDialog = false },
            title = { Text("LAN Discovery (NSD)") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Portly Peers (_portly._tcp.)", style = MaterialTheme.typography.titleSmall)
                    if (discoveredPeers.isEmpty()) {
                        Text(
                            text = "Listening for nearby Portly pairing broadcasts…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        discoveredPeers.forEach { peer ->
                            Card(
                                onClick = {
                                    showLanDiscoveryDialog = false
                                    showPairPeerDialog = true
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(peer.serviceName, style = MaterialTheme.typography.titleSmall)
                                    Text("${peer.hostAddress}:${peer.port} — Tap to enter pairing code", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }

                    Text("SMB Shares (_smb._tcp.)", style = MaterialTheme.typography.titleSmall)
                    if (discoveredSmb.isEmpty()) {
                        Text(
                            text = "Scanning LAN via mDNS (_smb._tcp.)…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        discoveredSmb.forEach { svc ->
                            Card(
                                onClick = {
                                    viewModel.saveProfile(
                                        existing = null,
                                        name = svc.serviceName,
                                        protocol = ProtocolType.SMB,
                                        host = svc.hostAddress,
                                        port = if (svc.port > 0) svc.port else 445,
                                        username = "",
                                        password = "",
                                        privateKeyPem = "",
                                        passphrase = "",
                                        passiveMode = true,
                                        ftpsMode = FtpsMode.EXPLICIT,
                                        tlsVersionPolicy = TlsVersionPolicy.TLS_1_2_OR_1_3,
                                        useHttpsForWebDav = true,
                                        smbShare = "",
                                        smbDomain = "",
                                        rootPath = "/",
                                        timeoutMs = 15_000,
                                        notes = "Discovered via NSD",
                                    )
                                    showLanDiscoveryDialog = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(svc.serviceName, style = MaterialTheme.typography.titleSmall)
                                    Text("${svc.hostAddress}:${svc.port}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanDiscoveryDialog = false }) { Text("Close") }
            },
        )
    }

    if (showPairPeerDialog) {
        val parsedResult = remember(pairingInputText) {
            if (pairingInputText.isBlank()) {
                null
            } else {
                PeerPairingManager.parseAndValidateIncoming(pairingInputText)
            }
        }
        val parsedPayload = parsedResult?.getOrNull()
        val parseError = parsedResult?.exceptionOrNull()?.message

        AlertDialog(
            onDismissRequest = { showPairPeerDialog = false },
            title = { Text("Pair with Portly Peer") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Scan a peer's QR code via camera/deep-link or paste a portly://pair?payload=... link (valid 60s).",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = pairingInputText,
                        onValueChange = { pairingInputText = it },
                        label = { Text("Pairing link or QR JSON") },
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (parseError != null) {
                        Text(
                            text = parseError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else if (parsedPayload != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(parsedPayload.deviceName, style = MaterialTheme.typography.titleSmall)
                                Text("${parsedPayload.protocol.displayName} • ${parsedPayload.host}:${parsedPayload.port}", style = MaterialTheme.typography.bodySmall)
                                parsedPayload.sha256CertFingerprint?.let { fp ->
                                    Text(
                                        text = "Cert SHA-256: $fp",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val payload = parsedPayload ?: return@Button
                        showPairPeerDialog = false
                        pairingInputText = ""
                        viewModel.approvePeerPairing(payload, onConnectProfile)
                    },
                    enabled = parsedPayload != null,
                ) {
                    Text("Approve & Connect")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPairPeerDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showTrustedCertsDialog) {
        AlertDialog(
            onDismissRequest = { showTrustedCertsDialog = false },
            title = { Text("Trusted TLS Certificates") },
            text = {
                if (trustedCerts.isEmpty()) {
                    Text("No self-signed host certificates pinned yet.")
                } else {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        trustedCerts.forEach { cert ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${cert.host}:${cert.port}", style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            cert.sha256Fingerprint,
                                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        )
                                    }
                                    IconButton(onClick = { viewModel.deleteTrustedCert(cert) }) {
                                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "Revoke certificate")
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTrustedCertsDialog = false }) { Text("Close") }
            },
        )
    }

    deleteCandidate?.let { candidate ->
        PortlyConfirmDialog(
            title = "Delete \"${candidate.name}\"?",
            consequenceText = "This removes the saved profile and erases its encrypted credentials from Keystore storage.",
            confirmLabel = "Delete",
            onConfirm = {
                viewModel.deleteProfile(candidate)
                deleteCandidate = null
            },
            onDismiss = { deleteCandidate = null },
        )
    }
}

@Composable
private fun ExportProfilesDialog(
    onConfirm: (includeSecrets: Boolean, passphrase: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var includeSecrets by remember { mutableStateOf(false) }
    var passphrase by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export Profiles") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "By default, passwords and SSH private keys are excluded from exported JSON.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = includeSecrets, onCheckedChange = { includeSecrets = it })
                    Text("Include encrypted credentials (PBKDF2 600k)")
                }
                if (includeSecrets) {
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text("Encryption passphrase") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !includeSecrets || passphrase.length >= 6,
                onClick = { onConfirm(includeSecrets, passphrase) },
            ) {
                Text("Export JSON")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun ImportPassphraseDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter Import Passphrase") },
        text = {
            OutlinedTextField(
                value = passphrase,
                onValueChange = { passphrase = it },
                label = { Text("Passphrase") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
        },
        confirmButton = {
            Button(enabled = passphrase.isNotEmpty(), onClick = { onConfirm(passphrase) }) {
                Text("Decrypt & Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun ConnectionProfileDialog(
    existing: ConnectionProfile?,
    onSave: (
        name: String,
        protocol: ProtocolType,
        host: String,
        port: Int,
        username: String,
        password: String,
        privateKeyPem: String,
        passphrase: String,
        passiveMode: Boolean,
        ftpsMode: FtpsMode,
        tlsVersionPolicy: TlsVersionPolicy,
        useHttpsForWebDav: Boolean,
        smbShare: String,
        smbDomain: String,
        rootPath: String,
        timeoutMs: Int,
        notes: String,
    ) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var name by remember(existing) { mutableStateOf(existing?.name.orEmpty()) }
    var protocol by remember(existing) { mutableStateOf(existing?.protocol ?: ProtocolType.SFTP) }
    var host by remember(existing) { mutableStateOf(existing?.host.orEmpty()) }
    var portText by remember(existing) { mutableStateOf((existing?.port ?: ProtocolType.SFTP.defaultPort).toString()) }
    var username by remember(existing) { mutableStateOf(existing?.username.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var usePrivateKey by remember(existing) { mutableStateOf(existing?.authType == AuthType.PRIVATE_KEY) }
    var privateKeyPem by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var passiveMode by remember(existing) { mutableStateOf(existing?.passiveMode ?: true) }
    var ftpsMode by remember(existing) { mutableStateOf(existing?.ftpsMode ?: FtpsMode.EXPLICIT) }
    var tlsPolicy by remember(existing) { mutableStateOf(existing?.tlsVersionPolicy ?: TlsVersionPolicy.TLS_1_2_OR_1_3) }
    var useHttpsWebDav by remember(existing) { mutableStateOf(existing?.useHttpsForWebDav ?: true) }
    var smbShare by remember(existing) { mutableStateOf(existing?.smbShare.orEmpty()) }
    var smbDomain by remember(existing) { mutableStateOf(existing?.smbDomain.orEmpty()) }
    var rootPath by remember(existing) { mutableStateOf(existing?.rootPath ?: "/") }
    var timeoutText by remember(existing) { mutableStateOf(((existing?.connectTimeoutMs ?: 15_000) / 1000).toString()) }
    var notes by remember(existing) { mutableStateOf(existing?.notes.orEmpty()) }

    val keyFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
            }.getOrNull()?.let { content ->
                privateKeyPem = content
            }
        }
    }

    val clientProtocols = remember {
        listOf(ProtocolType.SFTP, ProtocolType.FTPS, ProtocolType.FTP, ProtocolType.WEBDAV, ProtocolType.SMB)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New Connection Profile" else "Edit Connection") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    clientProtocols.take(3).forEach { p ->
                        FilterChip(
                            selected = protocol == p,
                            onClick = {
                                protocol = p
                                if (existing == null) portText = p.defaultPort.toString()
                            },
                            label = { Text(p.displayName) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    clientProtocols.drop(3).forEach { p ->
                        FilterChip(
                            selected = protocol == p,
                            onClick = {
                                protocol = p
                                if (existing == null) portText = p.defaultPort.toString()
                            },
                            label = { Text(p.displayName) },
                        )
                    }
                }
                if (protocol == ProtocolType.FTP) {
                    PlainFtpWarningBanner()
                }
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Profile name") }, singleLine = true)
                OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text("Host or IP") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = portText,
                        onValueChange = { portText = it.filter(Char::isDigit) },
                        label = { Text("Port") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = timeoutText,
                        onValueChange = { timeoutText = it.filter(Char::isDigit) },
                        label = { Text("Timeout (s)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(value = rootPath, onValueChange = { rootPath = it }, label = { Text("Initial remote path") }, singleLine = true)
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username (blank for anonymous)") },
                    singleLine = true,
                )
                if (protocol == ProtocolType.SFTP) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !usePrivateKey, onClick = { usePrivateKey = false }, label = { Text("Password") })
                        FilterChip(selected = usePrivateKey, onClick = { usePrivateKey = true }, label = { Text("Private Key") })
                    }
                }
                if (protocol == ProtocolType.SFTP && usePrivateKey) {
                    OutlinedButton(onClick = { keyFilePicker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Import PEM / OpenSSH Key File")
                    }
                    OutlinedTextField(
                        value = privateKeyPem,
                        onValueChange = { privateKeyPem = it },
                        label = { Text(if (existing != null) "Private key PEM (blank keeps existing)" else "Private key PEM") },
                        maxLines = 4,
                    )
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text("Key passphrase (optional)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                } else {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(if (existing != null) "Password (blank keeps existing)" else "Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }
                if (protocol == ProtocolType.FTP || protocol == ProtocolType.FTPS) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Passive mode (PASV)")
                        Switch(checked = passiveMode, onCheckedChange = { passiveMode = it })
                    }
                }
                if (protocol == ProtocolType.FTPS) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = ftpsMode == FtpsMode.EXPLICIT,
                            onClick = { ftpsMode = FtpsMode.EXPLICIT },
                            label = { Text("Explicit TLS") },
                        )
                        FilterChip(
                            selected = ftpsMode == FtpsMode.IMPLICIT,
                            onClick = { ftpsMode = FtpsMode.IMPLICIT },
                            label = { Text("Implicit TLS") },
                        )
                    }
                }
                if (protocol == ProtocolType.FTPS || protocol == ProtocolType.WEBDAV) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = tlsPolicy == TlsVersionPolicy.TLS_1_2_OR_1_3,
                            onClick = { tlsPolicy = TlsVersionPolicy.TLS_1_2_OR_1_3 },
                            label = { Text("TLS 1.2/1.3") },
                        )
                        FilterChip(
                            selected = tlsPolicy == TlsVersionPolicy.TLS_1_3_ONLY,
                            onClick = { tlsPolicy = TlsVersionPolicy.TLS_1_3_ONLY },
                            label = { Text("TLS 1.3 only") },
                        )
                    }
                }
                if (protocol == ProtocolType.WEBDAV) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Use HTTPS")
                        Switch(
                            checked = useHttpsWebDav,
                            onCheckedChange = {
                                useHttpsWebDav = it
                                if (existing == null) portText = if (it) "443" else "80"
                            },
                        )
                    }
                }
                if (protocol == ProtocolType.SMB) {
                    OutlinedTextField(value = smbShare, onValueChange = { smbShare = it }, label = { Text("SMB Share name (e.g. Media)") }, singleLine = true)
                    OutlinedTextField(value = smbDomain, onValueChange = { smbDomain = it }, label = { Text("Workgroup / Domain (optional)") }, singleLine = true)
                }
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes (optional)") }, maxLines = 2)
            }
        },
        confirmButton = {
            Button(
                enabled = host.isNotBlank(),
                onClick = {
                    val port = portText.toIntOrNull()?.coerceIn(1, 65535) ?: protocol.defaultPort
                    val timeoutMs = ((timeoutText.toIntOrNull() ?: 15).coerceIn(3, 120)) * 1000
                    onSave(
                        name,
                        protocol,
                        host,
                        port,
                        username,
                        password,
                        if (usePrivateKey) privateKeyPem else "",
                        if (usePrivateKey) passphrase else "",
                        passiveMode,
                        ftpsMode,
                        tlsPolicy,
                        useHttpsWebDav,
                        smbShare,
                        smbDomain,
                        rootPath,
                        timeoutMs,
                        notes,
                    )
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
