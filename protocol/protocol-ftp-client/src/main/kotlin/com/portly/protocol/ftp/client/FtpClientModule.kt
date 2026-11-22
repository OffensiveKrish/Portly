package com.portly.protocol.ftp.client

import com.portly.core.common.ProtocolClientKey
import com.portly.core.model.ProtocolClientFactory
import com.portly.core.model.ProtocolType
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap

@Module
@InstallIn(SingletonComponent::class)
object FtpClientModule {

    @Provides
    @IntoMap
    @ProtocolClientKey(ProtocolType.FTP)
    fun provideFtpClientFactory(): ProtocolClientFactory = ProtocolClientFactory {
        CommonsFtpProtocolClient(ProtocolType.FTP)
    }
}
