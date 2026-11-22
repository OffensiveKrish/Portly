package com.portly.feature.transfers

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.portly.core.common.Formatters
import com.portly.core.database.dao.TransferDao
import com.portly.core.model.ConflictPolicy
import com.portly.core.model.TransferDirection
import com.portly.core.model.TransferEngine
import com.portly.core.model.TransferId
import com.portly.core.model.TransferState
import com.portly.core.model.TransferStatus
import com.portly.core.ui.components.EmptyStateView
import com.portly.core.ui.components.PortlyErrorCard
import com.portly.core.ui.components.ProtocolBadge
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TransfersViewModel @Inject constructor(
    private val transferEngine: TransferEngine,
    private val transferDao: TransferDao,
) : ViewModel() {

    val transfers: StateFlow<List<TransferState>> = transferEngine.observeTransfers().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    fun pause(id: TransferId) = transferEngine.pause(id)
    fun resume(id: TransferId) = transferEngine.resume(id)
    fun cancel(id: TransferId) = transferEngine.cancel(id)
    fun retry(id: TransferId) = transferEngine.retry(id)
    fun resolveConflict(id: TransferId, policy: ConflictPolicy) = transferEngine.resolveConflict(id, policy)
    fun clearFinished() = viewModelScope.launch { transferDao.clearFinished() }
}

@Composable
fun TransfersScreen(
    modifier: Modifier = Modifier,
    viewModel: TransfersViewModel = hiltViewModel(),
) {
    val allTransfers by viewModel.transfers.collectAsStateWithLifecycle()
    val tabs = listOf(
        TransferStatus.RUNNING to "Active",
        TransferStatus.QUEUED to "Queued",
        TransferStatus.COMPLETED to "Completed",
        TransferStatus.FAILED to "Failed",
        TransferStatus.CANCELLED to "Cancelled",
    )
    var selectedTab by remember { mutableIntStateOf(0) }
    val activeStatus = tabs[selectedTab].first
    val filtered = remember(allTransfers, activeStatus) {
        if (activeStatus == TransferStatus.RUNNING) {
            allTransfers.filter { it.status == TransferStatus.RUNNING || it.status == TransferStatus.PAUSED }
        } else {
            allTransfers.filter { it.status == activeStatus }
        }
    }

    val conflictItem = remember(allTransfers) {
        allTransfers.firstOrNull { it.awaitingConflictResolution }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Transfers",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            if (allTransfers.any {
                    it.status in setOf(TransferStatus.COMPLETED, TransferStatus.FAILED, TransferStatus.CANCELLED)
                }
            ) {
                IconButton(onClick = viewModel::clearFinished) {
                    Icon(Icons.Outlined.ClearAll, contentDescription = "Clear finished transfers")
                }
            }
        }

        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            edgePadding = 16.dp,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = { tabPositions ->
                if (selectedTab < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            divider = {},
        ) {
            tabs.forEachIndexed { index, (status, label) ->
                val count = if (status == TransferStatus.RUNNING) {
                    allTransfers.count { it.status == TransferStatus.RUNNING || it.status == TransferStatus.PAUSED }
                } else {
                    allTransfers.count { it.status == status }
                }
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = "$label ($count)",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    },
                )
            }
        }

        if (filtered.isEmpty()) {
            EmptyStateView(
                icon = Icons.Outlined.SwapVert,
                title = "No ${tabs[selectedTab].second.lowercase()} transfers",
                subtitle = "Queue uploads or downloads from the File Manager or Connections.",
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(filtered, key = { it.id.value }) { item ->
                    val dismissState = rememberSwipeToDismissBoxState()
                    SwipeToDismissBox(
                        state = dismissState,
                        backgroundContent = {},
                        onDismiss = { direction ->
                            if (direction == SwipeToDismissBoxValue.StartToEnd) {
                                when (item.status) {
                                    TransferStatus.RUNNING -> viewModel.pause(item.id)
                                    TransferStatus.PAUSED -> viewModel.resume(item.id)
                                    TransferStatus.FAILED, TransferStatus.CANCELLED -> viewModel.retry(item.id)
                                    else -> Unit
                                }
                            } else if (direction == SwipeToDismissBoxValue.EndToStart) {
                                if (item.status in setOf(TransferStatus.RUNNING, TransferStatus.QUEUED, TransferStatus.PAUSED)) {
                                    viewModel.cancel(item.id)
                                }
                            }
                        },
                    ) {
                        TransferItemCard(
                            item = item,
                            onPause = { viewModel.pause(item.id) },
                            onResume = { viewModel.resume(item.id) },
                            onCancel = { viewModel.cancel(item.id) },
                            onRetry = { viewModel.retry(item.id) },
                        )
                    }
                }
            }
        }
    }

    conflictItem?.let { item ->
        AlertDialog(
            onDismissRequest = { viewModel.pause(item.id) },
            title = { Text("File already exists") },
            text = {
                Text("\"${item.spec.fileName}\" already exists at ${item.spec.destinationPath}. Choose how to proceed:")
            },
            confirmButton = {
                Button(onClick = { viewModel.resolveConflict(item.id, ConflictPolicy.OVERWRITE_ALWAYS) }) {
                    Text("Overwrite")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { viewModel.resolveConflict(item.id, ConflictPolicy.SKIP) }) {
                        Text("Skip")
                    }
                    OutlinedButton(onClick = { viewModel.resolveConflict(item.id, ConflictPolicy.RENAME) }) {
                        Text("Rename")
                    }
                }
            },
        )
    }
}

