package com.portly.protocol.ftps.server

import android.content.Context
import com.portly.core.common.ProtocolServerKey
import com.portly.core.database.CredentialVault
import com.portly.core.model.ProtocolServerFactory
import com.portly.core.model.ServerType
import com.portly.core.security.CertificateManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap

@Module
@InstallIn(SingletonComponent::class)
object FtpsServerModule {

    @Provides
    @IntoMap
    @ProtocolServerKey(ServerType.FTPS)
    fun provideFtpsServerFactory(
        @ApplicationContext context: Context,
        credentialVault: CredentialVault,
        certificateManager: CertificateManager,
    ): ProtocolServerFactory = ProtocolServerFactory {
        ApacheFtpsProtocolServer(
            context = context,
            credentialVault = credentialVault,
            certificateManager = certificateManager,
        )
    }
}
