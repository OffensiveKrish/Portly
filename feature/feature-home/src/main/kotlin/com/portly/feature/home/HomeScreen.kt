package com.portly.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.portly.core.database.CredentialVault
import com.portly.core.database.dao.ActivityLogDao
import com.portly.core.database.dao.ConnectionProfileDao
import com.portly.core.database.dao.TransferHistoryDao
import com.portly.core.database.toDomain
import com.portly.core.database.toEntity
import com.portly.core.model.ActivityLogEntry
import com.portly.core.model.ActivitySeverity
import com.portly.core.model.AuthType
import com.portly.core.model.ConnectionProfile
import com.portly.core.model.ProtocolType
import com.portly.core.model.ResolvedCredentials
import com.portly.core.model.TransferEngine
import com.portly.core.model.TransferHistoryEntry
import com.portly.core.model.TransferStatus
import com.portly.core.security.RootCheckResult
import com.portly.core.security.RootDetector
import com.portly.core.ui.components.ProtocolBadge
import com.portly.network.NetworkMonitor
import com.portly.network.NetworkStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

data class HomeUiState(
    val networkStatus: NetworkStatus,
    val profileCount: Int = 0,
    val recentProfiles: List<ConnectionProfile> = emptyList(),
    val activeTransferCount: Int = 0,
    val queuedTransferCount: Int = 0,
    val completedTransferCount: Int = 0,
    val failedTransferCount: Int = 0,
    val recentHistory: List<TransferHistoryEntry> = emptyList(),
    val recentActivity: List<ActivityLogEntry> = emptyList(),
    val rootCheck: RootCheckResult = RootCheckResult(false, emptyList()),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    networkMonitor: NetworkMonitor,
    private val profileDao: ConnectionProfileDao,
    private val credentialVault: CredentialVault,
    transferEngine: TransferEngine,
    private val transferHistoryDao: TransferHistoryDao,
    private val activityLogDao: ActivityLogDao,
    rootDetector: RootDetector,
) : ViewModel() {

    private val rootResult = rootDetector.check()

    val uiState: StateFlow<HomeUiState> = combine(
        networkMonitor.observeStatus(),
        profileDao.observeAll(),
        transferEngine.observeTransfers(),
        transferHistoryDao.observeAll(),
        activityLogDao.observeRecent(limit = 5),
    ) { net, profileEntities, transfers, historyEntities, activityEntities ->
        val profiles = profileEntities.map { it.toDomain() }
        val activeCount = transfers.count { it.status == TransferStatus.RUNNING }
        val queuedCount = transfers.count { it.status == TransferStatus.QUEUED }
        val completedCount = transfers.count { it.status == TransferStatus.COMPLETED }
        val failedCount = transfers.count { it.status == TransferStatus.FAILED }
        val recentHistory = historyEntities.take(3).map { it.toDomain() }
        val recentActivity = activityEntities.map { it.toDomain() }
        HomeUiState(
            networkStatus = net,
            profileCount = profiles.size,
            recentProfiles = profiles.take(5),
            activeTransferCount = activeCount,
            queuedTransferCount = queuedCount,
            completedTransferCount = completedCount,
            failedTransferCount = failedCount,
            recentHistory = recentHistory,
            recentActivity = recentActivity,
            rootCheck = rootResult,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(
            networkStatus = networkMonitor.currentStatus(),
            rootCheck = rootResult,
        ),
    )

    fun quickConnect(
        rawInput: String,
        fallbackProtocol: ProtocolType,
        onCreated: (String) -> Unit,
    ) {
        val trimmed = rawInput.trim().replace('\\', '/')
        if (trimmed.isEmpty()) return
        val hasScheme = trimmed.contains("://")
        val effectiveScheme = if (hasScheme) trimmed.substringBefore("://") else fallbackProtocol.scheme
        val schemeStripped = if (hasScheme) trimmed.substringAfter("://") else trimmed

        val userInfo = if (schemeStripped.contains('@')) schemeStripped.substringBeforeLast('@') else null
        val hostPart = if (schemeStripped.contains('@')) schemeStripped.substringAfterLast('@') else schemeStripped

        val pathPart = if (hostPart.contains('/')) "/" + hostPart.substringAfter('/').trim('/') else "/"
        val hostAndPort = hostPart.substringBefore('/')
        val host = hostAndPort.substringBefore(':').trim()
        val parsedPort = hostAndPort.substringAfter(':', "").toIntOrNull()

        if (host.isBlank()) return

        val proto = when (effectiveScheme.lowercase()) {
            "ftp" -> ProtocolType.FTP
            "ftps" -> ProtocolType.FTPS
            "sftp", "ssh" -> ProtocolType.SFTP
            "webdav", "dav", "davs", "http", "https" -> ProtocolType.WEBDAV
            "smb" -> ProtocolType.SMB
            else -> fallbackProtocol
        }
        val port = parsedPort?.takeIf { it in 1..65535 } ?: proto.defaultPort
        val user = userInfo?.substringBefore(':').orEmpty()
        val pass = userInfo?.substringAfter(':', "")?.takeIf { userInfo.contains(':') }
        val rootPath = pathPart.ifBlank { "/" }

        viewModelScope.launch {
            val id = UUID.randomUUID().toString()
            val secretRef = if (user.isNotBlank() || !pass.isNullOrEmpty()) {
                credentialVault.saveCredentials(null, ResolvedCredentials(user, pass))
            } else {
                null
            }
            val profile = ConnectionProfile(
                id = id,
                name = "$host (${proto.displayName})",
                protocol = proto,
                host = host,
                port = port,
                username = user,
                authType = if (user.isBlank() && pass.isNullOrEmpty()) AuthType.ANONYMOUS else AuthType.PASSWORD,
                authSecretRef = secretRef,
                rootPath = rootPath,
            )
            profileDao.upsert(profile.toEntity())
            onCreated(id)
        }
    }
}

@Composable
fun HomeScreen(
    onOpenFiles: () -> Unit,
    onOpenConnections: () -> Unit,
    onOpenServers: () -> Unit,
    onOpenTransfers: () -> Unit,
    onConnectProfile: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var quickInput by remember { mutableStateOf("") }
    var quickProto by remember { mutableStateOf(ProtocolType.SFTP) }

    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        // Pinned Header (Fixed at the top, does not scroll)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Image(
                    painter = painterResource(com.portly.core.ui.R.drawable.ic_portly_logo),
                    contentDescription = "Portly Logo",
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Portly",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = "Connect. Transfer. Manage. Anywhere.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {

        if (state.rootCheck.isPotentiallyRooted) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Outlined.WarningAmber, contentDescription = "Security warning")
                        Column {
                            Text(
                                text = "Root environment detected",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = "Device integrity checks flagged elevated privileges. Keys and app data may be exposed to privileged processes.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val net = state.networkStatus
                    val statusColor = when {
                        !net.isConnected -> Color(0xFFEF4444)
                        net.transport == com.portly.network.NetworkTransport.WIFI_OR_ETHERNET || net.isHotspotActive -> Color(0xFF10B981)
                        else -> Color(0xFFF59E0B)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(statusColor),
                        )
                        Text(
                            text = "Network Status",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(
                        text = when {
                            !net.isConnected -> "Offline — local file management remains available"
                            net.isHotspotActive -> "Mobile Hotspot Active"
                            net.transport == com.portly.network.NetworkTransport.WIFI_OR_ETHERNET -> "Connected via Wi-Fi / LAN"
                            else -> "Connected via Cellular (Mobile Data)"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (net.localIpv4Addresses.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Text(
                                    text = "IP: ${net.localIpv4Addresses.joinToString(", ")}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                    if (net.isConnected && net.transport != com.portly.network.NetworkTransport.WIFI_OR_ETHERNET && !net.isHotspotActive) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = "Cellular active: Use Wi-Fi or Hotspot to transfer with PC/local devices.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Quick Connect",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Text(
                                text = "Default Port: ${quickProto.defaultPort}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf(ProtocolType.SFTP, ProtocolType.FTPS, ProtocolType.FTP, ProtocolType.WEBDAV, ProtocolType.SMB).forEach { p ->
                            FilterChip(
                                selected = quickProto == p,
                                onClick = { quickProto = p },
                                label = { Text(p.displayName) },
                            )
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = when (quickProto) {
                                        ProtocolType.SFTP -> "SSH Secure File Transfer • Port 22"
                                        ProtocolType.FTPS -> "SSL/TLS Secure FTP • Port 2122 / 990"
                                        ProtocolType.FTP -> "Standard FTP Server • Port 2121 / 21"
                                        ProtocolType.WEBDAV -> "WebDAV HTTP/HTTPS • Port 8080"
                                        ProtocolType.SMB -> "Windows Share / Samba • Port 445"
                                        else -> "${quickProto.displayName} • Port ${quickProto.defaultPort}"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text = when (quickProto) {
                                        ProtocolType.SFTP -> "Connect to Linux, Mac, or SSH server"
                                        ProtocolType.FTPS -> "Encrypted transfer to secure FTP host"
                                        ProtocolType.FTP -> "Local NAS, router, or FTP server"
                                        ProtocolType.WEBDAV -> "Nextcloud, ownCloud, or WebDAV drive"
                                        ProtocolType.SMB -> "Windows PC shared folder or NAS"
                                        else -> "Connect to ${quickProto.displayName} server"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(
                                onClick = {
                                    quickInput = when (quickProto) {
                                        ProtocolType.SFTP -> "192.168.1.100:22"
                                        ProtocolType.FTPS -> "192.168.1.100:2122"
                                        ProtocolType.FTP -> "192.168.1.100:2121"
                                        ProtocolType.WEBDAV -> "192.168.1.100:8080"
                                        ProtocolType.SMB -> "192.168.1.100/SharedFolder"
                                        else -> "192.168.1.100:${quickProto.defaultPort}"
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            ) {
                                Text("Fill Example", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = quickInput,
                            onValueChange = { quickInput = it },
                            label = { Text("${quickProto.displayName} Host / IP") },
                            placeholder = {
                                Text(
                                    text = when (quickProto) {
                                        ProtocolType.SFTP -> "192.168.1.100:22"
                                        ProtocolType.FTPS -> "192.168.1.100:2122"
                                        ProtocolType.FTP -> "192.168.1.100:2121"
                                        ProtocolType.WEBDAV -> "192.168.1.100:8080"
                                        ProtocolType.SMB -> "192.168.1.100/share"
                                        else -> "192.168.1.100:${quickProto.defaultPort}"
                                    },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                )
                            },
                            prefix = {
                                Text(
                                    text = "${quickProto.scheme}://",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                )
                            },
                            trailingIcon = {
                                if (quickInput.isNotBlank()) {
                                    IconButton(onClick = { quickInput = "" }) {
                                        Icon(Icons.Outlined.Clear, contentDescription = "Clear")
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        )
                        Button(
                            enabled = quickInput.isNotBlank(),
                            onClick = {
                                viewModel.quickConnect(quickInput, quickProto) { createdId ->
                                    quickInput = ""
                                    onConnectProfile(createdId)
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.defaultMinSize(minWidth = 72.dp),
                        ) {
                            Text("Go")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Need username or password?",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(
                            onClick = onOpenConnections,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Icon(
                                Icons.Outlined.AddCircleOutline,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("New Profile", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                QuickActionCard(
                    title = "Files",
                    subtitle = "Browse storage",
                    icon = Icons.Outlined.FolderOpen,
                    iconTint = Color(0xFFF59E0B),
                    iconBg = Color(0xFFF59E0B).copy(alpha = 0.15f),
                    onClick = onOpenFiles,
                    modifier = Modifier.weight(1f),
                )
                QuickActionCard(
                    title = "Servers",
                    subtitle = "Host device",
                    icon = Icons.Outlined.Dns,
                    iconTint = Color(0xFF10B981),
                    iconBg = Color(0xFF10B981).copy(alpha = 0.15f),
                    onClick = onOpenServers,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                QuickActionCard(
                    title = "Connections",
                    subtitle = "${state.profileCount} saved",
                    icon = Icons.Outlined.CloudQueue,
                    iconTint = Color(0xFF38BDF8),
                    iconBg = Color(0xFF38BDF8).copy(alpha = 0.15f),
                    onClick = onOpenConnections,
                    modifier = Modifier.weight(1f),
                )
                QuickActionCard(
                    title = "Transfers",
                    subtitle = "${state.activeTransferCount} active",
                    icon = Icons.Outlined.SwapVert,
                    iconTint = Color(0xFFA78BFA),
                    iconBg = Color(0xFFA78BFA).copy(alpha = 0.15f),
                    onClick = onOpenTransfers,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (state.recentProfiles.isNotEmpty()) {
            item {
                Text(
                    text = "Saved & Recent Connections",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
            }
            items(state.recentProfiles, key = { it.id }) { profile ->
                Card(
                    onClick = { onConnectProfile(profile.id) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
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

        // Transfer Overview Card — always shown
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "Transfer Overview",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Icon(
                            imageVector = Icons.Outlined.SwapVert,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TransferStatChip(
                            label = "Active",
                            count = state.activeTransferCount,
                            color = Color(0xFF0EA5E9),
                            modifier = Modifier.weight(1f),
                        )
                        TransferStatChip(
                            label = "Queued",
                            count = state.queuedTransferCount,
                            color = Color(0xFFF59E0B),
                            modifier = Modifier.weight(1f),
                        )
                        TransferStatChip(
                            label = "Done",
                            count = state.completedTransferCount,
                            color = Color(0xFF10B981),
                            modifier = Modifier.weight(1f),
                        )
                        TransferStatChip(
                            label = "Failed",
                            count = state.failedTransferCount,
                            color = Color(0xFFEF4444),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        // Recent Transfer History
        if (state.recentHistory.isNotEmpty()) {
            item {
                Text(
                    text = "Recent Transfers",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() },
                )
            }
            items(state.recentHistory, key = { it.id }) { entry ->
                RecentHistoryRow(entry = entry, onClick = onOpenTransfers)
            }
        }

        // Recent Activity Log
        if (state.recentActivity.isNotEmpty()) {
            item {
                Text(
                    text = "Recent Activity",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() },
                )
            }
            items(state.recentActivity, key = { it.id }) { log ->
                ActivityLogRow(log = log)
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}
}

@Composable
private fun TransferStatChip(
    label: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.12f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecentHistoryRow(
    entry: TransferHistoryEntry,
    onClick: () -> Unit,
) {
    val statusColor = when (entry.status) {
        TransferStatus.COMPLETED -> Color(0xFF10B981)
        TransferStatus.FAILED -> Color(0xFFEF4444)
        TransferStatus.CANCELLED -> Color(0xFFF59E0B)
        else -> Color(0xFF6B7280)
    }
    val statusIcon = when (entry.status) {
        TransferStatus.COMPLETED -> Icons.Outlined.CheckCircle
        TransferStatus.FAILED -> Icons.Outlined.Error
        else -> Icons.Outlined.History
    }
    val df = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = statusIcon,
                contentDescription = entry.status.name,
                tint = statusColor,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = df.format(Date(entry.finishedAtMillis)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = statusColor.copy(alpha = 0.12f),
            ) {
                Text(
                    text = entry.status.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun ActivityLogRow(log: ActivityLogEntry) {
    val tint = when (log.severity) {
        ActivitySeverity.ERROR -> Color(0xFFEF4444)
        ActivitySeverity.WARN -> Color(0xFFF59E0B)
        ActivitySeverity.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val df = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(tint)
                .align(Alignment.CenterVertically),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = log.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = df.format(Date(log.timestampMillis)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun QuickActionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    iconBg: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
