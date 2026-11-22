package com.portly.protocol.smb.client

import com.portly.core.common.ProtocolClientKey
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
object SmbClientModule {

    @Provides
    @IntoMap
    @ProtocolClientKey(ProtocolType.SMB)
    fun provideSmbClientFactory(
        settingsDataStore: PortlySettingsDataStore,
    ): ProtocolClientFactory = ProtocolClientFactory {
        JcifsSmbProtocolClient(settingsDataStore = settingsDataStore)
    }
}
