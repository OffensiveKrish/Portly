package com.portly.core.database

import android.content.Context
import androidx.room.Room
import com.portly.core.database.dao.ActivityLogDao
import com.portly.core.database.dao.ConnectionProfileDao
import com.portly.core.database.dao.EncryptedSecretDao
import com.portly.core.database.dao.FavoritePathDao
import com.portly.core.database.dao.KnownHostDao
import com.portly.core.database.dao.TransferDao
import com.portly.core.database.dao.TransferHistoryDao
import com.portly.core.database.dao.TrustedCertDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun providePortlyDatabase(
        @ApplicationContext context: Context,
    ): PortlyDatabase = Room.databaseBuilder(
        context,
        PortlyDatabase::class.java,
        "portly.db",
    ).fallbackToDestructiveMigration(dropAllTables = false).build()

    @Provides
    fun provideConnectionProfileDao(db: PortlyDatabase): ConnectionProfileDao = db.connectionProfileDao()

    @Provides
    fun provideEncryptedSecretDao(db: PortlyDatabase): EncryptedSecretDao = db.encryptedSecretDao()

    @Provides
    fun provideTransferDao(db: PortlyDatabase): TransferDao = db.transferDao()

    @Provides
    fun provideTransferHistoryDao(db: PortlyDatabase): TransferHistoryDao = db.transferHistoryDao()

    @Provides
    fun provideActivityLogDao(db: PortlyDatabase): ActivityLogDao = db.activityLogDao()

    @Provides
    fun provideKnownHostDao(db: PortlyDatabase): KnownHostDao = db.knownHostDao()

    @Provides
    fun provideTrustedCertDao(db: PortlyDatabase): TrustedCertDao = db.trustedCertDao()

    @Provides
    fun provideFavoritePathDao(db: PortlyDatabase): FavoritePathDao = db.favoritePathDao()
}