@Composable
private fun TransferItemCard(
    item: TransferState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Direction icon
                val isDownload = item.spec.direction == TransferDirection.DOWNLOAD
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (isDownload) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isDownload) Icons.Outlined.ArrowDownward else Icons.Outlined.ArrowUpward,
                        contentDescription = item.spec.direction.name,
                        tint = if (isDownload) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = item.spec.fileName,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        item.spec.protocol?.let { ProtocolBadge(protocol = it) }
                        if (item.checksumVerified == true) {
                            Icon(
                                imageVector = Icons.Outlined.VerifiedUser,
                                contentDescription = "Checksum verified",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    Text(
                        text = "${item.spec.direction.name} • ${Formatters.formatBytes(item.progress.bytesTransferred)} / ${Formatters.formatBytes(item.progress.totalBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (item.status == TransferStatus.RUNNING) {
                        Text(
                            text = "${Formatters.formatSpeed(item.progress.speedBytesPerSec)} • ETA ${Formatters.formatEta(item.progress.etaSeconds)} • ${item.progress.percent.toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                when (item.status) {
                    TransferStatus.RUNNING -> {
                        IconButton(onClick = onPause) {
                            Icon(Icons.Outlined.Pause, contentDescription = "Pause")
                        }
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Outlined.Cancel, contentDescription = "Cancel")
                        }
                    }
                    TransferStatus.PAUSED -> {
                        IconButton(onClick = onResume) {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = "Resume")
                        }
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Outlined.Cancel, contentDescription = "Cancel")
                        }
                    }
                    TransferStatus.QUEUED -> {
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Outlined.Cancel, contentDescription = "Cancel")
                        }
                    }
                    TransferStatus.FAILED, TransferStatus.CANCELLED -> {
                        IconButton(onClick = onRetry) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Retry")
                        }
                    }
                    TransferStatus.COMPLETED -> Unit
                }
            }
            if (item.status == TransferStatus.RUNNING || item.status == TransferStatus.PAUSED) {
                LinearProgressIndicator(
                    progress = { (item.progress.percent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                )
            }
            item.error?.let { err ->
                PortlyErrorCard(error = err, onRetry = onRetry)
            }
        }
    }
}
