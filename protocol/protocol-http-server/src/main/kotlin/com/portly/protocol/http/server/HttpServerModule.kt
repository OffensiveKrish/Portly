package com.portly.protocol.http.server

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
object HttpServerModule {

    @Provides
    @IntoMap
    @ProtocolServerKey(ServerType.HTTP)
    fun provideHttpServerFactory(
        @ApplicationContext context: Context,
        credentialVault: CredentialVault,
    ): ProtocolServerFactory = ProtocolServerFactory {
        KtorHttpProtocolServer(context = context, credentialVault = credentialVault)
    }
}
