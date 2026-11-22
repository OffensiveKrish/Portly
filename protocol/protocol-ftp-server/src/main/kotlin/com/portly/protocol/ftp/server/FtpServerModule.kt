package com.portly.protocol.ftp.server

import android.content.Context
import com.portly.core.common.ProtocolServerKey
import com.portly.core.database.CredentialVault
import com.portly.core.model.ProtocolServerFactory
import com.portly.core.model.ServerType
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap

@Module
@InstallIn(SingletonComponent::class)
object FtpServerModule {

    @Provides
    @IntoMap
    @ProtocolServerKey(ServerType.FTP)
    fun provideFtpServerFactory(
        @ApplicationContext context: Context,
        credentialVault: CredentialVault,
    ): ProtocolServerFactory = ProtocolServerFactory {
        ApacheFtpProtocolServer(context = context, credentialVault = credentialVault)
    }
}
