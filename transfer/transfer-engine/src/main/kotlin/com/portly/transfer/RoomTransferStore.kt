package com.portly.transfer

import com.portly.core.database.CredentialVault
import com.portly.core.database.dao.ActivityLogDao
import com.portly.core.database.dao.ConnectionProfileDao
import com.portly.core.database.dao.TransferDao
import com.portly.core.database.dao.TransferHistoryDao
import com.portly.core.database.entity.ActivityLogEntity
import com.portly.core.database.toDomain
import com.portly.core.database.toEntity
import com.portly.core.model.ActivityLogEntry
import com.portly.core.model.ConnectionProfile
import com.portly.core.model.ResolvedCredentials
import com.portly.core.model.TransferHistoryEntry
import com.portly.core.model.TransferId
import com.portly.core.model.TransferState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomTransferStore @Inject constructor(
    private val transferDao: TransferDao,
    private val historyDao: TransferHistoryDao,
    private val activityLogDao: ActivityLogDao,
    private val profileDao: ConnectionProfileDao,
    private val credentialVault: CredentialVault,
) : TransferStore {

    override fun observeAll(): Flow<List<TransferState>> =
        transferDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeById(id: TransferId): Flow<TransferState?> =
        transferDao.observeById(id.value).map { it?.toDomain() }

    override suspend fun getById(id: TransferId): TransferState? =
        transferDao.getById(id.value)?.toDomain()

    override suspend fun getActiveOrQueued(): List<TransferState> =
        transferDao.getActiveOrQueued().map { it.toDomain() }

    override suspend fun getNetworkPaused(): List<TransferState> =
        transferDao.getNetworkPaused().map { it.toDomain() }

    override suspend fun upsert(state: TransferState, pausedByNetworkLoss: Boolean) {
        transferDao.upsert(state.toEntity(pausedByNetworkLoss = pausedByNetworkLoss))
    }

    override suspend fun recordHistory(entry: TransferHistoryEntry) {
        historyDao.insert(entry.toEntity())
    }

    override suspend fun logActivity(entry: ActivityLogEntry) {
        activityLogDao.insert(
            ActivityLogEntity(
                timestampMillis = entry.timestampMillis,
                category = entry.category,
                severity = entry.severity.name,
                message = entry.message,
                detail = entry.detail,
            ),
        )
    }

    override suspend fun loadConnection(profileId: String): Pair<ConnectionProfile, ResolvedCredentials>? {
        val profile = profileDao.getById(profileId)?.toDomain() ?: return null
        val creds = credentialVault.loadCredentials(profile.authSecretRef, profile.username)
        return profile to creds
    }
}
