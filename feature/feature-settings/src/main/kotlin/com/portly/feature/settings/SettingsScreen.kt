package com.portly.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.portly.core.database.dao.ActivityLogDao
import com.portly.core.database.dao.TransferHistoryDao
import com.portly.core.database.toDomain
import com.portly.core.datastore.PortlySettingsDataStore
import com.portly.core.common.update.UpdateManager
import com.portly.core.model.AppSettings
import com.portly.core.model.ConflictPolicy
import com.portly.core.model.EnterprisePolicy
import com.portly.core.model.HostKeyMode
import com.portly.core.model.ReleaseChannel
import com.portly.core.model.ThemeMode
import com.portly.core.model.UpdateInfo
import com.portly.core.model.UpdateStatus
import com.portly.core.security.BiometricAuthHelper
import com.portly.core.security.BiometricAvailability
import com.portly.core.security.EnterprisePolicyManager
import com.portly.core.ui.R as CoreUiR
import com.portly.core.ui.theme.PortlyBrand
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.net.InetAddress
import javax.inject.Inject
import kotlin.math.roundToInt

enum class SettingsCategory(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accentColor: Color,
) {
    SERVER(
        title = "Server Configuration & DDNS",
        subtitle = "Ports, root folder, idle timeout, DuckDNS, web access",
        icon = Icons.Outlined.Dns,
        accentColor = Color(0xFF2DD4BF), // Teal
    ),
    FILE_MANAGEMENT(
        title = "File Management",
        subtitle = "Downloads directory, overwrite rules, hidden files, folder stats",
        icon = Icons.Outlined.FolderOpen,
        accentColor = Color(0xFFFBBF24), // Amber
    ),
    TRANSFERS(
        title = "Transfers & Performance",
        subtitle = "Concurrency limits, timeouts, buffer size, retry policy",
        icon = Icons.Outlined.SwapVert,
        accentColor = Color(0xFFA78BFA), // Purple
    ),
    APPEARANCE(
        title = "Appearance & Theme",
        subtitle = "Theme mode (Dark, Light, AMOLED), dynamic Material You",
        icon = Icons.Outlined.Palette,
        accentColor = Color(0xFF38BDF8), // Sky
    ),
    SECURITY(
        title = "Security & Protocols",
        subtitle = "Biometrics, strict SSH host keys, SMB1, server encryption",
        icon = Icons.Outlined.Security,
        accentColor = Color(0xFF34D399), // Emerald
    ),
    PRIVACY_LOGS(
        title = "Privacy & Audit Diagnostics",
        subtitle = "Operational logs, retention window, redacted export, cache prune",
        icon = Icons.Outlined.Policy,
        accentColor = Color(0xFFFB7185), // Rose
    ),
    ABOUT(
        title = "About & Support",
        subtitle = "App version, feedback & bug report, privacy policy",
        icon = Icons.Outlined.Info,
        accentColor = Color(0xFF94A3B8), // Slate
    ),
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsStore: PortlySettingsDataStore,
    private val activityLogDao: ActivityLogDao,
    private val transferHistoryDao: TransferHistoryDao,
    val updateManager: UpdateManager,
    enterprisePolicyManager: EnterprisePolicyManager,
    biometricAuthHelper: BiometricAuthHelper,
) : ViewModel() {

    val biometricAvailability: BiometricAvailability = biometricAuthHelper.availability()
    val enterprisePolicy: EnterprisePolicy = enterprisePolicyManager.currentPolicy()

    val settings: StateFlow<AppSettings> = settingsStore.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings(),
    )

    val updateStatus: StateFlow<UpdateStatus> = updateManager.status

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    // Automatic Updates
    fun checkForUpdates() {
        val s = settings.value
        updateManager.checkForUpdates(
            repo = s.customGitHubRepo,
            channel = s.updateReleaseChannel,
        ) {
            viewModelScope.launch {
                settingsStore.setLastUpdateCheckTimestamp(System.currentTimeMillis())
            }
        }
    }

    fun startDownload(updateInfo: UpdateInfo) = updateManager.startDownload(updateInfo)
    fun installApk(apkPath: String): Boolean = updateManager.installApk(apkPath)
    fun canRequestPackageInstalls(): Boolean = updateManager.canRequestPackageInstalls()
    fun getInstallPermissionIntent() = updateManager.getInstallPermissionIntent()
    fun resetUpdateStatus() = updateManager.resetStatus()

    fun setAutoCheckUpdates(enabled: Boolean) = viewModelScope.launch { settingsStore.setAutoCheckUpdates(enabled) }
    fun setUpdateReleaseChannel(channel: ReleaseChannel) = viewModelScope.launch { settingsStore.setUpdateReleaseChannel(channel) }
    fun setNotifyOnUpdate(enabled: Boolean) = viewModelScope.launch { settingsStore.setNotifyOnUpdate(enabled) }
    fun setCustomGitHubRepo(repo: String) = viewModelScope.launch {
        val target = if (repo.isBlank() || repo.equals("upgrdify/portly", ignoreCase = true)) {
            "OffensiveKrish/Portly"
        } else {
            repo.trim()
        }
        settingsStore.setCustomGitHubRepo(target)
        _statusMessage.value = "GitHub repository updated: $target"
    }

    // Appearance
    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settingsStore.setThemeMode(mode) }
    fun setUseDynamicColor(enabled: Boolean) = viewModelScope.launch { settingsStore.setUseDynamicColor(enabled) }

    // Transfers & Concurrency
    fun setDefaultConcurrency(value: Int) = viewModelScope.launch { settingsStore.setDefaultConcurrency(value) }
    fun setDefaultConflictPolicy(policy: ConflictPolicy) = viewModelScope.launch { settingsStore.setDefaultConflictPolicy(policy) }
    fun setAutoRetry(enabled: Boolean) = viewModelScope.launch { settingsStore.setAutoRetry(enabled) }
    fun setVerifyChecksums(enabled: Boolean) = viewModelScope.launch { settingsStore.setVerifyChecksums(enabled) }
    fun setWifiOnlyTransfers(enabled: Boolean) = viewModelScope.launch { settingsStore.setWifiOnlyTransfers(enabled) }
    fun setConnectionTimeoutSeconds(seconds: Int) = viewModelScope.launch { settingsStore.setConnectionTimeoutSeconds(seconds) }
    fun setTransferBufferSizeBytes(bytes: Int) = viewModelScope.launch { settingsStore.setTransferBufferSizeBytes(bytes) }
    fun setRetryAttempts(attempts: Int) = viewModelScope.launch { settingsStore.setRetryAttempts(attempts) }
    fun setResumeTransfers(resume: Boolean) = viewModelScope.launch { settingsStore.setResumeTransfers(resume) }
    fun setAllowBackgroundTransfers(allow: Boolean) = viewModelScope.launch { settingsStore.setAllowBackgroundTransfers(allow) }

    // File Management
    fun setShowHiddenFiles(show: Boolean) = viewModelScope.launch { settingsStore.setShowHiddenFiles(show) }
    fun setOverwriteExistingFiles(overwrite: Boolean) = viewModelScope.launch { settingsStore.setOverwriteExistingFiles(overwrite) }
    fun setDynamicDownloadPath(dynamic: Boolean) = viewModelScope.launch { settingsStore.setDynamicDownloadPath(dynamic) }
    fun setDownloadDirectory(path: String) = viewModelScope.launch {
        settingsStore.setDownloadDirectory(path)
        _statusMessage.value = "Download path updated: $path"
    }
    fun setShowFolderStats(show: Boolean) = viewModelScope.launch { settingsStore.setShowFolderStats(show) }
    fun setHorizontalDualPaneMode(enabled: Boolean) = viewModelScope.launch { settingsStore.setHorizontalDualPaneMode(enabled) }

    // Server Configuration
    fun setServerAutoStopWhenIdle(enabled: Boolean) = viewModelScope.launch { settingsStore.setServerAutoStopWhenIdle(enabled) }
    fun setServerIdleTimeoutMinutes(minutes: Int) = viewModelScope.launch { settingsStore.setServerIdleTimeoutMinutes(minutes) }
    fun setServerPorts(ftp: Int, ftps: Int, http: Int, https: Int) = viewModelScope.launch {
        settingsStore.setServerFtpPort(ftp)
        settingsStore.setServerFtpsPort(ftps)
        settingsStore.setServerHttpPort(http)
        settingsStore.setServerHttpsPort(https)
        _statusMessage.value = "Server ports updated."
    }
    fun setServerRootFolder(path: String) = viewModelScope.launch {
        settingsStore.setServerRootFolder(path)
        _statusMessage.value = "Server shared path updated: $path"
    }
    fun setServerAllowAnonymousLogin(allow: Boolean) = viewModelScope.launch { settingsStore.setServerAllowAnonymousLogin(allow) }
    fun setServerReadOnlyMode(readOnly: Boolean) = viewModelScope.launch { settingsStore.setServerReadOnlyMode(readOnly) }
    fun setServerShowMultipleIps(show: Boolean) = viewModelScope.launch { settingsStore.setServerShowMultipleIps(show) }
    fun setServerEnableSslTls(enable: Boolean) = viewModelScope.launch { settingsStore.setServerEnableSslTls(enable) }
    fun setAllowBrowserAccessHttp(allow: Boolean) = viewModelScope.launch { settingsStore.setAllowBrowserAccessHttp(allow) }
    fun setShowHiddenFilesInWeb(show: Boolean) = viewModelScope.launch { settingsStore.setShowHiddenFilesInWeb(show) }

    // DuckDNS DDNS
    fun setDuckDnsEnabled(enabled: Boolean) = viewModelScope.launch { settingsStore.setDuckDnsEnabled(enabled) }
    fun setDuckDnsHostname(hostname: String) = viewModelScope.launch { settingsStore.setDuckDnsHostname(hostname) }
    fun setDuckDnsToken(token: String) = viewModelScope.launch { settingsStore.setDuckDnsToken(token) }
    fun testDuckDns(hostname: String) = viewModelScope.launch(Dispatchers.IO) {
        val cleanHost = hostname.trim()
        if (cleanHost.isBlank()) {
            _statusMessage.value = "Please configure a DDNS hostname first."
            return@launch
        }
        val target = if (cleanHost.contains(".")) cleanHost else "$cleanHost.duckdns.org"
        try {
            val addr = InetAddress.getByName(target)
            _statusMessage.value = "DDNS OK: $target resolved to ${addr.hostAddress}"
        } catch (e: Exception) {
            _statusMessage.value = "DDNS Failed: Could not resolve $target (${e.localizedMessage ?: e.message})"
        }
    }

    // Security & Protocols
    fun setBiometricLock(enabled: Boolean) = viewModelScope.launch { settingsStore.setBiometricLockEnabled(enabled) }
    fun setStrictHostKeyMode(mode: HostKeyMode) = viewModelScope.launch { settingsStore.setStrictHostKeyMode(mode) }
    fun setSmbLegacyEnabled(enabled: Boolean) = viewModelScope.launch { settingsStore.setSmbLegacyEnabled(enabled) }
    fun setHideLegacyUnencryptedServers(enabled: Boolean) = viewModelScope.launch { settingsStore.setHideLegacyUnencryptedServers(enabled) }

    // Privacy & Diagnostics
    fun setDiagnosticLoggingEnabled(enabled: Boolean) = viewModelScope.launch { settingsStore.setDiagnosticLoggingEnabled(enabled) }
    fun setLogRetentionDays(days: Int) = viewModelScope.launch { settingsStore.setLogRetentionDays(days) }
    fun dismissBatteryPrompt() = viewModelScope.launch { settingsStore.setBatteryPromptDismissed(true) }

    fun pruneExpiredLogs() = viewModelScope.launch {
        val retentionDays = settings.value.logRetentionDays.coerceIn(1, 365)
        val cutoff = System.currentTimeMillis() - retentionDays * 86_400_000L
        activityLogDao.pruneOlderThan(cutoff)
        transferHistoryDao.pruneOlderThan(cutoff)
        _statusMessage.value = "Pruned logs older than $retentionDays day(s)."
    }

    fun exportRedactedDiagnosticBundle(context: Context, uri: Uri) = viewModelScope.launch {
        runCatching {
            val currentSettings = settings.value
            val logs = activityLogDao.observeRecent(500).first().map { it.toDomain() }
            val history = transferHistoryDao.observeAll().first().map { it.toDomain() }
            val payload = DiagnosticBundleExporter.buildRedactedJson(
                generatedAtMillis = System.currentTimeMillis(),
                androidSdkInt = Build.VERSION.SDK_INT,
                deviceModel = Build.MODEL.orEmpty(),
                settings = currentSettings,
                enterprisePolicy = enterprisePolicy,
                activityLogs = logs,
                transferHistory = history,
            )
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(payload) }
                ?: error("Unable to open output stream")
        }.onSuccess {
            _statusMessage.value = "Redacted diagnostic bundle exported."
        }.onFailure { err ->
            _statusMessage.value = "Diagnostic export failed: ${err.message}"
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }
}

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val policy = viewModel.enterprisePolicy
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    val currentCategory = selectedCategory?.let { name ->
        runCatching { SettingsCategory.valueOf(name) }.getOrNull()
    }

    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showPortsDialog by remember { mutableStateOf(false) }
    var showDuckDnsHostDialog by remember { mutableStateOf(false) }
    var showDuckDnsTokenDialog by remember { mutableStateOf(false) }
    val updateStatus by viewModel.updateStatus.collectAsStateWithLifecycle()
    var showReleaseNotesDialog by remember { mutableStateOf<UpdateInfo?>(null) }
    var showLicenseDialog by remember { mutableStateOf(false) }
    var showRepoEditDialog by remember { mutableStateOf(false) }

    val diagnosticExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            viewModel.exportRedactedDiagnosticBundle(context, uri)
        }
    }

    val serverRootPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            viewModel.setServerRootFolder(uri.path ?: uri.toString())
        }
    }

    val downloadDirPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            viewModel.setDownloadDirectory(uri.path ?: uri.toString())
        }
    }

    BackHandler(enabled = currentCategory != null) {
        selectedCategory = null
    }

    LaunchedEffect(statusMessage) {
        val msg = statusMessage ?: return@LaunchedEffect
        viewModel.clearStatusMessage()
        snackbarHostState.showSnackbar(msg)
    }

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = currentCategory,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "SettingsNavigation",
            modifier = Modifier.fillMaxSize(),
        ) { category ->
            if (category == null) {
                // Top-Level Categorized Settings Menu (Stable, pinned top header)
                Column(modifier = Modifier.fillMaxSize()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = "Settings",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.semantics { heading() },
                            )
                            Text(
                                text = "Preferences & System Configuration",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (policy.isManaged) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Outlined.AdminPanelSettings, contentDescription = null)
                                    Column {
                                        Text("Managed by Enterprise Policy", style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            text = "Active MDM restrictions apply to protocols, binding, and security.",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (!settings.batteryPromptDismissed) {
                        item {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(Icons.Outlined.BatteryChargingFull, contentDescription = null, tint = Color(0xFFFBBF24))
                                        Text("Background Transfer Reliability", style = MaterialTheme.typography.titleSmall)
                                    }
                                    Text(
                                        text = "Review system battery optimization if large background transfers pause when the screen is off.",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = {
                                                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                                runCatching { context.startActivity(intent) }
                                                viewModel.dismissBatteryPrompt()
                                            },
                                        ) {
                                            Text("Battery Settings")
                                        }
                                        TextButton(onClick = viewModel::dismissBatteryPrompt) {
                                            Text("Dismiss")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Category Cards
                    items(SettingsCategory.entries.size) { idx ->
                        val cat = SettingsCategory.entries[idx]
                        SettingsCategoryItem(
                            category = cat,
                            versionName = viewModel.updateManager.currentVersionName,
                            onClick = { selectedCategory = cat.name },
                        )
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        } else {
                // Category Detail Sub-Screen
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 4.dp),
                    ) {
                        IconButton(onClick = { selectedCategory = null }) {
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "Back to Settings",
                            )
                        }
                        Column {
                            Text(
                                text = category.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "Settings",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        when (category) {
                            SettingsCategory.SERVER -> {
                                item {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            Text("Server Ports & Storage", style = MaterialTheme.typography.titleMedium)
                                            SettingsActionRow(
                                                title = "Server Ports",
                                                subtitle = "FTP: ${settings.serverFtpPort} • FTPS: ${settings.serverFtpsPort} • HTTP: ${settings.serverHttpPort} • HTTPS: ${settings.serverHttpsPort}",
                                                actionText = "Change",
                                                onClick = { showPortsDialog = true },
                                            )
                                            SettingsActionRow(
                                                title = "Root Shared Folder",
                                                subtitle = settings.serverRootFolder,
                                                actionText = "Browse",
                                                onClick = { serverRootPicker.launch(null) },
                                            )
                                        }
                                    }
                                }

                                item {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            Text("Idle & Access Policies", style = MaterialTheme.typography.titleMedium)
                                            Text("Idle Timeout", style = MaterialTheme.typography.bodyMedium)
                                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                listOf(5, 10, 15, 30, 0).forEach { mins ->
                                                    FilterChip(
                                                        selected = settings.serverIdleTimeoutMinutes == mins,
                                                        onClick = { viewModel.setServerIdleTimeoutMinutes(mins) },
                                                        label = { Text(if (mins == 0) "Never" else "$mins min") },
                                                    )
                                                }
                                            }
                                            SettingsSwitchRow(
                                                title = "Auto-stop when idle",
                                                subtitle = if (settings.serverIdleTimeoutMinutes == 0) {
                                                    "Automatically turn off server when idle"
                                                } else {
                                                    "Turn off server when no sessions are connected for ${settings.serverIdleTimeoutMinutes} minutes"
                                                },
                                                checked = settings.serverAutoStopWhenIdle,
                                                onCheckedChange = viewModel::setServerAutoStopWhenIdle,
                                            )
                                            Text("Authentication Mode", style = MaterialTheme.typography.bodyMedium)
                                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                FilterChip(
                                                    selected = !settings.serverAllowAnonymousLogin,
                                                    onClick = { viewModel.setServerAllowAnonymousLogin(false) },
                                                    label = { Text("Require Login") },
                                                )
                                                FilterChip(
                                                    selected = settings.serverAllowAnonymousLogin,
                                                    onClick = { viewModel.setServerAllowAnonymousLogin(true) },
                                                    label = { Text("Allow Anonymous") },
                                                )
                                            }
                                            SettingsSwitchRow(
                                                title = "Read-Only Mode",
                                                subtitle = "Prevent clients from uploading, deleting, or modifying files",
                                                checked = settings.serverReadOnlyMode,
                                                onCheckedChange = viewModel::setServerReadOnlyMode,
                                            )
                                            SettingsSwitchRow(
                                                title = "Show Multiple Internal IPs",
                                                subtitle = "Display all available local interface IPs for server binding",
                                                checked = settings.serverShowMultipleIps,
                                                onCheckedChange = viewModel::setServerShowMultipleIps,
                                            )
                                            SettingsSwitchRow(
                                                title = "Enable SSL/TLS",
                                                subtitle = "Enable secure connections (FTPS, HTTPS)",
                                                checked = settings.serverEnableSslTls,
                                                onCheckedChange = viewModel::setServerEnableSslTls,
                                            )
                                        }
                                    }
                                }

                                item {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            Text("Web Browser Access", style = MaterialTheme.typography.titleMedium)
                                            SettingsSwitchRow(
                                                title = "Allow HTTP Web Access",
                                                subtitle = "Permit browser file management via HTTP",
                                                checked = settings.allowBrowserAccessHttp,
                                                onCheckedChange = viewModel::setAllowBrowserAccessHttp,
                                            )
                                            SettingsSwitchRow(
                                                title = "Show Hidden Files in Web",
                                                subtitle = "Display dotfiles and hidden folders in web browser",
                                                checked = settings.showHiddenFilesInWeb,
                                                onCheckedChange = viewModel::setShowHiddenFilesInWeb,
                                            )
                                        }
                                    }
                                }

                                item {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            Text("DuckDNS (Dynamic DNS)", style = MaterialTheme.typography.titleMedium)
                                            SettingsSwitchRow(
                                                title = "Enable DDNS",
                                                subtitle = "Provide a static hostname for your server",
                                                checked = settings.duckDnsEnabled,
                                                onCheckedChange = viewModel::setDuckDnsEnabled,
                                            )
                                            if (settings.duckDnsEnabled) {
                                                SettingsActionRow(
                                                    title = "Hostname",
                                                    subtitle = settings.duckDnsHostname.ifBlank { "Not configured (e.g. myserver.duckdns.org)" },
                                                    actionText = "Edit",
                                                    onClick = { showDuckDnsHostDialog = true },
                                                )
                                                SettingsActionRow(
                                                    title = "API Token",
                                                    subtitle = if (settings.duckDnsToken.isBlank()) "Not configured" else "••••••••••••••••",
                                                    actionText = "Edit",
                                                    onClick = { showDuckDnsTokenDialog = true },
                                                )
                                                OutlinedButton(
                                                    onClick = { viewModel.testDuckDns(settings.duckDnsHostname) },
                                                    modifier = Modifier.fillMaxWidth(),
                                                ) {
                                                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                                                    Spacer(Modifier.width(8.dp))
                                                    Text("Test DDNS Configuration")
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            SettingsCategory.FILE_MANAGEMENT -> {
                                item {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            Text("Download Paths", style = MaterialTheme.typography.titleMedium)
                                            SettingsSwitchRow(
                                                title = "Dynamic Download Path",
                                                subtitle = "Download files to the currently open local directory",
                                                checked = settings.dynamicDownloadPath,
                                                onCheckedChange = viewModel::setDynamicDownloadPath,
                                            )
                                            SettingsActionRow(
                                                title = "Default Download Directory",
                                                subtitle = settings.downloadDirectory,
                                                actionText = "Change",
                                                onClick = { downloadDirPicker.launch(null) },
                                            )
                                        }
                                    }
                                }

                                item {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            Text("Listing & Interface Options", style = MaterialTheme.typography.titleMedium)
                                            SettingsSwitchRow(
                                                title = "Overwrite Existing Files",
                                                subtitle = "Automatically overwrite files with the same name during transfers",
                                                checked = settings.overwriteExistingFiles,
                                                onCheckedChange = viewModel::setOverwriteExistingFiles,
                                            )
                                            SettingsSwitchRow(
                                                title = "Show Hidden Files",
                                                subtitle = "Display files and folders starting with a dot (.)",
                                                checked = settings.showHiddenFiles,
                                                onCheckedChange = viewModel::setShowHiddenFiles,
                                            )
                                            SettingsSwitchRow(
                                                title = "Show Folder Stats",
                                                subtitle = "Display item count and folder size under directory cards",
                                                checked = settings.showFolderStats,
                                                onCheckedChange = viewModel::setShowFolderStats,
                                            )
                                            SettingsSwitchRow(
                                                title = "Horizontal Dual-Pane Mode",
                                                subtitle = "Show Remote and Local panels side-by-side on wide screens",
                                                checked = settings.horizontalDualPaneMode,
                                                onCheckedChange = viewModel::setHorizontalDualPaneMode,
                                            )
                                        }
                                    }
                                }
                            }

                            SettingsCategory.TRANSFERS -> {
                                item {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            Text("Performance & Buffers", style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                text = "Max Concurrent Transfers: ${settings.defaultTransferConcurrency}",
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                            Slider(
                                                value = settings.defaultTransferConcurrency.toFloat(),
                                                onValueChange = { viewModel.setDefaultConcurrency(it.roundToInt()) },
                                                valueRange = 1f..6f,
                                                steps = 4,
                                            )
                                            Text("Transfer Buffer Size", style = MaterialTheme.typography.bodyMedium)
                                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                listOf(
                                                    0 to "Auto",
                                                    65536 to "64 KB",
                                                    262144 to "256 KB",
                                                    1048576 to "1 MB",
                                                    4194304 to "4 MB",
                                                ).forEach { (bytes, label) ->
                                                    FilterChip(
                                                        selected = settings.transferBufferSizeBytes == bytes,
                                                        onClick = { viewModel.setTransferBufferSizeBytes(bytes) },
                                                        label = { Text(label) },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                item {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            Text("Resilience & Conflict Rules", style = MaterialTheme.typography.titleMedium)
                                            Text("Connection Timeout", style = MaterialTheme.typography.bodyMedium)
                                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                listOf(15, 30, 60, 120).forEach { secs ->
                                                    FilterChip(
                                                        selected = settings.connectionTimeoutSeconds == secs,
                                                        onClick = { viewModel.setConnectionTimeoutSeconds(secs) },
                                                        label = { Text("$secs s") },
                                                    )
                                                }
                                            }
                                            Text("Retry Attempts", style = MaterialTheme.typography.bodyMedium)
                                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                listOf(0 to "Disabled", 3 to "3 attempts", 5 to "5 attempts").forEach { (attempts, label) ->
                                                    FilterChip(
                                                        selected = settings.retryAttempts == attempts,
                                                        onClick = { viewModel.setRetryAttempts(attempts) },
                                                        label = { Text(label) },
                                                    )
                                                }
                                            }
                                            Text("Default Conflict Policy", style = MaterialTheme.typography.bodyMedium)
                                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                ConflictPolicy.entries.forEach { cp ->
                                                    val label = when (cp) {
                                                        ConflictPolicy.ASK -> "Ask"
                                                        ConflictPolicy.RENAME -> "Rename"
                                                        ConflictPolicy.SKIP -> "Skip"
                                                        ConflictPolicy.OVERWRITE_ALWAYS -> "Overwrite"
                                                    }
                                                    FilterChip(
                                                        selected = settings.defaultConflictPolicy == cp,
                                                        onClick = { viewModel.setDefaultConflictPolicy(cp) },
                                                        label = { Text(label) },
                                                    )
                                                }
                                            }
                                            SettingsSwitchRow(
                                                title = "Resume Interrupted Transfers",
                                                subtitle = "Resume uploads and downloads from where they stopped",
                                                checked = settings.resumeTransfers,
                                                onCheckedChange = viewModel::setResumeTransfers,
                                            )
                                            SettingsSwitchRow(
                                                title = "Background Transfers",
                                                subtitle = "Transfers continue in background with persistent notification",
                                                checked = settings.allowBackgroundTransfers,
                                                onCheckedChange = viewModel::setAllowBackgroundTransfers,
                                            )
                                            SettingsSwitchRow(
                                                title = "Wi-Fi Only Transfers",
                                                subtitle = "Pause active remote transfers when on cellular network",
                                                checked = settings.wifiOnlyTransfers,
                                                onCheckedChange = viewModel::setWifiOnlyTransfers,
                                            )
                                            SettingsSwitchRow(
                                                title = "Verify File Checksums",
                                                subtitle = "Verify SHA-256 or MD5 hashes after transfer",
                                                checked = settings.verifyChecksums,
                                                onCheckedChange = viewModel::setVerifyChecksums,
                                            )
                                        }
                                    }
                                }
                            }

                            SettingsCategory.APPEARANCE -> {
                                item {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            Text("Theme Mode", style = MaterialTheme.typography.titleMedium)
                                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                ThemeMode.entries.forEach { mode ->
                                                    FilterChip(
                                                        selected = settings.themeMode == mode,
                                                        onClick = { viewModel.setThemeMode(mode) },
                                                        label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                                    )
                                                }
                                            }
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                                SettingsSwitchRow(
                                                    title = "Dynamic Color",
                                                    subtitle = "Adapt color palette from system wallpaper (Material You)",
                                                    checked = settings.useDynamicColor,
                                                    onCheckedChange = viewModel::setUseDynamicColor,
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            SettingsCategory.SECURITY -> {
                                item {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            Text("Access & Encryption", style = MaterialTheme.typography.titleMedium)
                                            val biometricCanEnable = viewModel.biometricAvailability == BiometricAvailability.AVAILABLE
                                            val biometricLocked = policy.requireBiometricLock && biometricCanEnable
                                            SettingsSwitchRow(
                                                title = "Biometric / Credential Lock",
                                                subtitle = if (biometricLocked) {
                                                    "Required by enterprise MDM policy"
                                                } else if (biometricCanEnable) {
                                                    "Require authentication on launch and server changes"
                                                } else {
                                                    "Unavailable (${viewModel.biometricAvailability.name.lowercase().replace('_', ' ')})"
                                                },
                                                checked = if (biometricLocked) true else settings.biometricLockEnabled,
                                                enabled = !biometricLocked && (biometricCanEnable || settings.biometricLockEnabled),
                                                onCheckedChange = viewModel::setBiometricLock,
                                            )
                                            SettingsSwitchRow(
                                                title = "Strict SSH Host Key Verification",
                                                subtitle = "Hard-fail if an SFTP server fingerprint changes",
                                                checked = settings.strictHostKeyMode == HostKeyMode.STRICT,
                                                onCheckedChange = { strict ->
                                                    viewModel.setStrictHostKeyMode(if (strict) HostKeyMode.STRICT else HostKeyMode.TOFU_WARN)
                                                },
                                            )
                                            SettingsSwitchRow(
                                                title = "SMB1 Legacy Compatibility",
                                                subtitle = if (policy.disableSmb1Legacy) {
                                                    "Disabled by enterprise MDM policy (SMB2/3 enforced)"
                                                } else {
                                                    "Insecure legacy protocol — enable only for older NAS hardware"
                                                },
                                                checked = if (policy.disableSmb1Legacy) false else settings.smbLegacyEnabled,
                                                enabled = !policy.disableSmb1Legacy,
                                                onCheckedChange = viewModel::setSmbLegacyEnabled,
                                            )
                                            SettingsSwitchRow(
                                                title = "Hide Legacy Unencrypted Servers",
                                                subtitle = if (policy.disablePlainFtp) {
                                                    "Enforced by enterprise MDM policy"
                                                } else {
                                                    "Show only encrypted FTPS (Port 2122) and HTTPS (Port 8443)"
                                                },
                                                checked = if (policy.disablePlainFtp) true else settings.hideLegacyUnencryptedServers,
                                                enabled = !policy.disablePlainFtp,
                                                onCheckedChange = viewModel::setHideLegacyUnencryptedServers,
                                            )
                                        }
                                    }
                                }
                            }

                            SettingsCategory.PRIVACY_LOGS -> {
                                item {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            Text("Audit & Diagnostics", style = MaterialTheme.typography.titleMedium)
                                            val diagLocked = policy.diagnosticLoggingOverride != null
                                            SettingsSwitchRow(
                                                title = "Diagnostic & Audit Logging",
                                                subtitle = if (diagLocked) {
                                                    "Configured by enterprise MDM policy"
                                                } else {
                                                    "Records operational events, connection states, and errors (never credentials or keys)"
                                                },
                                                checked = policy.diagnosticLoggingOverride ?: settings.diagnosticLoggingEnabled,
                                                enabled = !diagLocked,
                                                onCheckedChange = viewModel::setDiagnosticLoggingEnabled,
                                            )
                                            Text("Log Retention Window", style = MaterialTheme.typography.bodyMedium)
                                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                listOf(7, 30, 90).forEach { days ->
                                                    FilterChip(
                                                        selected = settings.logRetentionDays == days,
                                                        onClick = { viewModel.setLogRetentionDays(days) },
                                                        label = { Text("$days days") },
                                                    )
                                                }
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Button(
                                                    onClick = {
                                                        diagnosticExportLauncher.launch("portly-diagnostics-${System.currentTimeMillis()}.json")
                                                    },
                                                ) {
                                                    Icon(Icons.Outlined.FileUpload, contentDescription = null)
                                                    Spacer(Modifier.height(0.dp).padding(start = 6.dp))
                                                    Text("Export Logs")
                                                }
                                                OutlinedButton(onClick = viewModel::pruneExpiredLogs) {
                                                    Text("Prune Now")
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            SettingsCategory.ABOUT -> {
                                // Hero App Identity Card
                                item {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                        shape = RoundedCornerShape(20.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(20.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                                        ) {
                                            Image(
                                                painter = painterResource(CoreUiR.drawable.ic_portly_logo),
                                                contentDescription = "Portly Logo",
                                                modifier = Modifier
                                                    .size(64.dp)
                                                    .clip(RoundedCornerShape(16.dp)),
                                            )
                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                            ) {
                                                Text(
                                                    text = "PORTLY",
                                                    style = MaterialTheme.typography.headlineSmall,
                                                    fontWeight = FontWeight.ExtraBold,
                                                )
                                                Text(
                                                    text = "Connect. Transfer. Manage. Anywhere.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Surface(
                                                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                                        shape = RoundedCornerShape(8.dp),
                                                    ) {
                                                        Text(
                                                            text = viewModel.updateManager.currentVersionName,
                                                            style = MaterialTheme.typography.labelMedium,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                        )
                                                    }
                                                    Surface(
                                                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                                        shape = RoundedCornerShape(8.dp),
                                                    ) {
                                                        Text(
                                                            text = "UNIVERSAL",
                                                            style = MaterialTheme.typography.labelMedium,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Installed Version & Updates Card
                                item {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                        shape = RoundedCornerShape(20.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            // Installed Version
                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text(
                                                    text = "Installed Version",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                )
                                                Text(
                                                    text = "Version ${viewModel.updateManager.currentVersionName} (Build ${viewModel.updateManager.currentVersionCode})",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }

                                            // Release Channel Selector
                                            Text(
                                                text = "Release Channel",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                FilterChip(
                                                    selected = settings.updateReleaseChannel == ReleaseChannel.STABLE,
                                                    onClick = { viewModel.setUpdateReleaseChannel(ReleaseChannel.STABLE) },
                                                    label = { Text("Stable Production") },
                                                )
                                                FilterChip(
                                                    selected = settings.updateReleaseChannel == ReleaseChannel.BETA,
                                                    onClick = { viewModel.setUpdateReleaseChannel(ReleaseChannel.BETA) },
                                                    label = { Text("Beta Pre-release") },
                                                )
                                            }

                                            // Automatic Checks Toggle
                                            SettingsSwitchRow(
                                                title = "Automatic Update Checks",
                                                subtitle = "Check GitHub Releases periodically via WorkManager",
                                                checked = settings.autoCheckUpdates,
                                                onCheckedChange = viewModel::setAutoCheckUpdates,
                                            )

                                            // Update Notification Toggle
                                            SettingsSwitchRow(
                                                title = "Update Notifications",
                                                subtitle = "Show notification when a new version is published",
                                                checked = settings.notifyOnUpdate,
                                                onCheckedChange = viewModel::setNotifyOnUpdate,
                                            )

                                            // GitHub Repository
                                            SettingsActionRow(
                                                title = "GitHub Repository",
                                                subtitle = settings.customGitHubRepo,
                                                actionText = "Change",
                                                onClick = { showRepoEditDialog = true },
                                            )

                                            // Last Checked
                                            val lastCheckedText = if (settings.lastUpdateCheckTimestamp > 0) {
                                                java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
                                                    .format(java.util.Date(settings.lastUpdateCheckTimestamp))
                                            } else {
                                                "Never checked"
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                            ) {
                                                Text("Last Checked", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(lastCheckedText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }

                                            // Update Status & Action Block
                                            when (val state = updateStatus) {
                                                is UpdateStatus.Idle -> {
                                                    Button(
                                                        onClick = viewModel::checkForUpdates,
                                                        modifier = Modifier.fillMaxWidth(),
                                                    ) {
                                                        Icon(Icons.Outlined.Refresh, contentDescription = null)
                                                        Spacer(Modifier.width(8.dp))
                                                        Text("Check for Updates")
                                                    }
                                                }
                                                is UpdateStatus.Checking -> {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                        modifier = Modifier.padding(vertical = 8.dp),
                                                    ) {
                                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                                        Text("Querying GitHub Releases API...", style = MaterialTheme.typography.bodyMedium)
                                                    }
                                                }
                                                is UpdateStatus.UpToDate -> {
                                                    Column(
                                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                                        modifier = Modifier.fillMaxWidth(),
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        ) {
                                                            Icon(
                                                                Icons.Outlined.CheckCircle,
                                                                contentDescription = null,
                                                                tint = PortlyBrand.StatusActiveGreen,
                                                            )
                                                            Text(
                                                                "Portly is up to date!",
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                fontWeight = FontWeight.SemiBold,
                                                                color = PortlyBrand.StatusActiveGreen,
                                                            )
                                                        }
                                                        Button(
                                                            onClick = viewModel::checkForUpdates,
                                                            modifier = Modifier.fillMaxWidth(),
                                                        ) {
                                                            Text("Check Again")
                                                        }
                                                    }
                                                }
                                                is UpdateStatus.UpdateAvailable -> {
                                                    val update = state.updateInfo
                                                    Card(
                                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                                        shape = RoundedCornerShape(12.dp),
                                                        modifier = Modifier.fillMaxWidth(),
                                                    ) {
                                                        Column(
                                                            modifier = Modifier.padding(14.dp),
                                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                                        ) {
                                                            Text(
                                                                "PORTLY UPDATE AVAILABLE",
                                                                style = MaterialTheme.typography.labelLarge,
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                            )
                                                            Text(
                                                                "Version ${update.versionName} (${update.releaseChannel.name})",
                                                                style = MaterialTheme.typography.titleMedium,
                                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                            )
                                                            if (update.apkSizeBytes > 0) {
                                                                Text(
                                                                    "Download Size: ~${"%.1f".format(update.apkSizeBytes / (1024f * 1024f))} MB",
                                                                    style = MaterialTheme.typography.bodySmall,
                                                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                                                )
                                                            }
                                                            Row(
                                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                                modifier = Modifier.fillMaxWidth(),
                                                            ) {
                                                                Button(
                                                                    onClick = { viewModel.startDownload(update) },
                                                                    modifier = Modifier.weight(1f),
                                                                ) {
                                                                    Icon(Icons.Outlined.CloudDownload, contentDescription = null)
                                                                    Spacer(Modifier.width(6.dp))
                                                                    Text("Update Now")
                                                                }
                                                                if (update.releaseNotes.isNotBlank()) {
                                                                    OutlinedButton(onClick = { showReleaseNotesDialog = update }) {
                                                                        Text("Changelog")
                                                                    }
                                                                }
                                                            }
                                                            TextButton(
                                                                onClick = viewModel::resetUpdateStatus,
                                                                modifier = Modifier.align(Alignment.CenterHorizontally),
                                                            ) {
                                                                Text("Remind Me Later")
                                                            }
                                                        }
                                                    }
                                                }
                                                is UpdateStatus.Downloading -> {
                                                    Column(
                                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                                        modifier = Modifier.fillMaxWidth(),
                                                    ) {
                                                        Text(
                                                            "Downloading Portly v${state.updateInfo.versionName}...",
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = FontWeight.SemiBold,
                                                        )
                                                        LinearProgressIndicator(
                                                            progress = { state.progress },
                                                            modifier = Modifier.fillMaxWidth(),
                                                        )
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                        ) {
                                                            Text(
                                                                "${(state.progress * 100).toInt()}%",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.primary,
                                                            )
                                                            Text(
                                                                "${"%.1f".format(state.bytesDownloaded / (1024f * 1024f))} MB / ${"%.1f".format(state.totalBytes / (1024f * 1024f))} MB",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            )
                                                        }
                                                    }
                                                }
                                                is UpdateStatus.Verifying -> {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                        modifier = Modifier.padding(vertical = 8.dp),
                                                    ) {
                                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                                        Text(
                                                            "Verifying SHA-256 and signing certificate...",
                                                            style = MaterialTheme.typography.bodyMedium,
                                                        )
                                                    }
                                                }
                                                is UpdateStatus.ReadyToInstall -> {
                                                    Card(
                                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                                                        shape = RoundedCornerShape(12.dp),
                                                        modifier = Modifier.fillMaxWidth(),
                                                    ) {
                                                        Column(
                                                            modifier = Modifier.padding(14.dp),
                                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                                        ) {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                            ) {
                                                                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                                Text("Update Verified & Ready", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                                            }
                                                            Text(
                                                                "SHA-256 and certificate signatures verified. Ready to install Portly v${state.updateInfo.versionName}.",
                                                                style = MaterialTheme.typography.bodySmall,
                                                            )
                                                            Button(
                                                                onClick = { viewModel.installApk(state.apkPath) },
                                                                modifier = Modifier.fillMaxWidth(),
                                                            ) {
                                                                Icon(Icons.Outlined.InstallMobile, contentDescription = null)
                                                                Spacer(Modifier.width(8.dp))
                                                                Text("Install Update")
                                                            }
                                                        }
                                                    }
                                                }
                                                is UpdateStatus.PermissionRequired -> {
                                                    Card(
                                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
                                                        shape = RoundedCornerShape(12.dp),
                                                        modifier = Modifier.fillMaxWidth(),
                                                    ) {
                                                        Column(
                                                            modifier = Modifier.padding(14.dp),
                                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                                        ) {
                                                            Text("Permission Required", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                                            Text(
                                                                "Android requires permission to install APK updates from Portly.",
                                                                style = MaterialTheme.typography.bodySmall,
                                                            )
                                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                                Button(
                                                                    onClick = { context.startActivity(viewModel.getInstallPermissionIntent()) },
                                                                    modifier = Modifier.weight(1f),
                                                                ) {
                                                                    Text("Grant Permission")
                                                                }
                                                                OutlinedButton(
                                                                    onClick = { viewModel.installApk(state.apkPath) },
                                                                ) {
                                                                    Text("Retry Install")
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                                is UpdateStatus.Error -> {
                                                    Card(
                                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                                        shape = RoundedCornerShape(12.dp),
                                                        modifier = Modifier.fillMaxWidth(),
                                                    ) {
                                                        Column(
                                                            modifier = Modifier.padding(14.dp),
                                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                                        ) {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                            ) {
                                                                Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                                                                Text(
                                                                    if (state.isTampered) "Security Verification Failed"
                                                                    else if (state.isDowngrade) "Downgrade Rejected"
                                                                    else "Update Error",
                                                                    style = MaterialTheme.typography.titleSmall,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                                                )
                                                            }
                                                            Text(
                                                                state.message,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                            )
                                                            OutlinedButton(
                                                                onClick = viewModel::checkForUpdates,
                                                                modifier = Modifier.fillMaxWidth(),
                                                            ) {
                                                                Text("Retry Check")
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // App Details & Support Section Header
                                item {
                                    Text(
                                        text = "App Details & Support",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                                    )
                                }

                                item {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                        shape = RoundedCornerShape(20.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .defaultMinSize(minHeight = 44.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(text = "Application", style = MaterialTheme.typography.bodyLarge)
                                                    Text(
                                                        text = "Portly Android (Package: ${context.packageName})",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            }
                                            SettingsActionRow(
                                                title = "License & Attribution",
                                                subtitle = "GNU General Public License v3.0 (GPL-3.0-or-later)",
                                                actionText = "View",
                                                onClick = { showLicenseDialog = true },
                                            )
                                            SettingsActionRow(
                                                title = "Feedback & Bug Report",
                                                subtitle = "Send crash reports, suggestions, or feedback",
                                                actionText = "Send",
                                                onClick = {
                                                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                                                        data = Uri.parse("mailto:support@portly.app")
                                                        putExtra(
                                                            Intent.EXTRA_SUBJECT,
                                                            "Portly Feedback / Bug Report (v${viewModel.updateManager.currentVersionName})",
                                                        )
                                                        putExtra(
                                                            Intent.EXTRA_TEXT,
                                                            "Device: ${Build.MANUFACTURER} ${Build.MODEL}\nAndroid SDK: ${Build.VERSION.SDK_INT}\nApp Version: ${viewModel.updateManager.currentVersionName}\n\nIssue / Feedback:\n",
                                                        )
                                                    }
                                                    runCatching {
                                                        context.startActivity(Intent.createChooser(intent, "Send Feedback"))
                                                    }.onFailure {
                                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                        cm.setPrimaryClip(ClipData.newPlainText("Support Email", "support@portly.app"))
                                                    }
                                                },
                                            )
                                            SettingsActionRow(
                                                title = "Privacy & Audit Disclosure",
                                                subtitle = "Zero analytics SDKs, zero trackers, Keystore encryption guarantee",
                                                actionText = "View",
                                                onClick = { showPrivacyDialog = true },
                                            )
                                        }
                                    }
                                }

                                // Bottom Brand & Quick Links Card
                                item {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                        shape = RoundedCornerShape(20.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(16.dp),
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                            ) {
                                                Image(
                                                    painter = painterResource(CoreUiR.drawable.ic_portly_logo),
                                                    contentDescription = "Portly Logo",
                                                    modifier = Modifier
                                                        .size(52.dp)
                                                        .clip(RoundedCornerShape(14.dp)),
                                                )
                                                Column {
                                                    Text(
                                                        text = "Portly",
                                                        style = MaterialTheme.typography.titleLarge,
                                                        fontWeight = FontWeight.Bold,
                                                    )
                                                    Text(
                                                        text = "GNU General Public License v3.0",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            ) {
                                                FilledTonalButton(
                                                    onClick = {
                                                        val repo = settings.customGitHubRepo.ifBlank { "OffensiveKrish/Portly" }
                                                        val intent = Intent(
                                                            Intent.ACTION_VIEW,
                                                            Uri.parse("https://github.com/$repo"),
                                                        )
                                                        runCatching { context.startActivity(intent) }
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                ) {
                                                    Icon(
                                                        Icons.Outlined.Code,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp),
                                                    )
                                                    Spacer(Modifier.width(8.dp))
                                                    Text("GitHub")
                                                }
                                                FilledTonalButton(
                                                    onClick = {
                                                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                                                            data = Uri.parse("mailto:support@portly.app")
                                                            putExtra(
                                                                Intent.EXTRA_SUBJECT,
                                                                "Portly Support (v${viewModel.updateManager.currentVersionName})",
                                                            )
                                                        }
                                                        runCatching {
                                                            context.startActivity(Intent.createChooser(intent, "Send Email"))
                                                        }.onFailure {
                                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                            cm.setPrimaryClip(ClipData.newPlainText("Support Email", "support@portly.app"))
                                                        }
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                ) {
                                                    Icon(
                                                        Icons.Outlined.Email,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp),
                                                    )
                                                    Spacer(Modifier.width(8.dp))
                                                    Text("Email")
                                                }
                                            }
                                        }
                                    }
                                }

                                // Bottom Tagline
                                item {
                                    Text(
                                        text = "Connect. Transfer. Manage. Anywhere.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                    )
                                }
                            }
                        }

                        item { Spacer(modifier = Modifier.height(24.dp)) }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }

    if (showPortsDialog) {
        PortEditDialog(
            initialFtp = settings.serverFtpPort,
            initialFtps = settings.serverFtpsPort,
            initialHttp = settings.serverHttpPort,
            initialHttps = settings.serverHttpsPort,
            onConfirm = { ftp, ftps, http, https ->
                viewModel.setServerPorts(ftp, ftps, http, https)
                showPortsDialog = false
            },
            onDismiss = { showPortsDialog = false },
        )
    }

    if (showDuckDnsHostDialog) {
        TextEditDialog(
            title = "DuckDNS Hostname",
            initialValue = settings.duckDnsHostname,
            placeholder = "e.g. myserver or myserver.duckdns.org",
            onConfirm = {
                viewModel.setDuckDnsHostname(it)
                showDuckDnsHostDialog = false
            },
            onDismiss = { showDuckDnsHostDialog = false },
        )
    }

    if (showDuckDnsTokenDialog) {
        TextEditDialog(
            title = "DuckDNS API Token",
            initialValue = settings.duckDnsToken,
            placeholder = "Paste your DuckDNS token",
            isSecret = true,
            onConfirm = {
                viewModel.setDuckDnsToken(it)
                showDuckDnsTokenDialog = false
            },
            onDismiss = { showDuckDnsTokenDialog = false },
        )
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("Privacy & Security Architecture") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "• Zero third-party trackers, zero analytics SDKs, and zero background cloud sync.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = "• Passwords and private keys are hardware-encrypted with Android Keystore (AES-256-GCM) and never logged or exported.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = "• Audit logs record operational events only: server lifecycle, auth outcomes, client kicks, transfer stats, and errors.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = "• Enforces Enterprise MDM policies via Android RestrictionsManager when managed by corporate IT.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) { Text("Close") }
            },
        )
    }

    showReleaseNotesDialog?.let { update ->
        AlertDialog(
            onDismissRequest = { showReleaseNotesDialog = null },
            title = { Text("Release Notes — v${update.versionName}") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = if (update.releaseNotes.isNotBlank()) update.releaseNotes else "No release notes provided for this build.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    showReleaseNotesDialog = null
                    viewModel.startDownload(update)
                }) {
                    Text("Download & Update")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReleaseNotesDialog = null }) { Text("Close") }
            },
        )
    }

    if (showLicenseDialog) {
        AlertDialog(
            onDismissRequest = { showLicenseDialog = false },
            title = { Text("GNU General Public License v3.0") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Portly is Free & Open Source Software licensed under GNU GPL-3.0-or-later.\n\n" +
                                "• Freedom to run the program for any purpose.\n" +
                                "• Freedom to study how the program works, and change it.\n" +
                                "• Freedom to redistribute copies of the source code.\n" +
                                "• Freedom to distribute copies of your modified versions.\n\n" +
                                "Third-party components remain licensed under their respective permissive licenses (Apache-2.0, MIT, LGPL-2.1).\n\n" +
                                "Portly trademarks and brand assets are protected under GPLv3 section 7(e).",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicenseDialog = false }) { Text("Close") }
            },
        )
    }

    if (showRepoEditDialog) {
        TextEditDialog(
            title = "GitHub Repository",
            initialValue = settings.customGitHubRepo,
            placeholder = "owner/repo (e.g. OffensiveKrish/Portly)",
            onConfirm = {
                viewModel.setCustomGitHubRepo(it)
                showRepoEditDialog = false
            },
            onDismiss = { showRepoEditDialog = false },
        )
    }
}

@Composable
private fun SettingsCategoryItem(
    category: SettingsCategory,
    versionName: String = "",
    onClick: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(category.accentColor.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = category.icon,
                    contentDescription = null,
                    tint = category.accentColor,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (category == SettingsCategory.ABOUT && versionName.isNotBlank()) {
                        "Version $versionName, feedback & bug report, privacy policy"
                    } else {
                        category.subtitle
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    subtitle: String,
    actionText: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = onClick) {
            Text(actionText)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun PortEditDialog(
    initialFtp: Int,
    initialFtps: Int,
    initialHttp: Int,
    initialHttps: Int,
    onConfirm: (ftp: Int, ftps: Int, http: Int, https: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var ftpText by remember { mutableStateOf(initialFtp.toString()) }
    var ftpsText by remember { mutableStateOf(initialFtps.toString()) }
    var httpText by remember { mutableStateOf(initialHttp.toString()) }
    var httpsText by remember { mutableStateOf(initialHttps.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configure Server Ports") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = ftpText,
                    onValueChange = { ftpText = it },
                    label = { Text("FTP Port") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = ftpsText,
                    onValueChange = { ftpsText = it },
                    label = { Text("FTPS Port") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = httpText,
                    onValueChange = { httpText = it },
                    label = { Text("HTTP Port") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = httpsText,
                    onValueChange = { httpsText = it },
                    label = { Text("HTTPS Port") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val ftp = ftpText.toIntOrNull()?.coerceIn(1024, 65535) ?: initialFtp
                    val ftps = ftpsText.toIntOrNull()?.coerceIn(1024, 65535) ?: initialFtps
                    val http = httpText.toIntOrNull()?.coerceIn(1024, 65535) ?: initialHttp
                    val https = httpsText.toIntOrNull()?.coerceIn(1024, 65535) ?: initialHttps
                    onConfirm(ftp, ftps, http, https)
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

@Composable
private fun TextEditDialog(
    title: String,
    initialValue: String,
    placeholder: String,
    isSecret: Boolean = false,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(placeholder) },
                singleLine = true,
                visualTransformation = if (isSecret) PasswordVisualTransformation() else VisualTransformation.None,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(text) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
