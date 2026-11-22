package com.portly.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.portly.core.database.dao.ActivityLogDao
import com.portly.core.database.dao.ConnectionProfileDao
import com.portly.core.database.dao.EncryptedSecretDao
import com.portly.core.database.dao.FavoritePathDao
import com.portly.core.database.dao.KnownHostDao
import com.portly.core.database.dao.TransferDao
import com.portly.core.database.dao.TransferHistoryDao
import com.portly.core.database.dao.TrustedCertDao
import com.portly.core.database.entity.ActivityLogEntity
import com.portly.core.database.entity.ConnectionProfileEntity
import com.portly.core.database.entity.EncryptedSecretEntity
import com.portly.core.database.entity.FavoritePathEntity
import com.portly.core.database.entity.KnownHostEntity
import com.portly.core.database.entity.TransferEntity
import com.portly.core.database.entity.TransferHistoryEntity
import com.portly.core.database.entity.TrustedCertEntity

@Database(
    entities = [
        ConnectionProfileEntity::class,
        EncryptedSecretEntity::class,
        TransferEntity::class,
        TransferHistoryEntity::class,
        ActivityLogEntity::class,
        KnownHostEntity::class,
        TrustedCertEntity::class,
        FavoritePathEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class PortlyDatabase : RoomDatabase() {
    abstract fun connectionProfileDao(): ConnectionProfileDao
    abstract fun encryptedSecretDao(): EncryptedSecretDao
    abstract fun transferDao(): TransferDao
    abstract fun transferHistoryDao(): TransferHistoryDao
    abstract fun activityLogDao(): ActivityLogDao
    abstract fun knownHostDao(): KnownHostDao
    abstract fun trustedCertDao(): TrustedCertDao
    abstract fun favoritePathDao(): FavoritePathDao
}
