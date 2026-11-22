package com.portly.feature.servers

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.PhonelinkSetup
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.portly.core.common.Formatters
import com.portly.core.database.CredentialVault
import com.portly.core.datastore.PortlySettingsDataStore
import com.portly.core.model.AppSettings
import com.portly.core.model.ConnectedClientInfo
import com.portly.core.model.LogLevel
import com.portly.core.model.ProtocolType
import com.portly.core.model.ResolvedCredentials
import com.portly.core.model.ServerConfig
import com.portly.core.model.ServerLogEntry
import com.portly.core.model.ServerPermission
import com.portly.core.model.ServerRuntimeState
import com.portly.core.model.ServerStatus
import com.portly.core.model.ServerType
import com.portly.core.model.ServerUserAccount
import com.portly.core.security.BiometricAuthHelper
import com.portly.core.security.BiometricResult
import com.portly.core.security.CertificateManager
import com.portly.core.ui.components.EmptyStateView
import com.portly.core.ui.components.PlainFtpWarningBanner
import com.portly.core.ui.components.PortlyErrorCard
import com.portly.network.ActivePairingOffer
import com.portly.network.NetworkMonitor
import com.portly.network.NetworkStatus
import com.portly.network.NetworkTransport
import com.portly.network.PeerPairingManager
import com.portly.network.PortlyServerManager
import com.portly.network.QrCodeGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ServersViewModel @Inject constructor(
    private val serverManager: PortlyServerManager,
    private val peerPairingManager: PeerPairingManager,
    private val certificateManager: CertificateManager,
    private val credentialVault: CredentialVault,
    networkMonitor: NetworkMonitor,
    settingsDataStore: PortlySettingsDataStore,
    val biometricAuthHelper: BiometricAuthHelper,
) : ViewModel() {

    val networkStatus: StateFlow<NetworkStatus> = networkMonitor.observeStatus().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = networkMonitor.currentStatus(),
    )

    val serverStates: StateFlow<Map<ServerType, ServerRuntimeState>> = serverManager.states
    val serverConfigs: StateFlow<Map<ServerType, ServerConfig>> = serverManager.configs
    val clients: StateFlow<List<ConnectedClientInfo>> = serverManager.clients
    val logs: StateFlow<List<ServerLogEntry>> = serverManager.logs
    val settings: StateFlow<AppSettings> = settingsDataStore.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings(),
    )

    private val _pairingOffer = MutableStateFlow<ActivePairingOffer?>(null)
    val pairingOffer: StateFlow<ActivePairingOffer?> = _pairingOffer.asStateFlow()

    private val _pairingSecondsRemaining = MutableStateFlow(0)
    val pairingSecondsRemaining: StateFlow<Int> = _pairingSecondsRemaining.asStateFlow()

    private var pairingTimerJob: Job? = null

    fun isSupported(type: ServerType): Boolean = serverManager.isProtocolSupported(type)

    fun toggleServer(type: ServerType) {
        viewModelScope.launch {
            val current = serverStates.value[type]?.status ?: ServerStatus.STOPPED
            if (current == ServerStatus.RUNNING || current == ServerStatus.STARTING) {
                serverManager.stopServer(type)
            } else {
                serverManager.startServer(type)
            }
        }
    }

    fun startPeerPairing(serverType: ServerType) {
        viewModelScope.launch {
            val baseConfig = serverConfigs.value[serverType] ?: return@launch
            val host = networkStatus.value.localIpv4Addresses.firstOrNull() ?: "127.0.0.1"
            val protocol = when (serverType) {
                ServerType.FTPS -> ProtocolType.FTPS
                ServerType.FTP -> ProtocolType.FTP
                else -> ProtocolType.FTPS
            }
            val certFp = if (serverType == ServerType.FTPS) {
                runCatching {
                    val ref = "portly_ftps_server_keystore_pw"
                    val existingPw = credentialVault.loadCredentials(ref, "ftps_keystore").password
                    val pw = if (!existingPw.isNullOrEmpty()) {
                        existingPw
                    } else {
                        val bytes = ByteArray(24).also { java.security.SecureRandom().nextBytes(it) }
                        val generated = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
                        credentialVault.saveCredentials(ref, ResolvedCredentials("ftps_keystore", generated))
                        generated
                    }
                    certificateManager.generateOrLoadServerKeystore(
                        serverName = "ftps",
                        sanIpAddresses = listOf(host),
                        keystorePassword = pw.toCharArray(),
                    ).sha256Fingerprint
                }.getOrNull()
            } else {
                null
            }
            val offer = peerPairingManager.createPairingOffer(
                protocol = protocol,
                host = host,
                port = baseConfig.port,
                sha256CertFingerprint = certFp,
                advertiseNsd = true,
            )
            val withoutOldPeer = baseConfig.users.filterNot { it.username == PeerPairingManager.PEER_USERNAME }
            val peerAccount = ServerUserAccount(
                username = PeerPairingManager.PEER_USERNAME,
                isAnonymous = false,
                permissions = setOf(
                    ServerPermission.READ,
                    ServerPermission.WRITE,
                    ServerPermission.DELETE,
                    ServerPermission.MKDIR,
                ),
            )
            serverManager.updateConfig(
                config = baseConfig.copy(users = withoutOldPeer + peerAccount),
                newPasswordsByUsername = mapOf(PeerPairingManager.PEER_USERNAME to offer.payload.token),
            )
            val runtimeStatus = serverStates.value[serverType]?.status ?: ServerStatus.STOPPED
            if (runtimeStatus != ServerStatus.RUNNING) {
                serverManager.startServer(serverType)
            }
            _pairingOffer.value = offer
            pairingTimerJob?.cancel()
            pairingTimerJob = viewModelScope.launch {
                while (true) {
                    val active = peerPairingManager.getActiveOffer()
                    if (active == null) {
                        _pairingOffer.value = null
                        _pairingSecondsRemaining.value = 0
                        break
                    }
                    val remaining = ((active.payload.expiresAtMillis - System.currentTimeMillis()) / 1_000L)
                        .toInt()
                        .coerceAtLeast(0)
                    _pairingSecondsRemaining.value = remaining
                    if (remaining <= 0) {
                        cancelPeerPairing()
                        break
                    }
                    delay(1_000L)
                }
            }
        }
    }

    fun cancelPeerPairing() {
        pairingTimerJob?.cancel()
        peerPairingManager.cancelActiveOffer()
        _pairingOffer.value = null
        _pairingSecondsRemaining.value = 0
    }

    fun kickClient(client: ConnectedClientInfo) {
        viewModelScope.launch {
            serverManager.kickClient(client.serverType, client.clientId)
        }
    }

    fun saveConfig(config: ServerConfig, newPasswords: Map<String, String>) {
        viewModelScope.launch {
            serverManager.updateConfig(config, newPasswords)
        }
    }
}

