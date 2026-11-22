package com.portly.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.portly.core.database.entity.ActivityLogEntity
import com.portly.core.database.entity.ConnectionProfileEntity
import com.portly.core.database.entity.EncryptedSecretEntity
import com.portly.core.database.entity.FavoritePathEntity
import com.portly.core.database.entity.KnownHostEntity
import com.portly.core.database.entity.TransferEntity
import com.portly.core.database.entity.TransferHistoryEntity
import com.portly.core.database.entity.TrustedCertEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConnectionProfileDao {
    @Query("SELECT * FROM connection_profiles ORDER BY isFavorite DESC, COALESCE(lastConnectedAtMillis, createdAtMillis) DESC")
    fun observeAll(): Flow<List<ConnectionProfileEntity>>

    @Query("SELECT * FROM connection_profiles WHERE isFavorite = 1 ORDER BY name ASC")
    fun observeFavorites(): Flow<List<ConnectionProfileEntity>>

    @Query("SELECT * FROM connection_profiles WHERE id = :id")
    suspend fun getById(id: String): ConnectionProfileEntity?

    @Query("SELECT * FROM connection_profiles")
    suspend fun getAllOnce(): List<ConnectionProfileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ConnectionProfileEntity)

    @Query("UPDATE connection_profiles SET lastConnectedAtMillis = :timestamp WHERE id = :id")
    suspend fun updateLastConnected(id: String, timestamp: Long)

    @Query("UPDATE connection_profiles SET isFavorite = :favorite WHERE id = :id")
    suspend fun updateFavorite(id: String, favorite: Boolean)

    @Query("DELETE FROM connection_profiles WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface EncryptedSecretDao {
    @Query("SELECT * FROM encrypted_secrets WHERE secretRef = :secretRef")
    suspend fun getByRef(secretRef: String): EncryptedSecretEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: EncryptedSecretEntity)

    @Query("DELETE FROM encrypted_secrets WHERE secretRef = :secretRef")
    suspend fun deleteByRef(secretRef: String)
}

@Dao
interface TransferDao {
    @Query("SELECT * FROM transfers ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<TransferEntity>>

    @Query("SELECT * FROM transfers WHERE id = :id")
    fun observeById(id: String): Flow<TransferEntity?>

    @Query("SELECT * FROM transfers WHERE id = :id")
    suspend fun getById(id: String): TransferEntity?

    @Query("SELECT * FROM transfers WHERE status IN ('QUEUED', 'RUNNING')")
    suspend fun getActiveOrQueued(): List<TransferEntity>

    @Query("SELECT * FROM transfers WHERE status = 'PAUSED' AND pausedByNetworkLoss = 1")
    suspend fun getNetworkPaused(): List<TransferEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TransferEntity)

    @Query("UPDATE transfers SET status = :status, pausedByNetworkLoss = :pausedByNetworkLoss, updatedAtMillis = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, pausedByNetworkLoss: Boolean, updatedAt: Long)

    @Query("DELETE FROM transfers WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM transfers WHERE status IN ('COMPLETED', 'CANCELLED', 'FAILED')")
    suspend fun clearFinished()
}

@Dao
interface TransferHistoryDao {
    @Query("SELECT * FROM transfer_history ORDER BY finishedAtMillis DESC")
    fun observeAll(): Flow<List<TransferHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TransferHistoryEntity)

    @Query("DELETE FROM transfer_history")
    suspend fun clearAll()

    @Query("DELETE FROM transfer_history WHERE finishedAtMillis < :cutoffMillis")
    suspend fun pruneOlderThan(cutoffMillis: Long)
}

@Dao
interface ActivityLogDao {
    @Query("SELECT * FROM activity_logs ORDER BY timestampMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int = 500): Flow<List<ActivityLogEntity>>

    @Insert
    suspend fun insert(entity: ActivityLogEntity)

    @Query("DELETE FROM activity_logs")
    suspend fun clearAll()

    @Query("DELETE FROM activity_logs WHERE timestampMillis < :cutoffMillis")
    suspend fun pruneOlderThan(cutoffMillis: Long)
}

@Dao
interface KnownHostDao {
    @Query("SELECT * FROM known_hosts ORDER BY lastVerifiedAtMillis DESC")
    fun observeAll(): Flow<List<KnownHostEntity>>

    @Query("SELECT * FROM known_hosts WHERE host = :host AND port = :port")
    suspend fun get(host: String, port: Int): KnownHostEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: KnownHostEntity)

    @Query("DELETE FROM known_hosts WHERE host = :host AND port = :port")
    suspend fun delete(host: String, port: Int)
}

@Dao
interface TrustedCertDao {
    @Query("SELECT * FROM trusted_certs ORDER BY trustedAtMillis DESC")
    fun observeAll(): Flow<List<TrustedCertEntity>>

    @Query("SELECT * FROM trusted_certs WHERE host = :host AND port = :port")
    suspend fun getForHost(host: String, port: Int): List<TrustedCertEntity>

    @Query("SELECT * FROM trusted_certs WHERE isUserCa = 1")
    suspend fun getUserCas(): List<TrustedCertEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TrustedCertEntity)

    @Query("DELETE FROM trusted_certs WHERE host = :host AND port = :port AND sha256Fingerprint = :fingerprint")
    suspend fun delete(host: String, port: Int, fingerprint: String)
}

@Dao
interface FavoritePathDao {
    @Query("SELECT * FROM favorite_paths ORDER BY addedAtMillis DESC")
    fun observeAll(): Flow<List<FavoritePathEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FavoritePathEntity)

    @Query("DELETE FROM favorite_paths WHERE path = :path")
    suspend fun delete(path: String)
}
