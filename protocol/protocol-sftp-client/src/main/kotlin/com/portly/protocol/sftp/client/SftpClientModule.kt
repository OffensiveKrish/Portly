package com.portly.protocol.sftp.client

import com.portly.core.common.ProtocolClientKey
import com.portly.core.database.dao.KnownHostDao
import com.portly.core.datastore.PortlySettingsDataStore
import com.portly.core.model.ProtocolClientFactory
import com.portly.core.model.ProtocolType
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap

@Module
@InstallIn(SingletonComponent::class)
object SftpClientModule {

    @Provides
    @IntoMap
    @ProtocolClientKey(ProtocolType.SFTP)
    fun provideSftpClientFactory(
        knownHostDao: KnownHostDao,
        settingsDataStore: PortlySettingsDataStore,
    ): ProtocolClientFactory = ProtocolClientFactory {
        SshjSftpProtocolClient(RoomHostKeyStore(knownHostDao, settingsDataStore))
    }
}