@Composable
fun ServersScreen(
    modifier: Modifier = Modifier,
    viewModel: ServersViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val net by viewModel.networkStatus.collectAsStateWithLifecycle()
    val states by viewModel.serverStates.collectAsStateWithLifecycle()
    val configs by viewModel.serverConfigs.collectAsStateWithLifecycle()
    val clients by viewModel.clients.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val pairingOffer by viewModel.pairingOffer.collectAsStateWithLifecycle()
    val pairingSeconds by viewModel.pairingSecondsRemaining.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    var editingConfig by remember { mutableStateOf<ServerConfig?>(null) }
    var qrUrlToShow by remember { mutableStateOf<Pair<ServerType, String>?>(null) }
    var showPairDialog by remember { mutableStateOf(false) }

    val safRootPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        val currentEditing = editingConfig
        if (uri != null && currentEditing != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            editingConfig = currentEditing.copy(
                rootTreeUriString = uri.toString(),
                rootDirectoryPath = uri.toString(),
            )
        }
    }

    fun openConfigWithOptionalBiometric(cfg: ServerConfig) {
        val activity = context as? FragmentActivity
        if (settings.biometricLockEnabled && activity != null) {
            viewModel.biometricAuthHelper.authenticate(
                activity = activity,
                title = "Authenticate",
                subtitle = "Confirm identity to modify ${cfg.serverType.displayName} settings",
            ) { result ->
                if (result is BiometricResult.Succeeded) {
                    editingConfig = cfg
                } else {
                    scope.launch {
                        snackbarHostState.showSnackbar("Authentication required to edit server settings.")
                    }
                }
            }
        } else {
            editingConfig = cfg
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Servers",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = when {
                            net.isHotspotActive -> "Hotspot IP: ${net.localIpv4Addresses.joinToString(", ").ifEmpty { "192.168.43.1" }}"
                            net.localIpv4Addresses.isNotEmpty() -> "LAN / Wi-Fi IP: ${net.localIpv4Addresses.joinToString(", ")}"
                            else -> "No Wi-Fi / Hotspot active (Turn on to connect PC)"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (net.localIpv4Addresses.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalButton(
                    onClick = {
                        showPairDialog = true
                        viewModel.startPeerPairing(ServerType.FTPS)
                    },
                ) {
                    Icon(Icons.Outlined.PhonelinkSetup, contentDescription = null)
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("Pair Peer")
                }
            }

            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Servers") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Clients (${clients.size})") })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Logs (${logs.size})") })
            }

            when (selectedTab) {
                0 -> {
                    var hasLocalNetPerm by remember {
                        mutableStateOf(
                            Build.VERSION.SDK_INT < 37 ||
                                ContextCompat.checkSelfPermission(context, "android.permission.ACCESS_LOCAL_NETWORK") ==
                                PackageManager.PERMISSION_GRANTED,
                        )
                    }
                    val localNetLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission(),
                    ) { granted ->
                        hasLocalNetPerm = granted
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Network & Hotspot Prerequisite Banner
                        item {
                            val hasLocalIp = net.localIpv4Addresses.isNotEmpty()
                            val isReady = net.transport == NetworkTransport.WIFI_OR_ETHERNET || net.isHotspotActive || hasLocalIp

                            if (!isReady) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                                    ),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.WifiOff,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(20.dp),
                                            )
                                            Text(
                                                text = "Wi-Fi or Hotspot Required for PC Access",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                            )
                                        }
                                        Text(
                                            text = "Other devices (e.g. PC, laptop, phone) cannot reach this server over Cellular / Mobile Data due to carrier firewalls.\n\nPrerequisite: Turn on Mobile Hotspot on this phone or connect both devices to the same Wi-Fi router.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    runCatching {
                                                        context.startActivity(Intent("android.settings.TETHER_SETTINGS"))
                                                    }.onFailure {
                                                        context.startActivity(Intent(AndroidSettings.ACTION_WIRELESS_SETTINGS))
                                                    }
                                                },
                                                modifier = Modifier.weight(1f),
                                            ) {
                                                Icon(
                                                    Icons.Outlined.WifiTethering,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Turn On Hotspot", style = MaterialTheme.typography.labelMedium)
                                            }
                                            Button(
                                                onClick = {
                                                    context.startActivity(Intent(AndroidSettings.ACTION_WIFI_SETTINGS))
                                                },
                                                modifier = Modifier.weight(1f),
                                            ) {
                                                Icon(
                                                    Icons.Outlined.Wifi,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Open Wi-Fi", style = MaterialTheme.typography.labelMedium)
                                            }
                                        }
                                    }
                                }
                            } else {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    ),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF10B981)),
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (net.isHotspotActive) "Mobile Hotspot Active" else "Ready for Local Connection",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Medium,
                                            )
                                            Text(
                                                text = "Clients connected to ${if (net.isHotspotActive) "your Hotspot" else "the same Wi-Fi"} can access servers at ${net.localIpv4Addresses.firstOrNull() ?: "local IP"}.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                runCatching {
                                                    context.startActivity(Intent("android.settings.TETHER_SETTINGS"))
                                                }.onFailure {
                                                    context.startActivity(Intent(AndroidSettings.ACTION_WIRELESS_SETTINGS))
                                                }
                                            },
                                            modifier = Modifier.size(36.dp),
                                        ) {
                                            Icon(
                                                Icons.Outlined.WifiTethering,
                                                contentDescription = "Hotspot settings",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (!hasLocalNetPerm) {
                            item {
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            text = "Android 17 requires Local Network permission for browsers on Wi-Fi to reach servers.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                                        )
                                        Button(onClick = { localNetLauncher.launch("android.permission.ACCESS_LOCAL_NETWORK") }) {
                                            Text("Grant")
                                        }
                                    }
                                }
                            }
                        }
                        val visibleServers = ServerType.entries.filter { serverType ->
                            if (settings.hideLegacyUnencryptedServers) {
                                serverType == ServerType.FTPS || serverType == ServerType.HTTPS
                            } else {
                                true
                            }
                        }
                        items(visibleServers) { serverType ->
                        val runtime = states[serverType] ?: ServerRuntimeState(serverType = serverType)
                        val config = configs[serverType]
                        val supported = viewModel.isSupported(serverType)

                        ServerCard(
                            serverType = serverType,
                            runtime = runtime,
                            config = config,
                            supported = supported,
                            onToggle = { viewModel.toggleServer(serverType) },
                            onConfigure = {
                                if (config != null) openConfigWithOptionalBiometric(config)
                            },
                            onCopyUrl = { url ->
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Portly Server URL", url))
                                scope.launch { snackbarHostState.showSnackbar("Copied $url") }
                            },
                            onShareUrl = { url ->
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, url)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share server URL"))
                            },
                            onShowQr = { url -> qrUrlToShow = serverType to url },
                            onOpenUrl = { url ->
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }.onFailure {
                                    scope.launch { snackbarHostState.showSnackbar("No app found to open $url") }
                                }
                            },
                        )
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
                }

                1 -> if (clients.isEmpty()) {
                    EmptyStateView(
                        icon = Icons.Outlined.PersonOff,
                        title = "No connected clients",
                        subtitle = "Active client sessions will appear here with real-time actions and a kick control.",
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(clients, key = { it.clientId }) { client ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "${client.serverType.name} • ${client.remoteAddress} (${client.username})",
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                        Text(
                                            "${client.currentAction} ${client.currentFile.orEmpty()} • ${Formatters.formatBytes(client.bytesTransferred)}".trim(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    TextButton(onClick = { viewModel.kickClient(client) }) {
                                        Text("Kick")
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> if (logs.isEmpty()) {
                    EmptyStateView(
                        icon = Icons.Outlined.Settings,
                        title = "No server logs yet",
                        subtitle = "Start a server to view redacted INFO/WARN/ERROR logs.",
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(logs, key = { it.id }) { entry ->
                            Surface(
                                color = when (entry.level) {
                                    LogLevel.ERROR -> MaterialTheme.colorScheme.errorContainer
                                    LogLevel.WARN -> MaterialTheme.colorScheme.secondaryContainer
                                    LogLevel.INFO -> MaterialTheme.colorScheme.surfaceVariant
                                },
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        "[${entry.serverType.name} • ${entry.level}] ${Formatters.formatTimestamp(entry.timestampMillis)}",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                    Text(
                                        entry.message,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editingConfig?.let { cfg ->
        ServerConfigDialog(
            initialConfig = cfg,
            onPickSafRoot = { safRootPicker.launch(null) },
            onSave = { updated, passwords ->
                viewModel.saveConfig(updated, passwords)
                editingConfig = null
            },
            onDismiss = { editingConfig = null },
        )
    }

    qrUrlToShow?.let { (type, url) ->
        val bmp = remember(url) { QrCodeGenerator.generateBitmap(url, 512) }
        AlertDialog(
            onDismissRequest = { qrUrlToShow = null },
            title = { Text("${type.displayName} QR") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "QR code for $url",
                        modifier = Modifier.size(220.dp),
                    )
                    Text(url, style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                TextButton(onClick = { qrUrlToShow = null }) { Text("Close") }
            },
        )
    }

    if (showPairDialog) {
        val offer = pairingOffer
        AlertDialog(
            onDismissRequest = {
                viewModel.cancelPeerPairing()
                showPairDialog = false
            },
            title = { Text("Pair Android Peer (60s)") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(ServerType.FTPS, ServerType.FTP).forEach { candidate ->
                            FilterChip(
                                selected = offer?.payload?.protocol?.name == candidate.name,
                                onClick = { viewModel.startPeerPairing(candidate) },
                                label = { Text(candidate.displayName) },
                            )
                        }
                    }
                    if (offer != null) {
                        val bmp = remember(offer.pairingUri) { QrCodeGenerator.generateBitmap(offer.pairingUri, 512) }
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Peer pairing QR code",
                            modifier = Modifier.size(210.dp),
                        )
                        Text(
                            text = "Expires in ${pairingSeconds}s • Advertising _portly._tcp. on LAN",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "${offer.payload.protocol.displayName} • ${offer.payload.host}:${offer.payload.port}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        offer.payload.sha256CertFingerprint?.let { fp ->
                            Text(
                                text = "Cert SHA-256: $fp",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("Portly Pairing Link", offer.pairingUri))
                                    scope.launch { snackbarHostState.showSnackbar("Copied pairing link") }
                                },
                            ) {
                                Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                                Spacer(modifier = Modifier.size(4.dp))
                                Text("Copy Link")
                            }
                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, offer.pairingUri)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Share Portly pairing link"))
                                },
                            ) {
                                Icon(Icons.Outlined.Share, contentDescription = null)
                                Spacer(modifier = Modifier.size(4.dp))
                                Text("Share")
                            }
                        }
                    } else {
                        Text(
                            text = "Pairing window expired or stopped.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(onClick = { viewModel.startPeerPairing(ServerType.FTPS) }) {
                            Text("Generate New 60s Token")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.cancelPeerPairing()
                        showPairDialog = false
                    },
                ) {
                    Text("Done")
                }
            },
        )
    }
}

@Composable
private fun ServerCard(
    serverType: ServerType,
    runtime: ServerRuntimeState,
    config: ServerConfig?,
    supported: Boolean,
    onToggle: () -> Unit,
    onConfigure: () -> Unit,
    onCopyUrl: (String) -> Unit,
    onShareUrl: (String) -> Unit,
    onShowQr: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val isRunning = runtime.status == ServerStatus.RUNNING
    val serverColor = when (serverType) {
        ServerType.HTTPS -> Color(0xFF34D399) // Emerald
        ServerType.FTPS -> Color(0xFFA78BFA) // Purple
        ServerType.HTTP -> Color(0xFF38BDF8) // Sky
        ServerType.FTP -> Color(0xFFFBBF24) // Amber
    }

    var showOtherUrls by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Protocol icon
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(serverColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Dns,
                        contentDescription = null,
                        tint = serverColor,
                        modifier = Modifier.size(22.dp),
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(serverType.displayName, style = MaterialTheme.typography.titleMedium)
                        Surface(
                            color = if (isRunning) Color(0xFF064E3B) else MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = CircleShape,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (isRunning) Color(0xFF34D399) else MaterialTheme.colorScheme.outline),
                                )
                                Text(
                                    text = if (isRunning) "RUNNING" else "STOPPED",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isRunning) Color(0xFF34D399) else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Text(
                        text = when {
                            !supported -> "Available in upcoming phase"
                            isRunning -> "Port ${runtime.port} • ${runtime.activeClientCount} client(s) • ${Formatters.formatBytes(runtime.totalBytesServed)} served"
                            else -> "Port ${config?.port ?: serverType.defaultPort}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onConfigure, enabled = config != null) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Configure ${serverType.displayName}")
                    }
                    Switch(
                        checked = isRunning,
                        enabled = supported,
                        onCheckedChange = { onToggle() },
                    )
                }
            }

            if (isRunning && !serverType.isEncrypted) {
                PlainFtpWarningBanner()
            }

            if (isRunning && runtime.activeUrls.isNotEmpty()) {
                val primaryUrl = runtime.activeUrls.firstOrNull { !it.contains("127.0.0.1") } ?: runtime.activeUrls.first()
                val otherUrls = runtime.activeUrls.filter { it != primaryUrl }

                // Primary URL Card
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = primaryUrl,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        if (primaryUrl.startsWith("http://") || primaryUrl.startsWith("https://")) {
                            IconButton(onClick = { onOpenUrl(primaryUrl) }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Outlined.OpenInBrowser, contentDescription = "Open in browser", modifier = Modifier.size(20.dp))
                            }
                        }
                        IconButton(onClick = { onCopyUrl(primaryUrl) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy URL", modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = { onShareUrl(primaryUrl) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Outlined.Share, contentDescription = "Share URL", modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = { onShowQr(primaryUrl) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Outlined.QrCode2, contentDescription = "Show QR code", modifier = Modifier.size(20.dp))
                        }
                    }
                }

                // Other URLs dropdown
                if (otherUrls.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showOtherUrls = !showOtherUrls }
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = if (showOtherUrls) "Hide other addresses" else "+${otherUrls.size} other addresses (loopback/LAN)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        Icon(
                            imageVector = if (showOtherUrls) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp),
                        )
                    }

                    if (showOtherUrls) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            otherUrls.forEach { secondaryUrl ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = secondaryUrl,
                                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f),
                                        )
                                        IconButton(onClick = { onCopyUrl(secondaryUrl) }, modifier = Modifier.size(32.dp)) {
                                            Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            runtime.lastError?.let { err ->
                PortlyErrorCard(error = err)
            }
        }
    }
}

