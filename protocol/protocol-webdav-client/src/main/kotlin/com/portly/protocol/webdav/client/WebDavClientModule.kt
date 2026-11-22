package com.portly.protocol.webdav.client

import com.portly.core.common.ProtocolClientKey
import com.portly.core.database.dao.TrustedCertDao
import com.portly.core.model.ProtocolClientFactory
import com.portly.core.model.ProtocolType
import com.portly.core.security.CertificateManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap

@Module
@InstallIn(SingletonComponent::class)
object WebDavClientModule {

    @Provides
    @IntoMap
    @ProtocolClientKey(ProtocolType.WEBDAV)
    fun provideWebDavClientFactory(
        trustedCertDao: TrustedCertDao,
        certificateManager: CertificateManager,
    ): ProtocolClientFactory = ProtocolClientFactory {
        OkHttpWebDavProtocolClient(
            certStore = RoomWebDavCertStore(trustedCertDao),
            certificateManager = certificateManager,
        )
    }
}
