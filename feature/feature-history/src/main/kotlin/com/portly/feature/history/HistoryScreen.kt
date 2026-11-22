package com.portly.feature.history

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.portly.core.common.Formatters
import com.portly.core.database.dao.ActivityLogDao
import com.portly.core.database.dao.TransferHistoryDao
import com.portly.core.database.toDomain
import com.portly.core.datastore.PortlySettingsDataStore
import com.portly.core.model.ActivityLogEntry
import com.portly.core.model.TransferHistoryEntry
import com.portly.core.security.LogRedactor
import com.portly.core.ui.components.EmptyStateView
import com.portly.core.ui.components.PortlyConfirmDialog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryUiState(
    val transfers: List<TransferHistoryEntry> = emptyList(),
    val logs: List<ActivityLogEntry> = emptyList(),
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyDao: TransferHistoryDao,
    private val activityLogDao: ActivityLogDao,
    private val settingsStore: PortlySettingsDataStore,
) : ViewModel() {

    init {
        viewModelScope.launch {
            val retentionDays = settingsStore.settings.first().logRetentionDays.coerceIn(1, 365)
            val cutoff = System.currentTimeMillis() - retentionDays * 86_400_000L
            activityLogDao.pruneOlderThan(cutoff)
            historyDao.pruneOlderThan(cutoff)
        }
    }

    val uiState: StateFlow<HistoryUiState> = combine(
        historyDao.observeAll(),
        activityLogDao.observeRecent(),
    ) { transfers, logs ->
        HistoryUiState(
            transfers = transfers.map { it.toDomain() },
            logs = logs.map { it.toDomain() },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HistoryUiState(),
    )

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun clearHistory() = viewModelScope.launch { historyDao.clearAll() }
    fun clearLogs() = viewModelScope.launch { activityLogDao.clearAll() }
    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun exportRedactedHistory(context: Context, uri: Uri) = viewModelScope.launch {
        runCatching {
            val state = uiState.value
            val sb = StringBuilder()
            sb.appendLine("{")
            sb.appendLine("""  "schema": "portly-history-export-v1",""")
            sb.appendLine("""  "exportedAtMillis": ${System.currentTimeMillis()},""")
            sb.appendLine("""  "transfers": [""")
            state.transfers.forEachIndexed { idx, item ->
                val comma = if (idx + 1 < state.transfers.size) "," else ""
                sb.appendLine(
                    """    {"fileName": "${esc(item.fileName)}", "protocol": "${item.protocol?.name.orEmpty()}", "status": "${item.status.name}", "bytesTransferred": ${item.bytesTransferred}, "checksumVerified": ${item.checksumVerified}, "finishedAtMillis": ${item.finishedAtMillis}}$comma""",
                )
            }
            sb.appendLine("""  ],""")
            sb.appendLine("""  "activityLogs": [""")
            state.logs.forEachIndexed { idx, entry ->
                val comma = if (idx + 1 < state.logs.size) "," else ""
                val msg = LogRedactor.redact(entry.message)
                sb.appendLine(
                    """    {"timestampMillis": ${entry.timestampMillis}, "severity": "${entry.severity.name}", "category": "${esc(entry.category)}", "message": "${esc(msg)}"}$comma""",
                )
            }
            sb.appendLine("""  ]""")
            sb.appendLine("}")
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(sb.toString()) }
                ?: error("Unable to open output stream")
        }.onSuccess {
            _statusMessage.value = "Redacted history exported."
        }.onFailure { err ->
            _statusMessage.value = "Export failed: ${err.message}"
        }
    }

    private fun esc(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
}

@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var showClearConfirm by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            viewModel.exportRedactedHistory(context, uri)
        }
    }

    LaunchedEffect(statusMessage) {
        val msg = statusMessage ?: return@LaunchedEffect
        viewModel.clearStatusMessage()
        snackbarHostState.showSnackbar(msg)
    }

    val filteredTransfers = remember(state.transfers, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) {
            state.transfers
        } else {
            state.transfers.filter {
                it.fileName.lowercase().contains(q) ||
                    it.status.name.lowercase().contains(q) ||
                    (it.profileName?.lowercase()?.contains(q) == true)
            }
        }
    }

    val filteredLogs = remember(state.logs, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) {
            state.logs
        } else {
            state.logs.filter {
                it.category.lowercase().contains(q) ||
                    it.message.lowercase().contains(q) ||
                    it.severity.name.lowercase().contains(q)
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "History & Logs",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
                Row {
                    IconButton(
                        onClick = {
                            exportLauncher.launch("portly-history-${System.currentTimeMillis()}.json")
                        },
                    ) {
                        Icon(Icons.Outlined.FileUpload, contentDescription = "Export redacted history")
                    }
                    if ((selectedTab == 0 && state.transfers.isNotEmpty()) ||
                        (selectedTab == 1 && state.logs.isNotEmpty())
                    ) {
                        IconButton(onClick = { showClearConfirm = true }) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Clear history")
                        }
                    }
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Filter history & activity logs...") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            )

            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Transfers (${filteredTransfers.size})") },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Activity (${filteredLogs.size})") },
                )
            }

            if (selectedTab == 0) {
                if (filteredTransfers.isEmpty()) {
                    EmptyStateView(
                        icon = Icons.Outlined.History,
                        title = if (searchQuery.isBlank()) "No transfer history yet" else "No matching transfers",
                        subtitle = "Completed, failed, and cancelled transfers will appear here.",
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(filteredTransfers, key = { it.id }) { item ->
                            val isSuccess = item.status.name == "COMPLETED"
                            val isFailed = item.status.name == "FAILED"
                            val statusBg = when {
                                isSuccess -> Color(0xFF064E3B)
                                isFailed -> MaterialTheme.colorScheme.errorContainer
                                else -> MaterialTheme.colorScheme.surfaceContainerHighest
                            }
                            val statusColor = when {
                                isSuccess -> Color(0xFF34D399)
                                isFailed -> MaterialTheme.colorScheme.onErrorContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }

                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(statusColor.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = if (isSuccess) Icons.Outlined.CheckCircle else if (isFailed) Icons.Outlined.ErrorOutline else Icons.Outlined.History,
                                            contentDescription = null,
                                            tint = statusColor,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Text(
                                                text = item.fileName,
                                                style = MaterialTheme.typography.titleSmall,
                                                modifier = Modifier.weight(1f, fill = false),
                                            )
                                            Surface(
                                                color = statusBg,
                                                shape = CircleShape,
                                            ) {
                                                Text(
                                                    text = item.status.name,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = statusColor,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            "${Formatters.formatBytes(item.bytesTransferred)} / ${Formatters.formatBytes(item.totalBytes)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            Formatters.formatTimestamp(item.finishedAtMillis),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                if (filteredLogs.isEmpty()) {
                    EmptyStateView(
                        icon = Icons.Outlined.History,
                        title = if (searchQuery.isBlank()) "No activity entries yet" else "No matching logs",
                        subtitle = "Connection, server, and security events will be logged here.",
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(filteredLogs, key = { it.id }) { entry ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                            shape = RoundedCornerShape(6.dp),
                                        ) {
                                            Text(
                                                text = "${entry.severity} • ${entry.category}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            )
                                        }
                                        Text(
                                            text = Formatters.formatTimestamp(entry.timestampMillis),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text(
                                        text = entry.message,
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

    if (showClearConfirm) {
        PortlyConfirmDialog(
            title = if (selectedTab == 0) "Clear transfer history?" else "Clear activity log?",
            consequenceText = "This permanently removes all recorded entries in this tab.",
            confirmLabel = "Clear",
            onConfirm = {
                if (selectedTab == 0) viewModel.clearHistory() else viewModel.clearLogs()
                showClearConfirm = false
            },
            onDismiss = { showClearConfirm = false },
        )
    }
}