@Composable
private fun ServerConfigDialog(
    initialConfig: ServerConfig,
    onPickSafRoot: () -> Unit,
    onSave: (ServerConfig, Map<String, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var portText by remember(initialConfig) { mutableStateOf(initialConfig.port.toString()) }
    var readOnly by remember(initialConfig) { mutableStateOf(initialConfig.readOnly) }
    var maxConnText by remember(initialConfig) { mutableStateOf(initialConfig.maxConnections.toString()) }
    var maxPerIpText by remember(initialConfig) { mutableStateOf(initialConfig.maxConnectionsPerIp.toString()) }
    var allowPublicBind by remember(initialConfig) { mutableStateOf(initialConfig.allowPublicBindOptIn) }
    var requireAuth by remember(initialConfig) { mutableStateOf(initialConfig.requireAuth) }
    var username by remember { mutableStateOf(initialConfig.users.firstOrNull { !it.isAnonymous }?.username ?: "portly") }
    var password by remember { mutableStateOf("") }
    var allowWrite by remember { mutableStateOf(true) }
    var allowDelete by remember { mutableStateOf(true) }
    var allowMkdir by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configure ${initialConfig.serverType.displayName}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Root: ${initialConfig.rootDirectoryPath}", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = onPickSafRoot, modifier = Modifier.fillMaxWidth()) {
                    Text("Choose Root Folder (SAF)")
                }
                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it.filter(Char::isDigit) },
                    label = { Text("Port (1024-65535)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = maxConnText,
                        onValueChange = { maxConnText = it.filter(Char::isDigit) },
                        label = { Text("Max clients") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = maxPerIpText,
                        onValueChange = { maxPerIpText = it.filter(Char::isDigit) },
                        label = { Text("Per IP") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = readOnly, onCheckedChange = { readOnly = it })
                    Text("Read-only mode")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = allowPublicBind, onCheckedChange = { allowPublicBind = it })
                    Text("Allow non-LAN bind (0.0.0.0 opt-in)")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = requireAuth, onCheckedChange = { requireAuth = it })
                    Text("Require username & password")
                }
                if (requireAuth) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password (leave blank to keep existing)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = allowWrite, onClick = { allowWrite = !allowWrite }, label = { Text("Write") })
                        FilterChip(selected = allowDelete, onClick = { allowDelete = !allowDelete }, label = { Text("Delete") })
                        FilterChip(selected = allowMkdir, onClick = { allowMkdir = !allowMkdir }, label = { Text("Mkdir") })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val port = (portText.toIntOrNull() ?: initialConfig.port).coerceIn(1024, 65535)
                    val maxConn = (maxConnText.toIntOrNull() ?: initialConfig.maxConnections).coerceIn(1, 50)
                    val maxIp = (maxPerIpText.toIntOrNull() ?: initialConfig.maxConnectionsPerIp).coerceIn(1, 20)
                    val users = if (requireAuth) {
                        val perms = buildSet {
                            add(ServerPermission.READ)
                            if (allowWrite) add(ServerPermission.WRITE)
                            if (allowDelete) add(ServerPermission.DELETE)
                            if (allowMkdir) add(ServerPermission.MKDIR)
                        }
                        val existing = initialConfig.users.firstOrNull { it.username == username.trim() }
                        listOf(
                            ServerUserAccount(
                                username = username.trim().ifEmpty { "portly" },
                                secretRef = existing?.secretRef,
                                isAnonymous = false,
                                permissions = perms,
                            ),
                        )
                    } else {
                        listOf(ServerUserAccount(username = "anonymous", isAnonymous = true))
                    }
                    val pwMap = if (requireAuth && password.isNotEmpty()) {
                        mapOf(users.first().username to password)
                    } else {
                        emptyMap()
                    }
                    onSave(
                        initialConfig.copy(
                            port = port,
                            readOnly = readOnly,
                            maxConnections = maxConn,
                            maxConnectionsPerIp = maxIp,
                            lanOnlyBind = !allowPublicBind,
                            allowPublicBindOptIn = allowPublicBind,
                            requireAuth = requireAuth,
                            users = users,
                        ),
                        pwMap,
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
