package com.portly.transfer

import com.portly.core.model.ActivityLogEntry
import com.portly.core.model.ConnectionProfile
import com.portly.core.model.ResolvedCredentials
import com.portly.core.model.TransferHistoryEntry
import com.portly.core.model.TransferId
import com.portly.core.model.TransferState
import kotlinx.coroutines.flow.Flow

interface TransferStore {
    fun observeAll(): Flow<List<TransferState>>
    fun observeById(id: TransferId): Flow<TransferState?>
    suspend fun getById(id: TransferId): TransferState?
    suspend fun getActiveOrQueued(): List<TransferState>
    suspend fun getNetworkPaused(): List<TransferState>
    suspend fun upsert(state: TransferState, pausedByNetworkLoss: Boolean = false)
    suspend fun recordHistory(entry: TransferHistoryEntry)
    suspend fun logActivity(entry: ActivityLogEntry)
    suspend fun loadConnection(profileId: String): Pair<ConnectionProfile, ResolvedCredentials>?
}
